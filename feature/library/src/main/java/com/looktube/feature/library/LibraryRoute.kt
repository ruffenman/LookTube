package com.looktube.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.looktube.designsystem.LookTubeCard
import com.looktube.model.VideoSummary

@Composable
fun LibraryRoute(
    paddingValues: PaddingValues,
    videos: List<VideoSummary>,
    onVideoSelected: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item {
            Text("Latest Premium videos")
        }

        items(videos) { video ->
            LookTubeCard(
                title = video.title,
                body = "${video.feedCategory}\n${video.description}",
                modifier = Modifier.clickable { onVideoSelected(video.id) },
            )
        }
    }
}
