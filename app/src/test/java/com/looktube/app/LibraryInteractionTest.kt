package com.looktube.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.looktube.designsystem.LookTubeTheme
import com.looktube.feature.library.LibraryRoute
import com.looktube.model.*
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xhdpi")
class LibraryInteractionTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before fun resetBrowsePreferences() {
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("looktube.library.browse", 0).edit().clear().commit()
    }

    private val videos = (0 until 20).map { episode ->
        VideoSummary(id = "episode-$episode", title = "Episode $episode", description = "A long description ".repeat(150),
            isPremium = true, feedCategory = "Premium", playbackUrl = null, seriesTitle = "First Show", publishedAtEpochMillis = 2_000L)
    } + (0 until 16).map { group ->
        VideoSummary(id = "other-$group", title = "Other episode $group", description = "Other details",
            isPremium = true, feedCategory = "Premium", playbackUrl = null, seriesTitle = "Other Show ${('A'.code + group).toChar()}", publishedAtEpochMillis = 1_000L - group)
    }

    @Composable private fun Library(
        progress: Map<String, PlaybackProgress> = emptyMap(),
        engagement: Map<String, VideoEngagementRecord> = emptyMap(),
    ) {
        LookTubeTheme {
            LibraryRoute(
                paddingValues = PaddingValues(), syncState = LibrarySyncState(SyncPhase.Success, "Synced"), hasSavedFeedUrl = true,
                videos = videos, playbackProgress = progress, videoEngagement = engagement, seriesCompletionSummaries = emptyMap(),
                onVideoSelected = {}, onMarkVideoWatched = {}, onMarkVideoUnwatched = {},
                onMarkVideosWatched = {}, onMarkVideosUnwatched = {},
            )
        }
    }

    private fun firstHeader() = composeRule.onNodeWithTag("library-section-First Show")
    private fun goToFirstHeader() = composeRule.onNodeWithTag("library-list").performScrollToIndex(2)
    private fun assertCollapsed() = firstHeader().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))

    @Test fun collapsedGroupSurvivesScrollingAwayAndPinnedTopReturnsDirectly() {
        composeRule.setContent { Library() }
        goToFirstHeader()
        firstHeader().performClick()
        composeRule.onNodeWithTag("library-list").performScrollToNode(hasTestTag("library-section-Other Show P"))
        composeRule.onNodeWithTag("library-top").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("library-search").assertIsDisplayed()
        goToFirstHeader()
        assertCollapsed()
    }

    @Test fun hidingGroupsWithSearchAndClearingItKeepsCollapseChoice() {
        composeRule.setContent { Library() }
        goToFirstHeader()
        firstHeader().performClick()
        composeRule.onNodeWithTag("library-top").performClick()
        composeRule.onNodeWithTag("library-search").performTextInput("Other Show")
        firstHeader().assertDoesNotExist()
        composeRule.onNodeWithTag("library-reset-filters").performClick()
        goToFirstHeader()
        assertCollapsed()
    }

    @Test fun savedStateRestoresSearchAndCollapseChoice() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { Library() }
        goToFirstHeader()
        firstHeader().performClick()
        composeRule.onNodeWithTag("library-top").performClick()
        composeRule.onNodeWithTag("library-search").performTextInput("Other Show")
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag("library-search").assertTextContains("Other Show")
        composeRule.onNodeWithTag("library-reset-filters").performClick()
        goToFirstHeader()
        assertCollapsed()
    }

    @Test fun sectionIndexCanScrollWithoutHidingPinnedTop() {
        composeRule.setContent { Library() }
        goToFirstHeader()
        composeRule.onNodeWithTag("library-index-toggle").performClick()
        val topPosition = composeRule.onNodeWithTag("library-top").getUnclippedBoundsInRoot().top
        composeRule.onNodeWithTag("library-section-index").performScrollToNode(hasText("Other Show P"))
        assertEquals(topPosition, composeRule.onNodeWithTag("library-top").getUnclippedBoundsInRoot().top)
        composeRule.onNodeWithTag("library-top").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("library-search").assertIsDisplayed()
        composeRule.onNodeWithTag("library-section-index").assertDoesNotExist()
    }

    @Test fun longExpandedGroupDoesNotComposeItsLastEpisodeOffscreen() {
        composeRule.setContent { Library() }
        goToFirstHeader()
        composeRule.onNodeWithText("Episode 19").assertDoesNotExist()
    }

    @Test fun watchFiltersCombineWithSearchAndResetToTheWholeLibrary() {
        composeRule.setContent {
            Library(
                progress = mapOf("episode-1" to PlaybackProgress("episode-1", 30, 600)),
                engagement = mapOf("episode-0" to VideoEngagementRecord("episode-0", manualWatchState = ManualWatchState.Watched)),
            )
        }
        composeRule.onNodeWithTag("library-watch-Watched").performClick()
        composeRule.onNodeWithText("1 of 36 videos").assertIsDisplayed()
        composeRule.onNodeWithTag("library-search").performTextInput("Other Show")
        composeRule.onNodeWithText("0 of 36 videos").assertIsDisplayed()
        composeRule.onNodeWithTag("library-reset-filters").performClick()
        composeRule.onNodeWithTag("library-watch-InProgress").performClick()
        composeRule.onNodeWithText("1 of 36 videos").assertIsDisplayed()
        composeRule.onNodeWithTag("library-watch-Unwatched").performClick()
        composeRule.onNodeWithText("35 of 36 videos").assertIsDisplayed()
        composeRule.onNodeWithTag("library-reset-filters").performClick()
        composeRule.onNodeWithText("36 of 36 videos").assertIsDisplayed()
    }

    @Test fun collapseChoiceSurvivesSwitchingGroupingOffAndBack() {
        composeRule.setContent { Library() }
        goToFirstHeader()
        firstHeader().performClick()
        composeRule.onNodeWithTag("library-top").performClick()
        composeRule.onNodeWithText("Browse options").performClick()
        composeRule.onNodeWithTag("browse-dropdown-Group By").performScrollTo().performClick()
        composeRule.onNode(hasText("None") and hasAnyAncestor(isPopup()), useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("browse-dropdown-Group By").performClick()
        composeRule.onNode(hasText("Show") and hasAnyAncestor(isPopup()), useUnmergedTree = true).performClick()
        goToFirstHeader()
        assertCollapsed()
    }

    @Test fun scrollTrackSupportsAccessibilityProgressWithoutLosingTop() {
        composeRule.setContent { Library() }
        goToFirstHeader()
        composeRule.onNodeWithTag("library-scroll-track").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(1f) }
        composeRule.onNodeWithTag("library-top").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("library-search").assertIsDisplayed()
    }

    @Test fun longDescriptionCanScrollWhileCloseStaysReachable() {
        composeRule.setContent { Library() }
        composeRule.onNodeWithTag("library-list").performScrollToNode(hasTestTag("library-details-episode-0"))
        composeRule.onNodeWithTag("library-details-episode-0").performClick()
        composeRule.onNodeWithTag("video-details-scroll").performTouchInput { swipeUp() }
        composeRule.onNodeWithText("Close").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("video-details-scroll").assertDoesNotExist()
    }

    @Test fun collapseChoiceSurvivesLeavingCompositionWithoutSavedState() {
        val visible = mutableStateOf(true)
        composeRule.setContent { if (visible.value) Library() }
        goToFirstHeader()
        firstHeader().performClick()
        composeRule.runOnIdle { visible.value = false }
        composeRule.waitForIdle()
        composeRule.runOnIdle { visible.value = true }
        goToFirstHeader()
        assertCollapsed()
    }
}
