package com.looktube.feature.library
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
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
import kotlin.random.Random

@Composable
fun LibraryRoute(
    paddingValues: PaddingValues,
    syncState: LibrarySyncState,
    hasSavedFeedUrl: Boolean,
    videos: List<VideoSummary>,
    playbackProgress: Map<String, PlaybackProgress>,
    videoEngagement: Map<String, VideoEngagementRecord>,
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
    var groupMenuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var railEmphasized by remember { mutableStateOf(false) }
    var lastRailInteraction by remember { mutableStateOf(JumpRailInteraction.Scroll) }
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
            lastRailInteraction = JumpRailInteraction.Scroll
            railEmphasized = true
        } else {
            delay(jumpRailFadeDelayMs(lastRailInteraction))
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
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = LIST_TOP_CONTENT_PADDING),
        ) {
            if (videos.isEmpty()) {
                item {
                    LibraryEmptyStatePanel(
                        syncState = syncState,
                        hasSavedFeedUrl = hasSavedFeedUrl,
                    )
                }
            } else {
                item(key = "library-overview-panel") {
                    LibraryOverviewPanel(
                        libraryStatusBody = libraryStatusBody,
                        groupingMode = groupingMode,
                        groupMenuExpanded = groupMenuExpanded,
                        onGroupMenuExpandedChanged = { groupMenuExpanded = it },
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
            }
            if (videos.isEmpty()) {
                // Empty state handled above.
            } else if (sortedVideos.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = when {
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
                    val groupedHeaderEndPadding = cappedGroupedContentEndPadding(
                        textEndPadding = railTextClearance,
                        maxPadding = GROUPED_HEADER_MAX_END_PADDING,
                    )
                    val groupedVideoEndPadding = cappedGroupedContentEndPadding(
                        textEndPadding = railTextClearance,
                        maxPadding = GROUPED_VIDEO_MAX_END_PADDING,
                    )
                    item(key = "section-${displayedSection.section.key}") {
                        GroupedSeriesSectionCard(
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
                            dismissDetailsSignal = listState.isScrollInProgress,
                            onToggleExpanded = {
                                collapsedSectionKeys = collapsedSectionKeys.toggle(displayedSection.section.key)
                            },
                            onMarkSectionWatched = {
                                onMarkVideosWatched(displayedSection.section.videos.map(VideoSummary::id))
                            },
                            onMarkSectionUnwatched = {
                                onMarkVideosUnwatched(displayedSection.section.videos.map(VideoSummary::id))
                            },
                            headerTextEndPadding = groupedHeaderEndPadding,
                            videoTextEndPadding = groupedVideoEndPadding,
                            playbackProgress = playbackProgress,
                            videoEngagement = videoEngagement,
                            onMarkVideoWatched = onMarkVideoWatched,
                            onMarkVideoUnwatched = onMarkVideoUnwatched,
                            onVideoSelected = onVideoSelected,
                        )
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
                    lastRailInteraction = JumpRailInteraction.TargetSelection
                    railEmphasized = true
                    scope.launch {
                        listState.scrollToItem(target.itemIndex)
                    }
                },
                onScrollFractionChanged = { fraction ->
                    lastRailInteraction = JumpRailInteraction.TargetSelection
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
private fun ExpandedSeriesSectionBackdrop(
    section: SeriesSection,
) {
    if (section.videos.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)),
        )
        return
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)),
    ) {
        SectionHeaderBackdropBaseImage(
            video = section.videos.first(),
            artAlpha = 0.34f,
        )
        val tileSpecs = remember(section.key) { groupHeaderBackdropTileSpecs(section.key) }
        tileSpecs.forEachIndexed { index, tile ->
            val video = section.videos[index % section.videos.size]
            SectionHeaderBackdropPanel(
                video = video,
                sectionKey = section.key,
                colorIndex = index,
                artAlpha = 1f,
                rotationDegrees = tile.rotationDegrees,
                modifier = Modifier
                    .offset(
                        x = (maxWidth * tile.xFraction) - (GROUP_HEADER_BACKDROP_TILE_BLEED / 2),
                        y = (maxHeight * tile.yFraction) - (GROUP_HEADER_BACKDROP_TILE_BLEED / 2),
                    )
                    .width((maxWidth * tile.widthFraction) + GROUP_HEADER_BACKDROP_TILE_BLEED)
                    .height((maxHeight * tile.heightFraction) + GROUP_HEADER_BACKDROP_TILE_BLEED),
            )
        }
    }
}

@Composable
private fun CollapsedSeriesSectionBackdrop(
    section: SeriesSection,
) {
    val leadVideo = section.videos.firstOrNull()
    if (leadVideo == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)),
        )
        return
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
    ) {
        SectionHeaderBackdropBaseImage(
            video = leadVideo,
            artAlpha = 0.78f,
        )
    }
}

