package io.github.whitenoise0000.shukutenalarm.platform

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.provider.Settings
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.whitenoise0000.shukutenalarm.R

/**
 * 通知関連のユーティリティ。
 * - アラーム用チャンネル（IMPORTANCE_HIGH）を作成。
 * - フルスクリーン通知で鳴動画面を前面化する。
 */
 object Notifications {
    const val CHANNEL_ALARM = "ALARM"

    /** アラーム用の通知チャンネルを作成する。 */
    fun ensureChannels(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ALARM) == null) {
            val channel = NotificationChannel(
                CHANNEL_ALARM,
                context.getString(R.string.notif_channel_alarm_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                description = context.getString(R.string.notif_channel_alarm_desc)
            }
            mgr.createNotificationChannel(channel)
        }
    }

    /** FSIの設定画面を開く（ユーザーがオフにしている場合の誘導用） */
    fun openFullScreenIntentSettings(context: Context) {
        val i = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(i)
    }

    /**
     * フルスクリーン通知を表示して鳴動画面へ。
     * POST_NOTIFICATIONS は別途アプリ側でリクエスト必須（Android 13+）
     */
    @SuppressLint("FullScreenIntentPolicy")
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showAlarmFullScreen(context: Context, notificationId: Int, activityIntent: Intent) {
        ensureChannels(context)

        // FSIが無効なら通常通知に格下げされる（画面OFFでも全画面にならない）
        // FSIが有効でも、画面がONの場合はヘッズアップ通知となる
        val content = PendingIntent.getActivity(
            context, notificationId, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notif_alarm_ringing))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(content)
            .setFullScreenIntent(content, true)

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }
}
