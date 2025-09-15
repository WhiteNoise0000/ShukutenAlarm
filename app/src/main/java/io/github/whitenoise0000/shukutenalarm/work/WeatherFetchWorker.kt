package io.github.whitenoise0000.shukutenalarm.work

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.github.whitenoise0000.shukutenalarm.network.EtagCacheInterceptor
import io.github.whitenoise0000.shukutenalarm.settings.SettingsRepository
import io.github.whitenoise0000.shukutenalarm.weather.WeatherRepository
import io.github.whitenoise0000.shukutenalarm.weather.jma.AreaRepository
import io.github.whitenoise0000.shukutenalarm.weather.jma.GsiApi
import io.github.whitenoise0000.shukutenalarm.weather.jma.JmaConstApi
import io.github.whitenoise0000.shukutenalarm.weather.jma.JmaForecastApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

/**
 * 天気の定期取得を行うWorker。
 * - 設定に基づき、現在地連動時はGSI→JMA、手動選択時はoffice/class10で取得する。
 */
class WeatherFetchWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            val settingsRepo = SettingsRepository(applicationContext)
            val settings = settingsRepo.settingsFlow.first()

            // 現在地の推定（COARSEのみ利用）。
            val (lat, lon) = if (settings.useCurrentLocation) {
                val granted = ContextCompat.checkSelfPermission(
                    applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    val lm = applicationContext.getSystemService(LocationManager::class.java)
                    val providers = listOf(
                        LocationManager.NETWORK_PROVIDER,
                        LocationManager.PASSIVE_PROVIDER,
                        LocationManager.GPS_PROVIDER
                    )
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
                .addInterceptor(EtagCacheInterceptor(applicationContext))
                .build()
            val contentType = "application/json".toMediaType()
            val jmaRetrofit = Retrofit.Builder()
                .baseUrl("https://www.jma.go.jp/")
                .client(client)
                .addConverterFactory(json.asConverterFactory(contentType))
                .build()
            val gsiRetrofit = Retrofit.Builder()
                .baseUrl("https://mreversegeocoder.gsi.go.jp/")
                .client(client)
                .addConverterFactory(json.asConverterFactory(contentType))
                .build()
            val forecastApi = jmaRetrofit.create(JmaForecastApi::class.java)
                        val gsiApi = gsiRetrofit.create(GsiApi::class.java)
            val constApi = jmaRetrofit.create(JmaConstApi::class.java)
            val areaRepo = AreaRepository(applicationContext, constApi)
            val telopsRepo = io.github.whitenoise0000.shukutenalarm.weather.jma.TelopsRepository(applicationContext)
            val repo = WeatherRepository(applicationContext, forecastApi, gsiApi, areaRepo, telopsRepo)
            if (settings.useCurrentLocation) {
                repo.prefetchByCurrentLocation(lat, lon)
            } else {
                val office = settings.selectedOffice
                if (!office.isNullOrBlank()) {
                    repo.prefetchByOffice(office, settings.selectedClass10)
                }
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