@Composable
private fun SectionHeaderBackdropBaseImage(
    video: VideoSummary,
    artAlpha: Float,
) {
    val thumbnailUrl = video.thumbnailUrl
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)),
        )
        if (!thumbnailUrl.isNullOrBlank()) {
            ThumbnailImage(
                thumbnailUrl = thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = artAlpha,
            )
        }
    }
}

@Composable
private fun SectionHeaderBackdropPanel(
    video: VideoSummary,
    sectionKey: String,
    colorIndex: Int,
    artAlpha: Float,
    rotationDegrees: Float,
    modifier: Modifier = Modifier,
) {
    val thumbnailUrl = video.thumbnailUrl
    Surface(
        modifier = modifier.graphicsLayer { rotationZ = rotationDegrees },
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(groupHeaderBackdropColor(sectionKey, video.id, colorIndex)),
        )
        if (!thumbnailUrl.isNullOrBlank()) {
            ThumbnailImage(
                thumbnailUrl = thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = artAlpha,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.04f)),
        )
    }
}

@Composable
private fun CollapsedHeaderCardPeeks(
    section: SeriesSection,
    modifier: Modifier = Modifier,
) {
    val peekOffsets = collapsedHeaderPeekOffsets(section.videos.size)
    val peekVideos = section.videos.drop(1).take(peekOffsets.size)
    if (peekVideos.isEmpty()) {
        return
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds(),
    ) {
        peekVideos.indices.reversed().forEach { index ->
            val video = peekVideos[index]
            SectionPeekCard(
                video = video,
                sectionKey = section.key,
                colorIndex = index + 1,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(
                        x = GROUP_HEADER_PEEK_HORIZONTAL_STAGGER * index,
                        y = peekOffsets[index],
                    )
                    .fillMaxWidth(GROUP_HEADER_PEEK_CARD_WIDTH_FRACTION)
                    .height(GROUP_HEADER_PEEK_CARD_HEIGHT),
            )
        }
    }
}

@Composable
private fun SectionPeekCard(
    video: VideoSummary,
    sectionKey: String,
    colorIndex: Int,
    modifier: Modifier = Modifier,
) {
    val thumbnailUrl = video.thumbnailUrl
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.82f)),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(groupHeaderBackdropColor(sectionKey, video.id, colorIndex)),
            )
            if (!thumbnailUrl.isNullOrBlank()) {
                ThumbnailImage(
                    thumbnailUrl = thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.34f,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            ),
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun LibraryEmptyStatePanel(
    syncState: LibrarySyncState,
    hasSavedFeedUrl: Boolean,
    modifier: Modifier = Modifier,
) {
    val primaryTitle = if (hasSavedFeedUrl) {
        "Ready to sync your library"
    } else {
        "Your library is empty"
    }
    val primaryBody = if (hasSavedFeedUrl) {
        "Your Premium feed URL is already saved. Open Settings and run a sync to load your videos on this device."
    } else {
        "Add a copied Giant Bomb Premium RSS URL in Settings, then run your first sync to load the library."
    }
    val statusBody = buildString {
        append(syncState.message)
        syncState.lastSuccessfulSyncSummary?.let { summary ->
            append("\n\n")
            append(summary)
        }
    }

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
                subtitle = "Your synced Premium videos will appear here after the first successful sync.",
            )
            LookTubeCard(
                title = primaryTitle,
                body = primaryBody,
            )
            LookTubeCard(
                title = "Current status",
                body = statusBody,
            )
        }
    }
}

