package com.looktube.app

import com.looktube.model.PlaybackProgress
import com.looktube.model.VideoSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.media3.common.Player
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
                captionTrack = null,
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
                captionTrack = null,
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
                captionTrack = null,
            ),
        )

        assertTrue(controller.setMediaItemIds.isEmpty())
        assertTrue(controller.seekPositionsMs.isEmpty())
        assertTrue(controller.playWhenReady)
    }

    @Test
    fun handoffReloadsSameMediaItemWhenControllerIsIdle() {
        val controller = FakePlaybackHandoffController(
            currentMediaId = "video-1",
            currentPositionMs = 215_000L,
            playbackState = Player.STATE_IDLE,
        )

        handoffSelectedPlaybackTarget(
            controller = controller,
            playbackTarget = SelectedPlaybackTarget(
                video = video(id = "video-1"),
                playbackProgress = PlaybackProgress(
                    videoId = "video-1",
                    positionSeconds = 215L,
                    durationSeconds = 900L,
                ),
                captionTrack = null,
            ),
        )

        assertEquals(listOf("video-1"), controller.setMediaItemIds)
        assertEquals(listOf(215_000L), controller.setMediaItemStartPositionsMs)
        assertEquals(1, controller.prepareCount)
        assertTrue(controller.seekPositionsMs.isEmpty())
    }

    @Test
    fun handoffReloadsSameMediaItemWhenSelectionExplicitlyRequestsIt() {
        val controller = FakePlaybackHandoffController(
            currentMediaId = "video-1",
            currentPositionMs = 240_000L,
            playbackState = Player.STATE_READY,
        )

        handoffSelectedPlaybackTarget(
            controller = controller,
            playbackTarget = SelectedPlaybackTarget(
                video = video(id = "video-1"),
                playbackProgress = PlaybackProgress(
                    videoId = "video-1",
                    positionSeconds = 215L,
                    durationSeconds = 900L,
                ),
                captionTrack = null,
            ),
            forceReload = true,
        )

        assertEquals(listOf("video-1"), controller.setMediaItemIds)
        assertEquals(listOf(240_000L), controller.setMediaItemStartPositionsMs)
        assertEquals(1, controller.prepareCount)
        assertTrue(controller.seekPositionsMs.isEmpty())
    }

    @Test
    fun handoffCanLeavePreviewSelectionPaused() {
        val controller = FakePlaybackHandoffController(
            currentMediaId = "other-video",
            currentPositionMs = 0L,
        )

        handoffSelectedPlaybackTarget(
            controller = controller,
            playbackTarget = SelectedPlaybackTarget(
                video = video(id = "video-1"),
                playbackProgress = PlaybackProgress(
                    videoId = "video-1",
                    positionSeconds = 125L,
                    durationSeconds = 600L,
                ),
                captionTrack = null,
            ),
            requestedPlayWhenReady = false,
        )

        assertEquals(listOf("video-1"), controller.setMediaItemIds)
        assertEquals(listOf(125_000L), controller.setMediaItemStartPositionsMs)
        assertFalse(controller.playWhenReady)
    }


    @Test
    fun replaceDecisionOnlyForcesSameMediaWhenIdleEndedOrExplicit() {
        assertFalse(
            shouldReplaceMediaItemForPlaybackTarget(
                currentMediaId = "video-1",
                targetMediaId = "video-1",
                playbackState = Player.STATE_READY,
                forceReload = false,
            ),
        )
        assertTrue(
            shouldReplaceMediaItemForPlaybackTarget(
                currentMediaId = "video-1",
                targetMediaId = "video-1",
                playbackState = Player.STATE_IDLE,
                forceReload = false,
            ),
        )
        assertTrue(
            shouldReplaceMediaItemForPlaybackTarget(
                currentMediaId = "video-1",
                targetMediaId = "video-1",
                playbackState = Player.STATE_ENDED,
                forceReload = false,
            ),
        )
        assertTrue(
            shouldReplaceMediaItemForPlaybackTarget(
                currentMediaId = "video-1",
                targetMediaId = "video-1",
                playbackState = Player.STATE_READY,
                forceReload = true,
            ),
        )
    }

    @Test
    fun replaceDecisionForcesReloadWhenControllerIsStillMarkedRemoteWithoutCastSession() {
        assertTrue(
            shouldReplaceMediaItemForPlaybackTarget(
                currentMediaId = "video-1",
                targetMediaId = "video-1",
                playbackState = Player.STATE_READY,
                forceReload = false,
                isPlaybackRouteRemote = true,
                hasConnectedCastSession = false,
            ),
        )
    }

    @Test
    fun handledPlaybackSelectionRequestIsNotRetreatedAsExplicitAfterRecreation() {
        assertFalse(
            isExplicitPlaybackSelectionRequest(
                playbackSelectionRequest = 4L,
                lastHandledPlaybackSelectionRequest = 4L,
            ),
        )
    }

    @Test
    fun newerPlaybackSelectionRequestStillForcesExplicitReload() {
        assertTrue(
            isExplicitPlaybackSelectionRequest(
                playbackSelectionRequest = 5L,
                lastHandledPlaybackSelectionRequest = 4L,
            ),
        )
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
    override val playbackState: Int = Player.STATE_READY,
    override val isPlaybackRouteRemote: Boolean = false,
    override val hasConnectedCastSession: Boolean = false,
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
