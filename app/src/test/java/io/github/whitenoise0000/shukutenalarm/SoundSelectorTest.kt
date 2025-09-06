package io.github.whitenoise0000.shukutenalarm

import android.net.Uri
import io.github.whitenoise0000.shukutenalarm.alarm.SoundSelector
import io.github.whitenoise0000.shukutenalarm.data.model.AlarmSpec
import io.github.whitenoise0000.shukutenalarm.data.model.HolidayPolicy
import io.github.whitenoise0000.shukutenalarm.data.model.WeatherCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * サウンド選択と鳴動可否のロジックを検証するテスト。
 */
class SoundSelectorTest {

    private fun spec(policy: HolidayPolicy, mapping: Map<WeatherCategory, Uri> = emptyMap(), holiday: Uri? = null) =
        AlarmSpec(
            id = 1,
            time = LocalTime.of(7, 0),
            daysOfWeek = setOf(DayOfWeek.MONDAY),
            holidayPolicy = policy,
            soundMapping = mapping,
            holidaySound = holiday
        )

    @Test
    fun shouldRing_skipOnHoliday() {
        assertFalse(SoundSelector.shouldRing(spec(HolidayPolicy.SKIP), isHoliday = true))
        assertTrue(SoundSelector.shouldRing(spec(HolidayPolicy.SKIP), isHoliday = false))
    }

    @Test
    fun selectSound_holidayOverrides() {
        val holidayUri = Uri.parse("content://sound/holiday")
        val s = spec(HolidayPolicy.SAME, holiday = holidayUri)
        val selected = SoundSelector.selectSound(s, isHoliday = true, weather = null) {
            Uri.parse("content://default")
        }
        assertEquals(holidayUri, selected)
    }

    @Test
    fun selectSound_weatherMappingOrDefault() {
        val clear = Uri.parse("content://sound/clear")
        val s = spec(HolidayPolicy.SAME, mapping = mapOf(WeatherCategory.CLEAR to clear))
        val fromWeather = SoundSelector.selectSound(s, isHoliday = false, weather = WeatherCategory.CLEAR) {
            Uri.parse("content://default")
        }
        assertEquals(clear, fromWeather)

        val fallback = SoundSelector.selectSound(s, isHoliday = false, weather = WeatherCategory.RAIN) {
            Uri.parse("content://default")
        }
        assertEquals(Uri.parse("content://default"), fallback)
    }
}

