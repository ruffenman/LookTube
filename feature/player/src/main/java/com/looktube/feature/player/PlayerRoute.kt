package com.looktube.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.looktube.designsystem.LookTubeCard
import com.looktube.model.PlaybackProgress
import com.looktube.model.VideoSummary

@Composable
fun PlayerRoute(
    paddingValues: PaddingValues,
    selectedVideo: VideoSummary?,
    playbackProgress: PlaybackProgress?,
) {
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

        LookTubeCard(
            title = "Playback spike status",
            body = buildString {
                appendLine("Feed category: ${selectedVideo.feedCategory}")
                appendLine("Premium: ${selectedVideo.isPremium}")
                if (playbackProgress != null) {
                    append("Resume at ${playbackProgress.positionSeconds}s of ${playbackProgress.durationSeconds}s.")
                } else {
                    append("No stored resume point yet.")
                }
            },
        )
    }
}
