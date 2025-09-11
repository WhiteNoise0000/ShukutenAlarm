package io.github.whitenoise0000.shukutenalarm.weather.jma

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.whitenoise0000.shukutenalarm.data.PreferencesKeys
import io.github.whitenoise0000.shukutenalarm.data.appDataStore
import io.github.whitenoise0000.shukutenalarm.data.model.WeatherCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * JMA予報取得＋クラス10抽出＋カテゴリ正規化＋キャッシュ保存を行うリポジトリ。
 * - weathers(日本語) or weatherCodes(数値文字列)のどちらかからカテゴリへ正規化。
 * - 文字列優先、無ければコードから判定。
 */
class ForecastRepository(
    private val context: Context,
    private val api: JmaForecastApi
) {

    /** 指定office/class10に対し、先頭の予報カテゴリを取得する。*/
    suspend fun fetchCategory(office: String, class10: String): WeatherCategory? {
        val roots = api.forecast(office)
        // weathers/ weatherCodes を持つ最初の timeseries から該当class10を抽出
        for (root in roots) {
            for (ts in root.timeSeries) {
                val area = ts.areas.firstOrNull { it.area.code == class10 }
                if (area != null) {
                    val cat = area.weathers?.firstOrNull()?.let { mapWeatherTextToCategory(it) }
                        ?: area.weatherCodes?.firstOrNull()?.let { mapWeatherCodeToCategory(it) }
                    if (cat != null) return cat
                }
            }
        }
        return null
    }

    /** DataStoreへ直近カテゴリをJSONで保存。*/
    suspend fun cacheCategory(category: WeatherCategory?) = withContext(Dispatchers.IO) {
        val key = stringPreferencesKey(PreferencesKeys.KEY_LAST_WEATHER_JSON)
        val now = System.currentTimeMillis()
        val value = category?.name ?: ""
        val text = "{\"timestamp\":$now,\"category\":\"$value\"}"
        context.appDataStore.edit { prefs -> prefs[key] = text }
    }

    companion object {
        /** 日本語の天気テキストからカテゴリへマッピング。*/
        fun mapWeatherTextToCategory(text: String): WeatherCategory {
            val s = text.trim()
            // 優先度: 雪 > 雨/雷 > くもり > 晴れ
            return when {
                s.contains("雪") -> WeatherCategory.SNOW
                s.contains("雷") -> WeatherCategory.RAIN
                s.contains("雨") -> WeatherCategory.RAIN
                s.contains("曇") || s.contains("くもり") -> WeatherCategory.CLOUDY
                s.contains("晴") -> WeatherCategory.CLEAR
                else -> WeatherCategory.CLOUDY
            }
        }

        /** JMA天気コード(例: "100","200","300","400","500")からカテゴリへマッピング。*/
        fun mapWeatherCodeToCategory(code: String): WeatherCategory {
            val c = code.trim()
            val head = c.firstOrNull() ?: return WeatherCategory.CLOUDY
            return when (head) {
                '1' -> WeatherCategory.CLEAR
                '2' -> WeatherCategory.CLOUDY
                '3' -> WeatherCategory.RAIN
                '4' -> WeatherCategory.SNOW
                '5' -> WeatherCategory.RAIN // 雷雨は雨扱い
                else -> WeatherCategory.CLOUDY
            }
        }
    }
}
