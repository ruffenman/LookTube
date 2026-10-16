package com.looktube.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.looktube.designsystem.LookTubeTheme
import com.looktube.feature.auth.AuthRoute
import com.looktube.feature.auth.CaptionDataManagementItem
import com.looktube.model.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xhdpi")
class SettingsInteractionTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    private var libraryClears = 0
    private var captionClears = 0

    private fun showSettings(phase: SyncPhase = SyncPhase.Success) {
        composeRule.setContent {
            LookTubeTheme {
                AuthRoute(
                    paddingValues = PaddingValues(), accountSession = AccountSession(true, "Saved feed", "Cached library available"),
                    feedConfiguration = FeedConfiguration(feedUrl = "https://example.com/feed"),
                    syncState = LibrarySyncState(phase, "Refresh status", "Cached videos available"),
                    availableLocalCaptionEngines = listOf(WhisperCppLocalCaptionEngine), selectedLocalCaptionEngine = WhisperCppLocalCaptionEngine,
                    localCaptionModelState = LocalCaptionModelState(),
                    captionDataItems = listOf(CaptionDataManagementItem("video", "Video", "Complete", "Generated captions")),
                    onFeedUrlChanged = {}, onAutoGenerateCaptionsForNewVideosChanged = {}, onSignInRequested = {},
                    onLocalCaptionEngineSelected = {}, onDownloadLocalCaptionModel = {}, onOpenCaptionDataVideoRequested = {},
                    onClearSyncedDataRequested = { libraryClears++ }, onClearCaptionDataRequested = { captionClears++ },
                )
            }
        }
    }

    @Test fun cachedLibraryDoesNotHideRefreshError() {
        showSettings(SyncPhase.Error)
        composeRule.onNodeWithText("Needs attention").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Synced").assertDoesNotExist()
    }

    @Test fun libraryClearRequiresConfirmationAndCancelDoesNothing() {
        showSettings()
        composeRule.onNodeWithText("Clear synced data").performScrollTo().performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.runOnIdle { assertEquals(0, libraryClears) }
        composeRule.onNodeWithText("Clear synced data").performClick()
        composeRule.onNodeWithText("Clear data").performClick()
        composeRule.runOnIdle { assertEquals(1, libraryClears); assertEquals(0, captionClears) }
    }

    @Test fun captionClearHasItsOwnConfirmation() {
        showSettings()
        composeRule.onNodeWithText("Clear all caption data").performScrollTo().performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.runOnIdle { assertEquals(0, captionClears) }
        composeRule.onNodeWithText("Clear all caption data").performClick()
        composeRule.onNodeWithText("Clear data").performClick()
        composeRule.runOnIdle { assertEquals(0, libraryClears); assertEquals(1, captionClears) }
    }

    @Test fun savedFeedIsHiddenUntilExplicitlyRevealed() {
        showSettings()
        composeRule.onNode(hasSetTextAction()).performScrollTo()
            .assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Password))
        composeRule.onNodeWithText("Show").performClick()
        composeRule.onNodeWithText("Hide").assertIsDisplayed()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        composeRule.onNode(hasSetTextAction()).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        composeRule.runOnIdle { assertEquals("https://example.com/feed", layouts.first().layoutInput.text.text) }
    }
}
