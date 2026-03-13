package com.looktube.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.looktube.designsystem.LookTubeTheme
import com.looktube.feature.auth.AuthRoute
import com.looktube.feature.library.LibraryRoute
import com.looktube.feature.player.PlayerRoute
import com.looktube.feature.settings.SettingsRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LookTubeApp(viewModel: LookTubeAppViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val accountSession by viewModel.accountSession.collectAsStateWithLifecycle()
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val selectedVideo by viewModel.selectedVideo.collectAsStateWithLifecycle()
    val selectedProgress by viewModel.selectedProgress.collectAsStateWithLifecycle()

    val topLevelDestinations = listOf(
        TopLevelDestination("auth", "Auth", Icons.Outlined.AccountCircle),
        TopLevelDestination("library", "Library", Icons.Outlined.VideoLibrary),
        TopLevelDestination("player", "Player", Icons.Outlined.PlayCircle),
        TopLevelDestination("settings", "Settings", Icons.Outlined.Settings),
    )

    LookTubeTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Text("LookTube")
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    topLevelDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = backStackEntry?.destination?.route == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                }
                            },
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
            },
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = "auth",
                modifier = Modifier.fillMaxSize(),
            ) {
                composable("auth") {
                    AuthRoute(
                        paddingValues = paddingValues,
                        accountSession = accountSession,
                        onAuthModeSelected = viewModel::selectAuthMode,
                    )
                }
                composable("library") {
                    LibraryRoute(
                        paddingValues = paddingValues,
                        videos = videos,
                        onVideoSelected = { videoId ->
                            viewModel.selectVideo(videoId)
                            navController.navigate("player")
                        },
                    )
                }
                composable("player") {
                    PlayerRoute(
                        paddingValues = paddingValues,
                        selectedVideo = selectedVideo,
                        playbackProgress = selectedProgress,
                    )
                }
                composable("settings") {
                    SettingsRoute(
                        paddingValues = paddingValues,
                    )
                }
            }
        }
    }
}
