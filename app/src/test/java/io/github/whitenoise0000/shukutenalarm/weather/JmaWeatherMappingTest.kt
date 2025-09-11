package io.github.whitenoise0000.shukutenalarm.weather

import io.github.whitenoise0000.shukutenalarm.data.model.WeatherCategory
import io.github.whitenoise0000.shukutenalarm.weather.jma.ForecastRepository
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JMAの天気テキスト/コードからカテゴリへの正規化テスト。
 */
class JmaWeatherMappingTest {
    @Test
    fun mapsTextToCategory() {
        assertEquals(WeatherCategory.CLEAR, ForecastRepository.mapWeatherTextToCategory("晴れ"))
        assertEquals(WeatherCategory.CLOUDY, ForecastRepository.mapWeatherTextToCategory("くもり一時晴"))
        assertEquals(WeatherCategory.RAIN, ForecastRepository.mapWeatherTextToCategory("雨のち晴"))
        assertEquals(WeatherCategory.SNOW, ForecastRepository.mapWeatherTextToCategory("雪時々くもり"))
        assertEquals(WeatherCategory.RAIN, ForecastRepository.mapWeatherTextToCategory("雷を伴う"))
    }

    @Test
    fun mapsCodeToCategory() {
        assertEquals(WeatherCategory.CLEAR, ForecastRepository.mapWeatherCodeToCategory("100"))
        assertEquals(WeatherCategory.CLOUDY, ForecastRepository.mapWeatherCodeToCategory("200"))
        assertEquals(WeatherCategory.RAIN, ForecastRepository.mapWeatherCodeToCategory("300"))
        assertEquals(WeatherCategory.SNOW, ForecastRepository.mapWeatherCodeToCategory("400"))
        assertEquals(WeatherCategory.RAIN, ForecastRepository.mapWeatherCodeToCategory("500"))
    }
}
