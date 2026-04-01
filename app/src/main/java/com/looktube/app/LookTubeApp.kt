package com.looktube.app
import android.Manifest

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.android.gms.cast.framework.CastContext
import com.looktube.designsystem.LookTubeTheme
import com.looktube.heuristics.displaySeriesTitle
import com.looktube.feature.auth.CaptionDataManagementItem
import com.looktube.feature.auth.AuthRoute
import com.looktube.feature.library.LibraryRoute
import com.looktube.feature.player.PlayerRoute
import com.looktube.model.LocalCaptionEngine
import com.looktube.model.LookPointsSummary
import com.looktube.model.VideoCaptionData
import com.looktube.model.VideoCaptionTrack
import com.looktube.model.VideoSummary
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@UnstableApi
fun LookTubeApp(
    viewModel: LookTubeAppViewModel,
    launchIntent: Intent? = null,
    showLaunchIntroOnStart: Boolean = false,
) {
    val accountSession by viewModel.accountSession.collectAsStateWithLifecycle()
    val feedConfiguration by viewModel.feedConfiguration.collectAsStateWithLifecycle()
    val librarySyncState by viewModel.librarySyncState.collectAsStateWithLifecycle()
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val playbackProgress by viewModel.playbackProgress.collectAsStateWithLifecycle()
    val videoEngagement by viewModel.videoEngagement.collectAsStateWithLifecycle()
    val selectedPlaybackTarget by viewModel.selectedPlaybackTarget.collectAsStateWithLifecycle()
    val availableLocalCaptionEngines by viewModel.availableLocalCaptionEngines.collectAsStateWithLifecycle()
    val selectedLocalCaptionEngine by viewModel.selectedLocalCaptionEngine.collectAsStateWithLifecycle()
    val localCaptionModelState by viewModel.localCaptionModelState.collectAsStateWithLifecycle()
    val videoCaptions by viewModel.videoCaptions.collectAsStateWithLifecycle()
    val captionData by viewModel.captionData.collectAsStateWithLifecycle()
    val selectedVideoCaptionTrack by viewModel.selectedVideoCaptionTrack.collectAsStateWithLifecycle()
    val selectedCaptionGenerationStatus by viewModel.selectedCaptionGenerationStatus.collectAsStateWithLifecycle()
    val videoSelectionMode by viewModel.videoSelectionMode.collectAsStateWithLifecycle()
    val playbackSelectionRequest by viewModel.playbackSelectionRequest.collectAsStateWithLifecycle()
    val requestedPage by viewModel.requestedPage.collectAsStateWithLifecycle()
    val recentPlaybackVideos by viewModel.recentPlaybackVideos.collectAsStateWithLifecycle()
    val lookPointsSummary by viewModel.lookPointsSummary.collectAsStateWithLifecycle()
    val seriesCompletionSummaries by viewModel.seriesCompletionSummaries.collectAsStateWithLifecycle()
    val playbackController = rememberPlaybackController()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var fullscreenModeName by rememberSaveable { mutableStateOf(PlayerFullscreenMode.Off.name) }
    var notificationPermissionPrompted by rememberSaveable { mutableStateOf(false) }
    var lastHandledPlaybackSelectionRequest by rememberSaveable { mutableStateOf(0L) }
    var lastHandledCaptionTrackPath by rememberSaveable { mutableStateOf<String?>(null) }
    var showLaunchIntro by rememberSaveable { mutableStateOf(showLaunchIntroOnStart) }
    val fullscreenMode = PlayerFullscreenMode.valueOf(fullscreenModeName)
    val isPlayerFullscreen = fullscreenMode.isPlayerSurfaceFullscreen()
    val launchIntroQuote = remember(
        feedConfiguration.launchIntroQuoteDeckSeed,
        feedConfiguration.launchIntroQuoteDeckIndex,
    ) {
        currentLaunchIntroQuote(feedConfiguration)
    }
    val selectedCaptionData = selectedPlaybackTarget?.video?.id?.let(captionData::get)
    val captionDataManagementItems = remember(
        videos,
        captionData,
        videoCaptions,
        availableLocalCaptionEngines,
    ) {
        buildCaptionDataManagementItems(
            videos = videos,
            captionData = captionData,
            videoCaptions = videoCaptions,
            availableLocalCaptionEngines = availableLocalCaptionEngines,
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    val topLevelDestinations = listOf(
        TopLevelDestination("settings", "Settings", Icons.Outlined.AccountCircle),
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

    LaunchedEffect(
        selectedPlaybackTarget?.video?.id,
        selectedPlaybackTarget?.captionTrack?.filePath,
        playbackController,
        playbackSelectionRequest,
        videoSelectionMode,
    ) {
        val controller = playbackController ?: return@LaunchedEffect
        val playbackTarget = selectedPlaybackTarget ?: return@LaunchedEffect
        val explicitSelectionReloadRequested = isExplicitPlaybackSelectionRequest(
            playbackSelectionRequest = playbackSelectionRequest,
            lastHandledPlaybackSelectionRequest = lastHandledPlaybackSelectionRequest,
        )
        val captionTrackReloadRequested =
            playbackTarget.captionTrack?.filePath != lastHandledCaptionTrackPath &&
                controller.currentMediaItem?.mediaId == playbackTarget.video.id
        val currentMediaId = controller.currentMediaItem?.mediaId
        val shouldSkipHandoff = when {
            captionTrackReloadRequested -> false
            videoSelectionMode == VideoSelectionMode.Passive -> true
            videoSelectionMode == VideoSelectionMode.Preview &&
                currentMediaId == playbackTarget.video.id -> true
            else -> false
        }
        if (shouldSkipHandoff) {
            if (explicitSelectionReloadRequested) {
                lastHandledPlaybackSelectionRequest = playbackSelectionRequest
            }
            lastHandledCaptionTrackPath = playbackTarget.captionTrack?.filePath
            return@LaunchedEffect
        }
        val forceReload = explicitSelectionReloadRequested || captionTrackReloadRequested
        handoffSelectedPlaybackTarget(
            controller = MediaControllerPlaybackHandoffController(
                controller = controller,
                context = context,
            ),
            playbackTarget = playbackTarget,
            forceReload = forceReload,
            requestedPlayWhenReady = if (captionTrackReloadRequested) {
                controller.playWhenReady
            } else {
                videoSelectionMode == VideoSelectionMode.Play
            },
        )
        if (explicitSelectionReloadRequested) {
            lastHandledPlaybackSelectionRequest = playbackSelectionRequest
        }
        lastHandledCaptionTrackPath = playbackTarget.captionTrack?.filePath
    }
    LaunchedEffect(playbackController?.currentMediaItem?.mediaId, videos) {
        val currentMediaId = playbackController?.currentMediaItem?.mediaId ?: return@LaunchedEffect
        if (
            currentMediaId.isNotBlank() &&
            selectedPlaybackTarget?.video?.id != currentMediaId &&
            videos.any { video -> video.id == currentMediaId }
        ) {
            viewModel.syncVideoWithPlaybackSession(currentMediaId)
        }
    }
    LaunchedEffect(playbackController?.deviceInfo?.playbackType, selectedPlaybackTarget?.video?.id) {
        val controller = playbackController ?: return@LaunchedEffect
        val playbackTarget = selectedPlaybackTarget ?: return@LaunchedEffect
        if (controller.deviceInfo.playbackType == androidx.media3.common.DeviceInfo.PLAYBACK_TYPE_REMOTE) {
            if (!hasConnectedCastSession(context)) {
                handoffSelectedPlaybackTarget(
                    controller = MediaControllerPlaybackHandoffController(
                        controller = controller,
                        context = context,
                    ),
                    playbackTarget = playbackTarget,
                    forceReload = true,
                    requestedPlayWhenReady = controller.playWhenReady,
                )
                lastHandledCaptionTrackPath = playbackTarget.captionTrack?.filePath
            }
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
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                viewModel.noteAppOpened()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val topBarPlaybackIndicatorVisible = rememberTopBarPlaybackIndicatorVisible(playbackController)

    LookTubeTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    if (!isPlayerFullscreen) {
                        LookTubeTopBar(
                            playbackIndicatorVisible = topBarPlaybackIndicatorVisible,
                            lookPointsSummary = lookPointsSummary,
                            onPlaybackIndicatorClick = {
                                playbackController?.currentMediaItem?.mediaId
                                    ?.takeIf(String::isNotBlank)
                                    ?.let(viewModel::syncVideoWithPlaybackSession)
                                scope.launch {
                                    pagerState.animateScrollToPage(LookTubeLaunchContract.PLAYER_PAGE_INDEX)
                                }
                            },
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
                        "settings" -> AuthRoute(
                            paddingValues = paddingValues,
                            accountSession = accountSession,
                            feedConfiguration = feedConfiguration,
                            syncState = librarySyncState,
                            availableLocalCaptionEngines = availableLocalCaptionEngines,
                            selectedLocalCaptionEngine = selectedLocalCaptionEngine,
                            localCaptionModelState = localCaptionModelState,
                            captionDataItems = captionDataManagementItems,
                            onFeedUrlChanged = viewModel::updateFeedUrl,
                            onAutoGenerateCaptionsForNewVideosChanged = viewModel::updateAutoGenerateCaptionsForNewVideos,
                            onSignInRequested = viewModel::signInToPremiumFeed,
                            onLocalCaptionEngineSelected = viewModel::selectLocalCaptionEngine,
                            onDownloadLocalCaptionModel = viewModel::downloadLocalCaptionModel,
                            onOpenCaptionDataVideoRequested = viewModel::inspectVideoInPlayer,
                            onClearSyncedDataRequested = viewModel::clearSyncedData,
                            onClearCaptionDataRequested = viewModel::clearCaptionData,
                        )

                        "library" -> LibraryRoute(
                            paddingValues = paddingValues,
                            syncState = librarySyncState,
                            hasSavedFeedUrl = feedConfiguration.feedUrl.isNotBlank(),
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
                            availableLocalCaptionEngines = availableLocalCaptionEngines,
                            selectedLocalCaptionEngine = selectedLocalCaptionEngine,
                            localCaptionModelState = localCaptionModelState,
                            selectedCaptionData = selectedCaptionData,
                            selectedCaptionTrack = selectedVideoCaptionTrack,
                            selectedCaptionGenerationStatus = selectedCaptionGenerationStatus,
                            player = playbackController,
                            isFullscreen = isPlayerFullscreen,
                            onRecentVideoSelected = viewModel::selectVideo,
                            onMarkVideoWatched = viewModel::markVideoWatched,
                            onMarkVideoUnwatched = viewModel::markVideoUnwatched,
                            onLocalCaptionEngineSelected = viewModel::selectLocalCaptionEngine,
                            onGenerateCaptionsRequested = viewModel::generateCaptions,
                            onDeleteCaptionDataRequested = viewModel::deleteCaptionData,
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
            if (showLaunchIntro) {
                LookTubeLaunchIntroOverlay(
                    quote = launchIntroQuote,
                    onDismiss = {
                        if (showLaunchIntro) {
                            showLaunchIntro = false
                            viewModel.consumeLaunchIntroQuote(LaunchIntroQuoteDeckSize)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
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
internal fun LookTubeTopBar(
    playbackIndicatorVisible: Boolean,
    lookPointsSummary: LookPointsSummary,
    onPlaybackIndicatorClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = "LookTube",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Box(
                modifier = Modifier.width(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (playbackIndicatorVisible) {
                    TopBarPlaybackIndicator(
                        onClick = onPlaybackIndicatorClick,
                    )
                }
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd,
            ) {
                LookPointsTopBarBadge(
                    lookPointsSummary = lookPointsSummary,
                )
            }
        }
    }
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

@Composable
private fun TopBarPlaybackIndicator(
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(24.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Playback active",
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun rememberTopBarPlaybackIndicatorVisible(
    controller: MediaController?,
): Boolean {
    var visible by remember(controller) {
        mutableStateOf(controller?.showsTopBarPlaybackIndicator() == true)
    }
    DisposableEffect(controller) {
        if (controller == null) {
            visible = false
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    visible = controller.showsTopBarPlaybackIndicator()
                }
            }
            visible = controller.showsTopBarPlaybackIndicator()
            controller.addListener(listener)
            onDispose {
                controller.removeListener(listener)
            }
        }
    }
    return visible
}

private fun MediaController.showsTopBarPlaybackIndicator(): Boolean =
    currentMediaItem != null && (isPlaying || (playWhenReady && playbackState != Player.STATE_IDLE))

internal fun handoffSelectedPlaybackTarget(
    controller: PlaybackHandoffController,
    playbackTarget: SelectedPlaybackTarget,
    forceReload: Boolean = false,
    requestedPlayWhenReady: Boolean = true,
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
    val startPositionMs = when {
        shouldReplaceMediaItem &&
            controller.currentMediaId == video.id &&
            controller.currentPositionMs > 0L -> controller.currentPositionMs
        else -> resumePositionMs
    }
    if (shouldReplaceMediaItem) {
        controller.setMediaItem(
            mediaItem = video.toPlaybackMediaItem(
                playbackUrl = playbackUrl,
                captionTrack = playbackTarget.captionTrack,
            ),
            startPositionMs = startPositionMs,
        )
        controller.prepare()
    } else if (resumePositionMs != null && controller.currentPositionMs <= 0L) {
        controller.seekTo(resumePositionMs)
    }
    controller.playWhenReady = requestedPlayWhenReady
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

private fun VideoSummary.toPlaybackMediaItem(
    playbackUrl: String,
    captionTrack: VideoCaptionTrack?,
): MediaItem =
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
        .setSubtitleConfigurations(
            captionTrack
                ?.takeIf { track -> track.filePath.isNotBlank() }
                ?.let { track ->
                    listOf(
                        MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(File(track.filePath)))
                            .setMimeType(MimeTypes.TEXT_VTT)
                            .setLanguage(track.languageTag)
                            .setLabel(track.label)
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            .build(),
                    )
                }
                ?: emptyList(),
        )
        .build()

private val CaptionDataUpdatedAtFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, h:mm a")

internal fun buildCaptionDataManagementItems(
    videos: List<VideoSummary>,
    captionData: Map<String, VideoCaptionData>,
    videoCaptions: Map<String, VideoCaptionTrack>,
    availableLocalCaptionEngines: List<LocalCaptionEngine>,
): List<CaptionDataManagementItem> {
    val engineNameById = availableLocalCaptionEngines.associate { engine ->
        engine.id to engine.displayName
    }
    return videos.mapNotNull { video ->
        val savedCaptionData = captionData[video.id]
        val savedCaptionTrack = videoCaptions[video.id]
        if (savedCaptionData == null && savedCaptionTrack == null) {
            return@mapNotNull null
        }
        val updatedAtEpochMillis = maxOf(
            savedCaptionData?.updatedAtEpochMillis ?: 0L,
            savedCaptionTrack?.generatedAtEpochMillis ?: 0L,
        )
        val hasSavedCaptionTrack = savedCaptionData?.hasSavedCaptionTrack == true || savedCaptionTrack != null
        val stateLabel = if (hasSavedCaptionTrack) "Completed" else "Partial"
        val engineLabel = (savedCaptionData?.engineId ?: savedCaptionTrack?.engineId)
            ?.let(engineNameById::get)
        val supportingText = buildList {
            add(video.displaySeriesTitle)
            add(stateLabel)
            engineLabel?.let(::add)
            if (updatedAtEpochMillis > 0L) {
                add("Updated ${formatCaptionDataTimestamp(updatedAtEpochMillis)}")
            }
        }.joinToString(" • ")
        CaptionDataManagementItem(
            videoId = video.id,
            title = video.title,
            stateLabel = stateLabel,
            supportingText = supportingText,
        ) to updatedAtEpochMillis
    }.sortedByDescending { (_, updatedAtEpochMillis) ->
        updatedAtEpochMillis
    }.map { (item, _) ->
        item
    }
}

private fun formatCaptionDataTimestamp(updatedAtEpochMillis: Long): String =
    CaptionDataUpdatedAtFormatter.format(
        Instant.ofEpochMilli(updatedAtEpochMillis).atZone(ZoneId.systemDefault()),
    )

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
