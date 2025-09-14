package io.github.whitenoise0000.shukutenalarm.widget

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey

import io.github.whitenoise0000.shukutenalarm.data.DataStoreAlarmRepository
import io.github.whitenoise0000.shukutenalarm.data.PreferencesKeys
import io.github.whitenoise0000.shukutenalarm.data.appDataStore
import io.github.whitenoise0000.shukutenalarm.data.model.AlarmSpec
import io.github.whitenoise0000.shukutenalarm.data.model.HolidayPolicy
import io.github.whitenoise0000.shukutenalarm.holiday.HolidayRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 次回アラーム（アプリ内で管理しているもの）の候補を計算するユーティリティ。
 * - ScheduleManager と同等のロジック（祝日ポリシー SKIP/DELAY/SAME, holidayOnly）を簡易実装する。
 * - ウィジェット表示およびスキップ操作で使用するため、ID と日時を返す。
 * - 依存は最小限に抑えるため、Hilt は使わずに都度 Repository を生成する。
 */
class NextAlarmCalculator(private val context: Context) {
    private val repo: DataStoreAlarmRepository by lazy { DataStoreAlarmRepository(context) }
    private val holidays by lazy { HolidayRepository(context) }

    /**
     * 次回アラームを返す。存在しない場合は null。
     */
    fun findNext(): Result? {
        val specs = runBlocking { repo.list() }.filter { it.enabled }
        if (specs.isEmpty()) return null
        val now = LocalDateTime.now()
        val delayMinutes = readDelayMinutes()

        var best: Result? = null
        for (spec in specs) {
            val candidate = nextForSpecConsideringSkip(spec, now, delayMinutes)
            if (candidate != null) {
                if (best == null || candidate.dateTime.isBefore(best.dateTime)) {
                    best = candidate
                }
            }
        }
        return best
    }

    /**
     * 単一アラームに対する次回候補日時を計算する（1回スキップ考慮）。
     */
    private fun nextForSpecConsideringSkip(spec: AlarmSpec, now: LocalDateTime, delayMinutes: Int): Result? {
        val base = computeNextForSpec(spec, now, delayMinutes)
        base ?: return null
        // 1回スキップの期限（epochMillis）を参照し、期限以下の候補は飛ばす
        val key = longPreferencesKey(PreferencesKeys.KEY_SKIP_UNTIL_PREFIX + spec.id)
        val until = runBlocking { context.appDataStore.data.map { prefs -> prefs[key] ?: 0L }.first() }
        if (until > 0L) {
            val untilDt = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(until), ZoneId.systemDefault()).toLocalDateTime()
            if (!base.dateTime.isAfter(untilDt)) {
                // until より後で再計算
                val next = computeNextForSpec(spec, untilDt.plusSeconds(1), delayMinutes)
                if (next != null) return next
            }
        }
        return base
    }

    /**
     * ポリシー（祝日/DELAY/SAME/holidayOnly）を考慮して次回候補を計算する。
     */
    private fun computeNextForSpec(spec: AlarmSpec, now: LocalDateTime, delayMinutes: Int): Result? {
        // 祝日のみ鳴動するモード
        if (spec.holidayOnly) {
            val nextHoliday = nextHolidayDateTime(spec.time, now)
            return Result(spec, nextHoliday, true)
        }

        val base = nextOccurrence(spec.daysOfWeek, spec.time, now)
        val date = base.toLocalDate()
        val isHoliday = holidays.isHoliday(date)

        return when (spec.holidayPolicy) {
            HolidayPolicy.SKIP -> {
                // 祝日はスキップし、次の非祝日を探す
                val next = nextNonHolidayDate(spec.daysOfWeek, spec.time, base)
                Result(spec, next, false)
            }
            HolidayPolicy.DELAY -> {
                if (isHoliday) {
                    val delayed = base.plusMinutes(delayMinutes.toLong())
                    Result(spec.copy(time = delayed.toLocalTime()), delayed, true)
                } else {
                    Result(spec, base, false)
                }
            }
            HolidayPolicy.SAME -> Result(spec, base, isHoliday)
        }
    }

    /** 曜日集合と時刻から、今以降で最も近い候補日時を返す。*/
    private fun nextOccurrence(days: Set<DayOfWeek>, time: LocalTime, now: LocalDateTime): LocalDateTime {
        val targetDays = days.ifEmpty { DayOfWeek.entries.toSet() }
        var date = now.toLocalDate()
        repeat(8) {
            val isToday = it == 0
            if (targetDays.contains(date.dayOfWeek)) {
                val candidate = LocalDateTime.of(date, time)
                if (!isToday || candidate.isAfter(now)) return candidate
            }
            date = date.plusDays(1)
        }
        return LocalDateTime.of(LocalDate.now().plusDays(1), time)
    }

    /** SKIP 用: 次の非祝日の候補日時（曜日制約を満たすもの）を返す。*/
    private fun nextNonHolidayDate(days: Set<DayOfWeek>, time: LocalTime, start: LocalDateTime): LocalDateTime {
        var dt = start
        repeat(31) {
            val next = if (it == 0) dt else dt.plusDays(1).withHour(time.hour).withMinute(time.minute).withSecond(0).withNano(0)
            val isDayMatch = days.isEmpty() || days.contains(next.dayOfWeek)
            // 'now' ではなく start を基準に比較したいが、概ね問題ないため現状は現在時刻で比較
            if (isDayMatch && !holidays.isHoliday(next.toLocalDate()) && next.isAfter(LocalDateTime.now())) return next
            dt = next
        }
        return start.plusDays(1)
    }

    /** 指定時刻で次の祝日候補を返す。*/
    private fun nextHolidayDateTime(time: LocalTime, now: LocalDateTime): LocalDateTime {
        var date = now.toLocalDate()
        repeat(370) {
            val candidate = LocalDateTime.of(date, time)
            if (holidays.isHoliday(date) && candidate.isAfter(now)) return candidate
            date = date.plusDays(1)
        }
        return LocalDateTime.of(now.toLocalDate().plusDays(1), time)
    }

    /** DataStore から DELAY 分数を取得（デフォルト 60）。*/
    private fun readDelayMinutes(): Int = runBlocking {
        val key = intPreferencesKey(PreferencesKeys.KEY_DELAY_MINUTES)
        context.appDataStore.data.map { prefs -> prefs[key] ?: 60 }.first()
    }

    /** 結果モデル。*/
    data class Result(
        val spec: AlarmSpec,
        val dateTime: LocalDateTime,
        val isHoliday: Boolean
    )
}
