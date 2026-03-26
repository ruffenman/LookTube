package com.looktube.app

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackServiceTest {
    @Test
    fun playbackConfigRequestsExclusiveMediaFocusForVideo() {
        assertEquals(C.USAGE_MEDIA, PLAYBACK_AUDIO_ATTRIBUTES.usage)
        assertEquals(C.AUDIO_CONTENT_TYPE_MOVIE, PLAYBACK_AUDIO_ATTRIBUTES.contentType)
        assertTrue(PLAYBACK_HANDLES_AUDIO_FOCUS)
        assertTrue(PLAYBACK_HANDLES_AUDIO_BECOMING_NOISY)
    }

    @Test
    fun configureLocalPlayerAppliesAudioFocusAndNoisyHandling() {
        val fakePlayer = FakeLocalPlaybackConfigurable()

        configureLocalPlayerForPlayback(fakePlayer)

        assertNotNull(fakePlayer.audioAttributes)
        assertEquals(PLAYBACK_AUDIO_ATTRIBUTES, fakePlayer.audioAttributes)
        assertEquals(PLAYBACK_HANDLES_AUDIO_FOCUS, fakePlayer.handleAudioFocus)
        assertEquals(PLAYBACK_HANDLES_AUDIO_BECOMING_NOISY, fakePlayer.handleAudioBecomingNoisy)
    }

    @Test
    fun skipsRemoteTransferWhenCastSessionAlreadyHasActiveMatchingMedia() {
        val snapshot = playbackSnapshot("video-1", positionMs = 125_000L)

        assertFalse(
            shouldTransferPlaybackToRemoteSession(
                remoteCurrentMediaId = "video-1",
                remotePlaybackState = Player.STATE_READY,
                remotePositionMs = 130_000L,
                snapshot = snapshot,
            ),
        )
    }

    @Test
    fun transfersRemotePlaybackWhenCastSessionIsIdle() {
        val snapshot = playbackSnapshot("video-1", positionMs = 125_000L)

        assertTrue(
            shouldTransferPlaybackToRemoteSession(
                remoteCurrentMediaId = null,
                remotePlaybackState = Player.STATE_IDLE,
                remotePositionMs = 0L,
                snapshot = snapshot,
            ),
        )
    }

    @Test
    fun skipsLocalRestoreWhenLocalPlayerAlreadyRecoveredTheSnapshot() {
        val snapshot = playbackSnapshot("video-1", positionMs = 125_000L)

        assertFalse(
            shouldRestoreLocalPlaybackFromSnapshot(
                localCurrentMediaId = "video-1",
                localPlaybackState = Player.STATE_READY,
                localPositionMs = 130_000L,
                snapshot = snapshot,
            ),
        )
    }

    @Test
    fun restoresLocalPlaybackWhenPlayerIsIdleAfterCastDisconnect() {
        val snapshot = playbackSnapshot("video-1", positionMs = 125_000L)

        assertTrue(
            shouldRestoreLocalPlaybackFromSnapshot(
                localCurrentMediaId = "video-1",
                localPlaybackState = Player.STATE_IDLE,
                localPositionMs = 0L,
                snapshot = snapshot,
            ),
        )
    }
}

private fun playbackSnapshot(
    mediaId: String,
    positionMs: Long,
): PlaybackSnapshot = PlaybackSnapshot(
    mediaItem = MediaItem.Builder()
        .setMediaId(mediaId)
        .build(),
    positionMs = positionMs,
    playWhenReady = true,
)

private class FakeLocalPlaybackConfigurable : LocalPlaybackConfigurable {
    var audioAttributes: androidx.media3.common.AudioAttributes? = null
    var handleAudioFocus: Boolean? = null
    var handleAudioBecomingNoisy: Boolean? = null

    override fun setAudioAttributes(
        audioAttributes: androidx.media3.common.AudioAttributes,
        handleAudioFocus: Boolean,
    ) {
        this.audioAttributes = audioAttributes
        this.handleAudioFocus = handleAudioFocus
    }

    override fun setHandleAudioBecomingNoisy(handleAudioBecomingNoisy: Boolean) {
        this.handleAudioBecomingNoisy = handleAudioBecomingNoisy
    }
}
