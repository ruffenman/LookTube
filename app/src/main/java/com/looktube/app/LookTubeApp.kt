package com.looktube.app
import android.Manifest

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.android.gms.cast.framework.CastContext
import com.looktube.designsystem.LookTubeTheme
import com.looktube.heuristics.displaySeriesTitle
import com.looktube.feature.auth.AuthRoute
import com.looktube.feature.library.LibraryRoute
import com.looktube.feature.player.PlayerRoute
import com.looktube.model.LookPointsSummary
import com.looktube.model.VideoSummary
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@UnstableApi
fun LookTubeApp(
    viewModel: LookTubeAppViewModel,
    launchIntent: Intent? = null,
) {
    val accountSession by viewModel.accountSession.collectAsStateWithLifecycle()
    val feedConfiguration by viewModel.feedConfiguration.collectAsStateWithLifecycle()
    val librarySyncState by viewModel.librarySyncState.collectAsStateWithLifecycle()
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val playbackProgress by viewModel.playbackProgress.collectAsStateWithLifecycle()
    val videoEngagement by viewModel.videoEngagement.collectAsStateWithLifecycle()
    val selectedPlaybackTarget by viewModel.selectedPlaybackTarget.collectAsStateWithLifecycle()
    val playbackSelectionRequest by viewModel.playbackSelectionRequest.collectAsStateWithLifecycle()
    val requestedPage by viewModel.requestedPage.collectAsStateWithLifecycle()
    val recentPlaybackVideos by viewModel.recentPlaybackVideos.collectAsStateWithLifecycle()
    val lookPointsSummary by viewModel.lookPointsSummary.collectAsStateWithLifecycle()
    val seriesCompletionSummaries by viewModel.seriesCompletionSummaries.collectAsStateWithLifecycle()
    val playbackController = rememberPlaybackController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var fullscreenModeName by rememberSaveable { mutableStateOf(PlayerFullscreenMode.Off.name) }
    var notificationPermissionPrompted by rememberSaveable { mutableStateOf(false) }
    var lastHandledPlaybackSelectionRequest by rememberSaveable { mutableStateOf(0L) }
    val fullscreenMode = PlayerFullscreenMode.valueOf(fullscreenModeName)
    val isPlayerFullscreen = fullscreenMode.isPlayerSurfaceFullscreen()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    val topLevelDestinations = listOf(
        TopLevelDestination("auth", "Auth", Icons.Outlined.AccountCircle),
        TopLevelDestination("library", "Library", Icons.Outlined.VideoLibrary),
        TopLevelDestination("player", "Player", Icons.Outlined.PlayCircle),
    )
    val pagerState = rememberPagerState(initialPage = 0) { topLevelDestinations.size }
    BackHandler(enabled = isPlayerFullscreen) {
        fullscreenModeName = exitFullscreenModeForBack(isLandscape).name
        if (pagerState.currentPage != LookTubeLaunchContract.PLAYER_PAGE_INDEX) {
            scope.launch {
                pagerState.animateScrollToPage(LookTubeLaunchContract.PLAYER_PAGE_INDEX)
            }
        }
    }

    LaunchedEffect(launchIntent) {
        viewModel.handleLaunchIntent(launchIntent)
    }
    LaunchedEffect(requestedPage) {
        requestedPage?.let { pageIndex ->
            pagerState.animateScrollToPage(pageIndex)
            viewModel.consumeRequestedPage(pageIndex)
        }
    }

    LaunchedEffect(selectedPlaybackTarget?.video?.id, playbackController, playbackSelectionRequest) {
        val controller = playbackController ?: return@LaunchedEffect
        val playbackTarget = selectedPlaybackTarget ?: return@LaunchedEffect
        val forceReload = isExplicitPlaybackSelectionRequest(
            playbackSelectionRequest = playbackSelectionRequest,
            lastHandledPlaybackSelectionRequest = lastHandledPlaybackSelectionRequest,
        )
        handoffSelectedPlaybackTarget(
            controller = MediaControllerPlaybackHandoffController(
                controller = controller,
                context = context,
            ),
            playbackTarget = playbackTarget,
            forceReload = forceReload,
        )
        if (forceReload) {
            lastHandledPlaybackSelectionRequest = playbackSelectionRequest
        }
    }
    LaunchedEffect(pagerState.currentPage, selectedPlaybackTarget?.video?.id, isLandscape, fullscreenModeName) {
        when {
            pagerState.currentPage != LookTubeLaunchContract.PLAYER_PAGE_INDEX || selectedPlaybackTarget == null -> {
                fullscreenModeName = PlayerFullscreenMode.Off.name
            }
            isLandscape && fullscreenMode == PlayerFullscreenMode.Off -> {
                fullscreenModeName = PlayerFullscreenMode.AutoLandscape.name
            }
            !isLandscape && (
                fullscreenMode == PlayerFullscreenMode.AutoLandscape ||
                    fullscreenMode == PlayerFullscreenMode.LandscapeSuppressed
                ) -> {
                fullscreenModeName = PlayerFullscreenMode.Off.name
            }
        }
    }
    LaunchedEffect(feedConfiguration.feedUrl, notificationPermissionPrompted) {
        val shouldRequestPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            feedConfiguration.feedUrl.isNotBlank() &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED &&
            !notificationPermissionPrompted
        if (shouldRequestPermission) {
            notificationPermissionPrompted = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LookTubeTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                if (!isPlayerFullscreen) {
                    TopAppBar(
                        title = {
                            Text("LookTube")
                        },
                        actions = {
                            LookPointsTopBarBadge(
                                lookPointsSummary = lookPointsSummary,
                                modifier = Modifier.padding(end = 4.dp),
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                }
            },
            bottomBar = {
                if (!isPlayerFullscreen) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        topLevelDestinations.forEachIndexed { index, destination ->
                            NavigationBarItem(
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                icon = {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = destination.label,
                                    )
                                },
                                label = { Text(destination.label) },
                            )
                        }
                    }
                }
            },
        ) { paddingValues ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                userScrollEnabled = !isPlayerFullscreen,
            ) {
                when (topLevelDestinations[it].route) {
                    "auth" -> AuthRoute(
                        paddingValues = paddingValues,
                        accountSession = accountSession,
                        feedConfiguration = feedConfiguration,
                        syncState = librarySyncState,
                        onFeedUrlChanged = viewModel::updateFeedUrl,
                        onSignInRequested = viewModel::signInToPremiumFeed,
                        onClearSyncedDataRequested = viewModel::clearSyncedData,
                    )

                    "library" -> LibraryRoute(
                        paddingValues = paddingValues,
                        syncState = librarySyncState,
                        videos = videos,
                        playbackProgress = playbackProgress,
                        videoEngagement = videoEngagement,
                        seriesCompletionSummaries = seriesCompletionSummaries,
                        onVideoSelected = { videoId ->
                            viewModel.selectVideo(videoId)
                            scope.launch {
                                pagerState.animateScrollToPage(LookTubeLaunchContract.PLAYER_PAGE_INDEX)
                            }
                        },
                        onMarkVideoWatched = viewModel::markVideoWatched,
                        onMarkVideoUnwatched = viewModel::markVideoUnwatched,
                        onMarkVideosWatched = viewModel::markVideosWatched,
                        onMarkVideosUnwatched = viewModel::markVideosUnwatched,
                    )

                    else -> PlayerRoute(
                        paddingValues = paddingValues,
                        selectedVideo = selectedPlaybackTarget?.video,
                        playbackProgress = selectedPlaybackTarget?.playbackProgress,
                        playbackSelectionRequest = playbackSelectionRequest,
                        selectedVideoEngagement = selectedPlaybackTarget?.video?.id?.let(videoEngagement::get),
                        recentPlaybackVideos = recentPlaybackVideos,
                        player = playbackController,
                        isFullscreen = isPlayerFullscreen,
                        onRecentVideoSelected = viewModel::selectVideo,
                        onMarkVideoWatched = viewModel::markVideoWatched,
                        onMarkVideoUnwatched = viewModel::markVideoUnwatched,
                        onFullscreenChanged = { enabled ->
                            fullscreenModeName = when {
                                enabled -> PlayerFullscreenMode.Manual.name
                                isLandscape -> PlayerFullscreenMode.LandscapeSuppressed.name
                                else -> PlayerFullscreenMode.Off.name
                            }
                        },
                    )

                }
            }
        }
    }
}

