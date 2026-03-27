package com.looktube.feature.library
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
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
import com.looktube.heuristics.castGroupingKey
import com.looktube.heuristics.castGroupingTitle
import com.looktube.heuristics.displaySeriesTitle
import com.looktube.heuristics.seriesGroupingKey
import com.looktube.heuristics.topicGroupingKey
import com.looktube.heuristics.topicGroupingTitle
import com.looktube.model.LibrarySyncState
import com.looktube.model.LookPointsSummary
import com.looktube.model.PlaybackProgress
import com.looktube.model.SeriesCompletionSummary
import com.looktube.model.VideoEngagementRecord
import com.looktube.model.VideoSummary
import com.looktube.model.bestDurationSeconds
import com.looktube.model.isWatched
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LibraryRoute(
    paddingValues: PaddingValues,
    syncState: LibrarySyncState,
    videos: List<VideoSummary>,
    playbackProgress: Map<String, PlaybackProgress>,
    videoEngagement: Map<String, VideoEngagementRecord>,
    lookPointsSummary: LookPointsSummary,
    seriesCompletionSummaries: Map<String, SeriesCompletionSummary>,
    onVideoSelected: (String) -> Unit,
    onMarkVideoWatched: (String) -> Unit,
    onMarkVideoUnwatched: (String) -> Unit,
    onMarkVideosWatched: (List<String>) -> Unit,
    onMarkVideosUnwatched: (List<String>) -> Unit,
) {
    val density = LocalDensity.current
    val listTopContentPaddingPx = with(density) { LIST_TOP_CONTENT_PADDING.roundToPx() }
    var sortOption by rememberSaveable { mutableStateOf(LibrarySortOption.Latest) }
    var selectedSeriesFilter by rememberSaveable { mutableStateOf(ALL_SERIES_FILTER) }
    var groupingMode by rememberSaveable { mutableStateOf(HeuristicGroupingMode.Show) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var railEmphasized by remember { mutableStateOf(false) }
    var collapsedSectionKeys by remember(groupingMode.name) { mutableStateOf(emptySet<String>()) }
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
    val sections = remember(filteredVideos, sortOption, groupingMode, isGrouped) {
        if (!isGrouped) {
            emptyList()
        } else {
            filteredVideos
                .groupBy { video -> video.groupingKey(groupingMode) }
                .map { (groupKey, groupedVideos) ->
                    val sortedGroupVideos = groupedVideos.sortedWith(videoComparator(sortOption))
                    SeriesSection(
                        key = "${groupingMode.name}:$groupKey",
                        title = groupedVideos.resolveGroupTitle(groupingMode),
                        kindLabel = groupingMode.sectionLabel,
                        videos = sortedGroupVideos,
                        sortAnchor = sortedGroupVideos.first(),
                    )
                }
                .sortedWith(sectionComparator(sortOption))
        }
    }
    LaunchedEffect(sections) {
        val validSectionKeys = sections.mapTo(mutableSetOf(), SeriesSection::key)
        collapsedSectionKeys = collapsedSectionKeys.filterTo(mutableSetOf()) { it in validSectionKeys }
    }
    val displayedSections = remember(sections, collapsedSectionKeys) {
        buildDisplayedSections(
            sections = sections,
            collapsedSectionKeys = collapsedSectionKeys,
        )
    }
    val expandedSectionCount = remember(displayedSections) {
        displayedSections.count(DisplayedSeriesSection::isExpanded)
    }
    val sectionStartIndices = remember(displayedSections) {
        buildSectionStartIndices(displayedSections)
    }
    val jumpTargets = remember(displayedSections, sectionStartIndices) {
        buildList {
            add(JumpRailTarget(title = "Top", itemIndex = 0))
            displayedSections.forEachIndexed { index, displayedSection ->
                add(
                    JumpRailTarget(
                        title = displayedSection.section.title,
                        itemIndex = sectionStartIndices[index],
                    ),
                )
            }
        }
    }
    val hasWideRailLabels = sections.isNotEmpty()
    val railTextClearance = when {
        sections.isNotEmpty() -> JUMP_RAIL_TEXT_CLEARANCE
        sortedVideos.isNotEmpty() -> TOP_ONLY_RAIL_TEXT_CLEARANCE
        else -> TRACK_ONLY_CONTENT_CLEARANCE
    }
    val resultsAnchorOffsetPx by remember(listState, listTopContentPaddingPx) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == RESULTS_ANCHOR_INDEX }
                ?.offset
                ?.minus(listTopContentPaddingPx)
                ?.coerceAtLeast(0)
                ?: if (listState.firstVisibleItemIndex >= VIDEO_LIST_START_INDEX) 0 else Int.MAX_VALUE
        }
    }
    val currentJumpTargetIndex by remember(sections, sectionStartIndices, listState) {
        derivedStateOf {
            if (sections.isEmpty()) {
                0
            } else if (resultsAnchorOffsetPx > 0 || listState.firstVisibleItemIndex < VIDEO_LIST_START_INDEX) {
                0
            } else {
                1 + sectionStartIndices.indexOfLast { it <= listState.firstVisibleItemIndex }
                    .coerceAtLeast(0)
            }
        }
    }
    val railHasScrollableContent by remember(listState, sortedVideos, jumpTargets) {
        derivedStateOf {
            sortedVideos.isNotEmpty() && (
                listState.canScrollBackward ||
                    listState.canScrollForward ||
                    jumpTargets.size > 1
                )
        }
    }
    val showRailLabels by remember(listState, railEmphasized, jumpTargets) {
        derivedStateOf {
            jumpTargets.isNotEmpty() && (
                railEmphasized ||
                    jumpTargets.size > 1 ||
                    resultsAnchorOffsetPx == 0
                )
        }
    }
    LaunchedEffect(currentJumpTargetIndex, jumpTargets.size) {
        if (jumpTargets.isNotEmpty()) {
            showRailState.animateScrollToItem(currentJumpTargetIndex.coerceAtMost(jumpTargets.lastIndex))
        }
    }

    LaunchedEffect(listState.isScrollInProgress, railHasScrollableContent) {
        if (!railHasScrollableContent) {
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
    ) {
        val railTopOffset = if (resultsAnchorOffsetPx == Int.MAX_VALUE) {
            maxHeight
        } else {
            resultsAnchorOffsetPx.dp
        }
        val railHeight = (maxHeight - railTopOffset).coerceAtLeast(0.dp)
        if (resultsAnchorOffsetPx != Int.MAX_VALUE) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = railTopOffset)
                    .height(railHeight)
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                tonalElevation = 2.dp,
            ) {}
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = LIST_TOP_CONTENT_PADDING),
        ) {
            item(key = "library-overview-panel") {
                LibraryOverviewPanel(
                    libraryStatusBody = libraryStatusBody,
                    lookPointsSummary = lookPointsSummary,
                    groupingMode = groupingMode,
                    onGroupingModeChanged = { groupingMode = it },
                    sortOption = sortOption,
                    sortMenuExpanded = sortMenuExpanded,
                    onSortMenuExpandedChanged = { sortMenuExpanded = it },
                    onSortOptionChanged = { sortOption = it },
                    isGrouped = isGrouped,
                    groupSectionCount = displayedSections.size,
                    expandedGroupCount = expandedSectionCount,
                    onExpandAllGroups = {
                        collapsedSectionKeys = emptySet()
                    },
                    onCollapseAllGroups = {
                        collapsedSectionKeys = displayedSections.mapTo(mutableSetOf()) { it.section.key }
                    },
                    seriesFilters = seriesFilters,
                    selectedSeriesFilter = selectedSeriesFilter,
                    onSeriesFilterChanged = { selectedSeriesFilter = it },
                    totalVideoCount = videos.size,
                    filteredVideoCount = filteredVideos.size,
                )
            }
            item(key = "results-anchor") {
                Spacer(modifier = Modifier.fillMaxWidth().height(0.dp))
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
                            modifier = Modifier.padding(
                                start = 16.dp,
                                top = 16.dp,
                                end = 16.dp + railTextClearance,
                                bottom = 16.dp,
                            ),
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
                        engagementRecord = videoEngagement[video.id],
                        dismissDetailsSignal = listState.isScrollInProgress,
                        textEndPadding = railTextClearance,
                        onMarkVideoWatched = onMarkVideoWatched,
                        onMarkVideoUnwatched = onMarkVideoUnwatched,
                        modifier = Modifier.clickable { onVideoSelected(video.id) },
                    )
                }
            } else {
                displayedSections.forEach { displayedSection ->
                    item(key = "section-${displayedSection.section.key}") {
                        SeriesSectionHeader(
                            section = displayedSection.section,
                            watchedVideoCount = displayedSection.section.videos.count { video ->
                                videoEngagement[video.id].isWatched(playbackProgress[video.id])
                            },
                            completionSummary = if (groupingMode == HeuristicGroupingMode.Show) {
                                seriesCompletionSummaries[displayedSection.section.title]
                            } else {
                                null
                            },
                            isExpanded = displayedSection.isExpanded,
                            onToggleExpanded = {
                                collapsedSectionKeys = collapsedSectionKeys.toggle(displayedSection.section.key)
                            },
                            onMarkSectionWatched = {
                                onMarkVideosWatched(displayedSection.section.videos.map(VideoSummary::id))
                            },
                            onMarkSectionUnwatched = {
                                onMarkVideosUnwatched(displayedSection.section.videos.map(VideoSummary::id))
                            },
                            textEndPadding = railTextClearance,
                        )
                    }
                    if (displayedSection.isExpanded) {
                        items(
                            items = displayedSection.section.videos,
                            key = { video -> "${displayedSection.section.key}-${video.id}" },
                        ) { video ->
                            VideoListCard(
                                video = video,
                                progress = playbackProgress[video.id],
                                engagementRecord = videoEngagement[video.id],
                                dismissDetailsSignal = listState.isScrollInProgress,
                                textEndPadding = railTextClearance,
                                onMarkVideoWatched = onMarkVideoWatched,
                                onMarkVideoUnwatched = onMarkVideoUnwatched,
                                modifier = Modifier.clickable { onVideoSelected(video.id) },
                            )
                        }
                    }
                }
            }
        }
        if (railHasScrollableContent && railHeight > 0.dp) {
            ShowJumpRail(
                targets = jumpTargets,
                contentListState = listState,
                listState = showRailState,
                currentTargetIndex = currentJumpTargetIndex,
                isEmphasized = railEmphasized,
                showLabels = showRailLabels,
                hasWideLabels = hasWideRailLabels,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = railTopOffset)
                    .padding(top = LIST_TOP_CONTENT_PADDING, end = 4.dp, bottom = LIST_TOP_CONTENT_PADDING)
                    .height((railHeight - (LIST_TOP_CONTENT_PADDING * 2)).coerceAtLeast(0.dp)),
                onTargetSelected = { target ->
                    railEmphasized = true
                    scope.launch {
                        listState.scrollToItem(target.itemIndex)
                    }
                },
                onScrollFractionChanged = { fraction ->
                    railEmphasized = true
                    scope.launch {
                        listState.scrollToItem(listState.targetItemIndexForFraction(fraction))
                    }
                },
            )
        }
    }
}

