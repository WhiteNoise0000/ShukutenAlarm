package io.github.whitenoise0000.shukutenalarm.alarm

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.whitenoise0000.shukutenalarm.data.model.AlarmSpec
import io.github.whitenoise0000.shukutenalarm.data.model.HolidayPolicy
import io.github.whitenoise0000.shukutenalarm.data.model.WeatherCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * サウンド選択ロジックの計測テスト（Uri を使用するため androidTest 側）。
 */
@RunWith(AndroidJUnit4::class)
class SoundSelectorTest {
    private val rain = Uri.parse("content://sounds/rain")
    private val clear = Uri.parse("content://sounds/clear")
    private val holiday = Uri.parse("content://sounds/holiday")
    private val defUri = Uri.parse("content://sounds/default")

    private fun spec(policy: HolidayPolicy) = AlarmSpec(
        id = 1,
        time = LocalTime.of(7, 0),
        daysOfWeek = setOf(DayOfWeek.MONDAY),
        holidayPolicy = policy,
        soundMapping = mapOf(
            WeatherCategory.RAIN to rain,
            WeatherCategory.CLEAR to clear
        ),
        holidaySound = holiday
    )

    @Test
    fun shouldRing_false_onHolidaySkip() {
        assertFalse(SoundSelector.shouldRing(spec(HolidayPolicy.SKIP), isHoliday = true))
    }

    @Test
    fun shouldRing_true_onHolidayDelayOrSame() {
        assertTrue(SoundSelector.shouldRing(spec(HolidayPolicy.DELAY), isHoliday = true))
        assertTrue(SoundSelector.shouldRing(spec(HolidayPolicy.SAME), isHoliday = true))
    }

    @Test
    fun selectSound_usesWeatherMapping() {
        val uri = SoundSelector.selectSound(
            spec = spec(HolidayPolicy.SAME),
            weather = WeatherCategory.RAIN,
            defaultUriProvider = { defUri }
        )
        assertEquals(rain, uri)
    }

    @Test
    fun selectSound_usesHolidaySound_asFallback() {
        val s = spec(HolidayPolicy.SAME)
        val uri = SoundSelector.selectSound(
            spec = s.copy(soundMapping = emptyMap()), // No weather mapping
            weather = WeatherCategory.RAIN,
            defaultUriProvider = { defUri }
        )
        assertEquals(holiday, uri)
    }

    @Test
    fun selectSound_fallbacksToDefault() {
        val s = spec(HolidayPolicy.SAME)
        val uri = SoundSelector.selectSound(
            spec = s.copy(soundMapping = emptyMap(), holidaySound = null), // No weather mapping and no holiday sound
            weather = WeatherCategory.RAIN,
            defaultUriProvider = { defUri }
        )
        assertEquals(defUri, uri)
    }
}

