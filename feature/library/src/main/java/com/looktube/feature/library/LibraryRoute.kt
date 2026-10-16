package com.looktube.feature.library
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntOffset
import coil3.compose.AsyncImage
import com.looktube.designsystem.LookTubeStyle
import com.looktube.designsystem.LookTubeCard
import com.looktube.designsystem.LookTubePageHeader
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

@OptIn(ExperimentalLayoutApi::class)
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
    initialCollapsedSectionTitles: Set<String> = emptySet(),
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val browsePreferences = remember(context) { context.getSharedPreferences("looktube.library.browse", Context.MODE_PRIVATE) }
    val listTopContentPaddingPx = with(density) { LIST_TOP_CONTENT_PADDING.roundToPx() }
    var sortOption by rememberSaveable { mutableStateOf(LibrarySortOption.entries.firstOrNull { it.name == browsePreferences.getString("sort", null) } ?: LibrarySortOption.Latest) }
    var selectedSeriesFilter by rememberSaveable { mutableStateOf(ALL_SERIES_FILTER) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var watchFilter by rememberSaveable { mutableStateOf(LibraryWatchFilter.All) }
    var groupingMode by rememberSaveable { mutableStateOf(HeuristicGroupingMode.entries.firstOrNull { it.name == browsePreferences.getString("grouping", null) } ?: HeuristicGroupingMode.Show) }
    var groupMenuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var collapsedSectionKeys by rememberSaveable(
        stateSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() }),
    ) {
        mutableStateOf(
            if (initialCollapsedSectionTitles.isNotEmpty()) {
                videos.filter { it.displaySeriesTitle in initialCollapsedSectionTitles }
                    .mapTo(mutableSetOf()) { "Show:${it.groupingKey(HeuristicGroupingMode.Show)}" }
            } else browsePreferences.getStringSet("collapsed", emptySet()).orEmpty().toSet(),
        )
    }
    var railMenuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(collapsedSectionKeys, sortOption, groupingMode) {
        browsePreferences.edit().putStringSet("collapsed", collapsedSectionKeys)
            .putString("sort", sortOption.name).putString("grouping", groupingMode.name).apply()
    }
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
    val filteredVideos = remember(videos, selectedSeriesFilter, searchQuery, watchFilter, playbackProgress, videoEngagement) {
        val query = searchQuery.trim()
        videos.filter { video ->
            val watched = videoEngagement[video.id].isWatched(playbackProgress[video.id])
            (selectedSeriesFilter == ALL_SERIES_FILTER || video.displaySeriesTitle == selectedSeriesFilter) &&
                (query.isEmpty() || listOf(video.title, video.description, video.displaySeriesTitle, video.topicGroupingTitle).any { it.contains(query, ignoreCase = true) }) &&
                when (watchFilter) {
                    LibraryWatchFilter.All -> true
                    LibraryWatchFilter.Unwatched -> !watched
                    LibraryWatchFilter.InProgress -> !watched && (playbackProgress[video.id]?.positionSeconds ?: 0) > 0
                    LibraryWatchFilter.Watched -> watched
                }
        }
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
    // Retain outgoing lazy rows until their measured shrink finishes. Keeping the
    // same keys also lets a second tap reverse the transition without replacing cards.
    var closingSectionKeys by remember { mutableStateOf(emptySet<String>()) }
    var outgoingVideoIds by remember { mutableStateOf(emptyMap<String, Set<String>>()) }
    var openingSectionKeys by remember { mutableStateOf(emptySet<String>()) }
    val groupTransitionJobs = remember { mutableMapOf<String, Job>() }
    fun toggleSection(key: String) {
        groupTransitionJobs.remove(key)?.cancel()
        val expanding = key in collapsedSectionKeys
        collapsedSectionKeys = collapsedSectionKeys.toggle(key)
        if (expanding) {
            openingSectionKeys = openingSectionKeys + key
            closingSectionKeys = closingSectionKeys - key
        } else {
            // Only rows already measured at the tap may animate out. Retaining the
            // whole group would compose successive episodes as earlier rows shrink.
            outgoingVideoIds = outgoingVideoIds + (key to listState.layoutInfo.visibleItemsInfo
                .mapNotNull { (it.key as? String)?.takeIf { value -> value.startsWith("video-") }?.removePrefix("video-") }
                .toSet())
            closingSectionKeys = closingSectionKeys + key
            openingSectionKeys = openingSectionKeys - key
        }
        groupTransitionJobs[key] = scope.launch {
            Animatable(0f).animateTo(1f, tween(GROUP_TRANSITION_MILLIS + 32))
            closingSectionKeys = closingSectionKeys - key
            openingSectionKeys = openingSectionKeys - key
            outgoingVideoIds = outgoingVideoIds - key
            groupTransitionJobs.remove(key)
        }
    }
    val effectiveCollapsedSectionKeys = collapsedSectionKeys - closingSectionKeys
    val displayedSections = remember(sections, effectiveCollapsedSectionKeys, closingSectionKeys, outgoingVideoIds) {
        buildDisplayedSections(
            sections = sections,
            collapsedSectionKeys = effectiveCollapsedSectionKeys,
        ).map { displayed ->
            if (displayed.section.key in closingSectionKeys) displayed.copy(
                renderedVideos = displayed.section.videos.filter { it.id in outgoingVideoIds[displayed.section.key].orEmpty() },
            ) else displayed
        }
    }
    val expandedSectionCount = remember(displayedSections, collapsedSectionKeys) {
        displayedSections.count { it.section.key !in collapsedSectionKeys }
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
    val railTextClearance = 16.dp
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
    LaunchedEffect(railMenuOpen) {
        if (railMenuOpen && jumpTargets.size > 1) {
            showRailState.scrollToItem((currentJumpTargetIndex - 1).coerceIn(0, jumpTargets.size - 2))
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
    ) {
        val jumpMenuHeight = (maxHeight - 100.dp).coerceAtMost(420.dp).coerceAtLeast(48.dp)
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("library-list")
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
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
                                modifier = Modifier.padding(bottom = 14.dp),
                                searchContent = {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        modifier = Modifier.fillMaxWidth().testTag("library-search"),
                                        label = { Text("Search videos, shows, and topics") },
                                        singleLine = true,
                                        trailingIcon = if (searchQuery.isNotEmpty()) { { TextButton(onClick = { searchQuery = "" }) { Text("Clear") } } } else null,
                                    )
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        LibraryWatchFilter.entries.forEach { filter ->
                                            FilterChip(
                                                modifier = Modifier.testTag("library-watch-${filter.name}"),
                                                selected = watchFilter == filter,
                                                onClick = { watchFilter = filter },
                                                label = { Text(filter.label) },
                                            )
                                        }
                                    }
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text("${filteredVideos.size} of ${videos.size} videos", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                        if (searchQuery.isNotBlank() || watchFilter != LibraryWatchFilter.All || selectedSeriesFilter != ALL_SERIES_FILTER) {
                                            TextButton(modifier = Modifier.testTag("library-reset-filters"), onClick = {
                                                searchQuery = ""; watchFilter = LibraryWatchFilter.All; selectedSeriesFilter = ALL_SERIES_FILTER
                                            }) { Text("Reset filters") }
                                        }
                                    }
                                },
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
                                    collapsedSectionKeys = collapsedSectionKeys - displayedSections.map { it.section.key }.toSet()
                                },
                                onCollapseAllGroups = {
                                    collapsedSectionKeys = collapsedSectionKeys + displayedSections.map { it.section.key }.toSet()
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
                                    text = "No videos match these filters. Use Reset filters above to see your library again.",
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
                            key = { video -> "video-${video.id}" },
                        ) { video ->
                            VideoListCard(
                                video = video,
                                progress = playbackProgress[video.id],
                                engagementRecord = videoEngagement[video.id],
                                dismissDetailsSignal = listState.isScrollInProgress,
                                textEndPadding = railTextClearance,
                                onMarkVideoWatched = onMarkVideoWatched,
                                onMarkVideoUnwatched = onMarkVideoUnwatched,
                                modifier = Modifier.padding(bottom = 14.dp).clickable { onVideoSelected(video.id) },
                            )
                        }
                    } else {
                        displayedSections.forEach { displayedSection ->
                            val groupedHeaderEndPadding = cappedGroupedContentEndPadding(
                                textEndPadding = railTextClearance,
                                maxPadding = GROUPED_HEADER_MAX_END_PADDING,
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
                                    isExpanded = displayedSection.section.key !in collapsedSectionKeys,
                                    onToggleExpanded = { toggleSection(displayedSection.section.key) },
                                    onMarkSectionWatched = {
                                        onMarkVideosWatched(displayedSection.section.videos.map(VideoSummary::id))
                                    },
                                    onMarkSectionUnwatched = {
                                        onMarkVideosUnwatched(displayedSection.section.videos.map(VideoSummary::id))
                                    },
                                    headerTextEndPadding = groupedHeaderEndPadding,
                                )
                            }
                            if (displayedSection.isExpanded) {
                                itemsIndexed(
                                    items = displayedSection.renderedVideos,
                                        key = { _, video -> "video-${video.id}" },
                                    contentType = { _, _ -> "grouped-episode" },
                                ) { index, video ->
                                    val isLast = index == displayedSection.renderedVideos.lastIndex
                                    val visibility = remember(video.id) {
                                        MutableTransitionState(displayedSection.section.key !in openingSectionKeys)
                                    }
                                    visibility.targetState = displayedSection.section.key !in collapsedSectionKeys
                                    androidx.compose.animation.AnimatedVisibility(
                                        visibleState = visibility,
                                        enter = expandVertically(tween(GROUP_TRANSITION_MILLIS), expandFrom = Alignment.Top) +
                                            slideInVertically(tween(GROUP_TRANSITION_MILLIS)) { -it },
                                        exit = shrinkVertically(tween(GROUP_TRANSITION_MILLIS), shrinkTowards = Alignment.Top) +
                                            slideOutVertically(tween(GROUP_TRANSITION_MILLIS)) { -it },
                                    ) {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(bottomStart = if (isLast) 24.dp else 0.dp, bottomEnd = if (isLast) 24.dp else 0.dp),
                                            color = LookTubeStyle.panelColor,
                                            contentColor = MaterialTheme.colorScheme.onSurface,
                                        ) {
                                            val gutterColor = MaterialTheme.colorScheme.outlineVariant
                                            Column(Modifier.drawBehind {
                                                drawLine(gutterColor, Offset(10.dp.toPx(), 0f), Offset(10.dp.toPx(), size.height), 2.dp.toPx())
                                            }.padding(start = 22.dp, end = 10.dp, top = 12.dp, bottom = if (isLast) 10.dp else 0.dp)) {
                                                GroupedSectionVideoCard(
                                                    positionLabel = "Video ${displayedSection.section.videos.indexOf(video) + 1} of ${displayedSection.section.videos.size}",
                                                    video = video,
                                                    progress = playbackProgress[video.id],
                                                    engagementRecord = videoEngagement[video.id],
                                                    dismissDetailsSignal = listState.isScrollInProgress,
                                                    textEndPadding = railTextClearance,
                                                    onMarkVideoWatched = { id -> if (displayedSection.section.key !in collapsedSectionKeys) onMarkVideoWatched(id) },
                                                    onMarkVideoUnwatched = { id -> if (displayedSection.section.key !in collapsedSectionKeys) onMarkVideoUnwatched(id) },
                                                    onVideoSelected = { id -> if (displayedSection.section.key !in collapsedSectionKeys) onVideoSelected(id) },
                                                )
                                            }
                                        }
                                        if (isLast) Spacer(Modifier.height(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                if (videos.isNotEmpty()) {
                    val scrollFraction by remember(listState) { derivedStateOf { listState.approximateScrollFraction() } }
                    JumpRailTrack(
                        scrollFraction = scrollFraction,
                        isEmphasized = listState.isScrollInProgress,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp, top = 12.dp, bottom = 12.dp),
                        onScrollFractionChanged = { fraction -> scope.launch { listState.scrollToItem(listState.targetItemIndexForFraction(fraction)) } },
                    )
                }
            }
            if (videos.isNotEmpty()) {
                BrowseJumpBar(
                    targets = jumpTargets,
                    listState = showRailState,
                    currentTargetIndex = currentJumpTargetIndex,
                    canGoBack = listState.canScrollBackward,
                    expanded = railMenuOpen,
                    groupingLabel = if (groupingMode == HeuristicGroupingMode.Topic) "topic" else "show",
                    menuHeight = jumpMenuHeight,
                    onExpandedChanged = { railMenuOpen = it },
                    onTargetSelected = { target ->
                        railMenuOpen = false
                        scope.launch { listState.scrollToItem(target.itemIndex) }
                    },
                )
            }
        }
    }
}

