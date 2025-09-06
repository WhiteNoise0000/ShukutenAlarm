package io.github.whitenoise0000.shukutenalarm.work

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.github.whitenoise0000.shukutenalarm.settings.SettingsRepository
import io.github.whitenoise0000.shukutenalarm.weather.OpenMeteoApi
import io.github.whitenoise0000.shukutenalarm.weather.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.create

/**
 * 天気の事前取得を行う WorkManager の Worker。
 * - SettingsRepository から座標を読み、Open‑Meteo で取得・キャッシュする。
 */
class WeatherFetchWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            val settingsRepo = SettingsRepository(applicationContext)
            val settings = settingsRepo.settingsFlow.first()
            // 「現在地を使用」設定が有効なら、実行時点で端末のCOARSE位置を取得して座標に反映（権限未許可や取得不可時は保存値を使用）
            val (lat, lon) = if (settings.useCurrentLocation) {
                val granted = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    val lm = applicationContext.getSystemService(LocationManager::class.java)
                    val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
                    var out = settings.latitude to settings.longitude
                    providers.forEach { p ->
                        val loc = runCatching { lm?.getLastKnownLocation(p) }.getOrNull()
                        if (loc != null) { out = loc.latitude to loc.longitude; return@forEach }
                    }
                    out
                } else settings.latitude to settings.longitude
            } else settings.latitude to settings.longitude
            val json = Json { ignoreUnknownKeys = true }
            val client = OkHttpClient.Builder()
                .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
                .build()
            val contentType = "application/json".toMediaType()
            // baseUrl は末尾スラッシュ必須
            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.open-meteo.com/")
                .client(client)
                .addConverterFactory(json.asConverterFactory(contentType))
                .build()
            val api = retrofit.create<OpenMeteoApi>()
            val repo = WeatherRepository(applicationContext, api)
            repo.prefetchToday(lat, lon)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