@Composable
private fun LookPointsPanel(
    lookPointsSummary: LookPointsSummary,
    isExpanded: Boolean,
    onExpandedChanged: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onExpandedChanged(!isExpanded) },
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Look Points",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = lookPointsSummary.totalPoints.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = "${lookPointsSummary.watchedVideoCount}/${lookPointsSummary.totalVideoCount} videos watched • " +
                    "${lookPointsSummary.completedShowCount}/${lookPointsSummary.totalShowCount} shows complete",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isExpanded) {
                Text(
                    text = "${lookPointsSummary.videoPoints} points from watched videos. " +
                        "Show completion is visual only and does not add score.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatPublishedDateTime(epochMillis: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

private fun buildVideoDetailRows(
    video: VideoSummary,
    progress: PlaybackProgress?,
    engagementRecord: VideoEngagementRecord?,
): List<Pair<String, String>> = buildList {
    add("Video ID" to video.id)
    add("Show" to video.displaySeriesTitle)
    add("Feed category" to video.feedCategory)
    add("Watch status" to libraryWatchStatusLabel(engagementRecord.isWatched(progress), progress))
    video.publishedAtEpochMillis?.let { add("Published" to formatPublishedDateTime(it)) }
    video.durationSeconds?.let { add("Feed duration" to formatDuration(it)) }
    progress?.takeIf { it.durationSeconds > 0 }?.let {
        add("Playback progress" to "${formatDuration(it.positionSeconds)} of ${formatDuration(it.durationSeconds)}")
    }
    add("Playable URL" to (video.playbackUrl ?: "Unavailable"))
    add("Thumbnail URL" to (video.thumbnailUrl ?: "Unavailable"))
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
    isGrouped: Boolean,
    groupSectionCount: Int,
    expandedGroupCount: Int,
    onExpandAllGroups: () -> Unit,
    onCollapseAllGroups: () -> Unit,
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
            if (isGrouped) {
                BrowseControlSection(
                    label = "Group visibility",
                    contentEndPadding = chipRowEndPadding,
                    supportingText = "$expandedGroupCount of $groupSectionCount groups expanded.",
                    content = {
                        FilterChip(
                            selected = groupSectionCount > 0 && expandedGroupCount == groupSectionCount,
                            onClick = onExpandAllGroups,
                            label = { Text("Expand all groups") },
                        )
                        FilterChip(
                            selected = groupSectionCount > 0 && expandedGroupCount == 0,
                            onClick = onCollapseAllGroups,
                            label = { Text("Collapse all groups") },
                        )
                    },
                )
            }
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
            if (isSeriesFilterActive) {
                ActiveShowFilterPanel(
                    selectedSeriesFilter = selectedSeriesFilter,
                    filteredVideoCount = filteredVideoCount,
                    totalVideoCount = totalVideoCount,
                    contentEndPadding = chipRowEndPadding,
                    onSeriesFilterChanged = onSeriesFilterChanged,
                )
            }
        }
    }
}

