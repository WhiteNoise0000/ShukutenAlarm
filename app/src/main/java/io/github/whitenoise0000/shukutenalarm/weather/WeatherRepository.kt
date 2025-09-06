package io.github.whitenoise0000.shukutenalarm.weather

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.whitenoise0000.shukutenalarm.data.PreferencesKeys
import io.github.whitenoise0000.shukutenalarm.data.appDataStore
import io.github.whitenoise0000.shukutenalarm.data.model.WeatherCategory
import io.github.whitenoise0000.shukutenalarm.data.model.toCategory
import kotlinx.serialization.json.Json

/**
 * 天気取得用のリポジトリ。
 * - Open‑Meteo から当日分の天気を取得し、カテゴリへ変換する。
 * - 直近の結果を DataStore にキャッシュ（当日限り想定）。
 */
class WeatherRepository(
    private val context: Context,
    private val api: OpenMeteoApi,
    private val json: Json
) {
    /**
     * 当日の代表的な天気カテゴリを推定して返す（簡易: 最初の時間の weathercode を使用）。
     */
    suspend fun fetchTodayCategory(lat: Double, lon: Double): WeatherCategory? {
        val res = api.jma(lat = lat, lon = lon)
        val code = res.hourly.weathercode.firstOrNull() ?: return null
        return code.toCategory()
    }

    /**
     * 先読みとキャッシュ保存を行う。
     */
    suspend fun prefetchToday(lat: Double, lon: Double): WeatherCategory? {
        val cat = fetchTodayCategory(lat, lon)
        // 最小限の JSON を保存（timestamp は簡易に System.currentTimeMillis）
        val value = cat?.name ?: ""
        val now = System.currentTimeMillis()
        val jsonText = "{" + "\"timestamp\":$now,\"category\":\"$value\"}"
        val key = stringPreferencesKey(PreferencesKeys.KEY_LAST_WEATHER_JSON)
        context.appDataStore.edit { prefs ->
            prefs[key] = jsonText
        }
        return cat
    }
}
