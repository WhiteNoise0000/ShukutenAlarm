package io.github.whitenoise0000.shukutenalarm.alarm

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.whitenoise0000.shukutenalarm.data.DataStoreAlarmRepository
import io.github.whitenoise0000.shukutenalarm.data.PreferencesKeys
import io.github.whitenoise0000.shukutenalarm.data.appDataStore
import io.github.whitenoise0000.shukutenalarm.data.model.WeatherCategory
import io.github.whitenoise0000.shukutenalarm.holiday.HolidayRepository
import io.github.whitenoise0000.shukutenalarm.platform.Notifications
import io.github.whitenoise0000.shukutenalarm.ui.RingingActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.time.LocalDate

/**
 * アラーム発火時の BroadcastReceiver。
 * フルスクリーンの鳴動画面を起動し、次回アラームもスケジュールする。
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmGateway.ACTION_ALARM_FIRE) return
        val id = intent.getIntExtra(AlarmGateway.EXTRA_ALARM_ID, -1)
        if (id == -1) return

        val repo = DataStoreAlarmRepository(context)
        val spec = runBlocking { repo.load(id) } ?: return

        // 祝日判定
        val holidayRepo = HolidayRepository(context)
        val today = LocalDate.now()
        val isHolidayToday = holidayRepo.isHoliday(today)
        val holidayName = holidayRepo.nameOrNull(today)

        // 祝日 SKIP の場合は鳴動せず次回をスケジュール
        if (!SoundSelector.shouldRing(spec, isHolidayToday)) {
            ScheduleManager(context).scheduleNext(spec)
            return
        }

        // 直近の天気カテゴリをキャッシュから読み出し
        val weather = readCachedWeather(context)

        // 鳴動画面へ必要情報を渡す
        val activityIntent = Intent(context, RingingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("id", id)
            putExtra(
                "soundUri",
                SoundSelector.selectSound(spec, isHolidayToday, weather) {
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                        ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                }.toString()
            )
            putExtra("weatherLabel", weather?.name ?: "")
            putExtra("isHoliday", isHolidayToday)
            putExtra("holidayName", holidayName ?: "")
            putExtra("volumeMode", spec.volumeMode.name)
            putExtra("volumePercent", spec.volumePercent)
            putExtra("vibrate", spec.vibrate)
            putExtra("respectSilent", spec.respectSilentMode)
        }

        // 通知権限があればFSI通知、なければ直接起動
        val hasPost = if (android.os.Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        if (hasPost) {
            Notifications.showAlarmFullScreen(context, id, activityIntent)
        } else {
            ContextCompat.startActivity(context, activityIntent, null)
        }

        // 次回をスケジュール
        ScheduleManager(context).scheduleNext(spec)
    }

    /** DataStore のキャッシュから天気カテゴリを読み出す。 */
    private fun readCachedWeather(context: Context): WeatherCategory? = runBlocking {
        val key = stringPreferencesKey(PreferencesKeys.KEY_LAST_WEATHER_JSON)
        val text = context.appDataStore.data.map { prefs -> prefs[key] }.first()
        text?.let {
            runCatching {
                val obj = JSONObject(it)
                val cat = obj.optString("category", "").ifBlank { return@runCatching null }
                WeatherCategory.valueOf(cat)
            }.getOrNull()
        }
    }
}
