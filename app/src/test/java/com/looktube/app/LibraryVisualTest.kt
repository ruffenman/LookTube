package com.looktube.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.looktube.designsystem.LookTubeTheme
import com.looktube.feature.library.LibraryRoute
import com.looktube.model.LibrarySyncState
import com.looktube.model.LookPointsSummary
import com.looktube.model.PlaybackProgress
import com.looktube.model.SeriesCompletionSummary
import com.looktube.model.SyncPhase
import com.looktube.model.VideoEngagementRecord
import com.looktube.model.VideoSummary
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
class LibraryVisualTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun libraryBrowseSurface() {
        val videos = listOf(
            VideoSummary(
                id = "bombcast-1",
                title = "Giant Bombcast 901: Handheld Hot Takes",
                description = "The crew debates portable hardware and revisits a few recent releases.",
                isPremium = true,
                feedCategory = "Premium",
                playbackUrl = "https://video.example.com/bombcast-1.m3u8",
                seriesTitle = "Giant Bombcast",
                durationSeconds = 4_200,
            ),
            VideoSummary(
                id = "dump-truck-1",
                title = "Voicemail Dump Truck 88",
                description = "Questions, confessions, and a suspicious amount of snack talk.",
                isPremium = true,
                feedCategory = "Premium",
                playbackUrl = "https://video.example.com/dump-truck-1.m3u8",
                seriesTitle = "Voicemail Dump Truck",
                durationSeconds = 3_000,
            ),
        )
        val playbackProgress = mapOf(
            "bombcast-1" to PlaybackProgress(
                videoId = "bombcast-1",
                positionSeconds = 1_250,
                durationSeconds = 4_200,
            ),
        )
        val videoEngagement = mapOf(
            "bombcast-1" to VideoEngagementRecord(
                videoId = "bombcast-1",
                lastPlayedAtEpochMillis = 1_000L,
                completedAtEpochMillis = 2_000L,
            ),
        )
        val lookPointsSummary = LookPointsSummary(
            totalPoints = 10,
            watchedVideoCount = 1,
            totalVideoCount = 2,
            completedShowCount = 1,
            totalShowCount = 2,
            videoPoints = 10,
        )
        val seriesCompletionSummaries = mapOf(
            "Giant Bombcast" to SeriesCompletionSummary(
                seriesTitle = "Giant Bombcast",
                watchedVideoCount = 1,
                totalVideoCount = 1,
            ),
            "Voicemail Dump Truck" to SeriesCompletionSummary(
                seriesTitle = "Voicemail Dump Truck",
                watchedVideoCount = 0,
                totalVideoCount = 1,
            ),
        )

        composeRule.setContent {
            LookTubeTheme {
                Box(
                    modifier = Modifier
                        .width(412.dp)
                        .fillMaxWidth(),
                ) {
                    LibraryRoute(
                        paddingValues = PaddingValues(),
                        syncState = LibrarySyncState(
                            phase = SyncPhase.Success,
                            message = "Synced 42 Premium videos. Last refresh completed successfully.",
                            lastSuccessfulSyncSummary = "42 videos • 12 shows",
                        ),
                        videos = videos,
                        playbackProgress = playbackProgress,
                        videoEngagement = videoEngagement,
                        lookPointsSummary = lookPointsSummary,
                        seriesCompletionSummaries = seriesCompletionSummaries,
                        onVideoSelected = {},
                        onMarkVideoWatched = {},
                        onMarkVideoUnwatched = {},
                        onMarkVideosWatched = {},
                        onMarkVideosUnwatched = {},
                    )
                }
            }
        }

        composeRule.onRoot().captureRoboImage()
    }
}
