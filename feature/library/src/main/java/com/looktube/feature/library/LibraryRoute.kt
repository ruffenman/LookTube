package com.looktube.feature.library

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.looktube.designsystem.LookTubeCard
import com.looktube.designsystem.LookTubePageHeader
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LibraryRoute(
    paddingValues: PaddingValues,
    syncState: LibrarySyncState,
    videos: List<VideoSummary>,
    playbackProgress: Map<String, PlaybackProgress>,
    onVideoSelected: (String) -> Unit,
) {
    var sortOption by rememberSaveable { mutableStateOf(LibrarySortOption.Latest) }
    var selectedSeriesFilter by rememberSaveable { mutableStateOf(ALL_SERIES_FILTER) }
    var groupingMode by rememberSaveable { mutableStateOf(HeuristicGroupingMode.Show) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var railEmphasized by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
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
    val filteredVideos = remember(videos, selectedSeriesFilter) {
        videos.filter { selectedSeriesFilter == ALL_SERIES_FILTER || it.displaySeriesTitle == selectedSeriesFilter }
    }
    val sortedVideos = remember(filteredVideos, sortOption) {
        filteredVideos.sortedFor(sortOption)
    }
    val sections = remember(sortedVideos, sortOption, groupingMode) {
        sortedVideos
            .groupBy { video ->
                when (groupingMode) {
                    HeuristicGroupingMode.Show -> video.seriesGroupingKey
                    HeuristicGroupingMode.Cast -> video.castGroupingKey
                    HeuristicGroupingMode.Topic -> video.topicGroupingKey
                }
            }
            .values
            .map { groupedVideos ->
                SeriesSection(
                    title = groupedVideos.resolveGroupTitle(groupingMode),
                    videos = groupedVideos.sortedFor(sortOption),
                    latestPublishedAt = groupedVideos.maxOfOrNull { it.publishedAtEpochMillis ?: Long.MIN_VALUE } ?: Long.MIN_VALUE,
                )
            }
            .sortedWith(sectionComparator(sortOption))
    }
    val videoCardTextEndPadding = if (sections.isEmpty()) 0.dp else JUMP_RAIL_TEXT_CLEARANCE
    val sectionStartIndices = remember(sections) {
        buildList {
            var currentIndex = GROUP_LIST_START_INDEX
            sections.forEach { section ->
                add(currentIndex)
                currentIndex += 1 + section.videos.size
            }
        }
    }
    val currentGroupIndex by remember(sections, sectionStartIndices, listState) {
        derivedStateOf {
            if (sections.isEmpty()) {
                0
            } else {
                sectionStartIndices.indexOfLast { it <= listState.firstVisibleItemIndex }
                    .coerceAtLeast(0)
            }
        }
    }

    LaunchedEffect(currentGroupIndex, sections.size) {
        if (sections.isNotEmpty()) {
            showRailState.animateScrollToItem(currentGroupIndex.coerceAtMost(sections.lastIndex))
        }
    }

    LaunchedEffect(listState.isScrollInProgress, sections.size) {
        if (sections.isEmpty()) {
            railEmphasized = false
            return@LaunchedEffect
        }
        if (listState.isScrollInProgress) {
            railEmphasized = true
        } else {
            delay(RAIL_IDLE_FADE_DELAY_MS)
            if (!listState.isScrollInProgress) {
                railEmphasized = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, top = 0.dp, end = if (sections.isEmpty()) 16.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            item {
                LookTubePageHeader(
                    title = "Library",
                    subtitle = "Browse your synced Premium videos by show, cast, or topic and jump between sections quickly.",
                )
            }

            item {
                LookTubeCard(
                    title = "Library status",
                    body = syncState.message,
                )
            }

            item {
                BrowseControlsPanel(
                    groupingMode = groupingMode,
                    onGroupingModeChanged = { groupingMode = it },
                    sortOption = sortOption,
                    sortMenuExpanded = sortMenuExpanded,
                    onSortMenuExpandedChanged = { sortMenuExpanded = it },
                    onSortOptionChanged = { sortOption = it },
                    seriesFilters = seriesFilters,
                    selectedSeriesFilter = selectedSeriesFilter,
                    onSeriesFilterChanged = { selectedSeriesFilter = it },
                )
            }

            if (sortedVideos.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = when {
                                videos.isEmpty() -> "Sync your Premium feed on Auth to load your library."
                                selectedSeriesFilter != ALL_SERIES_FILTER -> "No videos match the current show filter."
                                else -> "No videos are available in your synced library yet."
                            },
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            } else {
                sections.forEach { section ->
                    item(key = "section-${section.title}") {
                        SeriesSectionHeader(section = section)
                    }
                    items(
                        items = section.videos,
                        key = { video -> "${section.title}-${video.id}" },
                    ) { video ->
                        VideoListCard(
                            video = video,
                            progress = playbackProgress[video.id],
                            textEndPadding = videoCardTextEndPadding,
                            modifier = Modifier.clickable { onVideoSelected(video.id) },
                        )
                    }
                }
            }
        }

        if (sections.isNotEmpty()) {
            ShowJumpRail(
                groups = sections,
                listState = showRailState,
                currentGroupIndex = currentGroupIndex,
                isEmphasized = railEmphasized,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(end = 4.dp, top = 16.dp, bottom = 16.dp),
                onGroupSelected = { groupIndex ->
                    railEmphasized = true
                    scope.launch {
                        listState.animateScrollToItem(sectionStartIndices[groupIndex])
                    }
                },
            )
        }
    }
}

@Composable
private fun BrowseControlsPanel(
    groupingMode: HeuristicGroupingMode,
    onGroupingModeChanged: (HeuristicGroupingMode) -> Unit,
    sortOption: LibrarySortOption,
    sortMenuExpanded: Boolean,
    onSortMenuExpandedChanged: (Boolean) -> Unit,
    onSortOptionChanged: (LibrarySortOption) -> Unit,
    seriesFilters: List<String>,
    selectedSeriesFilter: String,
    onSeriesFilterChanged: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BrowseControlSection(
                label = "Browse by",
                content = {
                    HeuristicGroupingMode.entries.forEach { mode ->
                        FilterChip(
                            selected = groupingMode == mode,
                            onClick = { onGroupingModeChanged(mode) },
                            label = { Text(mode.label) },
                        )
                    }
                },
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Order",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box {
                    FilterChip(
                        selected = false,
                        onClick = { onSortMenuExpandedChanged(true) },
                        label = { Text("Sort: ${sortOption.label}") },
                    )
                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { onSortMenuExpandedChanged(false) },
                    ) {
                        LibrarySortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    onSortOptionChanged(option)
                                    onSortMenuExpandedChanged(false)
                                },
                            )
                        }
                    }
                }
            }
            BrowseControlSection(
                label = "Filter show",
                content = {
                    seriesFilters.forEach { filter ->
                        FilterChip(
                            selected = selectedSeriesFilter == filter,
                            onClick = { onSeriesFilterChanged(filter) },
                            label = { Text(filter) },
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun BrowseControlSection(
    label: String,
    content: @Composable RowScope.() -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

private data class SeriesSection(
    val title: String,
    val videos: List<VideoSummary>,
    val latestPublishedAt: Long,
)

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
private const val RAIL_IDLE_FADE_DELAY_MS = 1_400L
private val JUMP_RAIL_WIDTH = 176.dp
private val JUMP_RAIL_LABEL_WIDTH = 156.dp
private val JUMP_RAIL_LABEL_END_PADDING = 6.dp
private val JUMP_RAIL_TEXT_CLEARANCE = JUMP_RAIL_LABEL_WIDTH + JUMP_RAIL_LABEL_END_PADDING
private val JUMP_RAIL_TRACK_WIDTH = 8.dp

private fun List<VideoSummary>.sortedFor(sortOption: LibrarySortOption): List<VideoSummary> =
    when (sortOption) {
        LibrarySortOption.Latest -> sortedByDescending { it.publishedAtEpochMillis ?: Long.MIN_VALUE }
        LibrarySortOption.Title -> sortedBy { it.title.lowercase() }
        LibrarySortOption.Show -> sortedWith(
            compareBy<VideoSummary> { it.displaySeriesTitle.lowercase() }
                .thenByDescending { it.publishedAtEpochMillis ?: Long.MIN_VALUE }
                .thenBy { it.title.lowercase() },
        )
    }

private fun sectionComparator(sortOption: LibrarySortOption): Comparator<SeriesSection> =
    when (sortOption) {
        LibrarySortOption.Latest -> compareByDescending<SeriesSection> { it.latestPublishedAt }
            .thenBy { it.title.lowercase() }
        LibrarySortOption.Title,
        LibrarySortOption.Show,
        -> compareBy<SeriesSection> { it.title.lowercase() }
            .thenByDescending { it.latestPublishedAt }
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
private fun SeriesSectionHeader(section: SeriesSection) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "${section.videos.size} videos",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun VideoListCard(
    video: VideoSummary,
    progress: PlaybackProgress?,
    textEndPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val progressFraction = progress?.takeIf { it.durationSeconds > 0 }
        ?.let { (it.positionSeconds.toFloat() / it.durationSeconds.toFloat()).coerceIn(0f, 1f) }

    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            ) {
                VideoThumbnail(video)
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(),
                    color = Color.Black.copy(alpha = 0.52f),
                ) {
                    Column(
                        modifier = Modifier.padding(
                            start = 16.dp,
                            top = 12.dp,
                            end = 16.dp + textEndPadding,
                            bottom = 12.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = video.displaySeriesTitle,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                        )
                        buildMetadataLine(video, progress).takeIf(String::isNotBlank)?.let { metadataLine ->
                            Text(
                                text = metadataLine,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.92f),
                            )
                        }
                    }
                }
            }
            if (progressFraction != null) {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Column(
                modifier = Modifier.padding(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp + textEndPadding,
                    bottom = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleLarge,
                )
                if (video.description.isNotBlank()) {
                    Text(
                        text = video.description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (progress != null && progress.durationSeconds > 0) {
                    Text(
                        text = "Resume at ${formatDuration(progress.positionSeconds)} of ${formatDuration(progress.durationSeconds)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoThumbnail(video: VideoSummary) {
    val shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    Box(
        modifier = Modifier
            .fillMaxSize()
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
                    .padding(20.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = video.displaySeriesTitle.take(1).ifBlank { "V" },
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = video.displaySeriesTitle,
                    style = MaterialTheme.typography.titleMedium,
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
    groups: List<SeriesSection>,
    listState: LazyListState,
    currentGroupIndex: Int,
    isEmphasized: Boolean,
    modifier: Modifier = Modifier,
    onGroupSelected: (Int) -> Unit,
) {
    val labelAlpha by animateFloatAsState(
        targetValue = if (isEmphasized) 0.96f else 0.26f,
        animationSpec = tween(durationMillis = 280),
        label = "jumpRailLabelAlpha",
    )
    val flyoutOffset by animateDpAsState(
        targetValue = if (isEmphasized) 0.dp else 12.dp,
        animationSpec = tween(durationMillis = 280),
        label = "jumpRailFlyoutOffset",
    )

    Row(
        modifier = modifier.width(JUMP_RAIL_WIDTH),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .width(JUMP_RAIL_LABEL_WIDTH)
                .graphicsLayer(alpha = labelAlpha)
                .padding(end = JUMP_RAIL_LABEL_END_PADDING),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.End,
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            itemsIndexed(groups) { index, group ->
                val selected = index == currentGroupIndex
                Surface(
                    modifier = Modifier
                        .offset(x = flyoutOffset)
                        .widthIn(max = 148.dp)
                        .clickable { onGroupSelected(index) },
                    shape = RoundedCornerShape(999.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.54f)
                    },
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    tonalElevation = if (selected) 3.dp else 0.dp,
                ) {
                    Text(
                        text = group.title,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }

        JumpRailTrack(
            groups = groups,
            currentGroupIndex = currentGroupIndex,
        )
    }
}

@Composable
private fun JumpRailTrack(
    groups: List<SeriesSection>,
    currentGroupIndex: Int,
) {
    BoxWithConstraints(
        modifier = Modifier
            .width(JUMP_RAIL_TRACK_WIDTH)
            .fillMaxHeight(),
    ) {
        val thumbHeight = 40.dp
        val fraction = if (groups.size <= 1) {
            0f
        } else {
            currentGroupIndex.toFloat() / groups.lastIndex.toFloat()
        }
        val trackHeight = maxHeight
        val thumbOffset = (trackHeight - thumbHeight) * fraction

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.68f)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = thumbOffset)
                .size(width = JUMP_RAIL_TRACK_WIDTH, height = thumbHeight)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
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
