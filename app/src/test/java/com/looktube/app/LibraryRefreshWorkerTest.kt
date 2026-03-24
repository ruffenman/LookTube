package com.looktube.app

import com.looktube.model.PersistedLibrarySnapshot
import com.looktube.model.VideoSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryRefreshWorkerTest {
    @Test
    fun detectsOnlyPreviouslyUnseenVideosForTheSameFeed() {
        val previousSnapshot = PersistedLibrarySnapshot(
            feedUrl = "https://example.com/feed.xml",
            videos = listOf(
                video(id = "video-1"),
                video(id = "video-2"),
            ),
            lastSuccessfulSyncSummary = "Loaded 2 items.",
        )
        val latestSnapshot = PersistedLibrarySnapshot(
            feedUrl = "https://example.com/feed.xml",
            videos = listOf(
                video(id = "video-3"),
                video(id = "video-2"),
                video(id = "video-1"),
            ),
            lastSuccessfulSyncSummary = "Loaded 3 items.",
        )

        val newVideoIds = latestSnapshot.newVideosComparedTo(previousSnapshot).map(VideoSummary::id)

        assertEquals(listOf("video-3"), newVideoIds)
    }

    @Test
    fun ignoresInitialSyncAndFeedSwitches() {
        val latestSnapshot = PersistedLibrarySnapshot(
            feedUrl = "https://example.com/next-feed.xml",
            videos = listOf(video(id = "video-3")),
            lastSuccessfulSyncSummary = "Loaded 1 item.",
        )
        val previousSnapshot = PersistedLibrarySnapshot(
            feedUrl = "https://example.com/feed.xml",
            videos = listOf(video(id = "video-1")),
            lastSuccessfulSyncSummary = "Loaded 1 item.",
        )

        assertTrue(latestSnapshot.newVideosComparedTo(previousSnapshot).isEmpty())
        assertTrue(latestSnapshot.newVideosComparedTo(null).isEmpty())
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
