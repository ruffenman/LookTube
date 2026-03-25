package com.looktube.feature.player

import android.view.View
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun showsCastChromeWhenControllerIsVisible() {
        assertTrue(shouldShowCastButtonChrome(View.VISIBLE))
    }

    @Test
    fun hidesCastChromeWhenControllerIsNotVisible() {
        assertFalse(shouldShowCastButtonChrome(View.GONE))
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
}
