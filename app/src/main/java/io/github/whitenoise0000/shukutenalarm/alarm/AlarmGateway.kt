package io.github.whitenoise0000.shukutenalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.whitenoise0000.shukutenalarm.data.model.AlarmSpec
import io.github.whitenoise0000.shukutenalarm.ui.RingingActivity
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**\n * AlarmManager を用いて正確なアラームを登録するゲートウェイ。\n * Doze 中でも確実に鳴動させるため setAlarmClock を使用。\n */
class AlarmGateway(private val context: Context) {
    fun scheduleExactAlarm(spec: AlarmSpec, date: LocalDate) {
        val trigger = ZonedDateTime.of(date, spec.time, ZoneId.systemDefault())
            .toInstant().toEpochMilli()

        val ringIntent = PendingIntent.getBroadcast(
            context, spec.id,
            Intent(context, AlarmReceiver::class.java)
                .setAction(ACTION_ALARM_FIRE)
                .putExtra(EXTRA_ALARM_ID, spec.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fsiIntent = PendingIntent.getActivity(
            context, spec.id,
            Intent(context, RingingActivity::class.java)
                .putExtra(EXTRA_ALARM_ID, spec.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val info = AlarmManager.AlarmClockInfo(trigger, fsiIntent)
        context.getSystemService(AlarmManager::class.java)
            ?.setAlarmClock(info, ringIntent)
    }

    companion object {
        const val ACTION_ALARM_FIRE = "io.github.whitenoise0000.holidayalerm.action.ALARM_FIRE"
        const val EXTRA_ALARM_ID = "id"
    }
}

