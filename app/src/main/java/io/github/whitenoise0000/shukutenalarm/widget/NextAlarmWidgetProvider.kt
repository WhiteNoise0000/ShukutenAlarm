package io.github.whitenoise0000.shukutenalarm.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast
import androidx.datastore.preferences.core.edit
import io.github.whitenoise0000.shukutenalarm.R
import io.github.whitenoise0000.shukutenalarm.alarm.ScheduleManager
import io.github.whitenoise0000.shukutenalarm.data.DataStoreAlarmRepository
import io.github.whitenoise0000.shukutenalarm.data.PreferencesKeys
import io.github.whitenoise0000.shukutenalarm.data.appDataStore
import io.github.whitenoise0000.shukutenalarm.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 2x1 次回アラームウィジェットの AppWidgetProvider。
 * - 次回アラームの日時を表示。
 * - 「1回スキップ」ボタンで直近のアラームを次回以降へ送る。
 */
class NextAlarmWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> updateSingle(context, appWidgetManager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_SKIP_ONCE -> handleSkipOnce(context)
            ACTION_REFRESH -> refreshAll(context)
        }
    }

    /** 単一ウィジェットの更新。 */
    private fun updateSingle(context: Context, mgr: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_next_alarm_2x1)

        val calc = NextAlarmCalculator(context)
        val next = calc.findNext()

        if (next == null) {
            views.setTextViewText(R.id.text_title, context.getString(R.string.widget_next_alarm_title))
            views.setTextViewText(R.id.text_time, context.getString(R.string.widget_no_alarm))
            views.setTextViewText(R.id.text_sub, "")
            views.setViewVisibility(R.id.text_alarm_name, android.view.View.GONE)
        } else {
            val timeFmt = DateTimeFormatter.ofPattern("H:mm", Locale.JAPAN)
            val dateFmt = DateTimeFormatter.ofPattern("M/d(E)", Locale.JAPAN)
            val dt = next.dateTime
            views.setTextViewText(R.id.text_title, context.getString(R.string.widget_next_alarm_title))
            views.setTextViewText(R.id.text_time, dt.toLocalTime().format(timeFmt))
            val holidayMark = if (next.isHoliday) " [" + context.getString(R.string.label_holiday) + "]" else ""
            views.setTextViewText(R.id.text_sub, dt.toLocalDate().format(dateFmt) + holidayMark)

            if (next.spec.name.isNotBlank()) {
                views.setTextViewText(R.id.text_alarm_name, next.spec.name)
                views.setViewVisibility(R.id.text_alarm_name, android.view.View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.text_alarm_name, android.view.View.GONE)
            }
        }

        // 本体タップでアプリ起動
        val openIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.root, openIntent)

        // スキップボタン
        val skipIntent = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, NextAlarmWidgetProvider::class.java).setAction(ACTION_SKIP_ONCE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.button_skip, skipIntent)

        // タイトルタップで明示更新
        val refreshIntent = PendingIntent.getBroadcast(
            context, 2,
            Intent(context, NextAlarmWidgetProvider::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.text_title, refreshIntent)

        mgr.updateAppWidget(widgetId, views)
    }

    /** 全ウィジェットを更新。 */
    private fun refreshAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, NextAlarmWidgetProvider::class.java))
        onUpdate(context, mgr, ids)
    }

    /** 1回スキップを処理。 */
    private fun handleSkipOnce(context: Context) {
        CoroutineScope(Dispatchers.Default).launch {
            val calc = NextAlarmCalculator(context)
            val next = calc.findNext()
            if (next == null) {
                showToast(context, context.getString(R.string.toast_no_alarm))
                refreshAll(context)
                return@launch
            }

            val sched = ScheduleManager(context)
            sched.cancel(next.spec.id)
            // skip_until_<id> に直近の日時を保存（次回計算で除外）
            val key = androidx.datastore.preferences.core.longPreferencesKey(PreferencesKeys.KEY_SKIP_UNTIL_PREFIX + next.spec.id)
            val until = next.dateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            context.appDataStore.edit { it[key] = until }
            sched.scheduleNext(runBlocking { DataStoreAlarmRepository(context).load(next.spec.id) } ?: next.spec)

            showToast(context, context.getString(R.string.toast_skip_done))
            refreshAll(context)
        }
    }

    /** トーストは必ずメインスレッドにポストし、失敗しても握り潰す。 */
    private fun showToast(context: Context, msg: String) {
        val appCtx = context.applicationContext
        val main = android.os.Looper.getMainLooper()
        runCatching {
            if (android.os.Looper.myLooper() == main) {
                Toast.makeText(appCtx, msg, Toast.LENGTH_SHORT).show()
            } else {
                android.os.Handler(main).post { runCatching { Toast.makeText(appCtx, msg, Toast.LENGTH_SHORT).show() } }
            }
        }
    }

    companion object {
        const val ACTION_SKIP_ONCE = "io.github.whitenoise0000.shukutenalarm.action.WIDGET_SKIP_ONCE"
        const val ACTION_REFRESH = "io.github.whitenoise0000.shukutenalarm.action.WIDGET_REFRESH"
    }
}

