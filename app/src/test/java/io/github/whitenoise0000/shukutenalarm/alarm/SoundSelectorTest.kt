package io.github.whitenoise0000.shukutenalarm.alarm

import android.net.Uri
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
 * SoundSelector の単体テスト。
 * - 祝日 SKIP の鳴動可否
 * - サウンド選択の優先順位（祝日音 > 天気別 > デフォルト）
 */
class SoundSelectorTest {
    private fun specBase(): AlarmSpec = AlarmSpec(
        id = 1,
        time = LocalTime.of(7, 0),
        daysOfWeek = setOf(DayOfWeek.MONDAY),
        holidayPolicy = HolidayPolicy.SAME,
        soundMapping = emptyMap(),
        holidaySound = null,
        enabled = true
    )

    @Test
    fun shouldRing_returnsFalse_onHolidaySkip() {
        val spec = specBase().copy(holidayPolicy = HolidayPolicy.SKIP)
        assertFalse(SoundSelector.shouldRing(spec, isHoliday = true))
        assertTrue(SoundSelector.shouldRing(spec, isHoliday = false))
    }

    @Test
    fun selectSound_prefersHolidaySound_whenHoliday() {
        val holiday = Uri.parse("content://holiday")
        val rain = Uri.parse("content://rain")
        val spec = specBase().copy(
            holidaySound = holiday,
            soundMapping = mapOf(WeatherCategory.RAIN to rain)
        )
        val selected = SoundSelector.selectSound(spec, isHoliday = true, weather = WeatherCategory.RAIN) {
            Uri.parse("content://default")
        }
        assertEquals(holiday, selected)
    }

    @Test
    fun selectSound_usesWeatherMapping_whenAvailable() {
        val rain = Uri.parse("content://rain")
        val spec = specBase().copy(
            soundMapping = mapOf(WeatherCategory.RAIN to rain)
        )
        val selected = SoundSelector.selectSound(spec, isHoliday = false, weather = WeatherCategory.RAIN) {
            Uri.parse("content://default")
        }
        assertEquals(rain, selected)
    }

    @Test
    fun selectSound_fallsBackToDefault_whenNoMatch() {
        val def = Uri.parse("content://default")
        val spec = specBase()
        val selected = SoundSelector.selectSound(spec, isHoliday = false, weather = null) { def }
        assertEquals(def, selected)
    }
}