@Composable
private fun BrowseControlSection(
    label: String,
    contentEndPadding: Dp = 0.dp,
    supportingText: String? = null,
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
        supportingText?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BrowseControlChipRow(
            contentEndPadding = contentEndPadding,
            content = content,
        )
    }
}

internal data class SeriesSection(
    val key: String,
    val title: String,
    val kindLabel: String,
    val videos: List<VideoSummary>,
    val sortAnchor: VideoSummary,
)

internal data class DisplayedSeriesSection(
    val section: SeriesSection,
    val isExpanded: Boolean,
)


@Composable
private fun LibraryOverviewPanel(
    modifier: Modifier = Modifier,
    libraryStatusBody: String,
    lookPointsSummary: LookPointsSummary,
    groupingMode: HeuristicGroupingMode,
    onGroupingModeChanged: (HeuristicGroupingMode) -> Unit,
    sortOption: LibrarySortOption,
    sortMenuExpanded: Boolean,
    onSortMenuExpandedChanged: (Boolean) -> Unit,
    onSortOptionChanged: (LibrarySortOption) -> Unit,
    isGrouped: Boolean,
    groupSectionCount: Int,
    expandedGroupCount: Int,
    onExpandAllGroups: () -> Unit,
    onCollapseAllGroups: () -> Unit,
    seriesFilters: List<String>,
    selectedSeriesFilter: String,
    onSeriesFilterChanged: (String) -> Unit,
    totalVideoCount: Int,
    filteredVideoCount: Int,
) {
    var showLookPointsDetails by rememberSaveable { mutableStateOf(false) }
    var showLibraryConfig by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LookTubePageHeader(
                title = "Library",
                subtitle = "Browse your synced Premium videos by show, cast, or topic and jump between sections quickly.",
            )
            LookPointsPanel(
                lookPointsSummary = lookPointsSummary,
                isExpanded = showLookPointsDetails,
                onExpandedChanged = { showLookPointsDetails = it },
            )
            LibraryConfigPanel(
                libraryStatusBody = libraryStatusBody,
                groupingMode = groupingMode,
                sortOption = sortOption,
                isGrouped = isGrouped,
                groupSectionCount = groupSectionCount,
                expandedGroupCount = expandedGroupCount,
                selectedSeriesFilter = selectedSeriesFilter,
                isExpanded = showLibraryConfig,
                onExpandedChanged = { showLibraryConfig = it },
            ) {
                BrowseControlsPanel(
                    groupingMode = groupingMode,
                    onGroupingModeChanged = onGroupingModeChanged,
                    sortOption = sortOption,
                    sortMenuExpanded = sortMenuExpanded,
                    onSortMenuExpandedChanged = onSortMenuExpandedChanged,
                    onSortOptionChanged = onSortOptionChanged,
                    isGrouped = isGrouped,
                    groupSectionCount = groupSectionCount,
                    expandedGroupCount = expandedGroupCount,
                    onExpandAllGroups = onExpandAllGroups,
                    onCollapseAllGroups = onCollapseAllGroups,
                    seriesFilters = seriesFilters,
                    selectedSeriesFilter = selectedSeriesFilter,
                    onSeriesFilterChanged = onSeriesFilterChanged,
                    totalVideoCount = totalVideoCount,
                    filteredVideoCount = filteredVideoCount,
                    chipRowEndPadding = 0.dp,
                )
            }
        }
    }
}

