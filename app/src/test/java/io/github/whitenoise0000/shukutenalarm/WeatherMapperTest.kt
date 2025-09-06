package io.github.whitenoise0000.shukutenalarm

import io.github.whitenoise0000.shukutenalarm.data.model.WeatherCategory
import io.github.whitenoise0000.shukutenalarm.weather.WeatherMapper
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * WeatherMapper の簡易単体テスト。
 */
class WeatherMapperTest {
    @Test fun clear() {
        assertEquals(WeatherCategory.CLEAR, WeatherMapper.fromWmo(0))
    }

    @Test fun cloudy() {
        assertEquals(WeatherCategory.CLOUDY, WeatherMapper.fromWmo(3))
    }

    @Test fun rain() {
        assertEquals(WeatherCategory.RAIN, WeatherMapper.fromWmo(61))
        assertEquals(WeatherCategory.RAIN, WeatherMapper.fromWmo(80))
    }

    @Test fun snow() {
        assertEquals(WeatherCategory.SNOW, WeatherMapper.fromWmo(71))
        assertEquals(WeatherCategory.SNOW, WeatherMapper.fromWmo(86))
    }

    @Test fun thunder() {
        assertEquals(WeatherCategory.RAIN, WeatherMapper.fromWmo(95))
        assertEquals(WeatherCategory.RAIN, WeatherMapper.fromWmo(99))
    }
}
