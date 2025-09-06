package io.github.whitenoise0000.shukutenalarm

import android.net.Uri
import io.github.whitenoise0000.shukutenalarm.data.AlarmSpecJson
import io.github.whitenoise0000.shukutenalarm.data.model.AlarmSpec
import io.github.whitenoise0000.shukutenalarm.data.model.HolidayPolicy
import io.github.whitenoise0000.shukutenalarm.data.model.WeatherCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * AlarmSpec の JSON 変換の往復（エンコード/デコード）を検証するテスト。
 * 重要フィールドが保持されることを確認する。
 */
class AlarmSpecJsonTest {
    @Test
    fun encodeDecode_roundTrip() {
        val spec = AlarmSpec(
            id = 42,
            time = LocalTime.of(7, 30),
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
            holidayPolicy = HolidayPolicy.DELAY,
            prefetchMinutes = 50,
            soundMapping = mapOf(
                WeatherCategory.CLEAR to Uri.parse("content://sound/clear"),
                WeatherCategory.RAIN to Uri.parse("content://sound/rain")
            ),
            holidaySound = Uri.parse("content://sound/holiday"),
            enabled = true
        )

        val json = AlarmSpecJson.encode(spec)
        val decoded = AlarmSpecJson.decode(json)

        // 核となるフィールドが一致していること
        assertEquals(spec.id, decoded.id)
        assertEquals(spec.time, decoded.time)
        assertEquals(spec.daysOfWeek, decoded.daysOfWeek)
        assertEquals(spec.holidayPolicy, decoded.holidayPolicy)
        assertEquals(spec.prefetchMinutes, decoded.prefetchMinutes)
        assertEquals(spec.enabled, decoded.enabled)
        assertEquals(spec.soundMapping.keys, decoded.soundMapping.keys)
        assertEquals(spec.soundMapping[WeatherCategory.CLEAR]?.toString(), decoded.soundMapping[WeatherCategory.CLEAR]?.toString())
        assertEquals(spec.holidaySound?.toString(), decoded.holidaySound?.toString())
        assertNotNull(decoded)
    }
}

