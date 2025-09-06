package io.github.whitenoise0000.shukutenalarm.weather

import io.github.whitenoise0000.shukutenalarm.data.model.WeatherCategory
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
        assertEquals(WeatherCategory.CLEAR, WeatherMapper.fromWmo(0))
    }

    @Test
    fun mapsCloudyCodesToCLOUDY() {
        // 1..3 は雲量に応じて概ね曇り扱い
        assertEquals(WeatherCategory.CLOUDY, WeatherMapper.fromWmo(1))
        assertEquals(WeatherCategory.CLOUDY, WeatherMapper.fromWmo(2))
        assertEquals(WeatherCategory.CLOUDY, WeatherMapper.fromWmo(3))
        // 未知コードは曇りに丸められる
        assertEquals(WeatherCategory.CLOUDY, WeatherMapper.fromWmo(1234))
    }

    @Test
    fun mapsRainCodesToRAIN() {
        // 51..57, 61..65, 80..82 は雨カテゴリ
        assertEquals(WeatherCategory.RAIN, WeatherMapper.fromWmo(51))
        assertEquals(WeatherCategory.RAIN, WeatherMapper.fromWmo(61))
        assertEquals(WeatherCategory.RAIN, WeatherMapper.fromWmo(80))
    }

    @Test
    fun mapsSnowCodesToSNOW() {
        // 71..75, 85, 86 は雪カテゴリ
        assertEquals(WeatherCategory.SNOW, WeatherMapper.fromWmo(71))
        assertEquals(WeatherCategory.SNOW, WeatherMapper.fromWmo(86))
    }

    @Test
    fun mapsThunderCodesToTHUNDER() {
        // 95..99 は雷ではなく雨カテゴリ
        assertEquals(WeatherCategory.RAIN, WeatherMapper.fromWmo(95))
    }
}

