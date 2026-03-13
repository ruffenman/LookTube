package com.looktube.database

import com.looktube.model.PlaybackProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryPlaybackBookmarkStoreTest {
    @Test
    fun storesPlaybackProgressByVideoId() {
        val store = InMemoryPlaybackBookmarkStore()
        val progress = PlaybackProgress(
            videoId = "premium-video-1",
            positionSeconds = 120,
            durationSeconds = 3600,
        )

        store.write(progress)

        assertEquals(progress, store.read(progress.videoId))
    }
}
