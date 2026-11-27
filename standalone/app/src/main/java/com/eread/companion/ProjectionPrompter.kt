package com.eread.companion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** 后台无法直接拉起授权页时，用通知给用户一个可点击的入口。 */
object ProjectionPrompter {

    private const val CHANNEL = "projection_auth"
    private const val ID = 92

    fun cancel(context: Context) {
        runCatching { context.getSystemService(NotificationManager::class.java)?.cancel(ID) }
    }

    fun prompt(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "录屏截屏授权", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val intent = Intent(context, ProjectionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, CHANNEL)
            .setContentTitle("允许 Eread 录屏截屏")
            .setContentText("点击后选择「立即开始」，即可使用截屏识别")
            .setSmallIcon(R.drawable.ic_search)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(ID, notification) }
    }
}
