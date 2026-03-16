package com.looktube.feature.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.looktube.designsystem.LookTubeCard
import com.looktube.model.PlaybackProgress
import com.looktube.model.VideoSummary
import com.looktube.model.displaySeriesTitle

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
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            onDispose {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(window, true)
            }
        }
    }

    when {
        selectedVideo == null -> PlayerStatusContent(
            paddingValues = paddingValues,
            cards = listOf(
                "Nothing queued yet" to "Pick a video from Library to start playback here.",
            ),
        )

        selectedVideo.playbackUrl.isNullOrBlank() -> PlayerStatusContent(
            paddingValues = paddingValues,
            cards = listOf(
                selectedVideo.title to selectedVideo.description,
                "Playback unavailable" to "This item does not expose a playable stream right now. Try another video or refresh your library from Auth.",
            ),
        )

        player == null -> PlayerStatusContent(
            paddingValues = paddingValues,
            cards = listOf(
                selectedVideo.title to selectedVideo.description,
                "Preparing player" to "Opening the shared playback session for this video.",
            ),
        )

        isFullscreen -> FullscreenPlayerSurface(
            player = player,
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
                title = "Now playing",
                body = buildString {
                    appendLine(selectedVideo.title)
                    if (selectedVideo.description.isNotBlank()) {
                        appendLine()
                        append(selectedVideo.description)
                    }
                },
            )
            EmbeddedPlayerSurface(
                player = player,
                onFullscreenToggle = { onFullscreenChanged(true) },
            )
            LookTubeCard(
                title = "Playback details",
                body = buildString {
                    appendLine("Show: ${selectedVideo.displaySeriesTitle}")
                    appendLine("Feed category: ${selectedVideo.feedCategory}")
                    appendLine("Premium: ${if (selectedVideo.isPremium) "Yes" else "No"}")
                    if (playbackProgress != null) {
                        appendLine("Resume at ${playbackProgress.positionSeconds}s of ${playbackProgress.durationSeconds}s.")
                    } else {
                        appendLine("No stored resume point yet.")
                    }
                    append("Double-tap the video or use the corner button to toggle fullscreen.")
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
    onFullscreenToggle: () -> Unit,
) {
    val context = LocalContext.current
    val doubleTapGestureDetector = remember(context, onFullscreenToggle) {
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(event: MotionEvent): Boolean = true
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
                    setFullscreenButtonClickListener { onFullscreenToggle() }
                }
            },
            update = { playerView ->
                playerView.player = player
                playerView.setFullscreenButtonClickListener { onFullscreenToggle() }
            },
        )
        CastRouteButton(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
        )
    }
}

@Composable
private fun FullscreenPlayerSurface(
    player: Player,
    onFullscreenToggle: () -> Unit,
) {
    val context = LocalContext.current
    val doubleTapGestureDetector = remember(context, onFullscreenToggle) {
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(event: MotionEvent): Boolean = true
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
                    setFullscreenButtonClickListener { onFullscreenToggle() }
                }
            },
            update = { playerView ->
                playerView.player = player
                playerView.setFullscreenButtonClickListener { onFullscreenToggle() }
            },
        )
        CastRouteButton(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
        )
    }
}

@Composable
private fun CastRouteButton(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
    ) {
        AndroidView(
            modifier = Modifier.size(48.dp),
            factory = { viewContext ->
                MediaRouteButton(viewContext).apply {
                    contentDescription = "Cast video"
                    CastButtonFactory.setUpMediaRouteButton(viewContext, this)
                }
            },
            update = { mediaRouteButton ->
                CastButtonFactory.setUpMediaRouteButton(mediaRouteButton.context, mediaRouteButton)
            },
        )
    }
}

@Composable
private fun rememberActivity(context: Context): Activity? = when (context) {
    is Activity -> context
    is ContextWrapper -> rememberActivity(context.baseContext)
    else -> null
}