@Composable
@UnstableApi
private fun rememberPlaybackController(): MediaController? {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    val controllerFuture = remember(context) {
        MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, PlaybackService::class.java)),
        ).buildAsync()
    }

    DisposableEffect(context, controllerFuture) {
        controllerFuture.addListener(
            {
                controller = runCatching { controllerFuture.get() }.getOrNull()
            },
            context.mainExecutor,
        )
        onDispose {
            controller?.release()
            if (!controllerFuture.isDone) {
                controllerFuture.cancel(true)
            }
        }
    }

    return controller
}

@Composable
private fun LookPointsTopBarBadge(
    lookPointsSummary: LookPointsSummary,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Look Points",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${lookPointsSummary.watchedVideoCount}/${lookPointsSummary.totalVideoCount} watched",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = lookPointsSummary.totalPoints.toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

internal fun handoffSelectedPlaybackTarget(
    controller: PlaybackHandoffController,
    playbackTarget: SelectedPlaybackTarget,
    forceReload: Boolean = false,
) {
    val video = playbackTarget.video
    val playbackUrl = video.playbackUrl ?: return
    val resumePositionMs = playbackTarget.playbackProgress
        ?.takeIf { progress ->
            progress.videoId == video.id && progress.positionSeconds > 0
        }
        ?.positionSeconds
        ?.times(1_000L)
    val shouldReplaceMediaItem = shouldReplaceMediaItemForPlaybackTarget(
        currentMediaId = controller.currentMediaId,
        targetMediaId = video.id,
        playbackState = controller.playbackState,
        forceReload = forceReload,
        isPlaybackRouteRemote = controller.isPlaybackRouteRemote,
        hasConnectedCastSession = controller.hasConnectedCastSession,
    )
    if (shouldReplaceMediaItem) {
        controller.setMediaItem(
            mediaItem = video.toPlaybackMediaItem(playbackUrl),
            startPositionMs = resumePositionMs,
        )
        controller.prepare()
    } else if (resumePositionMs != null && controller.currentPositionMs <= 0L) {
        controller.seekTo(resumePositionMs)
    }
    controller.playWhenReady = true
}

internal interface PlaybackHandoffController {
    val currentMediaId: String?
    val currentPositionMs: Long
    val playbackState: Int
    val isPlaybackRouteRemote: Boolean
    val hasConnectedCastSession: Boolean
    var playWhenReady: Boolean
    fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long?)
    fun prepare()
    fun seekTo(positionMs: Long)
}

