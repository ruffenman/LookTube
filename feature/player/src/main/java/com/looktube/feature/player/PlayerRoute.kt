package com.looktube.feature.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.GradientDrawable
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.looktube.designsystem.LookTubeCard
import com.looktube.designsystem.LookTubePageHeader
import com.looktube.heuristics.displaySeriesTitle
import com.looktube.model.PlaybackProgress
import com.looktube.model.VideoSummary

@Composable
fun PlayerRoute(
    paddingValues: PaddingValues,
    selectedVideo: VideoSummary?,
    playbackProgress: PlaybackProgress?,
    playbackSelectionRequest: Long,
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
            frameAtTop = true,
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
            frameAtTop = true,
        )

        isFullscreen -> FullscreenPlayerSurface(
            player = player,
            remotePlaybackStatus = rememberRemotePlaybackStatus(player),
            onFullscreenToggle = onFullscreenChanged,
        )

        else -> ActivePlayerContent(
            paddingValues = paddingValues,
            selectedVideo = selectedVideo,
            playbackProgress = playbackProgress,
            playbackSelectionRequest = playbackSelectionRequest,
            player = player,
            onFullscreenChanged = onFullscreenChanged,
        )
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
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
    frameAtTop: Boolean = false,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (frameAtTop) {
            item {
                PlayerFramePlaceholder(
                    title = frameTitle,
                    body = frameBody,
                )
            }
        }
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
                    body = buildString {
                        appendLine("Show: ${video.displaySeriesTitle}")
                        appendLine("Feed category: ${video.feedCategory}")
                        if (video.description.isNotBlank()) {
                            appendLine()
                            append(video.description)
                        }
                    },
                )
            }
        }
        if (!frameAtTop) {
            item {
                PlayerFramePlaceholder(
                    title = frameTitle,
                    body = frameBody,
                )
            }
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
private fun ActivePlayerContent(
    paddingValues: PaddingValues,
    selectedVideo: VideoSummary,
    playbackProgress: PlaybackProgress?,
    playbackSelectionRequest: Long,
    player: Player,
    onFullscreenChanged: (Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    val remotePlaybackStatus = rememberRemotePlaybackStatus(player)

    LaunchedEffect(selectedVideo.id, playbackSelectionRequest) {
        listState.scrollToItem(0)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            EmbeddedPlayerSurface(
                player = player,
                remotePlaybackStatus = remotePlaybackStatus,
                onFullscreenToggle = onFullscreenChanged,
            )
        }
        item {
            LookTubePageHeader(
                title = "Player",
                subtitle = remotePlaybackStatus?.title?.let { "$it. ${remotePlaybackStatus.detailsBody}" }
                    ?: playbackProgress?.takeIf { it.durationSeconds > 0 }?.let {
                        "Continue watching ${selectedVideo.displaySeriesTitle} from ${formatPlaybackTime(it.positionSeconds)}."
                    }
                    ?: "The player stays pinned at the top so video, controls, and details remain in one place.",
            )
        }
        item {
            LookTubeCard(
                title = "Now playing",
                body = buildString {
                    appendLine(selectedVideo.title)
                    appendLine()
                    appendLine("Show: ${selectedVideo.displaySeriesTitle}")
                    append("Feed category: ${selectedVideo.feedCategory}")
                    if (selectedVideo.description.isNotBlank()) {
                        appendLine()
                        appendLine()
                        append(selectedVideo.description)
                    }
                },
            )
        }
        item {
            LookTubeCard(
                title = if (remotePlaybackStatus != null) "Playback handoff" else "Playback details",
                body = buildString {
                    appendLine("Premium: ${if (selectedVideo.isPremium) "Yes" else "No"}")
                    playbackProgress?.let { progress ->
                        appendLine("Resume at ${formatPlaybackTime(progress.positionSeconds)} of ${formatPlaybackTime(progress.durationSeconds)}.")
                    } ?: appendLine("No stored resume point yet.")
                    appendLine("Double-tap the video or use the fullscreen button to toggle fullscreen.")
                    remotePlaybackStatus?.let { status ->
                        appendLine()
                        append(status.detailsBody)
                    }
                },
            )
        }
    }
}

@Composable
private fun EmbeddedPlayerSurface(
    player: Player,
    remotePlaybackStatus: RemotePlaybackStatus?,
    onFullscreenToggle: (Boolean) -> Unit,
) {
    PlayerSurface(
        player = player,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        castButtonInset = 12.dp,
        remotePlaybackStatus = remotePlaybackStatus,
        onFullscreenToggle = onFullscreenToggle,
        onDoubleTapToggle = { onFullscreenToggle(true) },
    )
}

@Composable
private fun FullscreenPlayerSurface(
    player: Player,
    remotePlaybackStatus: RemotePlaybackStatus?,
    onFullscreenToggle: (Boolean) -> Unit,
) {
    PlayerSurface(
        player = player,
        modifier = Modifier.fillMaxSize(),
        castButtonInset = 16.dp,
        remotePlaybackStatus = remotePlaybackStatus,
        onFullscreenToggle = onFullscreenToggle,
        onDoubleTapToggle = { onFullscreenToggle(false) },
    )
}

@Composable
private fun PlayerSurface(
    player: Player,
    modifier: Modifier,
    castButtonInset: Dp,
    remotePlaybackStatus: RemotePlaybackStatus?,
    onFullscreenToggle: (Boolean) -> Unit,
    onDoubleTapToggle: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val castButtonInsetPx = with(density) { castButtonInset.roundToPx() }
    val castButtonSizePx = with(density) { CAST_BUTTON_SIZE.roundToPx() }
    val castButtonElevationPx = with(density) { CAST_BUTTON_ELEVATION.toPx() }
    val castButtonContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f).toArgb()
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
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

    DisposableEffect(player, playerView) {
        val hostPlayerView = playerView
        if (hostPlayerView == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    hostPlayerView.keepScreenOn = shouldKeepScreenOn(
                        isPlaying = player.isPlaying,
                        playWhenReady = player.playWhenReady,
                        playbackState = player.playbackState,
                    )
                }
            }
            hostPlayerView.keepScreenOn = shouldKeepScreenOn(
                isPlaying = player.isPlaying,
                playWhenReady = player.playWhenReady,
                playbackState = player.playbackState,
            )
            player.addListener(listener)
            onDispose {
                hostPlayerView.keepScreenOn = false
                player.removeListener(listener)
            }
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    playerView = this
                    useController = true
                    setControllerAutoShow(true)
                    setControllerHideOnTouch(true)
                    this.player = player
                    keepScreenOn = shouldKeepScreenOn(
                        isPlaying = player.isPlaying,
                        playWhenReady = player.playWhenReady,
                        playbackState = player.playbackState,
                    )
                    syncCastButtonChrome(
                        insetPx = castButtonInsetPx,
                        buttonSizePx = castButtonSizePx,
                        containerColor = castButtonContainerColor,
                        shadowElevationPx = castButtonElevationPx,
                    )
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            updateCastButtonChromeVisibility(
                                shouldShowCastButtonChrome(visibility),
                            )
                        },
                    )
                    updateCastButtonChromeVisibility(
                        shouldShowCastButtonChrome(currentControllerVisibility()),
                        animate = false,
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
            update = { hostPlayerView ->
                playerView = hostPlayerView
                hostPlayerView.player = player
                hostPlayerView.keepScreenOn = shouldKeepScreenOn(
                    isPlaying = player.isPlaying,
                    playWhenReady = player.playWhenReady,
                    playbackState = player.playbackState,
                )
                hostPlayerView.syncCastButtonChrome(
                    insetPx = castButtonInsetPx,
                    buttonSizePx = castButtonSizePx,
                    containerColor = castButtonContainerColor,
                    shadowElevationPx = castButtonElevationPx,
                )
                hostPlayerView.setControllerVisibilityListener(
                    PlayerView.ControllerVisibilityListener { visibility ->
                        hostPlayerView.updateCastButtonChromeVisibility(
                            shouldShowCastButtonChrome(visibility),
                        )
                    },
                )
                hostPlayerView.updateCastButtonChromeVisibility(
                    shouldShowCastButtonChrome(hostPlayerView.currentControllerVisibility()),
                    animate = false,
                )
                hostPlayerView.setFullscreenButtonClickListener { isFullscreen ->
                    onFullscreenToggle(isFullscreen)
                }
                hostPlayerView.setOnTouchListener { _, motionEvent ->
                    doubleTapGestureDetector.onTouchEvent(motionEvent)
                    false
                }
            },
        )
        remotePlaybackStatus?.let { status ->
            RemotePlaybackBadge(
                status = status,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = castButtonInset, top = castButtonInset)
                    .widthIn(max = 240.dp),
            )
        }
    }
}

