package com.looktube.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.util.UnstableApi
import androidx.work.ForegroundInfo

@UnstableApi
class BackgroundMaintenanceNotifier(
    private val context: Context,
) {
    fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Background caption work",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Ongoing LookTube background refresh and caption generation work."
            },
        )
    }

    fun foregroundInfo(
        title: String = "LookTube is working in the background",
        text: String = "Refreshing your library and generating offline captions for new videos.",
    ): ForegroundInfo {
        ensureNotificationChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    fun dismiss() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    companion object {
        private const val CHANNEL_ID = "looktube.background.maintenance"
        private const val NOTIFICATION_ID = 7_214
    }
}
