package com.looktube.app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.looktube.designsystem.LookTubeTheme
import com.looktube.feature.player.CaptionDeleteAction
import com.looktube.feature.player.AutoplaySettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xhdpi")
class PlayerInteractionTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun deletingCaptionDataRequiresConfirmationForTheSelectedVideo() {
        val deleted = mutableListOf<String>()
        composeRule.setContent {
            LookTubeTheme { CaptionDeleteAction("video", "A video", false, true) { deleted += it } }
        }
        composeRule.onNodeWithText("Delete partial data").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.runOnIdle { assertEquals(emptyList<String>(), deleted) }
        composeRule.onNodeWithText("Delete partial data").performClick()
        composeRule.onNodeWithText("Delete captions").performClick()
        composeRule.runOnIdle { assertEquals(listOf("video"), deleted) }
    }

    @Test fun startingGenerationDismissesDeletionAndDisablesTheAction() {
        val enabled = mutableStateOf(true)
        val deleted = mutableListOf<String>()
        composeRule.setContent {
            LookTubeTheme { CaptionDeleteAction("video", "A video", true, enabled.value) { deleted += it } }
        }
        composeRule.onNodeWithText("Delete caption data").performClick()
        composeRule.runOnIdle { enabled.value = false }
        composeRule.onNodeWithText("Delete captions?").assertDoesNotExist()
        composeRule.onNodeWithText("Delete caption data").assertIsNotEnabled()
        composeRule.runOnIdle { assertEquals(emptyList<String>(), deleted) }
    }

    @Test fun autoplayToggleUpdatesAndExplicitNextPlayRemainsAvailableWhenOff() {
        val enabled = mutableStateOf(true)
        var nextRequests = 0
        composeRule.setContent {
            LookTubeTheme {
                AutoplaySettings(enabled.value, { enabled.value = it }, "Episode 2", "Unwatched continuation",
                    showNextVideo = true, onPlayNext = { nextRequests++ })
            }
        }
        composeRule.onNodeWithTag("autoplay-toggle").assertIsOn().performClick().assertIsOff()
        composeRule.onNodeWithText("Autoplay is off. Playback stops after this video.").assertExists()
        composeRule.onNodeWithText("Play next now").performClick()
        composeRule.runOnIdle { assertEquals(1, nextRequests) }
    }

    @Test fun exhaustedLibraryShowsNoNextPlayAction() {
        composeRule.setContent {
            LookTubeTheme { AutoplaySettings(true, {}, showNextVideo = true) }
        }
        composeRule.onNodeWithText("No unwatched playable videos available").assertExists()
        composeRule.onNodeWithText("Play next now").assertDoesNotExist()
    }
}
