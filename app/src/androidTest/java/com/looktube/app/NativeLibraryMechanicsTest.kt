package com.looktube.app

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.looktube.designsystem.LookTubeTheme
import com.looktube.feature.library.LibraryRoute
import com.looktube.model.LibrarySyncState
import com.looktube.model.SyncPhase
import com.looktube.model.VideoSummary
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Real Android rendering/window dispatch against synthetic data; no feed or playback service. */
class NativeLibraryMechanicsTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val selected = mutableListOf<String>()
    private val title = "A longer show title for testing wrapped text"
    private val videos = (0 until 40).map { index ->
        VideoSummary(id = "native-$index", title = "Native episode $index", description = "Layout fixture. ".repeat(20),
            isPremium = true, feedCategory = "Fixture", playbackUrl = null, seriesTitle = title, publishedAtEpochMillis = 2000L)
    } + (0 until 16).map { index ->
        VideoSummary(id = "other-$index", title = "Other $index", description = "Fixture", isPremium = true,
            feedCategory = "Fixture", playbackUrl = null, seriesTitle = "Other Show ${('A'.code + index).toChar()}",
            publishedAtEpochMillis = 1000L - index)
    }

    @Before fun isolateDebugPreferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".debug")) { "Native fixture tests may only target a debug package" }
        context.getSharedPreferences("looktube.library.browse", 0).edit().clear().commit()
    }

    private fun showLibrary(largeText: Boolean = false) {
        composeRule.setContent {
            LookTubeTheme {
                Surface {
                    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (largeText) 2f else 1f)) {
                        Box(if (largeText) Modifier.width(320.dp).fillMaxHeight() else Modifier) {
                            LibraryRoute(PaddingValues(), LibrarySyncState(SyncPhase.Success, "Offline native fixture"), true,
                                videos, emptyMap(), emptyMap(), emptyMap(), { selected += it }, {}, {}, {}, {})
                        }
                    }
                }
            }
        }
        composeRule.onNodeWithTag("library-list").performScrollToIndex(2)
    }

    private fun header() = composeRule.onNodeWithTag("library-section-$title")
    private fun assertCollapsed() = header().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))

    private fun evidence(name: String) {
        val runId = InstrumentationRegistry.getArguments().getString("localCiRunId") ?: return
        check(runId.matches(Regex("[a-z0-9-]+")))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.getExternalFilesDir(null), "local-ci/$runId").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use {
            check(composeRule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test fun contractionKeepsHeaderAndCanReverseWithoutSelectingVideo() {
        showLibrary()
        val bounds = header().getUnclippedBoundsInRoot()
        val watch = composeRule.onNodeWithTag("library-group-watch-$title").getUnclippedBoundsInRoot()
        assertTrue(watch.top >= bounds.top && watch.bottom <= bounds.bottom)
        assertTrue(composeRule.onNodeWithTag("library-video-native-0").getUnclippedBoundsInRoot().top < bounds.bottom)
        evidence("group-expanded")
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.onNodeWithTag("library-collapse-$title").performTouchInput { click() }
            composeRule.mainClock.advanceTimeBy(96)
            assertCollapsed()
            assertEquals(bounds, header().getUnclippedBoundsInRoot())
            composeRule.onNodeWithTag("library-video-native-0").assertExists()
            composeRule.onNodeWithText("Native episode 39").assertDoesNotExist()
            evidence("group-mid-contraction")
            header().performTouchInput { click() }
            composeRule.mainClock.advanceTimeBy(400)
            assertEquals(bounds, header().getUnclippedBoundsInRoot())
            composeRule.onNodeWithTag("library-video-native-0").assertExists()
            header().performTouchInput { click() }
            composeRule.mainClock.advanceTimeBy(400)
            composeRule.onNodeWithTag("library-video-native-0").assertDoesNotExist()
            val front = composeRule.onNodeWithTag("library-stack-card-native-0").getUnclippedBoundsInRoot()
            val behind = composeRule.onNodeWithTag("library-stack-card-native-1").getUnclippedBoundsInRoot()
            assertTrue(front.top < bounds.bottom && front.bottom > bounds.bottom)
            assertTrue(behind.bottom > front.bottom)
            evidence("group-collapsed")
            header().performTouchInput { click(Offset(centerX, height - 4f)) }
            composeRule.mainClock.advanceTimeBy(400)
            header().performTouchInput { click(Offset(centerX, height - 4f)) }
            composeRule.mainClock.advanceTimeBy(400)
            assertCollapsed()
            assertTrue(selected.isEmpty())
        } finally { composeRule.mainClock.autoAdvance = true }
    }

    @Test fun scrollReturnAndOutsideTapPreserveCollapsedGroup() {
        showLibrary()
        header().performTouchInput { click() }
        composeRule.onNodeWithTag("library-list").performScrollToNode(hasTestTag("library-section-Other Show P"))
        composeRule.onNodeWithTag("library-top").performTouchInput { click() }
        composeRule.onNodeWithTag("library-search").assertIsDisplayed()
        composeRule.onNodeWithTag("library-list").performScrollToIndex(2)
        assertCollapsed()
        val center = header().fetchSemanticsNode().boundsInRoot.center
        val origin = IntArray(2)
        composeRule.runOnIdle {
            fun findOwner(view: View): View? {
                if (view.javaClass.simpleName == "AndroidComposeView") return view
                if (view is ViewGroup) for (index in 0 until view.childCount) findOwner(view.getChildAt(index))?.let { return it }
                return null
            }
            checkNotNull(findOwner(composeRule.activity.window.decorView)).getLocationOnScreen(origin)
        }
        composeRule.onNodeWithTag("library-index-toggle").performTouchInput { click() }
        composeRule.onNodeWithTag("library-section-index").assertIsDisplayed()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val now = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(now, now + if (action == MotionEvent.ACTION_UP) 50 else 0,
                action, center.x + origin[0], center.y + origin[1], 0)
            try { check(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        composeRule.onNodeWithTag("library-section-index").assertDoesNotExist()
        assertCollapsed()
        assertTrue(selected.isEmpty())
        evidence("outside-dismissal")
    }

    @Test fun constrainedWidthAndLargeTextKeepCollapseReachable() {
        showLibrary(largeText = true)
        val role = composeRule.onNode(hasText("SHOW GROUP") and hasAnyAncestor(hasTestTag("library-section-$title")), useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertTrue("Group role must not wrap letter by letter", role.bottom - role.top <= 64.dp)
        composeRule.onNodeWithTag("library-collapse-$title").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            .performTouchInput { click() }
        assertCollapsed()
        assertTrue(selected.isEmpty())
        evidence("large-text-collapsed")
    }
}