@Composable
private fun rememberRemotePlaybackStatus(
    player: Player,
): RemotePlaybackStatus? {
    val context = LocalContext.current
    var status by remember(player, context) {
        mutableStateOf(player.toRemotePlaybackStatus(context))
    }

    DisposableEffect(player, context) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                status = player.toRemotePlaybackStatus(context)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    return status
}

private fun Player.toRemotePlaybackStatus(context: Context): RemotePlaybackStatus? {
    if (!isRemotePlayback(deviceInfo)) {
        return null
    }
    val deviceName = runCatching {
        CastContext.getSharedInstance(context)
            .sessionManager
            .currentCastSession
            ?.castDevice
            ?.friendlyName
    }.getOrNull()
    return RemotePlaybackStatus(
        title = remotePlaybackTitle(deviceName),
        badgeBody = remotePlaybackBadgeBody(
            isPlaying = isPlaying,
            playWhenReady = playWhenReady,
            playbackState = playbackState,
        ),
        detailsBody = remotePlaybackBody(
            isPlaying = isPlaying,
            playWhenReady = playWhenReady,
            playbackState = playbackState,
        ),
    )
}

@Composable
private fun RemotePlaybackBadge(
    status: RemotePlaybackStatus,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = status.title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = status.badgeBody,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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

internal fun shouldKeepScreenOn(
    isPlaying: Boolean,
    playWhenReady: Boolean,
    playbackState: Int,
): Boolean = isPlaying || (playWhenReady && playbackState == Player.STATE_BUFFERING)

internal fun shouldShowCastButtonChrome(controllerVisibility: Int): Boolean =
    controllerVisibility == View.VISIBLE

internal fun isRemotePlayback(deviceInfo: DeviceInfo): Boolean =
    deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE

internal fun remotePlaybackTitle(deviceName: String?): String =
    deviceName?.takeIf(String::isNotBlank)?.let { "Casting to $it" } ?: "Casting video"
internal fun remotePlaybackBadgeBody(
    isPlaying: Boolean,
    playWhenReady: Boolean,
    playbackState: Int,
): String = when {
    playbackState == Player.STATE_BUFFERING -> "Syncing to your cast device"
    isPlaying -> "Remote playback is active"
    playWhenReady -> "Reconnecting to your cast device"
    else -> "Playback is paused on your cast device"
}

internal fun remotePlaybackBody(
    isPlaying: Boolean,
    playWhenReady: Boolean,
    playbackState: Int,
): String = when {
    playbackState == Player.STATE_BUFFERING -> "LookTube is syncing playback to your cast device."
    isPlaying -> "Video is playing on your cast device. Use the standard player controls here to pause, resume, or stop casting."
    playWhenReady -> "LookTube is reconnecting playback on your cast device."
    else -> "Playback is paused on your cast device. Use the standard player controls here to resume or stop casting."
}

private fun PlayerView.syncCastButtonChrome(
    insetPx: Int,
    buttonSizePx: Int,
    containerColor: Int,
    shadowElevationPx: Float,
) {
    val overlay = overlayFrameLayout ?: return
    val castButtonContainer = overlay.findViewWithTag<FrameLayout>(CAST_BUTTON_CONTAINER_TAG)
        ?: FrameLayout(context).apply {
            tag = CAST_BUTTON_CONTAINER_TAG
            addView(
                MediaRouteButton(context).apply {
                    tag = CAST_BUTTON_TAG
                    contentDescription = "Cast video"
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER,
                    )
                    CastButtonFactory.setUpMediaRouteButton(context, this)
                },
            )
            overlay.addView(this)
        }
    castButtonContainer.layoutParams = FrameLayout.LayoutParams(
        buttonSizePx,
        buttonSizePx,
        Gravity.TOP or Gravity.END,
    ).apply {
        topMargin = insetPx
        rightMargin = insetPx
    }
    castButtonContainer.background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(containerColor)
    }
    castButtonContainer.elevation = shadowElevationPx
    castButtonContainer.clipToOutline = true
    castButtonContainer.findViewWithTag<MediaRouteButton>(CAST_BUTTON_TAG)?.let { mediaRouteButton ->
        CastButtonFactory.setUpMediaRouteButton(mediaRouteButton.context, mediaRouteButton)
    }
}

