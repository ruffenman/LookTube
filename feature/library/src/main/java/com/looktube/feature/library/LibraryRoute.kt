package com.looktube.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.looktube.designsystem.LookTubeCard
import com.looktube.model.LibrarySyncState
import com.looktube.model.PlaybackProgress
import com.looktube.model.VideoSummary
import com.looktube.model.bestDurationSeconds
import com.looktube.model.castGroupingKey
import com.looktube.model.castGroupingTitle
import com.looktube.model.displaySeriesTitle
import com.looktube.model.seriesGroupingKey
import com.looktube.model.topicGroupingKey
import com.looktube.model.topicGroupingTitle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun LibraryRoute(
    paddingValues: PaddingValues,
    syncState: LibrarySyncState,
    videos: List<VideoSummary>,
    playbackProgress: Map<String, PlaybackProgress>,
    onVideoSelected: (String) -> Unit,
) {
    var browseMode by rememberSaveable { mutableStateOf(LibraryBrowseMode.Videos) }
    var sortOption by rememberSaveable { mutableStateOf(LibrarySortOption.Latest) }
    var selectedSeriesFilter by rememberSaveable { mutableStateOf(ALL_SERIES_FILTER) }
    var groupingMode by rememberSaveable { mutableStateOf(HeuristicGroupingMode.Show) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val groupedListState = rememberLazyListState()
    val showRailState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val latestPublishedAtBySeries = remember(videos) {
        videos.groupBy(VideoSummary::displaySeriesTitle)
            .mapValues { (_, groupedVideos) ->
                groupedVideos.maxOfOrNull { it.publishedAtEpochMillis ?: Long.MIN_VALUE } ?: Long.MIN_VALUE
            }
    }
    val seriesFilters = remember(videos, sortOption, latestPublishedAtBySeries) {
        val sortedFilters = videos
            .map(VideoSummary::displaySeriesTitle)
            .distinct()
            .sortedWith(
                when (sortOption) {
                    LibrarySortOption.Latest -> compareByDescending<String> { latestPublishedAtBySeries[it] ?: Long.MIN_VALUE }
                        .thenBy { it.lowercase() }
                    LibrarySortOption.Title,
                    LibrarySortOption.Show,
                    -> compareBy(String::lowercase)
                },
            )
        listOf(ALL_SERIES_FILTER) + sortedFilters
    }
    val visibleVideos = remember(videos, sortOption, selectedSeriesFilter) {
        videos
            .asSequence()
            .filter { selectedSeriesFilter == ALL_SERIES_FILTER || it.displaySeriesTitle == selectedSeriesFilter }
            .let { filtered ->
                when (sortOption) {
                    LibrarySortOption.Latest -> filtered.sortedByDescending { it.publishedAtEpochMillis ?: Long.MIN_VALUE }
                    LibrarySortOption.Title -> filtered.sortedBy { it.title.lowercase() }
                    LibrarySortOption.Show -> filtered.sortedWith(
                        compareBy<VideoSummary> { it.displaySeriesTitle.lowercase() }
                            .thenByDescending { it.publishedAtEpochMillis ?: Long.MIN_VALUE }
                            .thenBy { it.title.lowercase() },
                    )
                }
            }
            .toList()
    }
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
    val currentGroupIndex by remember(seriesGroups, groupedListState) {
        derivedStateOf {
            if (seriesGroups.isEmpty()) {
                0
            } else {
                (groupedListState.firstVisibleItemIndex - GROUP_LIST_START_INDEX)
                    .coerceIn(0, seriesGroups.lastIndex)
            }
        }
    }

    LaunchedEffect(currentGroupIndex, seriesGroups.size) {
        if (seriesGroups.isNotEmpty()) {
            showRailState.animateScrollToItem(currentGroupIndex.coerceAtMost(seriesGroups.lastIndex))
        }
    }

    when (browseMode) {
        LibraryBrowseMode.Videos -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) {
                item {
                    Text("Library")
                }

                item {
                    LookTubeCard(
                        title = "Library sync",
                        body = syncState.message,
                    )
                }

                item {
                    BrowseModeRow(
                        browseMode = browseMode,
                        onBrowseModeChanged = { browseMode = it },
                    )
                }

                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box {
                            FilterChip(
                                selected = false,
                                onClick = { sortMenuExpanded = true },
                                label = { Text("Sort: ${sortOption.label}") },
                            )
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false },
                            ) {
                                LibrarySortOption.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = {
                                            sortOption = option
                                            sortMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            seriesFilters.forEach { filter ->
                                FilterChip(
                                    selected = selectedSeriesFilter == filter,
                                    onClick = { selectedSeriesFilter = filter },
                                    label = { Text(filter) },
                                )
                            }
                        }
                    }
                }

                if (visibleVideos.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = when {
                                    videos.isEmpty() -> "Sync a feed first to load your library."
                                    selectedSeriesFilter != ALL_SERIES_FILTER -> "No videos match the current show filter."
                                    else -> "No videos are available in the synced library yet."
                                },
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                } else {
                    items(visibleVideos) { video ->
                        VideoListCard(
                            video = video,
                            progress = playbackProgress[video.id],
                            modifier = Modifier.clickable { onVideoSelected(video.id) },
                        )
                    }
                }
            }
        }

        LibraryBrowseMode.Groups -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                LazyColumn(
                    state = groupedListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 16.dp, top = 0.dp, end = if (seriesGroups.isEmpty()) 16.dp else 112.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                ) {
                    item {
                        Text("Library")
                    }
                    item {
                        LookTubeCard(
                            title = "Library sync",
                            body = syncState.message,
                        )
                    }
                    item {
                        BrowseModeRow(
                            browseMode = browseMode,
                            onBrowseModeChanged = { browseMode = it },
                        )
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
                                    text = "Sync a feed first to browse grouped videos.",
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
                                            buildMetadataLine(video, playbackProgress[video.id]).takeIf(String::isNotBlank)?.let { metadata ->
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

                if (seriesGroups.isNotEmpty()) {
                    ShowJumpRail(
                        groups = seriesGroups,
                        listState = showRailState,
                        currentGroupIndex = currentGroupIndex,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(end = 12.dp, top = 16.dp, bottom = 16.dp),
                        onGroupSelected = { groupIndex ->
                            scope.launch {
                                groupedListState.animateScrollToItem(GROUP_LIST_START_INDEX + groupIndex)
                            }
                        },
                    )
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

private enum class LibraryBrowseMode(val label: String) {
    Videos("Videos"),
    Groups("Groups"),
}

private enum class HeuristicGroupingMode(val label: String) {
    Show("By show"),
    Cast("By cast"),
    Topic("By topic"),
}

private enum class LibrarySortOption(val label: String) {
    Latest("Latest"),
    Title("Title"),
    Show("Show"),
}

private const val ALL_SERIES_FILTER = "All shows"
private const val GROUP_LIST_START_INDEX = 4

@Composable
private fun BrowseModeRow(
    browseMode: LibraryBrowseMode,
    onBrowseModeChanged: (LibraryBrowseMode) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LibraryBrowseMode.entries.forEach { mode ->
            FilterChip(
                selected = browseMode == mode,
                onClick = { onBrowseModeChanged(mode) },
                label = { Text(mode.label) },
            )
        }
    }
}

private fun buildMetadataLine(
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

@Composable
private fun VideoListCard(
    video: VideoSummary,
    progress: PlaybackProgress?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            VideoThumbnail(video)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = video.displaySeriesTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (video.description.isNotBlank()) {
                    Text(
                        text = video.description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val metadataLine = buildMetadataLine(video, progress)
                if (metadataLine.isNotBlank()) {
                    Text(
                        text = metadataLine,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (progress != null && progress.durationSeconds > 0) {
                    val normalizedProgress = progress.positionSeconds.toFloat() / progress.durationSeconds.toFloat()
                    LinearProgressIndicator(
                        progress = { normalizedProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "Resume at ${formatDuration(progress.positionSeconds)} of ${formatDuration(progress.durationSeconds)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoThumbnail(video: VideoSummary) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .size(width = 120.dp, height = 68.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (!video.thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = "${video.title} thumbnail",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = video.displaySeriesTitle.take(1).ifBlank { "V" },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = video.displaySeriesTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ShowJumpRail(
    groups: List<SeriesGroup>,
    listState: LazyListState,
    currentGroupIndex: Int,
    modifier: Modifier = Modifier,
    onGroupSelected: (Int) -> Unit,
) {
    Surface(
        modifier = modifier.width(96.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 8.dp, horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(groups) { index, group ->
                val selected = index == currentGroupIndex
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    onClick = { onGroupSelected(index) },
                ) {
                    Text(
                        text = group.title,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
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
