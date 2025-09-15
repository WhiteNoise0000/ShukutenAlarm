package io.github.whitenoise0000.shukutenalarm.weather.jma

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.whitenoise0000.shukutenalarm.data.appDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 予報定数（forecast.json: weatherCodes→TELOPS）を管理するリポジトリ。
 * - 取得した辞書はシリアライズして DataStore にキャッシュ保存。
 * - ETag/IMS は OkHttp 側のインターセプタで処理。
 */
class ForecastConstRepository(
    private val context: Context,
    private val api: JmaForecastConstApi
) {
    private var memory: Map<String, String>? = null

    /**
     * TELOPS 辞書を取得（メモリ→DataStore→ネットワークの順に参照）。
     */
    suspend fun getTelopsMap(): Map<String, String> = withContext(Dispatchers.IO) {
        memory?.let { return@withContext it }
        // DataStore キャッシュから復元
        val key = stringPreferencesKey(KEY_FORECAST_CONST_CACHE)
        val prefs = context.appDataStore.data.first()
        val cached = prefs[key]
        if (!cached.isNullOrBlank()) {
            runCatching {
                val parsed = Json.decodeFromString(TelopsCache.serializer(), cached)
                val map = parsed.codes.zip(parsed.telops).toMap()
                memory = map
                return@withContext map
            }.getOrNull()
        }
        // ネットワーク
        val const = api.forecastConst()
        val map = const.weatherCodes.zip(const.telops).toMap()
        // 保存
        val cache = TelopsCache(codes = const.weatherCodes, telops = const.telops)
        context.appDataStore.edit { p ->
            p[key] = Json.encodeToString(TelopsCache.serializer(), cache)
        }
        memory = map
        map
    }

    @Serializable
    private data class TelopsCache(
        val codes: List<String>,
        val telops: List<String>
    )

    companion object {
        private const val KEY_FORECAST_CONST_CACHE = "forecast_const_telops_json"
    }
}

