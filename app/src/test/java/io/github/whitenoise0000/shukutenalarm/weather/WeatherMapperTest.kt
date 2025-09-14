package io.github.whitenoise0000.shukutenalarm.weather

import io.github.whitenoise0000.shukutenalarm.data.model.WeatherCategory
import io.github.whitenoise0000.shukutenalarm.data.model.toCategory
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * WeatherMapper の単体テスト。
 * - WMO コードからアプリ内カテゴリへのマッピングを検証する。
 */
class WeatherMapperTest {
    @Test
    fun mapsClearCodeToCLEAR() {
        // 0 は快晴
        assertEquals(WeatherCategory.CLEAR, 0.toCategory())
    }

    @Test
    fun mapsCloudyCodesToCLOUDY() {
        // 1..3 は雲量に応じて概ね曇り扱い
        assertEquals(WeatherCategory.CLOUDY, 1.toCategory())
        assertEquals(WeatherCategory.CLOUDY, 2.toCategory())
        assertEquals(WeatherCategory.CLOUDY, 3.toCategory())
        // 未知コードは曇りに丸められる
        assertEquals(WeatherCategory.CLOUDY, 1234.toCategory())
    }

    @Test
    fun mapsRainCodesToRAIN() {
        // 51..57, 61..65, 80..82 は雨カテゴリ
        assertEquals(WeatherCategory.RAIN, 51.toCategory())
        assertEquals(WeatherCategory.RAIN, 61.toCategory())
        assertEquals(WeatherCategory.RAIN, 80.toCategory())
    }

    @Test
    fun mapsSnowCodesToSNOW() {
        // 71..75, 85, 86 は雪カテゴリ
        assertEquals(WeatherCategory.SNOW, 71.toCategory())
        assertEquals(WeatherCategory.SNOW, 86.toCategory())
    }

    @Test
    fun mapsThunderCodesToTHUNDER() {
        // 95..99 は雷ではなく雨カテゴリ
        assertEquals(WeatherCategory.RAIN, 95.toCategory())
    }
}