package com.looktube.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.looktube.designsystem.LookTubeCard
import com.looktube.model.PlaybackProgress
import com.looktube.model.VideoSummary

@Composable
fun PlayerRoute(
    paddingValues: PaddingValues,
    selectedVideo: VideoSummary?,
    playbackProgress: PlaybackProgress?,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (selectedVideo == null) {
            Text("Select a video from the library to continue.")
            return
        }

        LookTubeCard(
            title = selectedVideo.title,
            body = selectedVideo.description,
        )
        val playbackUrl = selectedVideo.playbackUrl
        if (playbackUrl.isNullOrBlank()) {
            LookTubeCard(
                title = "Playback unavailable",
                body = "No playback URL is available yet for this item. Sync a configured feed result or choose a video that exposes a playable stream URL.",
            )
        } else {
            val player = remember(playbackUrl) {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(playbackUrl))
                    prepare()
                    playWhenReady = true
                }
            }

            DisposableEffect(player) {
                onDispose {
                    player.release()
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
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
            }
        }

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
