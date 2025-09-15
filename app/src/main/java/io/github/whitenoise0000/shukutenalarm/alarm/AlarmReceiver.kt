package io.github.whitenoise0000.shukutenalarm.alarm

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.github.whitenoise0000.shukutenalarm.data.DataStoreAlarmRepository
import io.github.whitenoise0000.shukutenalarm.data.PreferencesKeys
import io.github.whitenoise0000.shukutenalarm.data.appDataStore
import io.github.whitenoise0000.shukutenalarm.data.model.WeatherCategory
import io.github.whitenoise0000.shukutenalarm.data.model.RepeatType
import io.github.whitenoise0000.shukutenalarm.holiday.HolidayRepository
import io.github.whitenoise0000.shukutenalarm.platform.Notifications
import io.github.whitenoise0000.shukutenalarm.settings.SettingsRepository
import io.github.whitenoise0000.shukutenalarm.ui.RingingActivity
import io.github.whitenoise0000.shukutenalarm.ui.getLabel
import io.github.whitenoise0000.shukutenalarm.widget.NextAlarmWidgetProvider
import io.github.whitenoise0000.shukutenalarm.ui.normalizeWeatherTextForDisplay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import java.time.LocalDate

/**
 * アラーム発火時の BroadcastReceiver。
 * - 祝日ポリシーの判定、鳴動画面の起動、次回スケジュールを行う。
 * - 追加仕様: 鳴動時に天気未取得なら、最大10秒だけ再取得を試みる（取得できなくても即時フォールバックで鳴動）。
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmGateway.ACTION_ALARM_FIRE) return
        val id = intent.getIntExtra(AlarmGateway.EXTRA_ALARM_ID, -1)
        if (id == -1) return

        // BroadcastReceiver の長時間ブロックを避けるため、goAsync + コルーチンで非同期処理へ
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val repo = DataStoreAlarmRepository(context)
                val spec = withContext(Dispatchers.IO) { repo.load(id) } ?: return@launch

                // 祝日判定
                val holidayRepo = HolidayRepository(context)
                val today = LocalDate.now()
                val isHolidayToday = holidayRepo.isHoliday(today)
                val holidayName = holidayRepo.nameOrNull(today)

                // 祝日 SKIP の場合は鳴らさず次回のみ登録
                if (!SoundSelector.shouldRing(spec, isHolidayToday)) {
                    ScheduleManager(context).scheduleNext(spec)
                    // ウィジェットへ更新通知（次回が変わる可能性があるため）
                    context.sendBroadcast(
                        Intent(
                            context,
                            NextAlarmWidgetProvider::class.java
                        ).setAction(NextAlarmWidgetProvider.ACTION_REFRESH)
                    )
                    return@launch
                }

                // キャッシュされた天気（カテゴリ＋JMA文言）→ 未取得なら最大10秒で再取得
                val cached = readCachedWeatherSnapshot(context)
                Log.d("AlarmReceiver", "Cached weather snapshot: $cached")
                val weather = cached ?: runCatching {
                    Log.d("AlarmReceiver", "Attempting to fetch weather with timeout...")
                    fetchWeatherWithTimeout(
                        context,
                        timeoutMillis = 10_000
                    )
                }.getOrNull()
                Log.d("AlarmReceiver", "Final weather snapshot after fetch attempt: $weather")

                // 鳴動画面へ渡す情報
                val activityIntent = Intent(context, RingingActivity::class.java).apply {
                    // Intent にユニークな Data URI を設定して、PendingIntent のエクストラが確実に更新されるようにする
                    data = "intent://${context.packageName}/alarm/$id/${System.currentTimeMillis()}".toUri()
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT)
                    putExtra("id", id)
                    putExtra(
                        "soundUri",
                        SoundSelector.selectSound(spec, weather?.category) {
                            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                                ?: Settings.System.DEFAULT_ALARM_ALERT_URI
                        }.toString()
                    )
                    // 天気ラベルは「気象庁発表文言をそのまま優先」。無い場合はカテゴリのローカライズ文字列でフォールバック。
                    val weatherLabel = normalizeWeatherTextForDisplay(
                        weather?.text?.ifBlank { null }
                            ?: weather?.category?.getLabel(context)
                            ?: ""
                    )
                    Log.d("AlarmReceiver", "Weather label being put into intent: $weatherLabel")
                    putExtra("weatherLabel", weatherLabel)
                    putExtra("isHoliday", isHolidayToday)
                    putExtra("holidayName", holidayName ?: "")
                    putExtra("volumeMode", spec.volumeMode.name)
                    putExtra("volumePercent", spec.volumePercent)
                    putExtra("vibrate", spec.vibrate)
                    putExtra("respectSilent", spec.respectSilentMode)
                    putExtra("alarmName", spec.name)
                }

                val canFsi = context.getSystemService(NotificationManager::class.java)
                    ?.canUseFullScreenIntent() == true
                val hasPost = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (canFsi && hasPost) {
                    // ★ 常にFSIで鳴動させる（通知チャンネル: IMPORTANCE_HIGH, CATEGORY_ALARM 必須）
                    Notifications.showAlarmFullScreen(context, id, activityIntent)
                } else {
                    // ユーザーがFSIを無効化しているケース → 設定へ誘導
                    Notifications.openFullScreenIntentSettings(context)
                    // FSI無効の場合は通常通知に格下げされる
                    Notifications.showAlarmFullScreen(context, id, activityIntent)
                }


                // 次回をスケジュール（ONE_SHOT は鳴動後に自動無効化するため再登録しない）
                if (spec.repeatType == RepeatType.ONE_SHOT) {
                    // 1回のみ: 鳴動後は無効化して保存（一覧に残し、再起動時も再登録されない）
                    withContext(Dispatchers.IO) {
                        repo.save(spec.copy(enabled = false))
                    }
                } else {
                    ScheduleManager(context).scheduleNext(spec)
                }
                // ウィジェットへ更新通知
                context.sendBroadcast(
                    Intent(
                        context,
                        NextAlarmWidgetProvider::class.java
                    ).setAction(NextAlarmWidgetProvider.ACTION_REFRESH)
                )
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * DataStore のキャッシュから天気スナップショット（カテゴリ＋JMA文言）を読み出す。
     * - 旧バージョンのJSON（categoryのみ）も後方互換で読み出す。
     */
    private fun readCachedWeatherSnapshot(context: Context): io.github.whitenoise0000.shukutenalarm.weather.WeatherSnapshot? = runBlocking {
        val key = stringPreferencesKey(PreferencesKeys.KEY_LAST_WEATHER_JSON)
        val text = context.appDataStore.data.map { prefs -> prefs[key] }.first()
        text?.let {
            runCatching {
                val obj = JSONObject(it)
                val catName = obj.optString("category", "").trim()
                val textLabel = obj.optString("text", "").trim().ifBlank { null }
                val cat = catName.ifBlank {
                    Log.d("AlarmReceiver", "readCachedWeatherSnapshot: category is blank.")
                    null
                }?.let { name ->
                    runCatching { WeatherCategory.valueOf(name) }.getOrNull()
                }
                Log.d("AlarmReceiver", "readCachedWeatherSnapshot: parsed = cat=$cat, text=$textLabel")
                io.github.whitenoise0000.shukutenalarm.weather.WeatherSnapshot(cat, textLabel)
            }.getOrNull()
        }
    }

    /**
     * 天気を最大 [timeoutMillis] ミリ秒で再取得する。
     * - 設定（緯度/経度、現在地使用可否）に従って座標を決定（権限あれば最終既知位置を優先）。
     * - 成功時は DataStore にキャッシュ保存（WeatherRepository.prefetchToday）。
     * - 失敗・タイムアウト時は null。
     */
    private suspend fun fetchWeatherWithTimeout(
        context: Context,
        timeoutMillis: Long
    ): io.github.whitenoise0000.shukutenalarm.weather.WeatherSnapshot? = withTimeout(timeoutMillis) {
        Log.d("AlarmReceiver", "fetchWeatherWithTimeout: Entering withTimeout block.")
        val result = withContext(Dispatchers.IO) {
            val settings = SettingsRepository(context).settingsFlow.first()
            val (lat, lon) = if (settings.useCurrentLocation) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    val lm = context.getSystemService(android.location.LocationManager::class.java)
                    val providers = listOf(
                        android.location.LocationManager.NETWORK_PROVIDER,
                        android.location.LocationManager.PASSIVE_PROVIDER,
                        android.location.LocationManager.GPS_PROVIDER
                    )
                    var out = settings.latitude to settings.longitude
                    providers.forEach { p ->
                        val loc = runCatching { lm?.getLastKnownLocation(p) }.getOrNull()
                        if (loc != null) {
                            out = loc.latitude to loc.longitude; return@forEach
                        }
                    }
                    out
                } else settings.latitude to settings.longitude
            } else settings.latitude to settings.longitude

            val json = Json { ignoreUnknownKeys = true }
            val client = OkHttpClient.Builder()
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                })
                .addInterceptor(io.github.whitenoise0000.shukutenalarm.network.EtagCacheInterceptor(context))
                .build()
            val jmaRetrofit = Retrofit.Builder()
                .baseUrl("https://www.jma.go.jp/")
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            val gsiRetrofit = Retrofit.Builder()
                .baseUrl("https://mreversegeocoder.gsi.go.jp/")
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            val forecastApi = jmaRetrofit.create(io.github.whitenoise0000.shukutenalarm.weather.jma.JmaForecastApi::class.java)
            val gsiApi = gsiRetrofit.create(io.github.whitenoise0000.shukutenalarm.weather.jma.GsiApi::class.java)
            val constApi = jmaRetrofit.create(io.github.whitenoise0000.shukutenalarm.weather.jma.JmaConstApi::class.java)
            val areaRepo = io.github.whitenoise0000.shukutenalarm.weather.jma.AreaRepository(context, constApi)
            val telopsRepo = io.github.whitenoise0000.shukutenalarm.weather.jma.TelopsRepository(context)
            val repo = io.github.whitenoise0000.shukutenalarm.weather.WeatherRepository(context, forecastApi, gsiApi, areaRepo, telopsRepo)
            val fetched = if (settings.useCurrentLocation) {
                repo.prefetchByCurrentLocation(lat, lon)
            } else {
                val office = settings.selectedOffice
                if (office.isNullOrBlank()) null else repo.prefetchByOffice(office, settings.selectedClass10)
            }
            Log.d("AlarmReceiver", "fetchWeatherWithTimeout: Fetched snapshot = $fetched")
            fetched
        }
        Log.d("AlarmReceiver", "fetchWeatherWithTimeout: Exiting withTimeout block. Result = $result")
        result
    }
}
