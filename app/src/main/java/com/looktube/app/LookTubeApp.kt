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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.looktube.designsystem.LookTubeTheme
import com.looktube.feature.auth.AuthRoute
import com.looktube.feature.library.LibraryRoute
import com.looktube.feature.player.PlayerRoute
import com.looktube.model.displaySeriesTitle
import kotlinx.coroutines.launch

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
    val selectedVideo by viewModel.selectedVideo.collectAsStateWithLifecycle()
    val selectedProgress by viewModel.selectedProgress.collectAsStateWithLifecycle()
    val requestedPage by viewModel.requestedPage.collectAsStateWithLifecycle()
    val playbackController = rememberPlaybackController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var fullscreenModeName by rememberSaveable { mutableStateOf(PlayerFullscreenMode.Off.name) }
    var notificationPermissionPrompted by rememberSaveable { mutableStateOf(false) }
    val fullscreenMode = PlayerFullscreenMode.valueOf(fullscreenModeName)
    val isPlayerFullscreen = fullscreenMode != PlayerFullscreenMode.Off
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
        fullscreenModeName = if (isLandscape) {
            PlayerFullscreenMode.LandscapeSuppressed.name
        } else {
            PlayerFullscreenMode.Off.name
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

    LaunchedEffect(selectedVideo?.id, playbackController) {
        val controller = playbackController ?: return@LaunchedEffect
        val video = selectedVideo ?: return@LaunchedEffect
        val playbackUrl = video.playbackUrl ?: return@LaunchedEffect
        if (controller.currentMediaItem?.mediaId != video.id) {
            controller.setMediaItem(
                MediaItem.Builder()
                    .setMediaId(video.id)
                    .setUri(playbackUrl)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(video.title)
                                    .setDisplayTitle(video.title)
                            .setArtist(video.displaySeriesTitle)
                                    .setArtworkUri(video.thumbnailUrl?.let(Uri::parse))
                            .build(),
                    )
                    .build(),
            )
            controller.prepare()
            selectedProgress?.let { progress ->
                if (progress.positionSeconds > 0) {
                    controller.seekTo(progress.positionSeconds * 1_000)
                }
            }
            controller.playWhenReady = true
        }
    }
    LaunchedEffect(pagerState.currentPage, selectedVideo?.id, isLandscape, fullscreenModeName) {
        when {
            pagerState.currentPage != 2 || selectedVideo == null -> {
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
                        onVideoSelected = { videoId ->
                            viewModel.selectVideo(videoId)
                            scope.launch { pagerState.animateScrollToPage(2) }
                        },
                    )

                    else -> PlayerRoute(
                        paddingValues = paddingValues,
                        selectedVideo = selectedVideo,
                        playbackProgress = selectedProgress,
                        player = playbackController,
                        isFullscreen = isPlayerFullscreen,
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

private enum class PlayerFullscreenMode {
    Off,
    Manual,
    AutoLandscape,
    LandscapeSuppressed,
}
