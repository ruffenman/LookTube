package com.looktube.feature.library

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
import kotlinx.coroutines.CoroutineScope
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
    val isGrouped = groupingMode != HeuristicGroupingMode.None
    val listState = rememberLazyListState()
    val showRailState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val latestPublishedAtBySeries = remember(videos) {
        videos.groupBy(VideoSummary::displaySeriesTitle)
            .mapValues { (_, groupedVideos) ->
                groupedVideos.maxOfOrNull { it.publishedAtEpochMillis ?: Long.MIN_VALUE } ?: Long.MIN_VALUE
            }
    }
    val oldestPublishedAtBySeries = remember(videos) {
        videos.groupBy(VideoSummary::displaySeriesTitle)
            .mapValues { (_, groupedVideos) ->
                groupedVideos.minOfOrNull { it.publishedAtEpochMillis ?: Long.MAX_VALUE } ?: Long.MAX_VALUE
            }
    }
    val seriesFilters = remember(videos, sortOption, latestPublishedAtBySeries, oldestPublishedAtBySeries) {
        val sortedFilters = videos
            .map(VideoSummary::displaySeriesTitle)
            .distinct()
            .sortedWith(
                when (sortOption) {
                    LibrarySortOption.Latest -> compareByDescending<String> { latestPublishedAtBySeries[it] ?: Long.MIN_VALUE }
                        .thenBy { it.lowercase() }
                    LibrarySortOption.Oldest -> compareBy<String> { oldestPublishedAtBySeries[it] ?: Long.MAX_VALUE }
                        .thenBy(String::lowercase)
                    LibrarySortOption.Show -> compareBy(String::lowercase)
                },
            )
        listOf(ALL_SERIES_FILTER) + sortedFilters
    }
    val filteredVideos = remember(videos, selectedSeriesFilter) {
        videos.filter { selectedSeriesFilter == ALL_SERIES_FILTER || it.displaySeriesTitle == selectedSeriesFilter }
    }
    val sortedVideos = remember(filteredVideos, sortOption) {
        filteredVideos.sortedWith(videoComparator(sortOption))
    }
    val libraryStatusBody = remember(syncState) {
        buildString {
            append(syncState.message)
            syncState.lastSuccessfulSyncSummary?.let { summary ->
                append("\n\n")
                append(summary)
            }
        }
    }
    val showRailLabels by remember(listState, railEmphasized) {
        derivedStateOf {
            railEmphasized || listState.firstVisibleItemIndex >= GROUP_LIST_START_INDEX
        }
    }
    val sections = remember(filteredVideos, sortOption, groupingMode, isGrouped) {
        if (!isGrouped) {
            emptyList()
        } else {
            filteredVideos
                .groupBy { video ->
                    when (groupingMode) {
                        HeuristicGroupingMode.None -> video.id
                        HeuristicGroupingMode.Show -> video.seriesGroupingKey
                        HeuristicGroupingMode.Cast -> video.castGroupingKey
                        HeuristicGroupingMode.Topic -> video.topicGroupingKey
                    }
                }
                .values
                .map { groupedVideos ->
                    val sortedGroupVideos = groupedVideos.sortedWith(videoComparator(sortOption))
                    SeriesSection(
                        title = groupedVideos.resolveGroupTitle(groupingMode),
                        kindLabel = groupingMode.sectionLabel,
                        videos = sortedGroupVideos,
                        sortAnchor = sortedGroupVideos.first(),
                    )
                }
                .sortedWith(sectionComparator(sortOption))
        }
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
                    body = libraryStatusBody,
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
                    totalVideoCount = videos.size,
                    filteredVideoCount = filteredVideos.size,
                    chipRowEndPadding = if (sections.isEmpty()) 0.dp else JUMP_RAIL_CONTROL_CLEARANCE,
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
            } else if (!isGrouped) {
                items(
                    items = sortedVideos,
                    key = { video -> video.id },
                ) { video ->
                    VideoListCard(
                        video = video,
                        progress = playbackProgress[video.id],
                        dismissDetailsSignal = listState.isScrollInProgress,
                        modifier = Modifier.clickable { onVideoSelected(video.id) },
                    )
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
                            dismissDetailsSignal = listState.isScrollInProgress,
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
                contentListState = listState,
                listState = showRailState,
                currentGroupIndex = currentGroupIndex,
                isEmphasized = railEmphasized,
                showLabels = showRailLabels,
                railScope = scope,
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
@OptIn(ExperimentalLayoutApi::class)
private fun BrowseControlChipRow(
    contentEndPadding: Dp,
    content: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = contentEndPadding),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
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
    totalVideoCount: Int,
    filteredVideoCount: Int,
    chipRowEndPadding: Dp,
) {
    val isSeriesFilterActive = selectedSeriesFilter != ALL_SERIES_FILTER
    val availableShowCount = (seriesFilters.size - 1).coerceAtLeast(0)
    var showFiltersExpanded by rememberSaveable { mutableStateOf(false) }
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
            if (isSeriesFilterActive) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Show filter active",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = "Showing $filteredVideoCount of $totalVideoCount videos for $selectedSeriesFilter.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        BrowseControlChipRow(
                            contentEndPadding = chipRowEndPadding,
                        ) {
                            FilterChip(
                                selected = true,
                                onClick = { onSeriesFilterChanged(selectedSeriesFilter) },
                                label = { Text(selectedSeriesFilter) },
                            )
                            FilterChip(
                                selected = false,
                                onClick = { onSeriesFilterChanged(ALL_SERIES_FILTER) },
                                label = { Text("Clear filter") },
                            )
                        }
                    }
                }
            }
            BrowseControlSection(
                label = "Group By",
                contentEndPadding = chipRowEndPadding,
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
                    text = "Sort By",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box {
                    FilterChip(
                        selected = false,
                        onClick = { onSortMenuExpandedChanged(true) },
                        label = { Text("Sort By: ${sortOption.label}") },
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
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showFiltersExpanded = !showFiltersExpanded },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                tonalElevation = 1.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Show filters",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = if (isSeriesFilterActive) {
                                "Filtering to $selectedSeriesFilter."
                            } else {
                                "$availableShowCount shows available. Collapsed by default."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (showFiltersExpanded) "Hide" else "Show",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (showFiltersExpanded) {
                BrowseControlSection(
                    label = if (isSeriesFilterActive) "Filter show (active)" else "Filter show",
                    contentEndPadding = chipRowEndPadding,
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
}

@Composable
private fun BrowseControlSection(
    label: String,
    contentEndPadding: Dp = 0.dp,
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
        BrowseControlChipRow(
            contentEndPadding = contentEndPadding,
            content = content,
        )
    }
}

private data class SeriesSection(
    val title: String,
    val kindLabel: String,
    val videos: List<VideoSummary>,
    val sortAnchor: VideoSummary,
)

private enum class HeuristicGroupingMode(
    val label: String,
    val sectionLabel: String,
) {
    None("None", "Videos"),
    Show("Show", "Show"),
    Cast("Cast", "Cast"),
    Topic("Topic", "Topic"),
}

private enum class LibrarySortOption(val label: String) {
    Latest("Latest"),
    Show("Show"),
    Oldest("Oldest"),
}

private const val ALL_SERIES_FILTER = "All shows"
private const val GROUP_LIST_START_INDEX = 3
private const val RAIL_IDLE_FADE_DELAY_MS = 1_400L
private val JUMP_RAIL_WIDTH = 176.dp
private val JUMP_RAIL_LABEL_WIDTH = 156.dp
private val JUMP_RAIL_LABEL_END_PADDING = 6.dp
private val JUMP_RAIL_TEXT_CLEARANCE = JUMP_RAIL_LABEL_WIDTH + JUMP_RAIL_LABEL_END_PADDING
private val JUMP_RAIL_CONTROL_CLEARANCE = 92.dp
private val JUMP_RAIL_TRACK_WIDTH = 8.dp

private fun videoComparator(sortOption: LibrarySortOption): Comparator<VideoSummary> =
    when (sortOption) {
        LibrarySortOption.Latest -> compareByDescending<VideoSummary> { it.publishedAtEpochMillis ?: Long.MIN_VALUE }
            .thenBy { it.displaySeriesTitle.lowercase() }
            .thenBy { it.title.lowercase() }
        LibrarySortOption.Show -> compareBy<VideoSummary> { it.displaySeriesTitle.lowercase() }
            .thenByDescending { it.publishedAtEpochMillis ?: Long.MIN_VALUE }
            .thenBy { it.title.lowercase() }
        LibrarySortOption.Oldest -> compareBy<VideoSummary> { it.publishedAtEpochMillis ?: Long.MAX_VALUE }
            .thenBy { it.displaySeriesTitle.lowercase() }
            .thenBy { it.title.lowercase() }
    }

private fun sectionComparator(sortOption: LibrarySortOption): Comparator<SeriesSection> {
    val anchorComparator = videoComparator(sortOption)
    return Comparator { left, right ->
        val anchorComparison = anchorComparator.compare(left.sortAnchor, right.sortAnchor)
        if (anchorComparison != 0) {
            anchorComparison
        } else {
            left.title.lowercase().compareTo(right.title.lowercase())
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
private fun SeriesSectionHeader(section: SeriesSection) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.46f),
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = section.kindLabel.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Text(
                        text = "${section.videos.size} ${if (section.videos.size == 1) "video" else "videos"}",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoListCard(
    video: VideoSummary,
    progress: PlaybackProgress?,
    dismissDetailsSignal: Boolean = false,
    textEndPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val progressFraction = progress?.takeIf { it.durationSeconds > 0 }
        ?.let { (it.positionSeconds.toFloat() / it.durationSeconds.toFloat()).coerceIn(0f, 1f) }
    var showFullDetails by rememberSaveable(video.id) { mutableStateOf(false) }
    var descriptionTruncated by remember(video.id) { mutableStateOf(false) }

    LaunchedEffect(dismissDetailsSignal) {
        if (dismissDetailsSignal) {
            showFullDetails = false
        }
    }

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
                        onTextLayout = { descriptionTruncated = it.hasVisualOverflow },
                    )
                }
                if (descriptionTruncated) {
                    Text(
                        text = "More…",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { showFullDetails = true },
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
    if (showFullDetails) {
        Dialog(
            onDismissRequest = { showFullDetails = false },
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "Show: ${video.displaySeriesTitle}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    buildMetadataLine(video, progress).takeIf(String::isNotBlank)?.let { metadataLine ->
                        Text(
                            text = metadataLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (video.description.isNotBlank()) {
                        Text(
                            text = video.description,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        text = "Tap outside this card or scroll the list to dismiss.",
                        style = MaterialTheme.typography.bodySmall,
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
    contentListState: LazyListState,
    listState: LazyListState,
    currentGroupIndex: Int,
    isEmphasized: Boolean,
    showLabels: Boolean,
    railScope: CoroutineScope,
    modifier: Modifier = Modifier,
    onGroupSelected: (Int) -> Unit,
) {
    val labelAlpha by animateFloatAsState(
        targetValue = when {
            !showLabels -> 0f
            isEmphasized -> 0.96f
            else -> 0.26f
        },
        animationSpec = tween(durationMillis = 280),
        label = "jumpRailLabelAlpha",
    )
    val flyoutOffset by animateDpAsState(
        targetValue = if (isEmphasized) 0.dp else 12.dp,
        animationSpec = tween(durationMillis = 280),
        label = "jumpRailFlyoutOffset",
    )

    Row(
        modifier = modifier
            .width(JUMP_RAIL_WIDTH)
            .jumpRailDragModifier(contentListState, railScope),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyColumn(
            state = listState,
            userScrollEnabled = false,
            modifier = Modifier
                .width(JUMP_RAIL_LABEL_WIDTH)
                .graphicsLayer(alpha = labelAlpha)
                .padding(end = JUMP_RAIL_LABEL_END_PADDING)
                .jumpRailDragModifier(contentListState, railScope),
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
                        .jumpRailDragModifier(contentListState, railScope)
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
        HeuristicGroupingMode.None -> map(VideoSummary::title)
        HeuristicGroupingMode.Show -> map(VideoSummary::displaySeriesTitle)
        HeuristicGroupingMode.Cast -> map(VideoSummary::castGroupingTitle)
        HeuristicGroupingMode.Topic -> map(VideoSummary::topicGroupingTitle)
    }
        .groupingBy { it }
        .eachCount()
        .maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        ?.key
        ?: first().displaySeriesTitle

private fun Modifier.jumpRailDragModifier(
    contentListState: LazyListState,
    railScope: CoroutineScope,
): Modifier = pointerInput(contentListState) {
    detectVerticalDragGestures(
        onVerticalDrag = { change, dragAmount ->
            change.consume()
            railScope.launch {
                contentListState.scrollBy(-dragAmount)
            }
        },
    )
}
