package io.github.whitenoise0000.shukutenalarm.data

import android.net.Uri
import io.github.whitenoise0000.shukutenalarm.data.model.AlarmSpec
import io.github.whitenoise0000.shukutenalarm.data.model.HolidayPolicy
import io.github.whitenoise0000.shukutenalarm.data.model.WeatherCategory
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * AlarmSpecJson のエンコード/デコードの往復テスト。
 * - 重要フィールドが保持されることを確認する。
 */
class AlarmSpecJsonTest {
    @Test
    fun encodeDecode_roundTrip_preservesFields() {
        val original = AlarmSpec(
            id = 42,
            time = LocalTime.of(8, 30),
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
            holidayPolicy = HolidayPolicy.DELAY,
            prefetchMinutes = 30,
            soundMapping = mapOf(WeatherCategory.RAIN to Uri.parse("content://rain")),
            holidaySound = Uri.parse("content://holiday"),
            enabled = true
        )
        val json = AlarmSpecJson.encode(original)
        val decoded = AlarmSpecJson.decode(json)
        assertEquals(original.id, decoded.id)
        assertEquals(original.time, decoded.time)
        assertEquals(original.daysOfWeek, decoded.daysOfWeek)
        assertEquals(original.holidayPolicy, decoded.holidayPolicy)
        assertEquals(original.prefetchMinutes, decoded.prefetchMinutes)
        assertEquals(original.soundMapping[WeatherCategory.RAIN], decoded.soundMapping[WeatherCategory.RAIN])
        assertEquals(original.holidaySound, decoded.holidaySound)
        assertEquals(original.enabled, decoded.enabled)
    }
}

