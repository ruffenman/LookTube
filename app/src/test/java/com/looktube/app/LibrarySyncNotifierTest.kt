package com.looktube.app

import android.app.NotificationManager
import com.looktube.model.VideoSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class LibrarySyncNotifierTest {
    @Test
    fun notifyAboutNewVideosCreatesChannelAndDistinctNotificationsForDifferentReleases() {
        val context = RuntimeEnvironment.getApplication()
        val notifier = LibrarySyncNotifier(context)
        val notificationManager = context.getSystemService(NotificationManager::class.java)

        notifier.notifyAboutNewVideos(listOf(video(id = "video-1")))
        notifier.notifyAboutNewVideos(listOf(video(id = "video-2")))

        val notifications = shadowOf(notificationManager).allNotifications

        assertNotNull(notificationManager.getNotificationChannel("looktube.library.updates"))
        assertEquals(2, notifications.size)
        assertEquals(
            2,
            setOf(
                notifier.notificationIdFor("video-1"),
                notifier.notificationIdFor("video-2"),
            ).size,
        )
    }

    private fun video(id: String): VideoSummary = VideoSummary(
        id = id,
        title = "Video $id",
        description = "Description for $id",
        isPremium = true,
        feedCategory = "Premium",
        playbackUrl = "https://video.example.com/$id.mp4",
    )
}
