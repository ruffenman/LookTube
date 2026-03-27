package com.looktube.feature.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Player
import androidx.media3.cast.MediaRouteButtonViewProvider
import androidx.media3.ui.PlayerView
import com.google.android.gms.cast.framework.CastContext
import com.looktube.designsystem.LookTubeCard
import com.looktube.designsystem.LookTubePageHeader
import com.looktube.heuristics.displaySeriesTitle
import com.looktube.model.LookPointsSummary
import com.looktube.model.RecentPlaybackVideo
import com.looktube.model.VideoEngagementRecord
import com.looktube.model.PlaybackProgress
import com.looktube.model.VideoSummary
import com.looktube.model.isWatched

@Composable
fun PlayerRoute(
    paddingValues: PaddingValues,
    selectedVideo: VideoSummary?,
    playbackProgress: PlaybackProgress?,
    playbackSelectionRequest: Long,
    selectedVideoEngagement: VideoEngagementRecord?,
    recentPlaybackVideos: List<RecentPlaybackVideo>,
    lookPointsSummary: LookPointsSummary,
    player: Player?,
    isFullscreen: Boolean,
    onRecentVideoSelected: (String) -> Unit,
    onMarkVideoWatched: (String) -> Unit,
    onMarkVideoUnwatched: (String) -> Unit,
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
            selectedVideoEngagement = selectedVideoEngagement,
            recentPlaybackVideos = recentPlaybackVideos,
            lookPointsSummary = lookPointsSummary,
            player = player,
            onRecentVideoSelected = onRecentVideoSelected,
            onMarkVideoWatched = onMarkVideoWatched,
            onMarkVideoUnwatched = onMarkVideoUnwatched,
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
private fun PlayerSupportingCopy(
    playbackProgress: PlaybackProgress?,
    remotePlaybackStatus: RemotePlaybackStatus?,
    isWatched: Boolean,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = remotePlaybackStatus?.title?.let { "$it • ${remotePlaybackStatus.detailsBody}" }
                ?: compactResumeSummary(playbackProgress),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "${currentWatchStatusLabel(isWatched, playbackProgress)} • Double-tap left or right to skip 10 seconds.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlayerQuickActions(
    selectedVideo: VideoSummary,
    isWatched: Boolean,
    recentPlaybackVideos: List<RecentPlaybackVideo>,
    lookPointsSummary: LookPointsSummary,
    lookPointsExpanded: Boolean,
    recentPlaybackMenuExpanded: Boolean,
    onLookPointsExpandedChanged: (Boolean) -> Unit,
    onRecentPlaybackMenuExpandedChanged: (Boolean) -> Unit,
    onRecentVideoSelected: (String) -> Unit,
    onMarkVideoWatched: (String) -> Unit,
    onMarkVideoUnwatched: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = isWatched,
                    onClick = {
                        if (isWatched) {
                            onMarkVideoUnwatched(selectedVideo.id)
                        } else {
                            onMarkVideoWatched(selectedVideo.id)
                        }
                    },
                    label = { Text(if (isWatched) "Reset unwatched" else "Mark watched") },
                )
                Box {
                    FilterChip(
                        selected = lookPointsExpanded,
                        onClick = { onLookPointsExpandedChanged(!lookPointsExpanded) },
                        label = { Text("Look Points ${lookPointsSummary.totalPoints}") },
                    )
                }
                if (recentPlaybackVideos.isNotEmpty()) {
                    Box {
                        FilterChip(
                            selected = recentPlaybackMenuExpanded,
                            onClick = { onRecentPlaybackMenuExpandedChanged(!recentPlaybackMenuExpanded) },
                            label = { Text("Recent plays") },
                        )
                        DropdownMenu(
                            expanded = recentPlaybackMenuExpanded,
                            onDismissRequest = { onRecentPlaybackMenuExpandedChanged(false) },
                        ) {
                            recentPlaybackVideos.forEach { recentPlaybackVideo ->
                                DropdownMenuItem(
                                    text = {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            Text(recentPlaybackVideo.video.title)
                                            Text(
                                                text = recentPlaybackMenuSubtitle(recentPlaybackVideo),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                    onClick = {
                                        onRecentPlaybackMenuExpandedChanged(false)
                                        onRecentVideoSelected(recentPlaybackVideo.video.id)
                                    },
                                )
                            }
                        }
                    }
                }
            }
            if (lookPointsExpanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    HorizontalDivider()
                    Text(
                        text = lookPointsBreakdown(lookPointsSummary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
    selectedVideoEngagement: VideoEngagementRecord?,
    recentPlaybackVideos: List<RecentPlaybackVideo>,
    lookPointsSummary: LookPointsSummary,
    player: Player,
    onRecentVideoSelected: (String) -> Unit,
    onMarkVideoWatched: (String) -> Unit,
    onMarkVideoUnwatched: (String) -> Unit,
    onFullscreenChanged: (Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    val remotePlaybackStatus = rememberRemotePlaybackStatus(player)
    val isWatched = selectedVideoEngagement.isWatched(playbackProgress)
    val otherRecentPlaybackVideos = remember(recentPlaybackVideos, selectedVideo.id) {
        recentPlaybackVideos.filter { recentPlaybackVideo -> recentPlaybackVideo.video.id != selectedVideo.id }
    }
    var recentPlaybackMenuExpanded by rememberSaveable(selectedVideo.id) { mutableStateOf(false) }
    var lookPointsExpanded by rememberSaveable { mutableStateOf(false) }

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
            PlayerSupportingCopy(
                playbackProgress = playbackProgress,
                remotePlaybackStatus = remotePlaybackStatus,
                isWatched = isWatched,
            )
        }
        item {
            PlayerQuickActions(
                selectedVideo = selectedVideo,
                isWatched = isWatched,
                recentPlaybackVideos = otherRecentPlaybackVideos,
                lookPointsSummary = lookPointsSummary,
                lookPointsExpanded = lookPointsExpanded,
                recentPlaybackMenuExpanded = recentPlaybackMenuExpanded,
                onLookPointsExpandedChanged = { lookPointsExpanded = it },
                onRecentPlaybackMenuExpandedChanged = { recentPlaybackMenuExpanded = it },
                onRecentVideoSelected = onRecentVideoSelected,
                onMarkVideoWatched = onMarkVideoWatched,
                onMarkVideoUnwatched = onMarkVideoUnwatched,
            )
        }
        item {
            LookTubeCard(
                title = selectedVideo.title,
                body = buildString {
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
        overlayInset = 12.dp,
        remotePlaybackStatus = remotePlaybackStatus,
        onFullscreenToggle = onFullscreenToggle,
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
        overlayInset = 16.dp,
        remotePlaybackStatus = remotePlaybackStatus,
        onFullscreenToggle = onFullscreenToggle,
    )
}

@Composable
private fun PlayerSurface(
    player: Player,
    modifier: Modifier,
    overlayInset: Dp,
    remotePlaybackStatus: RemotePlaybackStatus?,
    onFullscreenToggle: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val mediaRouteButtonViewProvider = remember { MediaRouteButtonViewProvider() }
    val overlayInsetPx = with(density) { overlayInset.roundToPx() }
    val remoteBadgeCornerRadiusPx = with(density) { REMOTE_PLAYBACK_BADGE_CORNER_RADIUS.toPx() }
    val remoteBadgeStrokeWidthPx = with(density) { REMOTE_PLAYBACK_BADGE_STROKE_WIDTH.roundToPx() }
    val remoteBadgePaddingHorizontalPx = with(density) { REMOTE_PLAYBACK_BADGE_HORIZONTAL_PADDING.roundToPx() }
    val remoteBadgePaddingVerticalPx = with(density) { REMOTE_PLAYBACK_BADGE_VERTICAL_PADDING.roundToPx() }
    val remoteBadgeMaxWidthPx = with(density) { REMOTE_PLAYBACK_BADGE_MAX_WIDTH.roundToPx() }
    val remoteBadgeBackgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f).toArgb()
    val remoteBadgeOutlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f).toArgb()
    val remoteBadgeTitleColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val remoteBadgeBodyColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    val doubleTapGestureDetector = remember(context, player) {
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(event: MotionEvent): Boolean = true

                override fun onDoubleTap(event: MotionEvent): Boolean {
                    return when (doubleTapSeekDirection(event.x, playerView?.width ?: 0)) {
                        DoubleTapSeekDirection.Backward -> {
                            player.seekBack()
                            true
                        }
                        DoubleTapSeekDirection.Forward -> {
                            player.seekForward()
                            true
                        }
                        null -> false
                    }
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
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                playerView = this
                useController = true
                setControllerAutoShow(true)
                setControllerHideOnTouch(true)
                setShowPreviousButton(false)
                setShowNextButton(false)
                setMediaRouteButtonViewProvider(mediaRouteButtonViewProvider)
                this.player = player
                keepScreenOn = shouldKeepScreenOn(
                    isPlaying = player.isPlaying,
                    playWhenReady = player.playWhenReady,
                    playbackState = player.playbackState,
                )
                syncRemotePlaybackBadge(
                    status = remotePlaybackStatus,
                    insetPx = overlayInsetPx,
                    maxWidthPx = remoteBadgeMaxWidthPx,
                    backgroundColor = remoteBadgeBackgroundColor,
                    outlineColor = remoteBadgeOutlineColor,
                    titleColor = remoteBadgeTitleColor,
                    bodyColor = remoteBadgeBodyColor,
                    cornerRadiusPx = remoteBadgeCornerRadiusPx,
                    strokeWidthPx = remoteBadgeStrokeWidthPx,
                    horizontalPaddingPx = remoteBadgePaddingHorizontalPx,
                    verticalPaddingPx = remoteBadgePaddingVerticalPx,
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
            hostPlayerView.setShowPreviousButton(false)
            hostPlayerView.setShowNextButton(false)
            hostPlayerView.syncRemotePlaybackBadge(
                status = remotePlaybackStatus,
                insetPx = overlayInsetPx,
                maxWidthPx = remoteBadgeMaxWidthPx,
                backgroundColor = remoteBadgeBackgroundColor,
                outlineColor = remoteBadgeOutlineColor,
                titleColor = remoteBadgeTitleColor,
                bodyColor = remoteBadgeBodyColor,
                cornerRadiusPx = remoteBadgeCornerRadiusPx,
                strokeWidthPx = remoteBadgeStrokeWidthPx,
                horizontalPaddingPx = remoteBadgePaddingHorizontalPx,
                verticalPaddingPx = remoteBadgePaddingVerticalPx,
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

internal fun compactResumeSummary(playbackProgress: PlaybackProgress?): String =
    playbackProgress?.takeIf { it.durationSeconds > 0 }?.let { progress ->
        "Resume • ${formatPlaybackTime(progress.positionSeconds)} / ${formatPlaybackTime(progress.durationSeconds)}"
    } ?: "Ready to play from the beginning."

internal fun currentWatchStatusLabel(
    isWatched: Boolean,
    playbackProgress: PlaybackProgress?,
): String = when {
    isWatched -> "Watched"
    playbackProgress?.positionSeconds?.let { it > 0 } == true -> "In progress"
    else -> "Not started"
}

internal fun recentPlaybackMenuSubtitle(recentPlaybackVideo: RecentPlaybackVideo): String = buildList {
    add(recentPlaybackVideo.video.displaySeriesTitle)
    recentPlaybackVideo.playbackProgress?.takeIf { progress -> progress.durationSeconds > 0 }?.let { progress ->
        add("Resume ${formatPlaybackTime(progress.positionSeconds)}")
    }
    if (recentPlaybackVideo.isWatched) {
        add("Watched")
    }
}.joinToString(" • ")

internal fun lookPointsBreakdown(summary: LookPointsSummary): String =
    "${summary.watchedVideoCount}/${summary.totalVideoCount} videos watched • " +
        "${summary.completedShowCount}/${summary.totalShowCount} shows complete • " +
        "${summary.videoPoints} points from watched videos."
internal fun shouldKeepScreenOn(
    isPlaying: Boolean,
    playWhenReady: Boolean,
    playbackState: Int,
): Boolean = isPlaying || (playWhenReady && playbackState == Player.STATE_BUFFERING)


internal fun isRemotePlayback(deviceInfo: DeviceInfo): Boolean =
    deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE

internal enum class DoubleTapSeekDirection {
    Backward,
    Forward,
}

internal fun doubleTapSeekDirection(
    tapX: Float,
    surfaceWidthPx: Int,
): DoubleTapSeekDirection? = when {
    surfaceWidthPx <= 0 -> null
    tapX < surfaceWidthPx / 2f -> DoubleTapSeekDirection.Backward
    else -> DoubleTapSeekDirection.Forward
}

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

private fun PlayerView.syncRemotePlaybackBadge(
    status: RemotePlaybackStatus?,
    insetPx: Int,
    maxWidthPx: Int,
    backgroundColor: Int,
    outlineColor: Int,
    titleColor: Int,
    bodyColor: Int,
    cornerRadiusPx: Float,
    strokeWidthPx: Int,
    horizontalPaddingPx: Int,
    verticalPaddingPx: Int,
) {
    val overlay = overlayFrameLayout ?: return
    val badgeContainer = overlay.findViewWithTag<LinearLayout>(REMOTE_PLAYBACK_BADGE_CONTAINER_TAG)
        ?: LinearLayout(context).apply {
            tag = REMOTE_PLAYBACK_BADGE_CONTAINER_TAG
            orientation = LinearLayout.VERTICAL
            isClickable = false
            isFocusable = false
            isEnabled = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setOnTouchListener { _, _ -> false }
            addView(
                TextView(context).apply {
                    tag = REMOTE_PLAYBACK_BADGE_TITLE_TAG
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    textSize = 13f
                },
            )
            addView(
                TextView(context).apply {
                    tag = REMOTE_PLAYBACK_BADGE_BODY_TAG
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    textSize = 12f
                },
            )
            overlay.addView(this)
        }
    if (status == null) {
        badgeContainer.visibility = View.GONE
        return
    }
    badgeContainer.layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.WRAP_CONTENT,
        FrameLayout.LayoutParams.WRAP_CONTENT,
        Gravity.TOP or Gravity.START,
    ).apply {
        topMargin = insetPx
        leftMargin = insetPx
    }
    badgeContainer.setPadding(
        horizontalPaddingPx,
        verticalPaddingPx,
        horizontalPaddingPx,
        verticalPaddingPx,
    )
    badgeContainer.background = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = cornerRadiusPx
        setColor(backgroundColor)
        setStroke(strokeWidthPx, outlineColor)
    }
    badgeContainer.visibility = View.VISIBLE
    badgeContainer.alpha = 1f
    badgeContainer.findViewWithTag<TextView>(REMOTE_PLAYBACK_BADGE_TITLE_TAG)?.apply {
        text = status.title
        setTextColor(titleColor)
        maxWidth = maxWidthPx
    }
    badgeContainer.findViewWithTag<TextView>(REMOTE_PLAYBACK_BADGE_BODY_TAG)?.apply {
        text = status.badgeBody
        setTextColor(bodyColor)
        maxWidth = maxWidthPx
    }
}

private data class RemotePlaybackStatus(
    val title: String,
    val badgeBody: String,
    val detailsBody: String,
)

private val REMOTE_PLAYBACK_BADGE_CORNER_RADIUS = 18.dp
private val REMOTE_PLAYBACK_BADGE_STROKE_WIDTH = 1.dp
private val REMOTE_PLAYBACK_BADGE_HORIZONTAL_PADDING = 14.dp
private val REMOTE_PLAYBACK_BADGE_VERTICAL_PADDING = 10.dp
private val REMOTE_PLAYBACK_BADGE_MAX_WIDTH = 240.dp
private const val REMOTE_PLAYBACK_BADGE_CONTAINER_TAG = "looktube.remote_playback_badge"
private const val REMOTE_PLAYBACK_BADGE_TITLE_TAG = "looktube.remote_playback_badge_title"
private const val REMOTE_PLAYBACK_BADGE_BODY_TAG = "looktube.remote_playback_badge_body"
