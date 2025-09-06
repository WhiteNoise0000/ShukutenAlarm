package io.github.whitenoise0000.shukutenalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.github.whitenoise0000.shukutenalarm.data.appDataStore
import io.github.whitenoise0000.shukutenalarm.data.model.AlarmSpec
import io.github.whitenoise0000.shukutenalarm.data.model.HolidayPolicy
import io.github.whitenoise0000.shukutenalarm.holiday.HolidayRepository
import io.github.whitenoise0000.shukutenalarm.work.WeatherFetchWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 次回アラームのスケジュールを計算し、AlarmManager と WorkManager を登録するヘルパー。
 * - 祝日ポリシー（SKIP/DELAY/SAME）を考慮する。
 * - DELAY は固定で +60 分を適用（仕様の受入基準に準拠）。
 */
class ScheduleManager(private val context: Context) {
    private val holidays by lazy { HolidayRepository(context) }
    private val gateway by lazy { AlarmGateway(context) }
    private val prefs: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> by lazy { context.appDataStore }

    /** 次回分のアラームを登録する。*/
    fun scheduleNext(spec: AlarmSpec) {
        val now = LocalDateTime.now()
        // 祝日のみ鳴動するモードの場合は、次の祝日を探す
        if (spec.holidayOnly) {
            val nextHoliday = nextHolidayDateTime(spec.time, now)
            // holidayOnly と SKIP の組合せは無効のため SAME と同等に扱う
            when (spec.holidayPolicy) {
                HolidayPolicy.DELAY -> {
                    val delay = readDelayMinutes()
                    val delayed = nextHoliday.plusMinutes(delay.toLong())
                    gateway.scheduleExactAlarm(spec.copy(time = delayed.toLocalTime()), delayed.toLocalDate())
                    schedulePrefetchInternal(spec, delayed)
                }
                else -> {
                    gateway.scheduleExactAlarm(spec, nextHoliday.toLocalDate())
                    schedulePrefetchInternal(spec, nextHoliday)
                }
            }
            return
        }

        val base = nextOccurrence(spec.daysOfWeek, spec.time, now)
        val date = base.toLocalDate()
        val isHoliday = holidays.isHoliday(date)

        when (spec.holidayPolicy) {
            HolidayPolicy.SKIP -> {
                // 祝日は鳴らさない → 次の非祝日を探す
                val next = nextNonHolidayDate(spec.daysOfWeek, spec.time, base)
                gateway.scheduleExactAlarm(spec, next.toLocalDate())
                schedulePrefetchInternal(spec, next)
            }
            HolidayPolicy.DELAY -> {
                // 当日が祝日の場合は +60 分で鳴らす
                val delay = readDelayMinutes()
                if (isHoliday) {
                    val delayed = base.plusMinutes(delay.toLong())
                    gateway.scheduleExactAlarm(spec.copy(time = delayed.toLocalTime()), delayed.toLocalDate())
                    schedulePrefetchInternal(spec, delayed)
                } else {
                    gateway.scheduleExactAlarm(spec, date)
                    schedulePrefetchInternal(spec, base)
                }
            }
            HolidayPolicy.SAME -> {
                gateway.scheduleExactAlarm(spec, date)
                schedulePrefetchInternal(spec, base)
            }
        }
    }

    /** 次回の候補日時（曜日と時刻）を返す。*/
    private fun nextOccurrence(days: Set<DayOfWeek>, time: LocalTime, now: LocalDateTime): LocalDateTime {
        val targetDays = if (days.isEmpty()) DayOfWeek.values().toSet() else days
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

    /** 次に鳴らすべき非祝日を探す（SKIP 用）。*/
    private fun nextNonHolidayDate(days: Set<DayOfWeek>, time: LocalTime, start: LocalDateTime): LocalDateTime {
        var dt = start
        repeat(31) {
            val next = if (it == 0) dt else dt.plusDays(1).withHour(time.hour).withMinute(time.minute).withSecond(0).withNano(0)
            val isDayMatch = days.isEmpty() || days.contains(next.dayOfWeek)
            if (isDayMatch && !holidays.isHoliday(next.toLocalDate()) && next.isAfter(LocalDateTime.now())) return next
            dt = next
        }
        return start.plusDays(1)
    }

    /** 次の祝日の候補日時（指定時刻）を返す。*/
    private fun nextHolidayDateTime(time: LocalTime, now: LocalDateTime): LocalDateTime {
        var date = now.toLocalDate()
        repeat(370) {
            val candidate = LocalDateTime.of(date, time)
            if (holidays.isHoliday(date) && candidate.isAfter(now)) return candidate
            date = date.plusDays(1)
        }
        return LocalDateTime.of(now.toLocalDate().plusDays(1), time)
    }

    /** 事前天気取得のスケジュール（WorkManager, 一意キー: prefetch-<id>）。*/
private fun schedulePrefetchInternal(spec: AlarmSpec, triggerAt: LocalDateTime) {
        val prefetchAt = triggerAt.minusMinutes(spec.prefetchMinutes.toLong().coerceAtLeast(0))
        val delayMs = (Duration.between(LocalDateTime.now(), prefetchAt).toMillis()).coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<WeatherFetchWorker>()
            .setInitialDelay(delayMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("prefetch-${spec.id}", ExistingWorkPolicy.REPLACE, request)
    }

    /** DataStore から DELAY 分数を読み出す（デフォルト 60）。*/
    private fun readDelayMinutes(): Int = kotlinx.coroutines.runBlocking {
        val key = androidx.datastore.preferences.core.intPreferencesKey(
            io.github.whitenoise0000.shukutenalarm.data.PreferencesKeys.KEY_DELAY_MINUTES
        )
        prefs.data.map { prefs -> prefs[key] ?: 60 }.first()
    }

    /** 指定 ID のアラームをキャンセルし、関連する先読み Work も取り消す。*/
    fun cancel(alarmId: Int) {
        // WorkManager の unique work をキャンセル
        WorkManager.getInstance(context).cancelUniqueWork("prefetch-$alarmId")

        // AlarmManager の PendingIntent をキャンセル
        val ringIntent = PendingIntent.getBroadcast(
            context,
            alarmId,
            Intent(context, AlarmReceiver::class.java)
                .setAction(AlarmGateway.ACTION_ALARM_FIRE)
                .putExtra(AlarmGateway.EXTRA_ALARM_ID, alarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        context.getSystemService(AlarmManager::class.java)?.cancel(ringIntent)
        ringIntent.cancel()
    }
}