private class MediaControllerPlaybackHandoffController(
    private val controller: MediaController,
    private val context: android.content.Context,
) : PlaybackHandoffController {
    override val currentMediaId: String?
        get() = controller.currentMediaItem?.mediaId
    override val currentPositionMs: Long
        get() = controller.currentPosition
    override val playbackState: Int
        get() = controller.playbackState
    override val isPlaybackRouteRemote: Boolean
        get() = controller.deviceInfo.playbackType == androidx.media3.common.DeviceInfo.PLAYBACK_TYPE_REMOTE
    override val hasConnectedCastSession: Boolean
        get() = hasConnectedCastSession(context)
    override var playWhenReady: Boolean
        get() = controller.playWhenReady
        set(value) {
            controller.playWhenReady = value
        }
    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long?) {
        if (startPositionMs != null) {
            controller.setMediaItem(mediaItem, startPositionMs)
        } else {
            controller.setMediaItem(mediaItem)
        }
    }

    override fun prepare() {
        controller.prepare()
    }

    override fun seekTo(positionMs: Long) {
        controller.seekTo(positionMs)
    }
}

internal fun shouldReplaceMediaItemForPlaybackTarget(
    currentMediaId: String?,
    targetMediaId: String,
    playbackState: Int,
    forceReload: Boolean,
    isPlaybackRouteRemote: Boolean = false,
    hasConnectedCastSession: Boolean = false,
): Boolean = forceReload ||
    currentMediaId != targetMediaId ||
    playbackState == androidx.media3.common.Player.STATE_IDLE ||
    playbackState == androidx.media3.common.Player.STATE_ENDED ||
    (isPlaybackRouteRemote && !hasConnectedCastSession)

internal fun isExplicitPlaybackSelectionRequest(
    playbackSelectionRequest: Long,
    lastHandledPlaybackSelectionRequest: Long,
): Boolean = playbackSelectionRequest > lastHandledPlaybackSelectionRequest

internal fun hasConnectedCastSession(context: android.content.Context): Boolean = runCatching {
    CastContext.getSharedInstance(context)
        .sessionManager
        .currentCastSession
        ?.isConnected == true
}.getOrDefault(false)

private fun VideoSummary.toPlaybackMediaItem(playbackUrl: String): MediaItem =
    MediaItem.Builder()
        .setMediaId(id)
        .setUri(playbackUrl)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setDisplayTitle(title)
                .setArtist(displaySeriesTitle)
                .setArtworkUri(thumbnailUrl?.let(Uri::parse))
                .build(),
        )
        .build()

internal enum class PlayerFullscreenMode {
    Off,
    Manual,
    AutoLandscape,
    LandscapeSuppressed,
}

internal fun PlayerFullscreenMode.isPlayerSurfaceFullscreen(): Boolean = when (this) {
    PlayerFullscreenMode.Manual,
    PlayerFullscreenMode.AutoLandscape -> true
    PlayerFullscreenMode.Off,
    PlayerFullscreenMode.LandscapeSuppressed -> false
}

internal fun exitFullscreenModeForBack(isLandscape: Boolean): PlayerFullscreenMode =
    if (isLandscape) {
        PlayerFullscreenMode.LandscapeSuppressed
    } else {
        PlayerFullscreenMode.Off
    }