@Composable
private fun LibraryConfigPanel(
    libraryStatusBody: String,
    groupingMode: HeuristicGroupingMode,
    sortOption: LibrarySortOption,
    isGrouped: Boolean,
    groupSectionCount: Int,
    expandedGroupCount: Int,
    selectedSeriesFilter: String,
    isExpanded: Boolean,
    onExpandedChanged: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    val summary = remember(
        groupingMode,
        sortOption,
        isGrouped,
        groupSectionCount,
        expandedGroupCount,
        selectedSeriesFilter,
    ) {
        buildList {
            add("Sort ${sortOption.label}")
            add("Group ${groupingMode.label}")
            if (isGrouped) {
                add("$expandedGroupCount/$groupSectionCount groups open")
            }
            add(
                if (selectedSeriesFilter == ALL_SERIES_FILTER) {
                    "All shows"
                } else {
                    selectedSeriesFilter
                },
            )
        }.joinToString(" • ")
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable { onExpandedChanged(!isExpanded) }
            .animateContentSize(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Library Config",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Library status, grouping, visibility, sorting, and filters.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isExpanded) "Hide" else "Show",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isExpanded) {
                LookTubeCard(
                    title = "Library status",
                    body = libraryStatusBody,
                )
                content()
            }
        }
    }
}
private data class JumpRailTarget(
    val title: String,
    val itemIndex: Int,
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

internal enum class LibrarySortOption(val label: String) {
    Latest("Latest"),
    Show("Show"),
    Oldest("Oldest"),
}

private const val ALL_SERIES_FILTER = "All shows"
private const val RESULTS_ANCHOR_INDEX = 1
private const val VIDEO_LIST_START_INDEX = 2
private const val RAIL_IDLE_FADE_DELAY_MS = 1_400L
private val LIST_TOP_CONTENT_PADDING = 16.dp
private val JUMP_RAIL_WIDTH = 176.dp
private val TOP_ONLY_RAIL_WIDTH = 84.dp
private val JUMP_RAIL_LABEL_WIDTH = 156.dp
private val TOP_ONLY_LABEL_WIDTH = 60.dp
private val JUMP_RAIL_LABEL_END_PADDING = 6.dp
private val JUMP_RAIL_TEXT_CLEARANCE = JUMP_RAIL_LABEL_WIDTH + JUMP_RAIL_LABEL_END_PADDING
private val TOP_ONLY_RAIL_TEXT_CLEARANCE = TOP_ONLY_LABEL_WIDTH + JUMP_RAIL_LABEL_END_PADDING + 8.dp
private val TRACK_ONLY_CONTENT_CLEARANCE = 20.dp
private val JUMP_RAIL_TRACK_WIDTH = 8.dp

internal fun videoComparator(sortOption: LibrarySortOption): Comparator<VideoSummary> =
    when (sortOption) {
        LibrarySortOption.Latest -> compareByDescending<VideoSummary> { it.publishedAtEpochMillis ?: Long.MIN_VALUE }
            .thenBy(videoSeriesTitleKey())
            .thenBy(videoTitleKey())
            .thenBy(VideoSummary::id)
        LibrarySortOption.Show -> compareBy<VideoSummary>(videoSeriesTitleKey())
            .thenByDescending { it.publishedAtEpochMillis ?: Long.MIN_VALUE }
            .thenBy(videoTitleKey())
            .thenBy(VideoSummary::id)
        LibrarySortOption.Oldest -> compareBy<VideoSummary> { it.publishedAtEpochMillis ?: Long.MAX_VALUE }
            .thenBy(videoSeriesTitleKey())
            .thenBy(videoTitleKey())
            .thenBy(VideoSummary::id)
    }

private fun comparePublishedAtDescending(left: VideoSummary, right: VideoSummary): Int {
    val leftPublishedAt = left.publishedAtEpochMillis ?: Long.MIN_VALUE
    val rightPublishedAt = right.publishedAtEpochMillis ?: Long.MIN_VALUE
    return rightPublishedAt.compareTo(leftPublishedAt)
}

private fun comparePublishedAtAscending(left: VideoSummary, right: VideoSummary): Int {
    val leftPublishedAt = left.publishedAtEpochMillis ?: Long.MAX_VALUE
    val rightPublishedAt = right.publishedAtEpochMillis ?: Long.MAX_VALUE
    return leftPublishedAt.compareTo(rightPublishedAt)
}

private fun compareSeriesTitles(left: VideoSummary, right: VideoSummary): Int =
    left.displaySeriesTitle.lowercase().compareTo(right.displaySeriesTitle.lowercase())

private fun compareTitles(left: VideoSummary, right: VideoSummary): Int =
    left.title.lowercase().compareTo(right.title.lowercase())

internal fun sectionComparator(sortOption: LibrarySortOption): Comparator<SeriesSection> =
    when (sortOption) {
        LibrarySortOption.Latest -> compareByDescending<SeriesSection> {
            it.sortAnchor.publishedAtEpochMillis ?: Long.MIN_VALUE
        }
            .thenBy(sectionTitleKey())
            .thenBy(sectionAnchorTitleKey())
        LibrarySortOption.Show -> compareBy<SeriesSection>(sectionTitleKey())
            .thenByDescending { it.sortAnchor.publishedAtEpochMillis ?: Long.MIN_VALUE }
            .thenBy(sectionAnchorTitleKey())
        LibrarySortOption.Oldest -> compareBy<SeriesSection> {
            it.sortAnchor.publishedAtEpochMillis ?: Long.MAX_VALUE
        }
            .thenBy(sectionTitleKey())
            .thenBy(sectionAnchorTitleKey())
    }

private fun videoSeriesTitleKey(): (VideoSummary) -> String = { video ->
    video.displaySeriesTitle.lowercase()
}

private fun videoTitleKey(): (VideoSummary) -> String = { video ->
    video.title.lowercase()
}

private fun sectionTitleKey(): (SeriesSection) -> String = { section ->
    section.title.lowercase()
}

private fun sectionAnchorTitleKey(): (SeriesSection) -> String = { section ->
    section.sortAnchor.title.lowercase()
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

private fun libraryWatchStatusLabel(
    isWatched: Boolean,
    progress: PlaybackProgress?,
): String = when {
    isWatched -> "Watched"
    progress?.positionSeconds?.let { it > 0 } == true -> "In progress"
    else -> "Not started"
}

internal fun watchToggleActionLabel(isWatched: Boolean): String =
    if (isWatched) "Mark as Unwatched" else "Mark as Watched"

@Composable
private fun SeriesSectionHeader(
    section: SeriesSection,
    watchedVideoCount: Int,
    completionSummary: SeriesCompletionSummary?,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onMarkSectionWatched: () -> Unit,
    onMarkSectionUnwatched: () -> Unit,
    textEndPadding: Dp,
) {
    val supportingText = buildList {
        add("${section.videos.size} ${if (section.videos.size == 1) "video" else "videos"}")
        add("$watchedVideoCount/${section.videos.size} watched")
        if (completionSummary?.let { it.totalVideoCount > 0 && it.watchedVideoCount == it.totalVideoCount } == true) {
            add("Complete")
        }
    }.joinToString(" • ")
    val sectionIsFullyWatched = watchedVideoCount == section.videos.size && section.videos.isNotEmpty()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onToggleExpanded)
            .animateContentSize(),
        shape = RoundedCornerShape(18.dp),
        color = if (isExpanded) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.52f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        },
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    top = 14.dp,
                    end = 16.dp + textEndPadding,
                    bottom = 14.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = section.kindLabel.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onToggleExpanded),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isExpanded) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    contentColor = if (isExpanded) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    tonalElevation = 0.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (isExpanded) "−" else "+",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = sectionIsFullyWatched,
                    onClick = {
                        if (sectionIsFullyWatched) {
                            onMarkSectionUnwatched()
                        } else {
                            onMarkSectionWatched()
                        }
                    },
                    label = { Text(watchToggleActionLabel(sectionIsFullyWatched)) },
                )
            }
        }
    }
}