@Composable
private fun CollapsedHeaderPreviewStack(
    section: SeriesSection,
    textEndPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val cardCount = section.videos.size.coerceIn(0, GROUP_HEADER_MAX_PEEK_COUNT + 1)
    val cardVideos = section.videos.take(cardCount)
    if (cardVideos.isEmpty()) {
        return
    }
    val peekOffsets = (0 until cardVideos.size).map { index -> GROUP_HEADER_PEEK_REVEAL_STEP * index }
    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds(),
    ) {
        cardVideos.indices.reversed().forEach { index ->
            val video = cardVideos[index]
            CollapsedHeaderPreviewCard(
                video = video,
                textEndPadding = textEndPadding,
                showText = index == 0,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = peekOffsets[index])
                    .fillMaxWidth(collapsedHeaderPeekWidthFraction(index))
                    .height(GROUP_HEADER_COMPACT_PREVIEW_CARD_HEIGHT),
            )
        }
    }
}

@Composable
private fun CollapsedHeaderPreviewCard(
    video: VideoSummary,
    textEndPadding: Dp,
    showText: Boolean,
    modifier: Modifier = Modifier,
) {
    val thumbnailUrl = video.thumbnailUrl
    val metadataLine = remember(video) { buildCollapsedHeaderPreviewMetadataLine(video) }
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.82f)),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Thumbnail background — dimmed to match collapsed header treatment
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (!thumbnailUrl.isNullOrBlank()) {
                    ThumbnailImage(
                        thumbnailUrl = thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                // Dimming overlay — matches the collapsed header mosaic dimming
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.36f)),
                )
            }
            // Bottom metadata overlay — same visual language as full video cards
            if (showText) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(),
                    color = Color.Black.copy(alpha = 0.56f),
                ) {
                    Column(
                        modifier = Modifier.padding(
                            start = 14.dp,
                            top = 8.dp,
                            end = 14.dp + cappedGroupedContentEndPadding(
                                textEndPadding = textEndPadding,
                                maxPadding = GROUPED_COLLAPSED_PREVIEW_MAX_END_PADDING,
                            ),
                            bottom = 8.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        metadataLine.takeIf(String::isNotBlank)?.let { metadata ->
                            Text(
                                text = metadata,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.82f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
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
        shape = LookTubeStyle.panelShape,
        color = LookTubeStyle.panelColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
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

/** A small, non-playable version of the same pack that becomes the collapsed preview stack. */
@Composable
private fun GroupArtworkStack(section: SeriesSection) {
    val cards = section.videos.take(3)
    Box(Modifier.size(width = 80.dp, height = 60.dp).testTag("library-group-stack-${section.title}")) {
        cards.indices.reversed().forEach { index ->
            Surface(
                modifier = Modifier.offset(x = (index * 4).dp, y = (index * 6).dp).size(width = 68.dp, height = 44.dp),
                shape = RoundedCornerShape(7.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                cards[index].thumbnailUrl?.takeIf(String::isNotBlank)?.let { url ->
                    ThumbnailImage(url, contentDescription = null, modifier = Modifier.fillMaxSize())
                } ?: Box(contentAlignment = Alignment.Center) {
                    Text(section.title.take(1), style = MaterialTheme.typography.titleMedium)
                }
            }
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
                    .clip(LookTubeStyle.insetShape)
                    .testTag("browse-dropdown-$label")
                    .semantics { contentDescription = "$label: $value" }
                    .clickable { onExpandedChanged(true) },
                shape = LookTubeStyle.insetShape,
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
    add("Show" to video.displaySeriesTitle)
    add("Watch status" to libraryWatchStatusLabel(engagementRecord.isWatched(progress), progress))
    video.publishedAtEpochMillis?.let { add("Published" to formatPublishedDateTime(it)) }
    video.durationSeconds?.let { add("Duration" to formatDuration(it)) }
    progress?.takeIf { it.durationSeconds > 0 }?.let {
        add("Playback progress" to "${formatDuration(it.positionSeconds)} of ${formatDuration(it.durationSeconds)}")
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
        shape = LookTubeStyle.panelShape,
        color = LookTubeStyle.panelColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
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
                shape = LookTubeStyle.insetShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                tonalElevation = 0.dp,
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
    val renderedVideos: List<VideoSummary> = if (isExpanded) section.videos else emptyList(),
)
internal fun collapsedHeaderPeekCount(videoCount: Int): Int = (videoCount - 1).coerceIn(0, GROUP_HEADER_MAX_PEEK_COUNT)
internal fun collapsedHeaderPeekOffsets(videoCount: Int): List<Dp> =
    (1..collapsedHeaderPeekCount(videoCount)).map { index -> GROUP_HEADER_PEEK_REVEAL_STEP * index }

internal fun collapsedHeaderPeekReveal(videoCount: Int): Dp {
    return collapsedHeaderPeekOffsets(videoCount).lastOrNull() ?: 0.dp
}

private const val GROUP_HEADER_MAX_PEEK_COUNT = 3
private val GROUP_HEADER_PEEK_REVEAL_STEP = 22.dp
private val GROUP_HEADER_PEEK_HORIZONTAL_STAGGER = 18.dp
private const val GROUP_HEADER_PEEK_CARD_WIDTH_FRACTION = 0.96f
private val GROUP_HEADER_COMPACT_PREVIEW_CARD_HEIGHT = 100.dp
private const val GROUP_TRANSITION_MILLIS = 300
private fun collapsedHeaderPreviewStackHeight(videoCount: Int): Dp {
    val cardCount = videoCount.coerceIn(0, GROUP_HEADER_MAX_PEEK_COUNT + 1)
    return if (cardCount == 0) 0.dp
    else GROUP_HEADER_COMPACT_PREVIEW_CARD_HEIGHT + (GROUP_HEADER_PEEK_REVEAL_STEP * (cardCount - 1))
}

private fun collapsedHeaderPeekHorizontalOffset(index: Int): Dp = 0.dp

private fun collapsedHeaderPeekWidthFraction(index: Int): Float =
    (GROUP_HEADER_PEEK_CARD_WIDTH_FRACTION - (index * 0.015f)).coerceAtLeast(0.92f)

private fun buildCollapsedHeaderPreviewMetadataLine(video: VideoSummary): String = buildList {
    video.publishedAtEpochMillis?.let(::formatPublishedDate)?.let(::add)
    video.durationSeconds?.let(::formatDuration)?.let(::add)
}.joinToString(" • ")


@Composable
private fun LibraryOverviewPanel(
    modifier: Modifier = Modifier,
    searchContent: @Composable () -> Unit = {},
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
        shape = LookTubeStyle.panelShape,
        color = LookTubeStyle.panelColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LookTubePageHeader(
                title = "Library",
                subtitle = "Browse your synced Premium videos by show or topic and jump between sections quickly.",
            )
            searchContent()
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
        shape = LookTubeStyle.panelShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 0.dp,
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
                        text = "Browse options",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Grouping, sorting, and show filters",
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
    Topic("Topic", "Topic"),
}

private enum class LibraryWatchFilter(val label: String) {
    All("All"), Unwatched("Unwatched"), InProgress("In progress"), Watched("Watched")
}

internal enum class LibrarySortOption(val label: String) {
    Latest("Latest"),
    Show("Show"),
    Oldest("Oldest"),
}

private const val ALL_SERIES_FILTER = "All shows"
private const val RESULTS_ANCHOR_INDEX = 1
private const val VIDEO_LIST_START_INDEX = 2
private val LIST_TOP_CONTENT_PADDING = 16.dp
private val GROUPED_HEADER_MAX_END_PADDING = 148.dp
private val GROUPED_COLLAPSED_PREVIEW_MAX_END_PADDING = 72.dp

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
    modifier: Modifier = Modifier,
    section: SeriesSection,
    watchedVideoCount: Int,
    completionSummary: SeriesCompletionSummary?,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onMarkSectionWatched: () -> Unit,
    onMarkSectionUnwatched: () -> Unit,
    headerTextEndPadding: Dp,
) {
    val sectionIsFullyWatched = watchedVideoCount == section.videos.size && section.videos.isNotEmpty()
    Column(modifier = modifier.fillMaxWidth()) {
        // One identity, title, artwork and touch target in both states. Only the
        // material below this header moves; it never swaps to another composition.
        UnifiedSeriesSectionHeader(section, watchedVideoCount, completionSummary, isExpanded, onToggleExpanded)
        AnimatedVisibility(
            visible = !isExpanded,
            enter = expandVertically(tween(GROUP_TRANSITION_MILLIS), expandFrom = Alignment.Top),
            exit = shrinkVertically(tween(GROUP_TRANSITION_MILLIS), shrinkTowards = Alignment.Top),
        ) {
            Column {
                CollapsedHeaderPreviewStack(
                    section = section,
                    textEndPadding = headerTextEndPadding,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                        .height(collapsedHeaderPreviewStackHeight(section.videos.size)),
                )
                Spacer(Modifier.height(14.dp))
            }
        }
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(tween(GROUP_TRANSITION_MILLIS), expandFrom = Alignment.Top),
            exit = shrinkVertically(tween(GROUP_TRANSITION_MILLIS), shrinkTowards = Alignment.Top),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 10.dp, top = 4.dp)) {
                FilterChip(
                    selected = sectionIsFullyWatched,
                    enabled = isExpanded,
                    onClick = { if (sectionIsFullyWatched) onMarkSectionUnwatched() else onMarkSectionWatched() },
                    modifier = Modifier.testTag("library-group-watch-${section.title}"),
                    label = { Text(if (sectionIsFullyWatched) "Mark all unwatched" else "Mark all watched") },
                )
            }
        }
    }
}

@Composable
private fun UnifiedSeriesSectionHeader(
    section: SeriesSection,
    watchedVideoCount: Int,
    completionSummary: SeriesCompletionSummary?,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(LookTubeStyle.panelShape)
            .testTag("library-section-${section.title}")
            .semantics { stateDescription = if (isExpanded) "Expanded" else "Collapsed" }
            .clickable(role = Role.Button,
                onClickLabel = "${if (isExpanded) "Collapse" else "Expand"} ${section.title}",
                onClick = onToggleExpanded),
        shape = LookTubeStyle.panelShape,
        color = LookTubeStyle.panelColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = LookTubeStyle.panelBorder,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${section.kindLabel.uppercase()} GROUP", Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(
                    onClick = onToggleExpanded,
                    modifier = Modifier.widthIn(min = 148.dp).heightIn(min = 48.dp)
                        .testTag("library-collapse-${section.title}"),
                ) { Text(if (isExpanded) "Collapse group" else "Expand group") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(section.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(buildList {
                        add("${section.videos.size} ${if (section.videos.size == 1) "video" else "videos"}")
                        add("$watchedVideoCount/${section.videos.size} watched")
                        if (completionSummary?.isCompleted == true) add("Complete")
                    }.joinToString(" • "), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                GroupArtworkStack(section)
            }
        }
    }
}

@Composable
private fun GroupedSectionVideoCard(
    positionLabel: String,
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
        positionLabel = positionLabel,
        modifier = Modifier.testTag("library-video-${video.id}")
            .clickable(role = Role.Button, onClickLabel = "Play ${video.title}") { onVideoSelected(video.id) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
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
    positionLabel: String? = null,
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
    var showTechnicalDetails by remember(video.id, showFullDetails) { mutableStateOf(false) }
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
        shape = LookTubeStyle.panelShape,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = LookTubeStyle.panelColor, contentColor = MaterialTheme.colorScheme.onSurface),
        border = LookTubeStyle.panelBorder,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            ) {
                VideoThumbnail(video)
                positionLabel?.let { label ->
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.84f),
                        contentColor = Color.White,
                    ) {
                        Text("▶  $label", Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
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
                        maxLines = if (positionLabel != null) 2 else 4,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { descriptionTruncated = it.hasVisualOverflow },
                    )
                }
                TextButton(
                    modifier = Modifier.testTag("library-details-${video.id}"),
                    onClick = { showFullDetails = true },
                ) { Text(if (descriptionTruncated) "Read more" else "Details") }
                if (displayedProgress != null && displayedProgress.durationSeconds > 0) {
                    Text(
                        text = "Resume • ${formatDuration(displayedProgress.positionSeconds)} / ${formatDuration(displayedProgress.durationSeconds)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
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
        Dialog(onDismissRequest = { showFullDetails = false }) {
            Surface(shape = LookTubeStyle.panelShape, color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
                Column(Modifier.heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.85f).padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(video.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { showFullDetails = false }) { Text("Close") }
                    }
                    Column(
                        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).testTag("video-details-scroll"),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (video.description.isNotBlank()) {
                            Text(video.description, style = MaterialTheme.typography.bodyMedium)
                            HorizontalDivider()
                        }
                        detailRows.forEach { (label, value) ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(value, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        TextButton(onClick = { showTechnicalDetails = !showTechnicalDetails }) {
                            Text(if (showTechnicalDetails) "Hide technical details" else "Technical details")
                        }
                        if (showTechnicalDetails) {
                            listOf("Video ID" to video.id, "Feed category" to video.feedCategory,
                                "Playable URL" to (video.playbackUrl ?: "Unavailable"),
                                "Thumbnail URL" to (video.thumbnailUrl ?: "Unavailable")).forEach { (label, value) ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(value, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
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
    alignment: Alignment = Alignment.Center,
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
            alignment = alignment,
        )
    } else {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = contentDescription,
            modifier = imageModifier,
            contentScale = contentScale,
            alignment = alignment,
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
private fun BrowseJumpBar(
    targets: List<JumpRailTarget>,
    listState: LazyListState,
    currentTargetIndex: Int,
    canGoBack: Boolean,
    expanded: Boolean,
    groupingLabel: String,
    menuHeight: Dp,
    onExpandedChanged: (Boolean) -> Unit,
    onTargetSelected: (JumpRailTarget) -> Unit,
) {
    var jumpControlHeightPx by remember { mutableStateOf(0) }
    Surface(tonalElevation = 3.dp, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth().testTag("library-jump-bar")) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                modifier = Modifier.testTag("library-top").heightIn(min = 48.dp),
                enabled = canGoBack,
                onClick = { onTargetSelected(targets.first()) },
            ) { Text("↑ Top") }
            if (targets.size > 1) {
                Box(Modifier.weight(1f).onSizeChanged { jumpControlHeightPx = it.height }) {
                    OutlinedButton(
                        onClick = { onExpandedChanged(!expanded) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("library-index-toggle")
                            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
                    ) {
                        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.weight(1f)) {
                            Text("Jump to $groupingLabel", style = MaterialTheme.typography.labelLarge)
                            Text(targets.getOrElse(currentTargetIndex) { targets.first() }.title,
                                style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(if (expanded) "▴" else "▾", Modifier.padding(start = 12.dp))
                    }
                    if (expanded) {
                        val popupOffset = -jumpControlHeightPx - with(LocalDensity.current) { 8.dp.roundToPx() }
                        Popup(
                            alignment = Alignment.BottomEnd,
                            offset = IntOffset(0, popupOffset),
                            onDismissRequest = { onExpandedChanged(false) },
                            properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
                        ) {
                            Surface(
                                modifier = Modifier.width(264.dp).heightIn(max = menuHeight),
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                tonalElevation = 6.dp,
                                shadowElevation = 6.dp,
                            ) {
                                Column {
                                    TextButton(
                                        onClick = { onTargetSelected(targets.first()) },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("library-popup-top"),
                                    ) { Text("↑ Back to top") }
                                    HorizontalDivider()
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.weight(1f, fill = false).testTag("library-section-index"),
                                    contentPadding = PaddingValues(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    itemsIndexed(targets.drop(1), key = { _, target -> target.itemIndex }) { index, target ->
                                        val selected = index + 1 == currentTargetIndex
                                        Surface(
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                                .semantics { this.selected = selected }
                                                .clickable(role = Role.Button, onClickLabel = "Jump to ${target.title}") { onTargetSelected(target) },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        ) {
                                            Text(target.title, Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                                }
                            }
                        }
                    }
                }
            } else {
                Text("Videos", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun JumpRailTrack(
    scrollFraction: Float,
    isEmphasized: Boolean,
    modifier: Modifier = Modifier,
    onScrollFractionChanged: (Float) -> Unit,
) {
    // Touch passes through to the list. Fast navigation requires an explicit jump-menu
    // selection; a normal edge swipe must never become an absolute-position seek.
    BoxWithConstraints(modifier.width(8.dp).fillMaxHeight()) {
        val thumbHeight = 40.dp
        val thumbTravel = (maxHeight - thumbHeight).coerceAtLeast(0.dp)
        val currentOnFractionChanged by rememberUpdatedState(onScrollFractionChanged)
        Box(Modifier.fillMaxSize().testTag("library-scroll-track").semantics {
            contentDescription = "Library scroll position"
            progressBarRangeInfo = ProgressBarRangeInfo(scrollFraction, 0f..1f)
            setProgress { currentOnFractionChanged(it.coerceIn(0f, 1f)); true }
        }) {
            Box(Modifier.align(Alignment.Center).width(2.dp).fillMaxHeight().clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.outlineVariant))
            Box(Modifier.align(Alignment.TopCenter).offset(y = thumbTravel * scrollFraction.coerceIn(0f, 1f))
                .size(width = if (isEmphasized) 6.dp else 4.dp, height = thumbHeight)
                .clip(RoundedCornerShape(999.dp)).background(MaterialTheme.colorScheme.primary))
        }
    }
}

private fun List<VideoSummary>.resolveGroupTitle(mode: HeuristicGroupingMode): String =
    when (mode) {
        HeuristicGroupingMode.None -> map(VideoSummary::title)
        HeuristicGroupingMode.Show -> map(VideoSummary::displaySeriesTitle)
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
        currentIndex += 1 + section.renderedVideos.size
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

