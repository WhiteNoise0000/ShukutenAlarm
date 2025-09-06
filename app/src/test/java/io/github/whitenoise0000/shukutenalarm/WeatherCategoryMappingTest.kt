package io.github.whitenoise0000.shukutenalarm

import io.github.whitenoise0000.shukutenalarm.data.model.WeatherCategory
import io.github.whitenoise0000.shukutenalarm.data.model.toCategory
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * WMO コードからアプリ内の天気カテゴリへの変換テスト。
 * 仕様書のマッピング規則に従うことを検証する。
 */
class WeatherCategoryMappingTest {

    @Test
    fun mapsClear() {
        // 0 は快晴に分類されるべき
        assertEquals(WeatherCategory.CLEAR, 0.toCategory())
    }

    @Test
    fun mapsCloudyRange() {
        // 1〜3 は曇に分類
        assertEquals(WeatherCategory.CLOUDY, 1.toCategory())
        assertEquals(WeatherCategory.CLOUDY, 2.toCategory())
        assertEquals(WeatherCategory.CLOUDY, 3.toCategory())
    }

    @Test
    fun mapsRainGroups() {
        // 霧雨/雨/にわか雨の代表コード
        intArrayOf(51, 53, 57, 61, 63, 65, 80, 81, 82).forEach {
            assertEquals(WeatherCategory.RAIN, it.toCategory())
        }
    }

    @Test
    fun mapsSnowGroups() {
        intArrayOf(71, 73, 75, 85, 86).forEach {
            assertEquals(WeatherCategory.SNOW, it.toCategory())
        }
    }

    @Test
    fun mapsThunderRange() {
        intArrayOf(95, 96, 99).forEach {
            // 雷は雨扱い
            assertEquals(WeatherCategory.RAIN, it.toCategory())
        }
    }

    @Test
    fun mapsUnknownToCloudy() {
        // 未知コードは CLOUDY にフォールバック
        assertEquals(WeatherCategory.CLOUDY, 123.toCategory())
    }
}

