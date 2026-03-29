package com.looktube.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.looktube.designsystem.LookTubeTheme
import com.looktube.feature.player.PlayerRoute
import com.looktube.model.CaptionGenerationStatus
import com.looktube.model.LocalCaptionModelState
import com.looktube.model.PlaybackProgress
import com.looktube.model.VideoSummary
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
class PlayerVisualTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun playerEmptyQueueSurface() {
        composeRule.setContent {
            LookTubeTheme {
                PlayerRoute(
                    paddingValues = PaddingValues(),
                    selectedVideo = null,
                    playbackProgress = null,
                    playbackSelectionRequest = 0L,
                    selectedVideoEngagement = null,
                    recentPlaybackVideos = emptyList(),
                    availableLocalCaptionEngines = listOf(WhisperCppLocalCaptionEngine),
                    selectedLocalCaptionEngine = WhisperCppLocalCaptionEngine,
                    localCaptionModelState = LocalCaptionModelState(),
                    selectedCaptionTrack = null,
                    selectedCaptionGenerationStatus = CaptionGenerationStatus.Idle,
                    player = null,
                    isFullscreen = false,
                    onRecentVideoSelected = {},
                    onMarkVideoWatched = {},
                    onMarkVideoUnwatched = {},
                    onLocalCaptionEngineSelected = {},
                    onGenerateCaptionsRequested = {},
                    onFullscreenChanged = {},
                )
            }
        }

        composeRule.onRoot().captureRoboImage()
    }

    @Test
    fun playerPreparingSurface() {
        composeRule.setContent {
            LookTubeTheme {
                PlayerRoute(
                    paddingValues = PaddingValues(),
                    selectedVideo = VideoSummary(
                        id = "bombcast-901",
                        title = "Giant Bombcast 901: Handheld Hot Takes",
                        description = "The crew debates portable hardware and revisits a few recent releases.",
                        isPremium = true,
                        feedCategory = "Premium",
                        playbackUrl = "https://video.example.com/bombcast-901.m3u8",
                        seriesTitle = "Giant Bombcast",
                        durationSeconds = 4_200,
                    ),
                    playbackProgress = PlaybackProgress(
                        videoId = "bombcast-901",
                        positionSeconds = 1_250,
                        durationSeconds = 4_200,
                    ),
                    playbackSelectionRequest = 1L,
                    selectedVideoEngagement = null,
                    recentPlaybackVideos = emptyList(),
                    availableLocalCaptionEngines = listOf(WhisperCppLocalCaptionEngine),
                    selectedLocalCaptionEngine = WhisperCppLocalCaptionEngine,
                    localCaptionModelState = LocalCaptionModelState(),
                    selectedCaptionTrack = null,
                    selectedCaptionGenerationStatus = CaptionGenerationStatus.Idle,
                    player = null,
                    isFullscreen = false,
                    onRecentVideoSelected = {},
                    onMarkVideoWatched = {},
                    onMarkVideoUnwatched = {},
                    onLocalCaptionEngineSelected = {},
                    onGenerateCaptionsRequested = {},
                    onFullscreenChanged = {},
                )
            }
        }

        composeRule.onRoot().captureRoboImage()
    }
}
