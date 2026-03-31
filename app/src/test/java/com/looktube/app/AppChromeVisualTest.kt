package com.looktube.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.looktube.designsystem.LookTubeTheme
import com.looktube.model.LookPointsSummary
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
class AppChromeVisualTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun appTopBarPlaybackActive() {
        composeRule.setContent {
            LookTubeTheme {
                Box(
                    modifier = Modifier
                        .width(412.dp)
                        .fillMaxWidth(),
                ) {
                    LookTubeTopBar(
                        playbackIndicatorVisible = true,
                        lookPointsSummary = LookPointsSummary(
                            totalPoints = 49,
                            watchedVideoCount = 4,
                            totalVideoCount = 12,
                            completedShowCount = 1,
                            totalShowCount = 5,
                            videoPoints = 48,
                            dailyOpenPoints = 1,
                        ),
                    )
                }
            }
        }

        composeRule.onRoot().captureRoboImage()
    }

    @Test
    fun launchIntroOverlay() {
        composeRule.setContent {
            LookTubeTheme {
                LookTubeLaunchIntroOverlay(
                    onDismiss = {},
                    shouldAutoDismiss = false,
                )
            }
        }

        composeRule.onRoot().captureRoboImage()
    }
}