@Composable
private fun BrowseDropdownControlsRow(
    groupingMode: HeuristicGroupingMode,
    groupMenuExpanded: Boolean,
    onGroupMenuExpandedChanged: (Boolean) -> Unit,
    onGroupingModeChanged: (HeuristicGroupingMode) -> Unit,
    sortOption: LibrarySortOption,
    sortMenuExpanded: Boolean,
    onSortMenuExpandedChanged: (Boolean) -> Unit,
    onSortOptionChanged: (LibrarySortOption) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        BrowseSelectionDropdown(
            label = "Group By",
            value = groupingMode.label,
            expanded = groupMenuExpanded,
            modifier = Modifier.weight(1f),
            onExpandedChanged = onGroupMenuExpandedChanged,
        ) {
            HeuristicGroupingMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label) },
                    onClick = {
                        onGroupingModeChanged(mode)
                        onGroupMenuExpandedChanged(false)
                    },
                )
            }
        }
        BrowseSelectionDropdown(
            label = "Sort By",
            value = sortOption.label,
            expanded = sortMenuExpanded,
            modifier = Modifier.weight(1f),
            onExpandedChanged = onSortMenuExpandedChanged,
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

@Composable
private fun SeriesSectionHeaderBackdrop(
    section: SeriesSection,
    isExpanded: Boolean,
    shape: Shape,
) {
    val baseSurface = MaterialTheme.colorScheme.surface
    val baseSurfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape),
    ) {
        if (isExpanded) {
            ExpandedSeriesSectionBackdrop(section = section)
        } else {
            CollapsedSeriesSectionBackdrop(section = section)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = if (isExpanded) {
                            listOf(
                                baseSurface.copy(alpha = 0.08f),
                                Color.Transparent,
                                baseSurface.copy(alpha = 0.08f),
                                baseSurface.copy(alpha = 0.16f),
                            )
                        } else {
                            listOf(
                                baseSurface.copy(alpha = 0.18f),
                                baseSurface.copy(alpha = 0.38f),
                                baseSurfaceVariant.copy(alpha = 0.82f),
                            )
                        },
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = if (isExpanded) {
                            listOf(
                                baseSurface.copy(alpha = 0.22f),
                                baseSurface.copy(alpha = 0.08f),
                                Color.Transparent,
                            )
                        } else {
                            listOf(
                                baseSurface.copy(alpha = 0.42f),
                                baseSurface.copy(alpha = 0.16f),
                                baseSurface.copy(alpha = 0.28f),
                            )
                        },
                    ),
                ),
        )
        if (isExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.03f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.06f),
                            ),
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun BrowseSelectionDropdown(
    label: String,
    value: String,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onExpandedChanged: (Boolean) -> Unit,
    menuContent: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onExpandedChanged(true) },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 0.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (expanded) "▲" else "▼",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChanged(false) },
        ) {
            menuContent()
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
    groupMenuExpanded: Boolean,
    onGroupMenuExpandedChanged: (Boolean) -> Unit,
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
            BrowseDropdownControlsRow(
                groupingMode = groupingMode,
                groupMenuExpanded = groupMenuExpanded,
                onGroupMenuExpandedChanged = onGroupMenuExpandedChanged,
                onGroupingModeChanged = onGroupingModeChanged,
                sortOption = sortOption,
                sortMenuExpanded = sortMenuExpanded,
                onSortMenuExpandedChanged = onSortMenuExpandedChanged,
                onSortOptionChanged = onSortOptionChanged,
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
internal data class GroupHeaderBackdropTileSpec(
    val xFraction: Float,
    val yFraction: Float,
    val widthFraction: Float,
    val heightFraction: Float,
    val rotationDegrees: Float = 0f,
)
internal fun collapsedHeaderPeekCount(videoCount: Int): Int = (videoCount - 1).coerceIn(0, GROUP_HEADER_MAX_PEEK_COUNT)
internal fun collapsedHeaderPeekOffsets(videoCount: Int): List<Dp> =
    (1..collapsedHeaderPeekCount(videoCount)).map { index -> GROUP_HEADER_PEEK_REVEAL_STEP * index }

internal fun collapsedHeaderPeekReveal(videoCount: Int): Dp {
    return collapsedHeaderPeekOffsets(videoCount).lastOrNull() ?: 0.dp
}

private const val GROUP_HEADER_MAX_PEEK_COUNT = 3
private val GROUP_HEADER_PEEK_REVEAL_STEP = 10.dp
private val GROUP_HEADER_PEEK_HORIZONTAL_STAGGER = 0.dp
private const val GROUP_HEADER_PEEK_CARD_WIDTH_FRACTION = 1f
private val GROUP_HEADER_PEEK_CARD_HEIGHT = 196.dp
private val GROUP_HEADER_COLLAPSED_CARD_MIN_HEIGHT = 196.dp
private val GROUP_HEADER_EXPANDED_CARD_MIN_HEIGHT = 228.dp
private val GROUP_HEADER_BACKDROP_TILE_BLEED = 12.dp
private const val GROUP_HEADER_BACKDROP_POSITION_JITTER = 0.03f
private const val GROUP_HEADER_BACKDROP_WIDTH_JITTER = 0.03f
private const val GROUP_HEADER_BACKDROP_HEIGHT_JITTER = 0.03f
private const val GROUP_HEADER_BACKDROP_ROTATION_JITTER = 4f
private val GROUP_HEADER_BACKDROP_BASE_TILE_SPECS = listOf(
    GroupHeaderBackdropTileSpec(xFraction = -0.12f, yFraction = -0.08f, widthFraction = 0.34f, heightFraction = 0.22f),
    GroupHeaderBackdropTileSpec(xFraction = 0.16f, yFraction = -0.06f, widthFraction = 0.32f, heightFraction = 0.22f),
    GroupHeaderBackdropTileSpec(xFraction = 0.44f, yFraction = -0.08f, widthFraction = 0.34f, heightFraction = 0.24f),
    GroupHeaderBackdropTileSpec(xFraction = 0.72f, yFraction = -0.05f, widthFraction = 0.30f, heightFraction = 0.22f),
    GroupHeaderBackdropTileSpec(xFraction = -0.10f, yFraction = 0.24f, widthFraction = 0.30f, heightFraction = 0.24f),
    GroupHeaderBackdropTileSpec(xFraction = 0.16f, yFraction = 0.22f, widthFraction = 0.36f, heightFraction = 0.26f),
    GroupHeaderBackdropTileSpec(xFraction = 0.48f, yFraction = 0.26f, widthFraction = 0.32f, heightFraction = 0.24f),
    GroupHeaderBackdropTileSpec(xFraction = 0.76f, yFraction = 0.22f, widthFraction = 0.28f, heightFraction = 0.24f),
    GroupHeaderBackdropTileSpec(xFraction = -0.06f, yFraction = 0.56f, widthFraction = 0.32f, heightFraction = 0.24f),
    GroupHeaderBackdropTileSpec(xFraction = 0.22f, yFraction = 0.58f, widthFraction = 0.30f, heightFraction = 0.22f),
    GroupHeaderBackdropTileSpec(xFraction = 0.48f, yFraction = 0.54f, widthFraction = 0.34f, heightFraction = 0.24f),
    GroupHeaderBackdropTileSpec(xFraction = 0.76f, yFraction = 0.58f, widthFraction = 0.28f, heightFraction = 0.22f),
)
private val GroupHeaderBackdropPalette = listOf(
    Color(0xFF44556B),
    Color(0xFF6A503C),
    Color(0xFF3C5F58),
    Color(0xFF65506A),
    Color(0xFF586842),
)

private fun groupHeaderBackdropColor(
    sectionKey: String,
    videoId: String,
    index: Int,
): Color {
    val paletteIndex = ("$sectionKey:$videoId:$index".hashCode() and Int.MAX_VALUE) % GroupHeaderBackdropPalette.size
    return GroupHeaderBackdropPalette[paletteIndex]
}

internal fun groupHeaderBackdropTileSpecs(sectionKey: String): List<GroupHeaderBackdropTileSpec> {
    val random = Random(sectionKey.hashCode())
    return GROUP_HEADER_BACKDROP_BASE_TILE_SPECS.map { tile ->
        GroupHeaderBackdropTileSpec(
            xFraction = (tile.xFraction + random.centeredJitter(GROUP_HEADER_BACKDROP_POSITION_JITTER))
                .coerceIn(-0.14f, 0.82f),
            yFraction = (tile.yFraction + random.centeredJitter(GROUP_HEADER_BACKDROP_POSITION_JITTER))
                .coerceIn(-0.12f, 0.7f),
            widthFraction = (tile.widthFraction + random.centeredJitter(GROUP_HEADER_BACKDROP_WIDTH_JITTER))
                .coerceIn(0.26f, 0.38f),
            heightFraction = (tile.heightFraction + random.centeredJitter(GROUP_HEADER_BACKDROP_HEIGHT_JITTER))
                .coerceIn(0.18f, 0.28f),
            rotationDegrees = random.centeredJitter(GROUP_HEADER_BACKDROP_ROTATION_JITTER),
        )
    }
}

private fun Random.centeredJitter(magnitude: Float): Float = (nextFloat() - 0.5f) * magnitude * 2f


@Composable
private fun LibraryOverviewPanel(
    modifier: Modifier = Modifier,
    libraryStatusBody: String,
    groupingMode: HeuristicGroupingMode,
    groupMenuExpanded: Boolean,
    onGroupMenuExpandedChanged: (Boolean) -> Unit,
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
                    groupMenuExpanded = groupMenuExpanded,
                    onGroupMenuExpandedChanged = onGroupMenuExpandedChanged,
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
            add(sortOption.label)
            add(groupingMode.label)
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
private const val PASSIVE_RAIL_IDLE_FADE_DELAY_MS = 200L
private const val TAP_RAIL_IDLE_FADE_DELAY_MS = 1_000L
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
private val GROUPED_HEADER_MAX_END_PADDING = 148.dp
private val GROUPED_EXPANDED_INFO_PLATE_MAX_END_PADDING = 96.dp
private val GROUPED_VIDEO_MAX_END_PADDING = 112.dp

internal enum class JumpRailInteraction {
    Scroll,
    TargetSelection,
}

internal fun jumpRailFadeDelayMs(interaction: JumpRailInteraction): Long =
    if (interaction == JumpRailInteraction.TargetSelection) {
        TAP_RAIL_IDLE_FADE_DELAY_MS
    } else {
        PASSIVE_RAIL_IDLE_FADE_DELAY_MS
    }

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

internal fun displayedPlaybackProgress(
    video: VideoSummary,
    progress: PlaybackProgress?,
    isWatched: Boolean,
): PlaybackProgress? {
    if (!isWatched) {
        return progress
    }
    val durationSeconds = video.bestDurationSeconds(progress)?.takeIf { it > 0 } ?: return progress
    return PlaybackProgress(
        videoId = video.id,
        positionSeconds = durationSeconds,
        durationSeconds = durationSeconds,
    )
}

private fun cappedGroupedContentEndPadding(
    textEndPadding: Dp,
    maxPadding: Dp,
): Dp = if (textEndPadding > maxPadding) maxPadding else textEndPadding

@Composable
private fun GroupedSeriesSectionCard(
    section: SeriesSection,
    watchedVideoCount: Int,
    completionSummary: SeriesCompletionSummary?,
    isExpanded: Boolean,
    dismissDetailsSignal: Boolean,
    onToggleExpanded: () -> Unit,
    onMarkSectionWatched: () -> Unit,
    onMarkSectionUnwatched: () -> Unit,
    headerTextEndPadding: Dp,
    videoTextEndPadding: Dp,
    playbackProgress: Map<String, PlaybackProgress>,
    videoEngagement: Map<String, VideoEngagementRecord>,
    onMarkVideoWatched: (String) -> Unit,
    onMarkVideoUnwatched: (String) -> Unit,
    onVideoSelected: (String) -> Unit,
) {
    val collapsedPeekReveal = collapsedHeaderPeekReveal(section.videos.size)
    if (isExpanded) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
            tonalElevation = 1.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.76f)),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SeriesSectionHeader(
                    section = section,
                    watchedVideoCount = watchedVideoCount,
                    completionSummary = completionSummary,
                    isExpanded = true,
                    onToggleExpanded = onToggleExpanded,
                    onMarkSectionWatched = onMarkSectionWatched,
                    onMarkSectionUnwatched = onMarkSectionUnwatched,
                    textEndPadding = headerTextEndPadding,
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f),
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    section.videos.forEach { video ->
                        GroupedSectionVideoCard(
                            video = video,
                            progress = playbackProgress[video.id],
                            engagementRecord = videoEngagement[video.id],
                            dismissDetailsSignal = dismissDetailsSignal,
                            textEndPadding = videoTextEndPadding,
                            onMarkVideoWatched = onMarkVideoWatched,
                            onMarkVideoUnwatched = onMarkVideoUnwatched,
                            onVideoSelected = onVideoSelected,
                        )
                    }
                }
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = GROUP_HEADER_COLLAPSED_CARD_MIN_HEIGHT + collapsedPeekReveal),
        ) {
            if (collapsedPeekReveal > 0.dp) {
                CollapsedHeaderCardPeeks(
                    section = section,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .height(GROUP_HEADER_COLLAPSED_CARD_MIN_HEIGHT + collapsedPeekReveal),
                )
            }
            SeriesSectionHeader(
                section = section,
                watchedVideoCount = watchedVideoCount,
                completionSummary = completionSummary,
                isExpanded = false,
                onToggleExpanded = onToggleExpanded,
                onMarkSectionWatched = onMarkSectionWatched,
                onMarkSectionUnwatched = onMarkSectionUnwatched,
                textEndPadding = headerTextEndPadding,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }
}

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
    modifier: Modifier = Modifier,
) {
    val supportingText = buildList {
        add("${section.videos.size} ${if (section.videos.size == 1) "video" else "videos"}")
        add("$watchedVideoCount/${section.videos.size} watched")
        if (completionSummary?.let { it.totalVideoCount > 0 && it.watchedVideoCount == it.totalVideoCount } == true) {
            add("Complete")
        }
    }.joinToString(" • ")
    val sectionIsFullyWatched = watchedVideoCount == section.videos.size && section.videos.isNotEmpty()
    val shape = RoundedCornerShape(22.dp)
    val headerMinHeight = if (isExpanded) {
        GROUP_HEADER_EXPANDED_CARD_MIN_HEIGHT
    } else {
        GROUP_HEADER_COLLAPSED_CARD_MIN_HEIGHT
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = headerMinHeight)
            .clip(shape)
            .clickable(onClick = onToggleExpanded),
        shape = shape,
        color = if (isExpanded) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
        },
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = if (isExpanded) 1.dp else 0.dp,
        border = BorderStroke(
            1.dp,
            if (isExpanded) {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.92f)
            },
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
        ) {
            SeriesSectionHeaderBackdrop(
                section = section,
                isExpanded = isExpanded,
                shape = shape,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = headerMinHeight)
                    .padding(if (isExpanded) 10.dp else 14.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isExpanded) Arrangement.End else Arrangement.Start,
                ) {
                    Surface(
                        modifier = Modifier.size(if (isExpanded) 34.dp else 40.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = if (isExpanded) 0.88f else 0.94f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        tonalElevation = 0.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f)),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (isExpanded) "−" else "+",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                if (isExpanded) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.58f)
                            .widthIn(max = 300.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        tonalElevation = 0.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    text = section.kindLabel.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f),
                                )
                                Text(
                                    text = section.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = supportingText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                            ) {
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
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = textEndPadding),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = section.kindLabel.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = supportingText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupedSectionVideoCard(
    video: VideoSummary,
    progress: PlaybackProgress?,
    engagementRecord: VideoEngagementRecord?,
    dismissDetailsSignal: Boolean = false,
    textEndPadding: Dp = 0.dp,
    onMarkVideoWatched: (String) -> Unit,
    onMarkVideoUnwatched: (String) -> Unit,
    onVideoSelected: (String) -> Unit,
) {
    VideoListCard(
        video = video,
        progress = progress,
        engagementRecord = engagementRecord,
        dismissDetailsSignal = dismissDetailsSignal,
        textEndPadding = textEndPadding,
        onMarkVideoWatched = onMarkVideoWatched,
        onMarkVideoUnwatched = onMarkVideoUnwatched,
        modifier = Modifier.clickable { onVideoSelected(video.id) },
    )
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
    val isWatched = engagementRecord.isWatched(progress)
    val displayedProgress = displayedPlaybackProgress(
        video = video,
        progress = progress,
        isWatched = isWatched,
    )
    val progressFraction = displayedProgress?.takeIf { it.durationSeconds > 0 }
        ?.let { (it.positionSeconds.toFloat() / it.durationSeconds.toFloat()).coerceIn(0f, 1f) }
    var showFullDetails by rememberSaveable(video.id) { mutableStateOf(false) }
    var descriptionTruncated by remember(video.id) { mutableStateOf(false) }
    val detailRows = remember(video, displayedProgress, engagementRecord) {
        buildVideoDetailRows(video, displayedProgress, engagementRecord)
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
                        buildMetadataLine(video, displayedProgress).takeIf(String::isNotBlank)?.let { metadataLine ->
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
                LibraryCardProgressBar(
                    progressFraction = progressFraction,
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
                if (displayedProgress != null && displayedProgress.durationSeconds > 0) {
                    Text(
                        text = "Resume • ${formatDuration(displayedProgress.positionSeconds)} / ${formatDuration(displayedProgress.durationSeconds)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = libraryWatchStatusLabel(isWatched, displayedProgress),
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
private fun LibraryCardProgressBar(
    progressFraction: Float,
    modifier: Modifier = Modifier,
) {
    val clampedProgress = progressFraction.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(4.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(clampedProgress)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun VideoThumbnail(video: VideoSummary) {
    val shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    val thumbnailUrl = video.thumbnailUrl
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (!thumbnailUrl.isNullOrBlank()) {
            ThumbnailImage(
                thumbnailUrl = thumbnailUrl,
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
private fun ThumbnailImage(
    thumbnailUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alpha: Float = 1f,
) {
    val context = LocalContext.current
    val resourceId = remember(context, thumbnailUrl) {
        androidResourceIdFromUri(
            context = context,
            uriString = thumbnailUrl,
        )
    }
    val imageModifier = if (alpha < 0.999f) {
        modifier.graphicsLayer(alpha = alpha)
    } else {
        modifier
    }
    if (resourceId != null) {
        Image(
            painter = painterResource(resourceId),
            contentDescription = contentDescription,
            modifier = imageModifier,
            contentScale = contentScale,
        )
    } else {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = contentDescription,
            modifier = imageModifier,
            contentScale = contentScale,
        )
    }
}

private fun androidResourceIdFromUri(
    context: Context,
    uriString: String,
): Int? {
    val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
    if (uri.scheme != ContentResolver.SCHEME_ANDROID_RESOURCE) {
        return null
    }
    val resourcePackage = uri.authority ?: context.packageName
    val pathSegments = uri.pathSegments
    return when {
        pathSegments.size == 1 -> pathSegments.firstOrNull()?.toIntOrNull()
        pathSegments.size >= 2 -> {
            val resourceType = pathSegments[pathSegments.lastIndex - 1]
            val resourceName = pathSegments.last()
            context.resources.getIdentifier(resourceName, resourceType, resourcePackage)
                .takeIf { it != 0 }
        }
        else -> null
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
        animationSpec = tween(durationMillis = 160),
        label = "jumpRailLabelAlpha",
    )
    val flyoutOffset by animateDpAsState(
        targetValue = if (isEmphasized) 0.dp else 12.dp,
        animationSpec = tween(durationMillis = 160),
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
            animationSpec = tween(durationMillis = 140),
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
        currentIndex += 1
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

