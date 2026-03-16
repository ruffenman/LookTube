package com.looktube.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.looktube.designsystem.LookTubeTheme
import com.looktube.feature.auth.AuthRoute
import com.looktube.model.AccountSession
import com.looktube.model.FeedConfiguration
import com.looktube.model.LibrarySyncState
import com.looktube.model.SyncPhase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = "w412dp-h915dp-xhdpi",
)
class AuthVisualTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun authSetupSurface() {
        composeRule.setContent {
            LookTubeTheme {
                AuthRoute(
                    paddingValues = PaddingValues(),
                    accountSession = AccountSession(
                        isSignedIn = false,
                        accountLabel = null,
                        authMode = null,
                        notes = "Paste a copied Premium feed URL to begin.",
                    ),
                    feedConfiguration = FeedConfiguration(
                        authMode = null,
                        feedUrl = "",
                        username = "",
                        password = "",
                    ),
                    syncState = LibrarySyncState(
                        phase = SyncPhase.Idle,
                        message = "Paste a copied Premium RSS URL to start syncing your library.",
                    ),
                    onFeedUrlChanged = {},
                    onUsernameChanged = {},
                    onPasswordChanged = {},
                    onSignInRequested = {},
                    onSignOutRequested = {},
                )
            }
        }

        composeRule.onRoot().captureRoboImage()
    }

    @Test
    fun authSyncedSurface() {
        composeRule.setContent {
            LookTubeTheme {
                AuthRoute(
                    paddingValues = PaddingValues(),
                    accountSession = AccountSession(
                        isSignedIn = true,
                        accountLabel = "Premium library synced",
                        authMode = null,
                        notes = "Using copied Giant Bomb Premium feed URL.",
                    ),
                    feedConfiguration = FeedConfiguration(
                        authMode = null,
                        feedUrl = "https://www.giantbomb.com/feeds/premium-videos/?token=feed-token",
                        username = "premium-user",
                        password = "session-only-secret",
                    ),
                    syncState = LibrarySyncState(
                        phase = SyncPhase.Success,
                        message = "Synced 42 Premium videos. Last refresh completed successfully.",
                        lastSuccessfulSyncSummary = "42 videos • 12 shows",
                    ),
                    onFeedUrlChanged = {},
                    onUsernameChanged = {},
                    onPasswordChanged = {},
                    onSignInRequested = {},
                    onSignOutRequested = {},
                )
            }
        }

        composeRule.onRoot().captureRoboImage()
    }
}
