package com.looktube.app

import com.looktube.model.PlaybackProgress
import com.looktube.model.VideoSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LookTubeAppPlaybackTest {
    @Test
    fun handoffSeeksToSavedProgressWhenSwitchingToNewVideo() {
        val controller = FakePlaybackHandoffController(currentMediaId = "other-video", currentPositionMs = 0L)

        handoffSelectedPlaybackTarget(
            controller = controller,
            playbackTarget = SelectedPlaybackTarget(
                video = video(id = "video-1"),
                playbackProgress = PlaybackProgress(
                    videoId = "video-1",
                    positionSeconds = 125L,
                    durationSeconds = 600L,
                ),
            ),
        )

        assertEquals(listOf("video-1"), controller.setMediaItemIds)
        assertEquals(listOf(125_000L), controller.setMediaItemStartPositionsMs)
        assertEquals(1, controller.prepareCount)
        assertTrue(controller.seekPositionsMs.isEmpty())
        assertTrue(controller.playWhenReady)
    }

    @Test
    fun handoffSeeksSameMediaItemWhenControllerIsStillAtStart() {
        val controller = FakePlaybackHandoffController(currentMediaId = "video-1", currentPositionMs = 0L)

        handoffSelectedPlaybackTarget(
            controller = controller,
            playbackTarget = SelectedPlaybackTarget(
                video = video(id = "video-1"),
                playbackProgress = PlaybackProgress(
                    videoId = "video-1",
                    positionSeconds = 215L,
                    durationSeconds = 900L,
                ),
            ),
        )

        assertTrue(controller.setMediaItemIds.isEmpty())
        assertEquals(0, controller.prepareCount)
        assertEquals(listOf(215_000L), controller.seekPositionsMs)
        assertTrue(controller.playWhenReady)
    }

    @Test
    fun handoffDoesNotRewindSameMediaItemThatAlreadyHasProgress() {
        val controller = FakePlaybackHandoffController(currentMediaId = "video-1", currentPositionMs = 240_000L)

        handoffSelectedPlaybackTarget(
            controller = controller,
            playbackTarget = SelectedPlaybackTarget(
                video = video(id = "video-1"),
                playbackProgress = PlaybackProgress(
                    videoId = "video-1",
                    positionSeconds = 215L,
                    durationSeconds = 900L,
                ),
            ),
        )

        assertTrue(controller.setMediaItemIds.isEmpty())
        assertTrue(controller.seekPositionsMs.isEmpty())
        assertTrue(controller.playWhenReady)
    }

    private fun video(id: String): VideoSummary = VideoSummary(
        id = id,
        title = "Video $id",
        description = "Description for $id",
        isPremium = true,
        feedCategory = "Premium",
        playbackUrl = "https://video.example.com/$id.mp4",
        seriesTitle = "Quick Look",
    )
}

private class FakePlaybackHandoffController(
    override val currentMediaId: String?,
    override val currentPositionMs: Long,
) : PlaybackHandoffController {
    val setMediaItemIds = mutableListOf<String>()
    val setMediaItemStartPositionsMs = mutableListOf<Long?>()
    val seekPositionsMs = mutableListOf<Long>()
    var prepareCount = 0
    override var playWhenReady: Boolean = false
    override fun setMediaItem(mediaItem: androidx.media3.common.MediaItem, startPositionMs: Long?) {
        setMediaItemIds += mediaItem.mediaId
        setMediaItemStartPositionsMs += startPositionMs
    }

    override fun prepare() {
        prepareCount += 1
    }

    override fun seekTo(positionMs: Long) {
        seekPositionsMs += positionMs
    }
}
