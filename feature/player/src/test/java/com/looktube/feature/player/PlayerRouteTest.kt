package com.looktube.feature.player

import android.view.View
import android.widget.FrameLayout
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Player
import com.looktube.model.PlaybackProgress
import com.looktube.model.RecentPlaybackVideo
import com.looktube.model.VideoSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PlayerRouteTest {
    @Test
    fun keepsScreenOnWhilePlaybackIsActive() {
        assertTrue(
            shouldKeepScreenOn(
                isPlaying = true,
                playWhenReady = true,
                playbackState = Player.STATE_READY,
            ),
        )
    }

    @Test
    fun remotePlaybackKeepsControllerVisible() {
        assertTrue(persistentControllerVisibilityForRemotePlayback(isRemotePlayback = true))
        assertEquals(0, controllerShowTimeoutMsForRemotePlayback(isRemotePlayback = true))
        assertFalse(controllerHideOnTouchForRemotePlayback(isRemotePlayback = true))
    }

    @Test
    fun localPlaybackKeepsTransientControllerBehavior() {
        assertFalse(persistentControllerVisibilityForRemotePlayback(isRemotePlayback = false))
        assertEquals(5_000, controllerShowTimeoutMsForRemotePlayback(isRemotePlayback = false))
        assertTrue(controllerHideOnTouchForRemotePlayback(isRemotePlayback = false))
    }

    @Test
    fun keepsScreenOnWhilePlaybackIsBufferingForResume() {
        assertTrue(
            shouldKeepScreenOn(
                isPlaying = false,
                playWhenReady = true,
                playbackState = Player.STATE_BUFFERING,
            ),
        )
    }

    @Test
    fun doesNotKeepScreenOnWhenPlaybackIsPaused() {
        assertFalse(
            shouldKeepScreenOn(
                isPlaying = false,
                playWhenReady = false,
                playbackState = Player.STATE_READY,
            ),
        )
    }

    @Test
    fun doesNotKeepScreenOnWhenPlaybackHasEnded() {
        assertFalse(
            shouldKeepScreenOn(
                isPlaying = false,
                playWhenReady = true,
                playbackState = Player.STATE_ENDED,
            ),
        )
    }

    @Test
    fun doesNotKeepScreenOnDuringRemotePlayback() {
        assertFalse(
            shouldKeepScreenOn(
                isPlaying = true,
                playWhenReady = true,
                playbackState = Player.STATE_READY,
                isRemotePlayback = true,
            ),
        )
    }

    @Test
    fun doubleTapOnLeftHalfSeeksBackward() {
        assertEquals(
            DoubleTapSeekDirection.Backward,
            doubleTapSeekDirection(
                tapX = 40f,
                surfaceWidthPx = 200,
            ),
        )
    }

    @Test
    fun doubleTapOnRightHalfSeeksForward() {
        assertEquals(
            DoubleTapSeekDirection.Forward,
            doubleTapSeekDirection(
                tapX = 160f,
                surfaceWidthPx = 200,
            ),
        )
    }

    @Test
    fun doubleTapDirectionIsUnknownWithoutSurfaceWidth() {
        assertEquals(null, doubleTapSeekDirection(tapX = 50f, surfaceWidthPx = 0))
    }

    @Test
    fun invalidSeekIncrementFallsBackToDefaultFeedbackDuration() {
        assertEquals(10L, resolveDoubleTapSeekIncrementSeconds(0L))
    }

    @Test
    fun seekIncrementRoundsUpToWholeSecondsForFeedback() {
        assertEquals(11L, resolveDoubleTapSeekIncrementSeconds(10_001L))
    }

    @Test
    fun skipFeedbackLabelUsesPluralization() {
        assertEquals("1 second", doubleTapSeekFeedbackLabel(1L))
        assertEquals("10 seconds", doubleTapSeekFeedbackLabel(10L))
    }

    @Test
    fun detectsRemotePlaybackFromDeviceInfo() {
        assertTrue(
            isRemotePlayback(
                DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).build(),
            ),
        )
        assertFalse(
            isRemotePlayback(
                DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_LOCAL).build(),
            ),
        )
    }

    @Test
    fun remotePlaybackTitleUsesDeviceNameWhenAvailable() {
        assertEquals("Casting to Living Room TV", remotePlaybackTitle("Living Room TV"))
        assertEquals("Casting video", remotePlaybackTitle(null))
    }
    @Test
    fun remotePlaybackBadgeBodyStaysCompact() {
        assertEquals(
            "Syncing to your cast device",
            remotePlaybackBadgeBody(
                isPlaying = false,
                playWhenReady = true,
                playbackState = Player.STATE_BUFFERING,
            ),
        )
        assertEquals(
            "Remote playback is active",
            remotePlaybackBadgeBody(
                isPlaying = true,
                playWhenReady = true,
                playbackState = Player.STATE_READY,
            ),
        )
    }

    @Test
    fun remotePlaybackBodyReflectsPlaybackState() {
        assertEquals(
            "LookTube is syncing playback to your cast device.",
            remotePlaybackBody(
                isPlaying = false,
                playWhenReady = true,
                playbackState = Player.STATE_BUFFERING,
            ),
        )
        assertEquals(
            "Video is playing on your cast device. Use the standard player controls here to pause, resume, or stop casting.",
            remotePlaybackBody(
                isPlaying = true,
                playWhenReady = true,
                playbackState = Player.STATE_READY,
            ),
        )
    }

    @Test
    fun compactResumeSummaryStaysShort() {
        assertEquals(
            "Resume • 20:50 / 1:10:00",
            compactResumeSummary(
                PlaybackProgress(
                    videoId = "video-1",
                    positionSeconds = 1_250,
                    durationSeconds = 4_200,
                ),
            ),
        )
    }

    @Test
    fun recentPlaybackMenuSubtitleIncludesResumeAndWatchState() {
        assertEquals(
            "Quick Look • Resume 20:50 • Watched",
            recentPlaybackMenuSubtitle(
                RecentPlaybackVideo(
                    video = VideoSummary(
                        id = "video-1",
                        title = "Quick Look Test",
                        description = "",
                        isPremium = true,
                        feedCategory = "Premium",
                        playbackUrl = "https://video.example.com/video-1.mp4",
                        seriesTitle = "Quick Look",
                    ),
                    playbackProgress = PlaybackProgress(
                        videoId = "video-1",
                        positionSeconds = 1_250,
                        durationSeconds = 4_200,
                    ),
                    isWatched = true,
                    lastPlayedAtEpochMillis = 1_000L,
                ),
            ),
        )
    }

    @Test
    fun layoutSnapshotCaptureAndRestoreUseValidTagKeyWithoutCrashing() {
        val context = RuntimeEnvironment.getApplication()
        val view = View(context)
        view.layoutParams = FrameLayout.LayoutParams(120, 60).apply {
            leftMargin = 7
            topMargin = 11
            rightMargin = 13
            bottomMargin = 17
        }
        view.setPadding(2, 3, 5, 7)

        view.captureLayoutSnapshotIfNeeded()

        view.layoutParams = (view.layoutParams as FrameLayout.LayoutParams).apply {
            width = 40
            height = 24
            leftMargin = 1
            topMargin = 1
            rightMargin = 1
            bottomMargin = 1
        }
        view.setPadding(19, 23, 29, 31)

        view.restoreLayoutSnapshotIfNeeded()

        val restoredLayoutParams = view.layoutParams as FrameLayout.LayoutParams
        assertEquals(120, restoredLayoutParams.width)
        assertEquals(60, restoredLayoutParams.height)
        assertEquals(7, restoredLayoutParams.leftMargin)
        assertEquals(11, restoredLayoutParams.topMargin)
        assertEquals(13, restoredLayoutParams.rightMargin)
        assertEquals(17, restoredLayoutParams.bottomMargin)
        assertEquals(2, view.paddingLeft)
        assertEquals(3, view.paddingTop)
        assertEquals(5, view.paddingRight)
        assertEquals(7, view.paddingBottom)
    }

}
