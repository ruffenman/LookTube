package com.looktube.feature.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.looktube.designsystem.LookTubePageHeader
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
            headerSubtitle = "Choose a video from Library to start playback here.",
            statusTitle = "Nothing queued yet",
            statusBody = "Pick a video from Library to start playback here.",
            frameTitle = "Player ready",
            frameBody = "When you pick a video, playback controls, fullscreen, and cast options will appear here.",
        )

        selectedVideo.playbackUrl.isNullOrBlank() -> PlayerStatusContent(
            paddingValues = paddingValues,
            selectedVideo = selectedVideo,
            playbackProgress = playbackProgress,
            headerSubtitle = "The selected video is loaded, but it does not currently expose a playable stream.",
            statusTitle = "Playback unavailable",
            statusBody = "This item does not expose a playable stream right now. Try another video or refresh your library from Auth.",
            frameTitle = "No playable stream",
            frameBody = "LookTube found this video in the feed, but the current item does not include a playable URL.",
        )

        player == null -> PlayerStatusContent(
            paddingValues = paddingValues,
            selectedVideo = selectedVideo,
            playbackProgress = playbackProgress,
            headerSubtitle = "Opening the shared playback session for this video.",
            statusTitle = "Preparing player",
            statusBody = "LookTube is opening playback controls for this video now.",
            frameTitle = "Player is getting ready",
            frameBody = playbackProgress?.takeIf { it.durationSeconds > 0 }?.let {
                "Resume will pick up near ${formatPlaybackTime(it.positionSeconds)}."
            } ?: "Playback controls will appear here in a moment.",
        )

        isFullscreen -> FullscreenPlayerSurface(
            player = player,
            onFullscreenToggle = onFullscreenChanged,
        )

        else -> LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
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
            }
            item {
                EmbeddedPlayerSurface(
                    player = player,
                    onFullscreenToggle = onFullscreenChanged,
                )
            }
            item {
                LookTubeCard(
                    title = "Playback details",
                    body = buildString {
                        appendLine("Show: ${selectedVideo.displaySeriesTitle}")
                        appendLine("Feed category: ${selectedVideo.feedCategory}")
                        appendLine("Premium: ${if (selectedVideo.isPremium) "Yes" else "No"}")
                        if (playbackProgress != null) {
                            appendLine("Resume at ${formatPlaybackTime(playbackProgress.positionSeconds)} of ${formatPlaybackTime(playbackProgress.durationSeconds)}.")
                        } else {
                            appendLine("No stored resume point yet.")
                        }
                        append("Double-tap the video or use the fullscreen button to toggle fullscreen.")
                    },
                )
            }
        }
    }
}

@Composable
private fun PlayerFramePlaceholder(
    title: String,
    body: String,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
        tonalElevation = 2.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PlayerStatusContent(
    paddingValues: PaddingValues,
    headerSubtitle: String,
    statusTitle: String,
    statusBody: String,
    frameTitle: String,
    frameBody: String,
    selectedVideo: VideoSummary? = null,
    playbackProgress: PlaybackProgress? = null,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            LookTubePageHeader(
                title = "Player",
                subtitle = headerSubtitle,
            )
        }
        selectedVideo?.let { video ->
            item {
                LookTubeCard(
                    title = video.title,
                    body = video.description.ifBlank {
                        "From ${video.displaySeriesTitle}."
                    },
                )
            }
        }
        item {
            PlayerFramePlaceholder(
                title = frameTitle,
                body = frameBody,
            )
        }
        item {
            LookTubeCard(
                title = statusTitle,
                body = buildString {
                    append(statusBody)
                    selectedVideo?.let { video ->
                        append("\n\nShow: ${video.displaySeriesTitle}")
                        append("\nFeed category: ${video.feedCategory}")
                    }
                    playbackProgress?.takeIf { it.durationSeconds > 0 }?.let { progress ->
                        append("\nResume point: ${formatPlaybackTime(progress.positionSeconds)} of ${formatPlaybackTime(progress.durationSeconds)}.")
                    }
                },
            )
        }
    }
}

@Composable
private fun EmbeddedPlayerSurface(
    player: Player,
    onFullscreenToggle: (Boolean) -> Unit,
) {
    PlayerSurface(
        player = player,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        castButtonPadding = PaddingValues(12.dp),
        onFullscreenToggle = onFullscreenToggle,
        onDoubleTapToggle = { onFullscreenToggle(true) },
    )
}

@Composable
private fun FullscreenPlayerSurface(
    player: Player,
    onFullscreenToggle: (Boolean) -> Unit,
) {
    PlayerSurface(
        player = player,
        modifier = Modifier.fillMaxSize(),
        castButtonPadding = PaddingValues(16.dp),
        onFullscreenToggle = onFullscreenToggle,
        onDoubleTapToggle = { onFullscreenToggle(false) },
    )
}

@Composable
private fun PlayerSurface(
    player: Player,
    modifier: Modifier,
    castButtonPadding: PaddingValues,
    onFullscreenToggle: (Boolean) -> Unit,
    onDoubleTapToggle: () -> Unit,
) {
    val context = LocalContext.current
    var controllerVisible by remember { mutableStateOf(true) }
    val doubleTapGestureDetector = remember(context, onDoubleTapToggle) {
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(event: MotionEvent): Boolean = true

                override fun onDoubleTap(event: MotionEvent): Boolean {
                    onDoubleTapToggle()
                    return true
                }
            },
        )
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = true
                    setControllerAutoShow(true)
                    setControllerHideOnTouch(true)
                    this.player = player
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                        controllerVisible = visibility == View.VISIBLE
                        },
                    )
                    setFullscreenButtonClickListener { isFullscreen ->
                        onFullscreenToggle(isFullscreen)
                    }
                    setOnTouchListener { _, motionEvent ->
                        doubleTapGestureDetector.onTouchEvent(motionEvent)
                        false
                    }
                }
            },
            update = { playerView ->
                playerView.player = player
                playerView.setControllerVisibilityListener(
                    PlayerView.ControllerVisibilityListener { visibility ->
                        controllerVisible = visibility == View.VISIBLE
                    },
                )
                playerView.setFullscreenButtonClickListener { isFullscreen ->
                    onFullscreenToggle(isFullscreen)
                }
                playerView.setOnTouchListener { _, motionEvent ->
                    doubleTapGestureDetector.onTouchEvent(motionEvent)
                    false
                }
            },
        )
        AnimatedVisibility(
            visible = controllerVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(castButtonPadding),
        ) {
            CastRouteButton()
        }
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

private fun formatPlaybackTime(seconds: Long): String {
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    val remainingSeconds = seconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, remainingSeconds)
    } else {
        "%d:%02d".format(minutes, remainingSeconds)
    }
}
