package com.looktube.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.looktube.model.PlaybackProgress
import com.looktube.model.bestDurationSeconds
import com.looktube.model.castGroupingKey
import com.looktube.model.castGroupingTitle
import com.looktube.model.displaySeriesTitle
import com.looktube.model.seriesGroupingKey
import com.looktube.model.topicGroupingKey
import com.looktube.model.topicGroupingTitle
import com.looktube.model.VideoSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SettingsRoute(
    paddingValues: PaddingValues,
    videos: List<VideoSummary>,
    playbackProgress: Map<String, PlaybackProgress>,
    onVideoSelected: (String) -> Unit,
) {
    var groupingMode by rememberSaveable { mutableStateOf(HeuristicGroupingMode.Show) }
    val seriesGroups = remember(videos, groupingMode) {
        videos
            .groupBy { video ->
                when (groupingMode) {
                    HeuristicGroupingMode.Show -> video.seriesGroupingKey
                    HeuristicGroupingMode.Cast -> video.castGroupingKey
                    HeuristicGroupingMode.Topic -> video.topicGroupingKey
                }
            }
            .values
            .map { groupedVideos ->
                val sortedVideos = groupedVideos.sortedByDescending { it.publishedAtEpochMillis ?: Long.MIN_VALUE }
                SeriesGroup(
                    title = groupedVideos.resolveGroupTitle(groupingMode),
                    videos = sortedVideos,
                    latestPublishedAt = sortedVideos.firstOrNull()?.publishedAtEpochMillis ?: Long.MIN_VALUE,
                )
            }
            .sortedWith(
                compareByDescending<SeriesGroup> { it.latestPublishedAt }
                    .thenByDescending { it.videos.size }
                    .thenBy { it.title.lowercase() },
            )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item {
            Text("Shows")
        }
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HeuristicGroupingMode.entries.forEach { mode ->
                    FilterChip(
                        selected = groupingMode == mode,
                        onClick = { groupingMode = mode },
                        label = { Text(mode.label) },
                    )
                }
            }
        }
        if (seriesGroups.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Sync a feed first to browse shows and jump into videos by series.",
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        } else {
            items(seriesGroups) { group ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = group.title,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "${group.videos.size} videos",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        group.videos.take(8).forEachIndexed { index, video ->
                            if (index > 0) {
                                HorizontalDivider()
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onVideoSelected(video.id) }
                                    .padding(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = video.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                if (video.description.isNotBlank()) {
                                    Text(
                                        text = video.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                buildEpisodeMetadata(video, playbackProgress[video.id]).takeIf(String::isNotBlank)?.let { metadata ->
                                    Text(
                                        text = metadata,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                playbackProgress[video.id]?.takeIf { it.durationSeconds > 0 }?.let { progress ->
                                    LinearProgressIndicator(
                                        progress = { (progress.positionSeconds.toFloat() / progress.durationSeconds.toFloat()).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                        if (group.videos.size > 8) {
                            Text(
                                text = "+ ${group.videos.size - 8} more videos",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class SeriesGroup(
    val title: String,
    val videos: List<VideoSummary>,
    val latestPublishedAt: Long,
)

private enum class HeuristicGroupingMode(val label: String) {
    Show("By show"),
    Cast("By cast"),
    Topic("By topic"),
}

private fun List<VideoSummary>.resolveGroupTitle(mode: HeuristicGroupingMode): String =
    when (mode) {
        HeuristicGroupingMode.Show -> map(VideoSummary::displaySeriesTitle)
        HeuristicGroupingMode.Cast -> map(VideoSummary::castGroupingTitle)
        HeuristicGroupingMode.Topic -> map(VideoSummary::topicGroupingTitle)
    }
        .groupingBy { it }
        .eachCount()
        .maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        ?.key
        ?: first().displaySeriesTitle

private fun buildEpisodeMetadata(
    video: VideoSummary,
    progress: PlaybackProgress?,
): String = buildList {
    video.publishedAtEpochMillis?.let(::formatPublishedDate)?.let(::add)
    video.bestDurationSeconds(progress)?.let(::formatDuration)?.let(::add)
}.joinToString(" • ")

private fun formatPublishedDate(epochMillis: Long): String =
    DateTimeFormatter.ofPattern("MMM d, yyyy")
        .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    val remainingSeconds = seconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, remainingSeconds)
    } else {
        "%d:%02d".format(minutes, remainingSeconds)
    }
}
