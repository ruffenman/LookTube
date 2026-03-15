package com.looktube.feature.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.looktube.designsystem.LookTubeCard
import com.looktube.model.PlaybackProgress
import com.looktube.model.VideoSummary

@Composable
fun PlayerRoute(
    paddingValues: PaddingValues,
    selectedVideo: VideoSummary?,
    playbackProgress: PlaybackProgress?,
    player: Player?,
    isFullscreen: Boolean,
    onFullscreenChanged: (Boolean) -> Unit,
) {
    val activity = rememberActivity(LocalContext.current)

    DisposableEffect(activity, isFullscreen) {
        val hostActivity = activity
        if (!isFullscreen || hostActivity == null) {
            onDispose { }
        } else {
            val window = hostActivity.window
            val previousOrientation = hostActivity.requestedOrientation
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            hostActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            onDispose {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(window, true)
                hostActivity.requestedOrientation = previousOrientation
            }
        }
    }

    when {
        selectedVideo == null -> PlayerStatusContent(
            paddingValues = paddingValues,
            cards = listOf(
                "Choose something to watch" to "Open a video from Library or Shows to start playback.",
            ),
        )

        selectedVideo.playbackUrl.isNullOrBlank() -> PlayerStatusContent(
            paddingValues = paddingValues,
            cards = listOf(
                selectedVideo.title to selectedVideo.description,
                "Playback unavailable" to "No playback URL is available yet for this item. Sync a configured feed result or choose a video that exposes a playable stream URL.",
            ),
        )

        player == null -> PlayerStatusContent(
            paddingValues = paddingValues,
            cards = listOf(
                selectedVideo.title to selectedVideo.description,
                "Connecting player" to "Preparing the shared playback session for this video.",
            ),
        )

        isFullscreen -> FullscreenPlayerSurface(
            player = player,
            isFullscreen = true,
            onFullscreenToggle = { onFullscreenChanged(false) },
        )

        else -> Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LookTubeCard(
                title = selectedVideo.title,
                body = selectedVideo.description,
            )
            EmbeddedPlayerSurface(
                player = player,
                isFullscreen = false,
                onFullscreenToggle = { onFullscreenChanged(true) },
            )
            LookTubeCard(
                title = "Playback spike status",
                body = buildString {
                    appendLine("Feed category: ${selectedVideo.feedCategory}")
                    appendLine("Premium: ${selectedVideo.isPremium}")
                    appendLine("Playback URL: ${selectedVideo.playbackUrl ?: "Unavailable"}")
                    if (playbackProgress != null) {
                        append("Resume at ${playbackProgress.positionSeconds}s of ${playbackProgress.durationSeconds}s.")
                    } else {
                        append("No stored resume point yet.")
                    }
                },
            )
        }
    }
}

@Composable
private fun PlayerStatusContent(
    paddingValues: PaddingValues,
    cards: List<Pair<String, String>>,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        cards.forEach { (title, body) ->
            LookTubeCard(
                title = title,
                body = body,
            )
        }
    }
}

@Composable
private fun EmbeddedPlayerSurface(
    player: Player,
    isFullscreen: Boolean,
    onFullscreenToggle: () -> Unit,
) {
    val context = LocalContext.current
    val doubleTapGestureDetector = remember(context, onFullscreenToggle) {
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(event: MotionEvent): Boolean {
                    onFullscreenToggle()
                    return true
                }
            },
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .pointerInteropFilter { motionEvent ->
                    doubleTapGestureDetector.onTouchEvent(motionEvent)
                    false
                },
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = true
                    this.player = player
                }
            },
            update = { playerView ->
                playerView.player = player
            },
        )
        FullscreenToggleButton(
            isFullscreen = isFullscreen,
            onToggle = onFullscreenToggle,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
        )
    }
}

@Composable
private fun FullscreenPlayerSurface(
    player: Player,
    isFullscreen: Boolean,
    onFullscreenToggle: () -> Unit,
) {
    val context = LocalContext.current
    val doubleTapGestureDetector = remember(context, onFullscreenToggle) {
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(event: MotionEvent): Boolean {
                    onFullscreenToggle()
                    return true
                }
            },
        )
    }
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .pointerInteropFilter { motionEvent ->
                    doubleTapGestureDetector.onTouchEvent(motionEvent)
                    false
                },
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = true
                    this.player = player
                }
            },
            update = { playerView ->
                playerView.player = player
            },
        )
        FullscreenToggleButton(
            isFullscreen = isFullscreen,
            onToggle = onFullscreenToggle,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
        )
    }
}

@Composable
private fun FullscreenToggleButton(
    isFullscreen: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
    ) {
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = if (isFullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                contentDescription = if (isFullscreen) "Exit fullscreen" else "Enter fullscreen",
            )
        }
    }
}

@Composable
private fun rememberActivity(context: Context): Activity? = when (context) {
    is Activity -> context
    is ContextWrapper -> rememberActivity(context.baseContext)
    else -> null
}
