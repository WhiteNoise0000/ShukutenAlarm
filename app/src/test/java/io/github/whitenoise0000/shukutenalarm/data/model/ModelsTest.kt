package io.github.whitenoise0000.shukutenalarm.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 天気カテゴリ変換に関するユニットテスト。
 * - 仕様の WMO→カテゴリマッピングに準拠していることを確認する。
 */
class ModelsTest {
    @Test
    fun toCategory_mapsClear_on0() {
        assertEquals(WeatherCategory.CLEAR, 0.toCategory())
    }

    @Test
    fun toCategory_mapsCloudy_on1to3() {
        assertEquals(WeatherCategory.CLOUDY, 1.toCategory())
        assertEquals(WeatherCategory.CLOUDY, 3.toCategory())
    }

    @Test
    fun toCategory_mapsRain_on51to57_61to65_80to82() {
        assertEquals(WeatherCategory.RAIN, 51.toCategory())
        assertEquals(WeatherCategory.RAIN, 61.toCategory())
        assertEquals(WeatherCategory.RAIN, 80.toCategory())
    }

    @Test
    fun toCategory_mapsSnow_on71to75_85_86() {
        assertEquals(WeatherCategory.SNOW, 71.toCategory())
        assertEquals(WeatherCategory.SNOW, 86.toCategory())
    }

    @Test
    fun toCategory_mapsThunder_on95to99() {
        assertEquals(WeatherCategory.RAIN, 95.toCategory())
        assertEquals(WeatherCategory.RAIN, 99.toCategory())
    }
}

