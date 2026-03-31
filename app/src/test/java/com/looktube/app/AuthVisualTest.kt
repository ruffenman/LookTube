package com.looktube.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.looktube.designsystem.LookTubeTheme
import com.looktube.feature.auth.AuthRoute
import com.looktube.feature.auth.CaptionDataManagementItem
import com.looktube.model.AccountSession
import com.looktube.model.FeedConfiguration
import com.looktube.model.LibrarySyncState
import com.looktube.model.LocalCaptionModelState
import com.looktube.model.SyncPhase
import com.looktube.model.WhisperCppLocalCaptionEngine
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

    private val sampleCaptionDataItems = listOf(
        CaptionDataManagementItem(
            videoId = "bombcast-901",
            title = "Giant Bombcast 901: Handheld Hot Takes",
            stateLabel = "Completed",
            supportingText = "Giant Bombcast • Completed • Whisper.cpp • Updated Mar 31, 1:20 PM",
        ),
        CaptionDataManagementItem(
            videoId = "dump-truck-88",
            title = "Voicemail Dump Truck 88",
            stateLabel = "Partial",
            supportingText = "Voicemail Dump Truck • Partial • Whisper.cpp • Updated Mar 31, 12:48 PM",
        ),
    )

    @Test
    fun authSetupSurface() {
        composeRule.setContent {
            LookTubeTheme {
                AuthRoute(
                    paddingValues = PaddingValues(),
                    accountSession = AccountSession(
                        isSignedIn = false,
                        accountLabel = null,
                        notes = "Paste a copied Premium feed URL to begin.",
                    ),
                    feedConfiguration = FeedConfiguration(
                        feedUrl = "",
                    ),
                    syncState = LibrarySyncState(
                        phase = SyncPhase.Idle,
                        message = "Paste a copied Premium RSS URL to start syncing your library.",
                    ),
                    availableLocalCaptionEngines = listOf(WhisperCppLocalCaptionEngine),
                    selectedLocalCaptionEngine = WhisperCppLocalCaptionEngine,
                    localCaptionModelState = LocalCaptionModelState(),
                    captionDataItems = emptyList(),
                    onFeedUrlChanged = {},
                    onAutoGenerateCaptionsForNewVideosChanged = {},
                    onSignInRequested = {},
                    onLocalCaptionEngineSelected = {},
                    onDownloadLocalCaptionModel = {},
                    onOpenCaptionDataVideoRequested = {},
                    onClearSyncedDataRequested = {},
                    onClearCaptionDataRequested = {},
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
                        accountLabel = "Copied Premium feed",
                        notes = "Using copied Giant Bomb Premium feed URL.",
                    ),
                    feedConfiguration = FeedConfiguration(
                        feedUrl = "https://www.giantbomb.com/feeds/premium-videos/?token=feed-token",
                    ),
                    syncState = LibrarySyncState(
                        phase = SyncPhase.Success,
                        message = "Synced 42 Premium videos. Last refresh completed successfully.",
                        lastSuccessfulSyncSummary = "42 videos • 12 shows",
                    ),
                    availableLocalCaptionEngines = listOf(WhisperCppLocalCaptionEngine),
                    selectedLocalCaptionEngine = WhisperCppLocalCaptionEngine,
                    localCaptionModelState = LocalCaptionModelState(
                        localPath = "/data/user/0/com.looktube.app/files/captions/models/ggml-base.en-q5_1.bin",
                    ),
                    captionDataItems = sampleCaptionDataItems,
                    onFeedUrlChanged = {},
                    onAutoGenerateCaptionsForNewVideosChanged = {},
                    onSignInRequested = {},
                    onLocalCaptionEngineSelected = {},
                    onDownloadLocalCaptionModel = {},
                    onOpenCaptionDataVideoRequested = {},
                    onClearSyncedDataRequested = {},
                    onClearCaptionDataRequested = {},
                )
            }
        }

        composeRule.onRoot().captureRoboImage()
    }

    @Test
    fun authSyncingSurface() {
        composeRule.setContent {
            LookTubeTheme {
                AuthRoute(
                    paddingValues = PaddingValues(),
                    accountSession = AccountSession(
                        isSignedIn = true,
                        accountLabel = "Copied Premium feed",
                        notes = "Refreshing your saved Giant Bomb Premium feed.",
                    ),
                    feedConfiguration = FeedConfiguration(
                        feedUrl = "https://www.giantbomb.com/feeds/premium-videos/?token=feed-token",
                    ),
                    syncState = LibrarySyncState(
                        phase = SyncPhase.Refreshing,
                        message = "Refreshing your saved Premium library from the copied feed URL.",
                        lastSuccessfulSyncSummary = "42 videos • 12 shows",
                    ),
                    availableLocalCaptionEngines = listOf(WhisperCppLocalCaptionEngine),
                    selectedLocalCaptionEngine = WhisperCppLocalCaptionEngine,
                    localCaptionModelState = LocalCaptionModelState(),
                    captionDataItems = emptyList(),
                    onFeedUrlChanged = {},
                    onAutoGenerateCaptionsForNewVideosChanged = {},
                    onSignInRequested = {},
                    onLocalCaptionEngineSelected = {},
                    onDownloadLocalCaptionModel = {},
                    onOpenCaptionDataVideoRequested = {},
                    onClearSyncedDataRequested = {},
                    onClearCaptionDataRequested = {},
                )
            }
        }

        composeRule.onRoot().captureRoboImage()
    }

    @Test
    fun authReadySurface() {
        composeRule.setContent {
            LookTubeTheme {
                AuthRoute(
                    paddingValues = PaddingValues(),
                    accountSession = AccountSession(
                        isSignedIn = false,
                        accountLabel = "Copied Premium feed",
                        notes = "Saved feed settings are ready for the next sync.",
                    ),
                    feedConfiguration = FeedConfiguration(
                        feedUrl = "https://www.giantbomb.com/feeds/premium-videos/?token=feed-token",
                    ),
                    syncState = LibrarySyncState(
                        phase = SyncPhase.Idle,
                        message = "Saved feed URL detected. Sync your library when you're ready.",
                    ),
                    availableLocalCaptionEngines = listOf(WhisperCppLocalCaptionEngine),
                    selectedLocalCaptionEngine = WhisperCppLocalCaptionEngine,
                    localCaptionModelState = LocalCaptionModelState(),
                    captionDataItems = emptyList(),
                    onFeedUrlChanged = {},
                    onAutoGenerateCaptionsForNewVideosChanged = {},
                    onSignInRequested = {},
                    onLocalCaptionEngineSelected = {},
                    onDownloadLocalCaptionModel = {},
                    onOpenCaptionDataVideoRequested = {},
                    onClearSyncedDataRequested = {},
                    onClearCaptionDataRequested = {},
                )
            }
        }

        composeRule.onRoot().captureRoboImage()
    }
}
