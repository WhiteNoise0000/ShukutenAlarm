package io.github.whitenoise0000.shukutenalarm.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.github.whitenoise0000.shukutenalarm.holiday.HolidayRepository
import io.github.whitenoise0000.shukutenalarm.network.EtagCacheInterceptor
import io.github.whitenoise0000.shukutenalarm.weather.jma.AreaRepository
import io.github.whitenoise0000.shukutenalarm.weather.jma.JmaConstApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * マスタデータ（祝日YAML＋JMAエリアマスタ area.json）を更新するWorker。
 * - 定期（WorkManager）/手動のどちらからも呼び出される想定。
 * - ネットワーク条件（Wi‑Fiのみ等）はスケジューラ側Constraintsに従う。
 * - area.json は ETag/If-Modified-Since に対応し、304応答時はローカルキャッシュ本文を用いて
 *   200として復元する（EtagCacheInterceptor が担当）。
 * - 祝日YAMLは取得後にローカルへ保存し、メモリキャッシュも更新される。
 */
class MasterRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            // 祝日YAMLの更新
            HolidayRepository(applicationContext).forceRefresh()

            // JMA area.json の更新（ETag/IMS対応、304→キャッシュ本文で200復元）
            val json = Json { ignoreUnknownKeys = true }
            val client = OkHttpClient.Builder()
                .addInterceptor(EtagCacheInterceptor(applicationContext))
                .build()
            val jmaRetrofit = retrofit2.Retrofit.Builder()
                .baseUrl("https://www.jma.go.jp/")
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            val constApi = jmaRetrofit.create(JmaConstApi::class.java)
            val areaRepo = AreaRepository(applicationContext, constApi)
            areaRepo.refreshMaster()

            // TELOPS はローカルアセット/キャッシュのみ利用（オンライン更新は無効化）
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

/**
 * マスタデータ更新を定期実行するスケジューラ。
 * - wifiOnly=true の場合は UNMETERED（非従量）ネットワークを要求。
 */
object MasterRefreshScheduler {
    /** 本WorkerのPeriodicWork一意名（更新予約の一元管理用）。 */
    private const val UNIQUE_NAME = "master-refresh-periodic"

    fun schedule(context: Context, wifiOnly: Boolean, intervalDays: Int = 30) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val days = intervalDays.coerceAtLeast(1)
        val req = PeriodicWorkRequestBuilder<MasterRefreshWorker>(days.toLong(), TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            req
        )
    }

    /** 定期更新をキャンセルする。*/
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
    }
}
