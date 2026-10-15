package com.looktube.app

import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.looktube.designsystem.LookTubeTheme
import com.looktube.model.FeedConfiguration
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xhdpi")
class SplashQuotesInteractionTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun accessibleSkipDismissesImmediatelyAndTimerDoesNotDismissAgain() {
        var dismissals = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            LookTubeTheme {
                LookTubeLaunchIntroOverlay(
                    message = currentLaunchIntroMessage(FeedConfiguration(feedUrl = ""), emptyList(), null),
                    onDismiss = { dismissals++ },
                )
            }
        }
        composeRule.onNodeWithTag("launch-intro").performClick()
        composeRule.runOnIdle { assertEquals(1, dismissals) }
        composeRule.mainClock.advanceTimeBy(5_000L)
        composeRule.runOnIdle { assertEquals(1, dismissals) }
    }

    @Test
    fun introAutomaticallyDismissesAfterExistingTimerAndFade() {
        var dismissals = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            LookTubeTheme {
                LookTubeLaunchIntroOverlay(
                    message = currentLaunchIntroMessage(FeedConfiguration(feedUrl = ""), emptyList(), null),
                    onDismiss = { dismissals++ },
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(3_000L)
        composeRule.runOnIdle { assertEquals(0, dismissals) }
        composeRule.mainClock.advanceTimeBy(1_500L)
        composeRule.runOnIdle { assertEquals(1, dismissals) }
    }

    @Test
    fun sourceScreenOpensOriginalAndRoleEvidenceAndCanClose() {
        val opened = mutableListOf<String>()
        var dismissals = 0
        composeRule.setContent {
            LookTubeTheme {
                SplashQuoteSources(onDismiss = { dismissals++ }, onOpenSource = opened::add)
            }
        }
        composeRule.onNodeWithText(SplashQuoteAttributionNotice).assertIsDisplayed()
        composeRule.onAllNodesWithText("Read original source")[0].performClick()
        composeRule.onAllNodesWithText("Giant Bomb role source")[0].performClick()
        composeRule.onNodeWithText("Close").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(LaunchIntroQuotes.first().source.url, LaunchIntroQuotes.first().speaker.evidenceUrl), opened)
            assertEquals(1, dismissals)
        }
    }

    @Test
    fun sourceLinkFailureKeepsCatalogueAvailable() {
        composeRule.setContent {
            LookTubeTheme {
                SplashQuoteSources(onDismiss = {}, onOpenSource = { error("No browser") })
            }
        }
        composeRule.onAllNodesWithText("Read original source")[0].performClick()
        composeRule.onNodeWithText("Couldn't open the link. Check that a browser is installed and try again.").assertIsDisplayed()
        composeRule.onNodeWithText("Close").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w740dp-h360dp-xhdpi")
    fun catalogueRemainsScrollableAndCloseReachableInLandscapeWithLargeText() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.6f)) {
                LookTubeTheme { SplashQuoteSources(onDismiss = {}, onOpenSource = {}) }
            }
        }
        composeRule.onNodeWithText("Close").assertIsDisplayed()
        composeRule.onNodeWithTag("splash-quotes-list")
            .performScrollToNode(hasText("— ${LaunchIntroQuotes.last().speaker.name}"))
        composeRule.onNodeWithText("— ${LaunchIntroQuotes.last().speaker.name}").assertIsDisplayed()
        composeRule.onNodeWithText("Close").assertIsDisplayed()
    }
}