private fun PlayerView.updateCastButtonChromeVisibility(
    isVisible: Boolean,
    animate: Boolean = true,
) {
    val castButtonContainer = overlayFrameLayout?.findViewWithTag<FrameLayout>(CAST_BUTTON_CONTAINER_TAG)
        ?: return
    castButtonContainer.animate().cancel()
    if (isVisible) {
        castButtonContainer.visibility = View.VISIBLE
        if (animate) {
            castButtonContainer.animate()
                .alpha(1f)
                .setDuration(CAST_BUTTON_CHROME_ANIMATION_MS)
                .start()
        } else {
            castButtonContainer.alpha = 1f
        }
    } else if (animate) {
        castButtonContainer.animate()
            .alpha(0f)
            .setDuration(CAST_BUTTON_CHROME_ANIMATION_MS)
            .withEndAction {
                castButtonContainer.visibility = View.INVISIBLE
            }
            .start()
    } else {
        castButtonContainer.alpha = 0f
        castButtonContainer.visibility = View.INVISIBLE
    }
}

private fun PlayerView.currentControllerVisibility(): Int =
    findViewById<View>(androidx.media3.ui.R.id.exo_controller)?.visibility ?: View.VISIBLE

private data class RemotePlaybackStatus(
    val title: String,
    val badgeBody: String,
    val detailsBody: String,
)

private val CAST_BUTTON_SIZE = 48.dp
private val CAST_BUTTON_ELEVATION = 4.dp
private const val CAST_BUTTON_CHROME_ANIMATION_MS = 180L
private const val CAST_BUTTON_CONTAINER_TAG = "looktube.cast_button_container"
private const val CAST_BUTTON_TAG = "looktube.cast_button"