@Composable
private fun VideoListCard(
    video: VideoSummary,
    progress: PlaybackProgress?,
    engagementRecord: VideoEngagementRecord?,
    dismissDetailsSignal: Boolean = false,
    textEndPadding: Dp = 0.dp,
    onMarkVideoWatched: (String) -> Unit,
    onMarkVideoUnwatched: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progressFraction = progress?.takeIf { it.durationSeconds > 0 }
        ?.let { (it.positionSeconds.toFloat() / it.durationSeconds.toFloat()).coerceIn(0f, 1f) }
    val isWatched = engagementRecord.isWatched(progress)
    var showFullDetails by rememberSaveable(video.id) { mutableStateOf(false) }
    var descriptionTruncated by remember(video.id) { mutableStateOf(false) }
    val detailRows = remember(video, progress, engagementRecord) {
        buildVideoDetailRows(video, progress, engagementRecord)
    }

    LaunchedEffect(dismissDetailsSignal) {
        if (dismissDetailsSignal) {
            showFullDetails = false
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (descriptionTruncated) "More…" else "Info",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { showFullDetails = true },
                    )
                    Text(
                        text = "ID: ${video.id}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (progress != null && progress.durationSeconds > 0) {
                    Text(
                        text = "Resume • ${formatDuration(progress.positionSeconds)} / ${formatDuration(progress.durationSeconds)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = libraryWatchStatusLabel(isWatched, progress),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilterChip(
                        selected = isWatched,
                        onClick = {
                            if (isWatched) {
                                onMarkVideoUnwatched(video.id)
                            } else {
                                onMarkVideoWatched(video.id)
                            }
                        },
                        label = { Text(watchToggleActionLabel(isWatched)) },
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
                    detailRows.forEachIndexed { index, (label, value) ->
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (index != detailRows.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
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
    targets: List<JumpRailTarget>,
    contentListState: LazyListState,
    listState: LazyListState,
    currentTargetIndex: Int,
    isEmphasized: Boolean,
    showLabels: Boolean,
    hasWideLabels: Boolean,
    modifier: Modifier = Modifier,
    onTargetSelected: (JumpRailTarget) -> Unit,
    onScrollFractionChanged: (Float) -> Unit,
) {
    val scrollFraction by remember(contentListState) {
        derivedStateOf { contentListState.approximateScrollFraction() }
    }
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
            .width(if (hasWideLabels) JUMP_RAIL_WIDTH else TOP_ONLY_RAIL_WIDTH),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyColumn(
            state = listState,
            userScrollEnabled = false,
            modifier = Modifier
                .width(if (hasWideLabels) JUMP_RAIL_LABEL_WIDTH else TOP_ONLY_LABEL_WIDTH)
                .graphicsLayer(alpha = labelAlpha)
                .padding(end = JUMP_RAIL_LABEL_END_PADDING)
                .scrollable(
                    state = contentListState,
                    orientation = Orientation.Vertical,
                    reverseDirection = true,
                ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.End,
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            itemsIndexed(targets) { index, target ->
                val selected = index == currentTargetIndex
                Surface(
                    modifier = Modifier
                        .offset(x = flyoutOffset)
                        .widthIn(max = if (hasWideLabels) 148.dp else TOP_ONLY_LABEL_WIDTH)
                        .clickable { onTargetSelected(target) },
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
                        text = target.title,
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
            scrollFraction = scrollFraction,
            isEmphasized = isEmphasized,
            onScrollFractionChanged = onScrollFractionChanged,
        )
    }
}

@Composable
private fun JumpRailTrack(
    scrollFraction: Float,
    isEmphasized: Boolean,
    onScrollFractionChanged: (Float) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .width(JUMP_RAIL_TRACK_WIDTH)
            .fillMaxHeight(),
    ) {
        val thumbHeight = 40.dp
        val availableTravel = (maxHeight - thumbHeight).coerceAtLeast(0.dp)
        val thumbOffset = availableTravel * scrollFraction
        val density = LocalDensity.current
        val trackAlpha by animateFloatAsState(
            targetValue = if (isEmphasized) 0.94f else 0.68f,
            animationSpec = tween(durationMillis = 220),
            label = "jumpRailTrackAlpha",
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = trackAlpha))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val availableTravelPx = with(density) { availableTravel.toPx() }.coerceAtLeast(1f)
                        val targetFraction = ((offset.y - with(density) { thumbHeight.toPx() } / 2f) / availableTravelPx)
                            .coerceIn(0f, 1f)
                        onScrollFractionChanged(targetFraction)
                    }
                },
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

private fun VideoSummary.groupingKey(mode: HeuristicGroupingMode): String =
    when (mode) {
        HeuristicGroupingMode.None -> id
        HeuristicGroupingMode.Show -> seriesGroupingKey
        HeuristicGroupingMode.Cast -> castGroupingKey
        HeuristicGroupingMode.Topic -> topicGroupingKey
    }

internal fun buildDisplayedSections(
    sections: List<SeriesSection>,
    collapsedSectionKeys: Set<String>,
): List<DisplayedSeriesSection> = sections.map { section ->
    DisplayedSeriesSection(
        section = section,
        isExpanded = section.key !in collapsedSectionKeys,
    )
}

internal fun buildSectionStartIndices(
    sections: List<DisplayedSeriesSection>,
): List<Int> = buildList {
    var currentIndex = VIDEO_LIST_START_INDEX
    sections.forEach { section ->
        add(currentIndex)
        currentIndex += 1 + if (section.isExpanded) section.section.videos.size else 0
    }
}

private fun Set<String>.toggle(key: String): Set<String> = if (key in this) {
    this - key
} else {
    this + key
}

@Composable
private fun ActiveShowFilterPanel(
    selectedSeriesFilter: String,
    filteredVideoCount: Int,
    totalVideoCount: Int,
    contentEndPadding: Dp,
    onSeriesFilterChanged: (String) -> Unit,
) {
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
                contentEndPadding = contentEndPadding,
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

private fun LazyListState.approximateScrollFraction(): Float {
    val layoutInfo = layoutInfo
    if (!canScrollBackward && !canScrollForward) {
        return 0f
    }
    if (!canScrollForward) {
        return 1f
    }
    val totalItems = layoutInfo.totalItemsCount
    if (totalItems <= 1) {
        return 0f
    }
    val firstVisibleItemSize = layoutInfo.visibleItemsInfo.firstOrNull()?.size?.coerceAtLeast(1) ?: 1
    val currentPosition = firstVisibleItemIndex + (firstVisibleItemScrollOffset / firstVisibleItemSize.toFloat())
    return (currentPosition / (totalItems - 1).toFloat()).coerceIn(0f, 1f)
}

private fun LazyListState.targetItemIndexForFraction(fraction: Float): Int {
    val targetItemCount = layoutInfo.totalItemsCount
    if (targetItemCount <= 1) {
        return 0
    }
    return (fraction.coerceIn(0f, 1f) * (targetItemCount - 1)).toInt()
}
