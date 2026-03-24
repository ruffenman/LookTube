package com.looktube.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LookTubeAppFullscreenModeTest {
    @Test
    fun suppressedLandscapeIsNotTreatedAsFullscreen() {
        assertFalse(PlayerFullscreenMode.LandscapeSuppressed.isPlayerSurfaceFullscreen())
    }

    @Test
    fun manualAndAutoLandscapeModesAreFullscreen() {
        assertTrue(PlayerFullscreenMode.Manual.isPlayerSurfaceFullscreen())
        assertTrue(PlayerFullscreenMode.AutoLandscape.isPlayerSurfaceFullscreen())
    }

    @Test
    fun backFromLandscapeFullscreenSuppressesAutoReentryWithoutKeepingFullscreen() {
        assertEquals(
            PlayerFullscreenMode.LandscapeSuppressed,
            exitFullscreenModeForBack(isLandscape = true),
        )
    }

    @Test
    fun backFromPortraitFullscreenReturnsToOff() {
        assertEquals(
            PlayerFullscreenMode.Off,
            exitFullscreenModeForBack(isLandscape = false),
        )
    }
}
