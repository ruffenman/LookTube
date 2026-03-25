package com.looktube.app

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
}

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
