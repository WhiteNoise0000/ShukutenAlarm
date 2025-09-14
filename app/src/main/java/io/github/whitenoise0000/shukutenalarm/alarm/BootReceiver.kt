package io.github.whitenoise0000.shukutenalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.whitenoise0000.shukutenalarm.data.DataStoreAlarmRepository
import io.github.whitenoise0000.shukutenalarm.widget.NextAlarmWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * 端末起動時にアラームを再スケジュールする BroadcastReceiver。
 * - DataStore から有効なアラームを読み出し、次回分を登録する。
 * - 祝日ポリシーの詳細対応は今後拡張（DELAY の時刻調整等）。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val repo = DataStoreAlarmRepository(context)
        val scheduler = ScheduleManager(context)
        CoroutineScope(Dispatchers.Default).launch {
            val list = runCatching { repo.list() }.getOrElse { emptyList() }
            LocalDateTime.now()
            // 有効なもののみ再登録（ONE_SHOTで既に鳴動済み→enabled=false なら再登録されない）
            list.filter { it.enabled }.forEach { spec ->
                scheduler.scheduleNext(spec)
            }
            // ウィジェットへ更新通知（再起動後の次回反映）
            context.sendBroadcast(Intent(context, NextAlarmWidgetProvider::class.java).setAction(NextAlarmWidgetProvider.ACTION_REFRESH))
        }
    }
}
