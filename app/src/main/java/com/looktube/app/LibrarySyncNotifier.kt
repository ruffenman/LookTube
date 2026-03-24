package com.looktube.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import com.looktube.model.VideoSummary
import com.looktube.model.displaySeriesTitle
@UnstableApi

class LibrarySyncNotifier(
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
                "Library updates",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "New LookTube videos discovered by background refresh."
            },
        )
    }

    @SuppressLint("MissingPermission")
    fun notifyAboutNewVideos(newVideos: List<VideoSummary>) {
        val newestVideo = newVideos.firstOrNull() ?: return
        ensureNotificationChannel()
        if (!notificationsPermitted()) {
            Log.i(TAG, "Skipping library update notification because notifications are not permitted.")
            return
        }

        val title = if (newVideos.size == 1) {
            "New video discovered"
        } else {
            "${newVideos.size} new videos discovered"
        }
        val text = if (newVideos.size == 1) {
            "${newestVideo.title} • ${newestVideo.displaySeriesTitle}"
        } else {
            "${newestVideo.title} is the latest addition to your synced library."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(createOpenVideoPendingIntent(newestVideo.id))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setGroup(GROUP_KEY_LIBRARY_UPDATES)
            .build()
        val notificationId = notificationIdFor(newestVideo.id)
        Log.i(
            TAG,
            "Posting library update notification id=$notificationId count=${newVideos.size} latestVideoId=${newestVideo.id}",
        )
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private fun createOpenVideoPendingIntent(videoId: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(LookTubeLaunchContract.EXTRA_OPEN_VIDEO_ID, videoId)
                putExtra(LookTubeLaunchContract.EXTRA_TARGET_PAGE, LookTubeLaunchContract.PLAYER_PAGE_INDEX)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun notificationsPermitted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    internal fun notificationIdFor(videoId: String): Int =
        NOTIFICATION_ID_BASE + (videoId.hashCode() and NOTIFICATION_ID_MASK)

    companion object {
        private const val TAG = "LibrarySyncNotifier"
        private const val CHANNEL_ID = "looktube.library.updates"
        private const val GROUP_KEY_LIBRARY_UPDATES = "looktube.library.updates.group"
        private const val NOTIFICATION_ID_BASE = 7_213
        private const val NOTIFICATION_ID_MASK = 0x00FF_FFFF
    }
}
