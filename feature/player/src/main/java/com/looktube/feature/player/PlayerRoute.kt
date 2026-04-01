package com.looktube.feature.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.children
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Player
import androidx.media3.cast.MediaRouteButtonViewProvider
import androidx.media3.ui.PlayerView
import com.google.android.gms.cast.framework.CastContext
import com.looktube.designsystem.LookTubeCard
import com.looktube.designsystem.LookTubePageHeader
import com.looktube.heuristics.displaySeriesTitle
import com.looktube.model.CaptionGenerationPhase
import com.looktube.model.CaptionGenerationMetric
import com.looktube.model.CaptionGenerationStatus
import com.looktube.model.LocalCaptionEngine
import com.looktube.model.LocalCaptionModelState
import com.looktube.model.RecentPlaybackVideo
import com.looktube.model.VideoCaptionTrack
import com.looktube.model.VideoCaptionData
import com.looktube.model.VideoEngagementRecord
import com.looktube.model.PlaybackProgress
import com.looktube.model.VideoSummary
import com.looktube.model.isWatched
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
fun PlayerRoute(
    paddingValues: PaddingValues,
    selectedVideo: VideoSummary?,
    playbackProgress: PlaybackProgress?,
    playbackSelectionRequest: Long,
    selectedVideoEngagement: VideoEngagementRecord?,
    recentPlaybackVideos: List<RecentPlaybackVideo>,
    availableLocalCaptionEngines: List<LocalCaptionEngine>,
    selectedLocalCaptionEngine: LocalCaptionEngine,
    localCaptionModelState: LocalCaptionModelState,
    selectedCaptionData: VideoCaptionData?,
    selectedCaptionTrack: VideoCaptionTrack?,
    selectedCaptionGenerationStatus: CaptionGenerationStatus,
    player: Player?,
    isFullscreen: Boolean,
    onRecentVideoSelected: (String) -> Unit,
    onMarkVideoWatched: (String) -> Unit,
    onMarkVideoUnwatched: (String) -> Unit,
    onLocalCaptionEngineSelected: (String) -> Unit,
    onGenerateCaptionsRequested: (String) -> Unit,
    onDeleteCaptionDataRequested: (String) -> Unit,
    onFullscreenChanged: (Boolean) -> Unit,
) {
    val activity = rememberActivity(LocalContext.current)

    DisposableEffect(activity, isFullscreen) {
        val hostActivity = activity
        if (!isFullscreen || hostActivity == null) {
            onDispose { }
        } else {
            val window = hostActivity.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            onDispose {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(window, true)
            }
        }
    }

    when {
        selectedVideo == null -> PlayerStatusContent(
            paddingValues = paddingValues,
            headerSubtitle = "Choose a video from Library to start playback here.",
            statusTitle = "Nothing queued yet",
            statusBody = "Pick a video from Library to start playback here.",
            frameTitle = "Player ready",
            frameBody = "When you pick a video, playback controls, fullscreen, and cast options will appear here.",
        )

        selectedVideo.playbackUrl.isNullOrBlank() -> PlayerStatusContent(
            paddingValues = paddingValues,
            selectedVideo = selectedVideo,
            playbackProgress = playbackProgress,
            headerSubtitle = "The selected video is loaded, but it does not currently expose a playable stream.",
            statusTitle = "Playback unavailable",
            statusBody = "This item does not expose a playable stream right now. Try another video or refresh your library from Settings.",
            frameTitle = "No playable stream",
            frameBody = "LookTube found this video in the feed, but the current item does not include a playable URL.",
            frameAtTop = true,
        )

        player == null -> PlayerStatusContent(
            paddingValues = paddingValues,
            selectedVideo = selectedVideo,
            playbackProgress = playbackProgress,
            headerSubtitle = "Opening the shared playback session for this video.",
            statusTitle = "Preparing player",
            statusBody = "LookTube is opening playback controls for this video now.",
            frameTitle = "Player is getting ready",
            frameBody = playbackProgress?.takeIf { it.durationSeconds > 0 }?.let {
                "Resume will pick up near ${formatPlaybackTime(it.positionSeconds)}."
            } ?: "Playback controls will appear here in a moment.",
            frameAtTop = true,
        )

        isFullscreen -> FullscreenPlayerSurface(
            player = player,
            remotePlaybackStatus = rememberRemotePlaybackStatus(player),
            onFullscreenToggle = onFullscreenChanged,
        )

        else -> ActivePlayerContent(
            paddingValues = paddingValues,
            selectedVideo = selectedVideo,
            playbackProgress = playbackProgress,
            playbackSelectionRequest = playbackSelectionRequest,
            selectedVideoEngagement = selectedVideoEngagement,
            recentPlaybackVideos = recentPlaybackVideos,
            availableLocalCaptionEngines = availableLocalCaptionEngines,
            selectedLocalCaptionEngine = selectedLocalCaptionEngine,
            localCaptionModelState = localCaptionModelState,
            selectedCaptionData = selectedCaptionData,
            selectedCaptionTrack = selectedCaptionTrack,
            selectedCaptionGenerationStatus = selectedCaptionGenerationStatus,
            player = player,
            onRecentVideoSelected = onRecentVideoSelected,
            onMarkVideoWatched = onMarkVideoWatched,
            onMarkVideoUnwatched = onMarkVideoUnwatched,
            onLocalCaptionEngineSelected = onLocalCaptionEngineSelected,
            onGenerateCaptionsRequested = onGenerateCaptionsRequested,
            onDeleteCaptionDataRequested = onDeleteCaptionDataRequested,
            onFullscreenChanged = onFullscreenChanged,
        )
    }
}

@Composable
private fun CaptionStatusCard(
    selectedVideo: VideoSummary,
    availableLocalCaptionEngines: List<LocalCaptionEngine>,
    selectedLocalCaptionEngine: LocalCaptionEngine,
    localCaptionModelState: LocalCaptionModelState,
    selectedCaptionData: VideoCaptionData?,
    selectedCaptionTrack: VideoCaptionTrack?,
    selectedCaptionGenerationStatus: CaptionGenerationStatus,
    onLocalCaptionEngineSelected: (String) -> Unit,
    onGenerateCaptionsRequested: (String) -> Unit,
    onDeleteCaptionDataRequested: (String) -> Unit,
) {
    val isGenerating = selectedCaptionGenerationStatus.phase in setOf(
        CaptionGenerationPhase.ExtractingAudio,
        CaptionGenerationPhase.Transcribing,
        CaptionGenerationPhase.Saving,
    )
    val hasSavedCaptionTrack = selectedCaptionTrack != null || selectedCaptionData?.hasSavedCaptionTrack == true
    val hasCaptionData = hasSavedCaptionTrack || selectedCaptionData != null
    val progressFraction = captionStatusProgressFraction(
        selectedCaptionGenerationStatus = selectedCaptionGenerationStatus,
        hasSavedCaptionTrack = hasSavedCaptionTrack,
        hasCaptionData = hasCaptionData,
    )
    val detailMetrics = remember(
        selectedLocalCaptionEngine.id,
        localCaptionModelState.isReady,
        selectedCaptionData,
        selectedCaptionTrack,
        selectedCaptionGenerationStatus,
    ) {
        captionStatusDetailMetrics(
            selectedLocalCaptionEngine = selectedLocalCaptionEngine,
            localCaptionModelState = localCaptionModelState,
            selectedCaptionData = selectedCaptionData,
            selectedCaptionTrack = selectedCaptionTrack,
            selectedCaptionGenerationStatus = selectedCaptionGenerationStatus,
        )
    }
    var statsExpanded by rememberSaveable(selectedVideo.id) { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Offline captions",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Selected engine: ${selectedLocalCaptionEngine.displayName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            progressFraction?.let { determinateProgress ->
                LinearProgressIndicator(
                    progress = { determinateProgress },
                    modifier = Modifier.fillMaxWidth(),
                )
            } ?: LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { statsExpanded = !statsExpanded },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                tonalElevation = 0.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .animateContentSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Detailed stats",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (statsExpanded) "Hide" else "Show",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (statsExpanded) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            detailMetrics.forEach { metric ->
                                CaptionMetricRow(metric = metric)
                            }
                            if (availableLocalCaptionEngines.size > 1) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f),
                                )
                                availableLocalCaptionEngines.forEach { engine ->
                                    FilterChip(
                                        selected = engine.id == selectedLocalCaptionEngine.id,
                                        onClick = { onLocalCaptionEngineSelected(engine.id) },
                                        label = { Text(engine.displayName) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = hasSavedCaptionTrack,
                    enabled = localCaptionModelState.isReady && !isGenerating,
                    onClick = { onGenerateCaptionsRequested(selectedVideo.id) },
                    label = {
                        Text(
                            when {
                                isGenerating -> "Generating captions…"
                                hasSavedCaptionTrack -> "Regenerate captions"
                                localCaptionModelState.isReady -> "Generate with ${selectedLocalCaptionEngine.displayName}"
                                else -> "Model required in Settings"
                            },
                        )
                    },
                )
                if (hasCaptionData) {
                    FilterChip(
                        selected = false,
                        enabled = !isGenerating,
                        onClick = { onDeleteCaptionDataRequested(selectedVideo.id) },
                        label = {
                            Text(
                                if (hasSavedCaptionTrack) {
                                    "Delete caption data"
                                } else {
                                    "Delete partial data"
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CaptionMetricRow(
    metric: CaptionGenerationMetric,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = metric.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = metric.value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun captionStatusProgressFraction(
    selectedCaptionGenerationStatus: CaptionGenerationStatus,
    hasSavedCaptionTrack: Boolean,
    hasCaptionData: Boolean,
): Float? = when {
    selectedCaptionGenerationStatus.phase == CaptionGenerationPhase.Completed || hasSavedCaptionTrack -> 1f
    selectedCaptionGenerationStatus.progressFraction != null -> selectedCaptionGenerationStatus.progressFraction
    selectedCaptionGenerationStatus.phase == CaptionGenerationPhase.Saving -> 0.96f
    selectedCaptionGenerationStatus.phase == CaptionGenerationPhase.Error -> 0f
    hasCaptionData -> 0.45f
    else -> 0f
}

private fun captionStatusDetailMetrics(
    selectedLocalCaptionEngine: LocalCaptionEngine,
    localCaptionModelState: LocalCaptionModelState,
    selectedCaptionData: VideoCaptionData?,
    selectedCaptionTrack: VideoCaptionTrack?,
    selectedCaptionGenerationStatus: CaptionGenerationStatus,
): List<CaptionGenerationMetric> {
    val lastMessage = selectedCaptionGenerationStatus.message
        .takeIf(String::isNotBlank)
        ?: selectedCaptionData?.lastMessage
    val generatedTrack = selectedCaptionTrack
    return buildList {
        add(
            CaptionGenerationMetric(
                "State",
                when {
                    selectedCaptionGenerationStatus.phase == CaptionGenerationPhase.Completed ||
                        generatedTrack != null ||
                        selectedCaptionData?.hasSavedCaptionTrack == true -> "Completed"
                    selectedCaptionGenerationStatus.phase == CaptionGenerationPhase.Error -> "Error"
                    selectedCaptionGenerationStatus.phase == CaptionGenerationPhase.Idle && !localCaptionModelState.isReady -> "Model required"
                    selectedCaptionGenerationStatus.phase == CaptionGenerationPhase.Idle && selectedCaptionData != null -> "Partial"
                    selectedCaptionGenerationStatus.phase == CaptionGenerationPhase.Idle -> "Ready"
                    else -> selectedCaptionGenerationStatus.phase.name
                },
            ),
        )
        add(
            CaptionGenerationMetric(
                "Engine",
                captionEngineDisplayName(
                    engineId = generatedTrack?.engineId ?: selectedCaptionData?.engineId,
                    selectedLocalCaptionEngine = selectedLocalCaptionEngine,
                ),
            ),
        )
        selectedCaptionGenerationStatus.detailMetrics.forEach(::add)
        generatedTrack?.let { track ->
            add(CaptionGenerationMetric("Track", track.label))
            add(CaptionGenerationMetric("Language", track.languageTag))
        }
        if (selectedCaptionData != null) {
            add(CaptionGenerationMetric("Data", selectedCaptionData.stateLabel))
        }
        add(
            CaptionGenerationMetric(
                "Model",
                if (localCaptionModelState.isReady) "Ready" else "Not ready",
            ),
        )
        lastMessage?.takeIf(String::isNotBlank)?.let { message ->
            add(CaptionGenerationMetric("Last event", message))
        }
    }.distinctBy { metric -> metric.label to metric.value }
}

private fun captionEngineDisplayName(
    engineId: String?,
    selectedLocalCaptionEngine: LocalCaptionEngine,
): String = when (engineId) {
    null -> selectedLocalCaptionEngine.displayName
    selectedLocalCaptionEngine.id -> selectedLocalCaptionEngine.displayName
    "whisper_cpp" -> "Whisper.cpp"
    "moonshine" -> "Moonshine"
    else -> engineId
}

@Composable
private fun PlayerFramePlaceholder(
    title: String,
    body: String,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PlayerSupportingCopy(
    playbackProgress: PlaybackProgress?,
    remotePlaybackStatus: RemotePlaybackStatus?,
    isWatched: Boolean,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = remotePlaybackStatus?.title?.let { "$it • ${remotePlaybackStatus.detailsBody}" }
                ?: compactResumeSummary(playbackProgress),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "${currentWatchStatusLabel(isWatched, playbackProgress)} • Double-tap left or right to skip 10 seconds.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlayerQuickActions(
    selectedVideo: VideoSummary,
    isWatched: Boolean,
    recentPlaybackVideos: List<RecentPlaybackVideo>,
    recentPlaybackMenuExpanded: Boolean,
    onRecentPlaybackMenuExpandedChanged: (Boolean) -> Unit,
    onRecentVideoSelected: (String) -> Unit,
    onMarkVideoWatched: (String) -> Unit,
    onMarkVideoUnwatched: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                FilterChip(
                    selected = isWatched,
                    onClick = {
                        if (isWatched) {
                            onMarkVideoUnwatched(selectedVideo.id)
                        } else {
                            onMarkVideoWatched(selectedVideo.id)
                        }
                    },
                    label = { Text(if (isWatched) "Mark as Unwatched" else "Mark as Watched") },
                )
                if (recentPlaybackVideos.isNotEmpty()) {
                    HistoryMenuButton(
                        recentPlaybackVideos = recentPlaybackVideos,
                        expanded = recentPlaybackMenuExpanded,
                        modifier = Modifier.weight(1f),
                        onExpandedChanged = onRecentPlaybackMenuExpandedChanged,
                        onRecentVideoSelected = onRecentVideoSelected,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryMenuButton(
    recentPlaybackVideos: List<RecentPlaybackVideo>,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onExpandedChanged: (Boolean) -> Unit,
    onRecentVideoSelected: (String) -> Unit,
) {
    Box(modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = if (expanded) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
            },
            contentColor = if (expanded) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            tonalElevation = 0.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.82f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "History",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = "${recentPlaybackVideos.size} recent ${if (recentPlaybackVideos.size == 1) "play" else "plays"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = if (expanded) "▲" else "▼",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        Box(
            modifier = Modifier.matchParentSize(),
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { onExpandedChanged(!expanded) },
            )
        }
        HistoryDropdownMenu(
            recentPlaybackVideos = recentPlaybackVideos,
            expanded = expanded,
            onExpandedChanged = onExpandedChanged,
            onRecentVideoSelected = onRecentVideoSelected,
        )
    }
}

@Composable
private fun HistoryDropdownMenu(
    recentPlaybackVideos: List<RecentPlaybackVideo>,
    expanded: Boolean,
    onExpandedChanged: (Boolean) -> Unit,
    onRecentVideoSelected: (String) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val availableChromeAwareHeight = (configuration.screenHeightDp.dp - PLAYER_HISTORY_RESERVED_CHROME_HEIGHT)
        .coerceAtLeast(180.dp)
    val maxMenuHeight = availableChromeAwareHeight * (2f / 3f)
    var viewportHeightPx by remember { mutableStateOf(0) }
    val scrollState = rememberScrollState()

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { onExpandedChanged(false) },
        modifier = Modifier.widthIn(min = 300.dp, max = 360.dp),
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 300.dp, max = 360.dp)
                .onSizeChanged { viewportHeightPx = it.height },
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = maxMenuHeight)
                    .verticalScroll(scrollState)
                    .padding(top = 6.dp, end = 14.dp, bottom = 6.dp)
            ) {
                Text(
                    text = "History",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                recentPlaybackVideos.forEachIndexed { index, recentPlaybackVideo ->
                    DropdownMenuItem(
                        text = {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(recentPlaybackVideo.video.title)
                                Text(
                                    text = recentPlaybackMenuSubtitle(recentPlaybackVideo),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            onExpandedChanged(false)
                            onRecentVideoSelected(recentPlaybackVideo.video.id)
                        },
                    )
                    if (index != recentPlaybackVideos.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp, end = 28.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }
            HistoryMenuScrollIndicator(
                scrollValue = scrollState.value,
                maxScrollValue = scrollState.maxValue,
                viewportHeightPx = viewportHeightPx,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 6.dp),
            )
        }
    }
}

@Composable
private fun HistoryMenuScrollIndicator(
    scrollValue: Int,
    maxScrollValue: Int,
    viewportHeightPx: Int,
    modifier: Modifier = Modifier,
) {
    if (maxScrollValue <= 0 || viewportHeightPx <= 0) {
        return
    }
    val density = LocalDensity.current
    val contentHeightPx = viewportHeightPx + maxScrollValue
    val minimumThumbHeightPx = with(density) { 36.dp.toPx() }
    val thumbHeightPx = ((viewportHeightPx.toFloat() / contentHeightPx.toFloat()) * viewportHeightPx)
        .coerceAtLeast(minimumThumbHeightPx)
    val availableTravelPx = (viewportHeightPx - thumbHeightPx).coerceAtLeast(0f)
    val thumbOffsetPx = if (maxScrollValue == 0) {
        0f
    } else {
        (scrollValue.toFloat() / maxScrollValue.toFloat()) * availableTravelPx
    }

    Box(
        modifier = modifier
            .height(with(density) { viewportHeightPx.toDp() })
            .width(4.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.26f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = with(density) { thumbOffsetPx.toDp() })
                .height(with(density) { thumbHeightPx.toDp() })
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)),
        )
    }
}

@Composable
private fun PlayerStatusContent(
    paddingValues: PaddingValues,
    headerSubtitle: String,
    statusTitle: String,
    statusBody: String,
    frameTitle: String,
    frameBody: String,
    selectedVideo: VideoSummary? = null,
    playbackProgress: PlaybackProgress? = null,
    frameAtTop: Boolean = false,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (frameAtTop) {
            item {
                PlayerFramePlaceholder(
                    title = frameTitle,
                    body = frameBody,
                )
            }
        }
        item {
            LookTubePageHeader(
                title = "Player",
                subtitle = headerSubtitle,
            )
        }
        selectedVideo?.let { video ->
            item {
                LookTubeCard(
                    title = video.title,
                    body = buildString {
                        appendLine("Show: ${video.displaySeriesTitle}")
                        appendLine("Feed category: ${video.feedCategory}")
                        if (video.description.isNotBlank()) {
                            appendLine()
                            append(video.description)
                        }
                    },
                )
            }
        }
        if (!frameAtTop) {
            item {
                PlayerFramePlaceholder(
                    title = frameTitle,
                    body = frameBody,
                )
            }
        }
        item {
            LookTubeCard(
                title = statusTitle,
                body = buildString {
                    append(statusBody)
                    selectedVideo?.let { video ->
                        append("\n\nShow: ${video.displaySeriesTitle}")
                        append("\nFeed category: ${video.feedCategory}")
                    }
                    playbackProgress?.takeIf { it.durationSeconds > 0 }?.let { progress ->
                        append("\nResume point: ${formatPlaybackTime(progress.positionSeconds)} of ${formatPlaybackTime(progress.durationSeconds)}.")
                    }
                },
            )
        }
    }
}

@Composable
private fun ActivePlayerContent(
    paddingValues: PaddingValues,
    selectedVideo: VideoSummary,
    playbackProgress: PlaybackProgress?,
    playbackSelectionRequest: Long,
    selectedVideoEngagement: VideoEngagementRecord?,
    recentPlaybackVideos: List<RecentPlaybackVideo>,
    availableLocalCaptionEngines: List<LocalCaptionEngine>,
    selectedLocalCaptionEngine: LocalCaptionEngine,
    localCaptionModelState: LocalCaptionModelState,
    selectedCaptionData: VideoCaptionData?,
    selectedCaptionTrack: VideoCaptionTrack?,
    selectedCaptionGenerationStatus: CaptionGenerationStatus,
    player: Player,
    onRecentVideoSelected: (String) -> Unit,
    onMarkVideoWatched: (String) -> Unit,
    onMarkVideoUnwatched: (String) -> Unit,
    onLocalCaptionEngineSelected: (String) -> Unit,
    onGenerateCaptionsRequested: (String) -> Unit,
    onDeleteCaptionDataRequested: (String) -> Unit,
    onFullscreenChanged: (Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    val remotePlaybackStatus = rememberRemotePlaybackStatus(player)
    val isWatched = selectedVideoEngagement.isWatched(playbackProgress)
    val otherRecentPlaybackVideos = remember(recentPlaybackVideos, selectedVideo.id) {
        recentPlaybackVideos.filter { recentPlaybackVideo -> recentPlaybackVideo.video.id != selectedVideo.id }
    }
    var recentPlaybackMenuExpanded by rememberSaveable(selectedVideo.id) { mutableStateOf(false) }

    LaunchedEffect(selectedVideo.id, playbackSelectionRequest) {
        listState.scrollToItem(0)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            EmbeddedPlayerSurface(
                player = player,
                remotePlaybackStatus = remotePlaybackStatus,
                onFullscreenToggle = onFullscreenChanged,
            )
        }
        item {
            PlayerSupportingCopy(
                playbackProgress = playbackProgress,
                remotePlaybackStatus = remotePlaybackStatus,
                isWatched = isWatched,
            )
        }
        item {
            PlayerQuickActions(
                selectedVideo = selectedVideo,
                isWatched = isWatched,
                recentPlaybackVideos = otherRecentPlaybackVideos,
                recentPlaybackMenuExpanded = recentPlaybackMenuExpanded,
                onRecentPlaybackMenuExpandedChanged = { recentPlaybackMenuExpanded = it },
                onRecentVideoSelected = onRecentVideoSelected,
                onMarkVideoWatched = onMarkVideoWatched,
                onMarkVideoUnwatched = onMarkVideoUnwatched,
            )
        }
        item {
            LookTubeCard(
                title = selectedVideo.title,
                body = buildString {
                    appendLine("Show: ${selectedVideo.displaySeriesTitle}")
                    append("Feed category: ${selectedVideo.feedCategory}")
                    if (selectedVideo.description.isNotBlank()) {
                        appendLine()
                        appendLine()
                        append(selectedVideo.description)
                    }
                },
            )
        }
        item {
            CaptionStatusCard(
                selectedVideo = selectedVideo,
                availableLocalCaptionEngines = availableLocalCaptionEngines,
                selectedLocalCaptionEngine = selectedLocalCaptionEngine,
                localCaptionModelState = localCaptionModelState,
                selectedCaptionData = selectedCaptionData,
                selectedCaptionTrack = selectedCaptionTrack,
                selectedCaptionGenerationStatus = selectedCaptionGenerationStatus,
                onLocalCaptionEngineSelected = onLocalCaptionEngineSelected,
                onGenerateCaptionsRequested = onGenerateCaptionsRequested,
                onDeleteCaptionDataRequested = onDeleteCaptionDataRequested,
            )
        }
    }
}

@Composable
private fun EmbeddedPlayerSurface(
    player: Player,
    remotePlaybackStatus: RemotePlaybackStatus?,
    onFullscreenToggle: (Boolean) -> Unit,
) {
    PlayerSurface(
        player = player,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        overlayInset = 12.dp,
        remotePlaybackStatus = remotePlaybackStatus,
        onFullscreenToggle = onFullscreenToggle,
    )
}

@Composable
private fun FullscreenPlayerSurface(
    player: Player,
    remotePlaybackStatus: RemotePlaybackStatus?,
    onFullscreenToggle: (Boolean) -> Unit,
) {
    val configuration = LocalConfiguration.current
    PlayerSurface(
        player = player,
        modifier = Modifier.fillMaxSize(),
        overlayInset = 16.dp,
        remotePlaybackStatus = remotePlaybackStatus,
        isFullscreenLandscape = configuration.screenWidthDp > configuration.screenHeightDp,
        onFullscreenToggle = onFullscreenToggle,
    )
}

@Composable
private fun PlayerSurface(
    player: Player,
    modifier: Modifier,
    overlayInset: Dp,
    remotePlaybackStatus: RemotePlaybackStatus?,
    isFullscreenLandscape: Boolean = false,
    onFullscreenToggle: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val mediaRouteButtonViewProvider = remember { MediaRouteButtonViewProvider() }
    val overlayInsetPx = with(density) { overlayInset.roundToPx() }
    val remoteBadgeCornerRadiusPx = with(density) { REMOTE_PLAYBACK_BADGE_CORNER_RADIUS.toPx() }
    val remoteBadgeStrokeWidthPx = with(density) { REMOTE_PLAYBACK_BADGE_STROKE_WIDTH.roundToPx() }
    val remoteBadgePaddingHorizontalPx = with(density) { REMOTE_PLAYBACK_BADGE_HORIZONTAL_PADDING.roundToPx() }
    val remoteBadgePaddingVerticalPx = with(density) { REMOTE_PLAYBACK_BADGE_VERTICAL_PADDING.roundToPx() }
    val remoteBadgeMaxWidthPx = with(density) { REMOTE_PLAYBACK_BADGE_MAX_WIDTH.roundToPx() }
    val remoteBadgeBackgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f).toArgb()
    val remoteBadgeOutlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f).toArgb()
    val remoteBadgeTitleColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val remoteBadgeBodyColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val skipFeedbackHorizontalPaddingPx = with(density) { DOUBLE_TAP_SEEK_FEEDBACK_HORIZONTAL_PADDING.roundToPx() }
    val skipFeedbackVerticalPaddingPx = with(density) { DOUBLE_TAP_SEEK_FEEDBACK_VERTICAL_PADDING.roundToPx() }
    val skipFeedbackCornerRadiusPx = with(density) { DOUBLE_TAP_SEEK_FEEDBACK_CORNER_RADIUS.toPx() }
    val skipFeedbackStrokeWidthPx = with(density) { DOUBLE_TAP_SEEK_FEEDBACK_STROKE_WIDTH.roundToPx() }
    val skipFeedbackArrowSpacingPx = with(density) { DOUBLE_TAP_SEEK_FEEDBACK_ARROW_SPACING.roundToPx() }
    val skipFeedbackTravelPx = with(density) { DOUBLE_TAP_SEEK_FEEDBACK_TRAVEL.roundToPx() }
    val skipFeedbackBackgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f).toArgb()
    val skipFeedbackOutlineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f).toArgb()
    val skipFeedbackTextColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val skipFeedbackAccentColor = MaterialTheme.colorScheme.primary.toArgb()
    val fullscreenCenterButtonSizePx = with(density) { FULLSCREEN_LANDSCAPE_CENTER_BUTTON_SIZE.roundToPx() }
    val fullscreenPlayPauseButtonSizePx = with(density) { FULLSCREEN_LANDSCAPE_PLAY_PAUSE_BUTTON_SIZE.roundToPx() }
    val fullscreenBottomButtonSizePx = with(density) { FULLSCREEN_LANDSCAPE_BOTTOM_BUTTON_SIZE.roundToPx() }
    val fullscreenButtonPaddingPx = with(density) { FULLSCREEN_LANDSCAPE_BUTTON_PADDING.roundToPx() }
    val fullscreenCenterSpacingPx = with(density) { FULLSCREEN_LANDSCAPE_CENTER_BUTTON_SPACING.roundToPx() }
    val fullscreenBottomSpacingPx = with(density) { FULLSCREEN_LANDSCAPE_BOTTOM_BUTTON_SPACING.roundToPx() }
    val fullscreenCenterControlsPaddingPx = with(density) { FULLSCREEN_LANDSCAPE_CENTER_CONTROLS_PADDING.roundToPx() }
    val fullscreenBottomBarHeightPx = with(density) { FULLSCREEN_LANDSCAPE_BOTTOM_BAR_HEIGHT.roundToPx() }
    val fullscreenBottomBarHorizontalPaddingPx = with(density) { FULLSCREEN_LANDSCAPE_BOTTOM_BAR_HORIZONTAL_PADDING.roundToPx() }
    val fullscreenBottomBarVerticalPaddingPx = with(density) { FULLSCREEN_LANDSCAPE_BOTTOM_BAR_VERTICAL_PADDING.roundToPx() }
    val fullscreenProgressHeightPx = with(density) { FULLSCREEN_LANDSCAPE_PROGRESS_HEIGHT.roundToPx() }
    val fullscreenProgressBottomMarginPx = with(density) { FULLSCREEN_LANDSCAPE_PROGRESS_BOTTOM_MARGIN.roundToPx() }
    val fullscreenTimePaddingPx = with(density) { FULLSCREEN_LANDSCAPE_TIME_PADDING.roundToPx() }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    val doubleTapGestureDetector = remember(context, player) {
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(event: MotionEvent): Boolean = true

                override fun onDoubleTap(event: MotionEvent): Boolean {
                    val hostPlayerView = playerView ?: return false
                    return when (val direction = doubleTapSeekDirection(event.x, hostPlayerView.width)) {
                        DoubleTapSeekDirection.Backward -> {
                            val feedbackSeconds = player.doubleTapSeekFeedbackSeconds(direction)
                            player.seekBack()
                            hostPlayerView.showDoubleTapSeekFeedback(
                                direction = direction,
                                seconds = feedbackSeconds,
                                insetPx = overlayInsetPx,
                                backgroundColor = skipFeedbackBackgroundColor,
                                outlineColor = skipFeedbackOutlineColor,
                                textColor = skipFeedbackTextColor,
                                accentColor = skipFeedbackAccentColor,
                                cornerRadiusPx = skipFeedbackCornerRadiusPx,
                                strokeWidthPx = skipFeedbackStrokeWidthPx,
                                horizontalPaddingPx = skipFeedbackHorizontalPaddingPx,
                                verticalPaddingPx = skipFeedbackVerticalPaddingPx,
                                arrowSpacingPx = skipFeedbackArrowSpacingPx,
                                travelPx = skipFeedbackTravelPx,
                            )
                            true
                        }
                        DoubleTapSeekDirection.Forward -> {
                            val feedbackSeconds = player.doubleTapSeekFeedbackSeconds(direction)
                            player.seekForward()
                            hostPlayerView.showDoubleTapSeekFeedback(
                                direction = direction,
                                seconds = feedbackSeconds,
                                insetPx = overlayInsetPx,
                                backgroundColor = skipFeedbackBackgroundColor,
                                outlineColor = skipFeedbackOutlineColor,
                                textColor = skipFeedbackTextColor,
                                accentColor = skipFeedbackAccentColor,
                                cornerRadiusPx = skipFeedbackCornerRadiusPx,
                                strokeWidthPx = skipFeedbackStrokeWidthPx,
                                horizontalPaddingPx = skipFeedbackHorizontalPaddingPx,
                                verticalPaddingPx = skipFeedbackVerticalPaddingPx,
                                arrowSpacingPx = skipFeedbackArrowSpacingPx,
                                travelPx = skipFeedbackTravelPx,
                            )
                            true
                        }
                        null -> false
                    }
                }
            },
        )
    }

    DisposableEffect(player, playerView) {
        val hostPlayerView = playerView
        if (hostPlayerView == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    hostPlayerView.keepScreenOn = shouldKeepScreenOn(
                        isPlaying = player.isPlaying,
                        playWhenReady = player.playWhenReady,
                        playbackState = player.playbackState,
                    )
                }
            }
            hostPlayerView.keepScreenOn = shouldKeepScreenOn(
                isPlaying = player.isPlaying,
                playWhenReady = player.playWhenReady,
                playbackState = player.playbackState,
            )
            player.addListener(listener)
            onDispose {
                hostPlayerView.keepScreenOn = false
                player.removeListener(listener)
            }
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                playerView = this
                useController = true
                setControllerAutoShow(true)
                setControllerHideOnTouch(true)
                setShowSubtitleButton(true)
                setShowPreviousButton(false)
                setShowNextButton(false)
                setMediaRouteButtonViewProvider(mediaRouteButtonViewProvider)
                this.player = player
                keepScreenOn = shouldKeepScreenOn(
                    isPlaying = player.isPlaying,
                    playWhenReady = player.playWhenReady,
                    playbackState = player.playbackState,
                )
                syncRemotePlaybackBadge(
                    status = remotePlaybackStatus,
                    insetPx = overlayInsetPx,
                    maxWidthPx = remoteBadgeMaxWidthPx,
                    backgroundColor = remoteBadgeBackgroundColor,
                    outlineColor = remoteBadgeOutlineColor,
                    titleColor = remoteBadgeTitleColor,
                    bodyColor = remoteBadgeBodyColor,
                    cornerRadiusPx = remoteBadgeCornerRadiusPx,
                    strokeWidthPx = remoteBadgeStrokeWidthPx,
                    horizontalPaddingPx = remoteBadgePaddingHorizontalPx,
                    verticalPaddingPx = remoteBadgePaddingVerticalPx,
                )
                syncControllerLayout(
                    isFullscreenLandscape = isFullscreenLandscape,
                    overlayInsetPx = overlayInsetPx,
                    centerButtonSizePx = fullscreenCenterButtonSizePx,
                    playPauseButtonSizePx = fullscreenPlayPauseButtonSizePx,
                    bottomButtonSizePx = fullscreenBottomButtonSizePx,
                    buttonPaddingPx = fullscreenButtonPaddingPx,
                    centerSpacingPx = fullscreenCenterSpacingPx,
                    bottomSpacingPx = fullscreenBottomSpacingPx,
                    centerControlsPaddingPx = fullscreenCenterControlsPaddingPx,
                    bottomBarHeightPx = fullscreenBottomBarHeightPx,
                    bottomBarHorizontalPaddingPx = fullscreenBottomBarHorizontalPaddingPx,
                    bottomBarVerticalPaddingPx = fullscreenBottomBarVerticalPaddingPx,
                    progressHeightPx = fullscreenProgressHeightPx,
                    progressBottomMarginPx = fullscreenProgressBottomMarginPx,
                    timePaddingPx = fullscreenTimePaddingPx,
                )
                setFullscreenButtonClickListener { isFullscreen ->
                    onFullscreenToggle(isFullscreen)
                }
                setOnTouchListener { _, motionEvent ->
                    doubleTapGestureDetector.onTouchEvent(motionEvent)
                    false
                }
            }
        },
        update = { hostPlayerView ->
            playerView = hostPlayerView
            hostPlayerView.player = player
            hostPlayerView.keepScreenOn = shouldKeepScreenOn(
                isPlaying = player.isPlaying,
                playWhenReady = player.playWhenReady,
                playbackState = player.playbackState,
            )
            hostPlayerView.setShowPreviousButton(false)
            hostPlayerView.setShowNextButton(false)
            hostPlayerView.syncRemotePlaybackBadge(
                status = remotePlaybackStatus,
                insetPx = overlayInsetPx,
                maxWidthPx = remoteBadgeMaxWidthPx,
                backgroundColor = remoteBadgeBackgroundColor,
                outlineColor = remoteBadgeOutlineColor,
                titleColor = remoteBadgeTitleColor,
                bodyColor = remoteBadgeBodyColor,
                cornerRadiusPx = remoteBadgeCornerRadiusPx,
                strokeWidthPx = remoteBadgeStrokeWidthPx,
                horizontalPaddingPx = remoteBadgePaddingHorizontalPx,
                verticalPaddingPx = remoteBadgePaddingVerticalPx,
            )
            hostPlayerView.syncControllerLayout(
                isFullscreenLandscape = isFullscreenLandscape,
                overlayInsetPx = overlayInsetPx,
                centerButtonSizePx = fullscreenCenterButtonSizePx,
                playPauseButtonSizePx = fullscreenPlayPauseButtonSizePx,
                bottomButtonSizePx = fullscreenBottomButtonSizePx,
                buttonPaddingPx = fullscreenButtonPaddingPx,
                centerSpacingPx = fullscreenCenterSpacingPx,
                bottomSpacingPx = fullscreenBottomSpacingPx,
                centerControlsPaddingPx = fullscreenCenterControlsPaddingPx,
                bottomBarHeightPx = fullscreenBottomBarHeightPx,
                bottomBarHorizontalPaddingPx = fullscreenBottomBarHorizontalPaddingPx,
                bottomBarVerticalPaddingPx = fullscreenBottomBarVerticalPaddingPx,
                progressHeightPx = fullscreenProgressHeightPx,
                progressBottomMarginPx = fullscreenProgressBottomMarginPx,
                timePaddingPx = fullscreenTimePaddingPx,
            )
            hostPlayerView.setFullscreenButtonClickListener { isFullscreen ->
                onFullscreenToggle(isFullscreen)
            }
            hostPlayerView.setOnTouchListener { _, motionEvent ->
                doubleTapGestureDetector.onTouchEvent(motionEvent)
                false
            }
        },
    )
}

@Composable
private fun rememberRemotePlaybackStatus(
    player: Player,
): RemotePlaybackStatus? {
    val context = LocalContext.current
    var status by remember(player, context) {
        mutableStateOf(player.toRemotePlaybackStatus(context))
    }

    DisposableEffect(player, context) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                status = player.toRemotePlaybackStatus(context)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    return status
}

private fun Player.toRemotePlaybackStatus(context: Context): RemotePlaybackStatus? {
    if (!isRemotePlayback(deviceInfo)) {
        return null
    }
    val deviceName = runCatching {
        CastContext.getSharedInstance(context)
            .sessionManager
            .currentCastSession
            ?.castDevice
            ?.friendlyName
    }.getOrNull()
    return RemotePlaybackStatus(
        title = remotePlaybackTitle(deviceName),
        badgeBody = remotePlaybackBadgeBody(
            isPlaying = isPlaying,
            playWhenReady = playWhenReady,
            playbackState = playbackState,
        ),
        detailsBody = remotePlaybackBody(
            isPlaying = isPlaying,
            playWhenReady = playWhenReady,
            playbackState = playbackState,
        ),
    )
}


@Composable
private fun rememberActivity(context: Context): Activity? = when (context) {
    is Activity -> context
    is ContextWrapper -> rememberActivity(context.baseContext)
    else -> null
}

private fun formatPlaybackTime(seconds: Long): String {
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    val remainingSeconds = seconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, remainingSeconds)
    } else {
        "%d:%02d".format(minutes, remainingSeconds)
    }
}

internal fun compactResumeSummary(playbackProgress: PlaybackProgress?): String =
    playbackProgress?.takeIf { it.durationSeconds > 0 }?.let { progress ->
        "Resume • ${formatPlaybackTime(progress.positionSeconds)} / ${formatPlaybackTime(progress.durationSeconds)}"
    } ?: "Ready to play from the beginning."

internal fun currentWatchStatusLabel(
    isWatched: Boolean,
    playbackProgress: PlaybackProgress?,
): String = when {
    isWatched -> "Watched"
    playbackProgress?.positionSeconds?.let { it > 0 } == true -> "In progress"
    else -> "Not started"
}

internal fun recentPlaybackMenuSubtitle(recentPlaybackVideo: RecentPlaybackVideo): String = buildList {
    add(recentPlaybackVideo.video.displaySeriesTitle)
    recentPlaybackVideo.playbackProgress?.takeIf { progress -> progress.durationSeconds > 0 }?.let { progress ->
        add("Resume ${formatPlaybackTime(progress.positionSeconds)}")
    }
    if (recentPlaybackVideo.isWatched) {
        add("Watched")
    }
}.joinToString(" • ")
internal fun shouldKeepScreenOn(
    isPlaying: Boolean,
    playWhenReady: Boolean,
    playbackState: Int,
): Boolean = isPlaying || (playWhenReady && playbackState == Player.STATE_BUFFERING)


internal fun isRemotePlayback(deviceInfo: DeviceInfo): Boolean =
    deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE

internal enum class DoubleTapSeekDirection {
    Backward,
    Forward,
}

internal fun doubleTapSeekDirection(
    tapX: Float,
    surfaceWidthPx: Int,
): DoubleTapSeekDirection? = when {
    surfaceWidthPx <= 0 -> null
    tapX < surfaceWidthPx / 2f -> DoubleTapSeekDirection.Backward
    else -> DoubleTapSeekDirection.Forward
}

internal fun resolveDoubleTapSeekIncrementSeconds(seekIncrementMs: Long): Long =
    if (seekIncrementMs <= 0L) {
        DEFAULT_DOUBLE_TAP_SEEK_SECONDS
    } else {
        ceil(seekIncrementMs / 1_000.0).toLong()
    }

internal fun doubleTapSeekFeedbackLabel(seconds: Long): String =
    "$seconds ${if (seconds == 1L) "second" else "seconds"}"

private fun Player.doubleTapSeekFeedbackSeconds(direction: DoubleTapSeekDirection): Long =
    resolveDoubleTapSeekIncrementSeconds(
        when (direction) {
            DoubleTapSeekDirection.Backward -> seekBackIncrement
            DoubleTapSeekDirection.Forward -> seekForwardIncrement
        },
    )

internal fun remotePlaybackTitle(deviceName: String?): String =
    deviceName?.takeIf(String::isNotBlank)?.let { "Casting to $it" } ?: "Casting video"
internal fun remotePlaybackBadgeBody(
    isPlaying: Boolean,
    playWhenReady: Boolean,
    playbackState: Int,
): String = when {
    playbackState == Player.STATE_BUFFERING -> "Syncing to your cast device"
    isPlaying -> "Remote playback is active"
    playWhenReady -> "Reconnecting to your cast device"
    else -> "Playback is paused on your cast device"
}

internal fun remotePlaybackBody(
    isPlaying: Boolean,
    playWhenReady: Boolean,
    playbackState: Int,
): String = when {
    playbackState == Player.STATE_BUFFERING -> "LookTube is syncing playback to your cast device."
    isPlaying -> "Video is playing on your cast device. Use the standard player controls here to pause, resume, or stop casting."
    playWhenReady -> "LookTube is reconnecting playback on your cast device."
    else -> "Playback is paused on your cast device. Use the standard player controls here to resume or stop casting."
}

private fun PlayerView.syncRemotePlaybackBadge(
    status: RemotePlaybackStatus?,
    insetPx: Int,
    maxWidthPx: Int,
    backgroundColor: Int,
    outlineColor: Int,
    titleColor: Int,
    bodyColor: Int,
    cornerRadiusPx: Float,
    strokeWidthPx: Int,
    horizontalPaddingPx: Int,
    verticalPaddingPx: Int,
) {
    val overlay = overlayFrameLayout ?: return
    val badgeContainer = overlay.findViewWithTag<LinearLayout>(REMOTE_PLAYBACK_BADGE_CONTAINER_TAG)
        ?: LinearLayout(context).apply {
            tag = REMOTE_PLAYBACK_BADGE_CONTAINER_TAG
            orientation = LinearLayout.VERTICAL
            isClickable = false
            isFocusable = false
            isEnabled = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setOnTouchListener { _, _ -> false }
            addView(
                TextView(context).apply {
                    tag = REMOTE_PLAYBACK_BADGE_TITLE_TAG
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    textSize = 13f
                },
            )
            addView(
                TextView(context).apply {
                    tag = REMOTE_PLAYBACK_BADGE_BODY_TAG
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    textSize = 12f
                },
            )
            overlay.addView(this)
        }
    if (status == null) {
        badgeContainer.visibility = View.GONE
        return
    }
    badgeContainer.layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.WRAP_CONTENT,
        FrameLayout.LayoutParams.WRAP_CONTENT,
        Gravity.TOP or Gravity.START,
    ).apply {
        topMargin = insetPx
        leftMargin = insetPx
    }
    badgeContainer.setPadding(
        horizontalPaddingPx,
        verticalPaddingPx,
        horizontalPaddingPx,
        verticalPaddingPx,
    )
    badgeContainer.background = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = cornerRadiusPx
        setColor(backgroundColor)
        setStroke(strokeWidthPx, outlineColor)
    }
    badgeContainer.visibility = View.VISIBLE
    badgeContainer.alpha = 1f
    badgeContainer.findViewWithTag<TextView>(REMOTE_PLAYBACK_BADGE_TITLE_TAG)?.apply {
        text = status.title
        setTextColor(titleColor)
        maxWidth = maxWidthPx
    }
    badgeContainer.findViewWithTag<TextView>(REMOTE_PLAYBACK_BADGE_BODY_TAG)?.apply {
        text = status.badgeBody
        setTextColor(bodyColor)
        maxWidth = maxWidthPx
    }
}

private fun PlayerView.showDoubleTapSeekFeedback(
    direction: DoubleTapSeekDirection,
    seconds: Long,
    insetPx: Int,
    backgroundColor: Int,
    outlineColor: Int,
    textColor: Int,
    accentColor: Int,
    cornerRadiusPx: Float,
    strokeWidthPx: Int,
    horizontalPaddingPx: Int,
    verticalPaddingPx: Int,
    arrowSpacingPx: Int,
    travelPx: Int,
) {
    val overlay = overlayFrameLayout ?: return
    val container = overlay.findViewWithTag<LinearLayout>(doubleTapSeekFeedbackContainerTag(direction))
        ?: LinearLayout(context).apply {
            tag = doubleTapSeekFeedbackContainerTag(direction)
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = false
            isFocusable = false
            isEnabled = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            visibility = View.GONE
            alpha = 0f
            setOnTouchListener { _, _ -> false }
            addView(
                LinearLayout(context).apply {
                    tag = doubleTapSeekFeedbackArrowRowTag(direction)
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    repeat(DOUBLE_TAP_SEEK_FEEDBACK_ARROW_COUNT) { index ->
                        addView(
                            TextView(context).apply {
                                text = if (direction == DoubleTapSeekDirection.Backward) "❮" else "❯"
                                textSize = 24f
                                alpha = DOUBLE_TAP_SEEK_FEEDBACK_ARROW_IDLE_ALPHA
                                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                ).apply {
                                    if (index > 0) {
                                        marginStart = arrowSpacingPx
                                    }
                                }
                            },
                        )
                    }
                },
            )
            addView(
                TextView(context).apply {
                    tag = doubleTapSeekFeedbackLabelTag(direction)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    textSize = 15f
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
            )
            overlay.addView(this)
        }
    val sideMarginPx = doubleTapSeekFeedbackSideMarginPx(width, insetPx)
    val topMarginPx = doubleTapSeekFeedbackTopMarginPx(height, insetPx)
    container.layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.WRAP_CONTENT,
        FrameLayout.LayoutParams.WRAP_CONTENT,
        Gravity.TOP or if (direction == DoubleTapSeekDirection.Backward) Gravity.START else Gravity.END,
    ).apply {
        topMargin = topMarginPx
        if (direction == DoubleTapSeekDirection.Backward) {
            leftMargin = sideMarginPx
        } else {
            rightMargin = sideMarginPx
        }
    }
    container.setPadding(horizontalPaddingPx, verticalPaddingPx, horizontalPaddingPx, verticalPaddingPx)
    container.background = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = cornerRadiusPx
        setColor(backgroundColor)
        setStroke(strokeWidthPx, outlineColor)
    }
    container.findViewWithTag<TextView>(doubleTapSeekFeedbackLabelTag(direction))?.apply {
        text = doubleTapSeekFeedbackLabel(seconds)
        setTextColor(textColor)
    }
    val arrowViews = container.findViewWithTag<LinearLayout>(doubleTapSeekFeedbackArrowRowTag(direction))
        ?.children
        ?.mapNotNull { it as? TextView }
        ?.toList()
        .orEmpty()
    arrowViews.forEachIndexed { index, arrowView ->
        arrowView.animate().cancel()
        arrowView.alpha = DOUBLE_TAP_SEEK_FEEDBACK_ARROW_IDLE_ALPHA
        arrowView.translationX = if (direction == DoubleTapSeekDirection.Backward) {
            DOUBLE_TAP_SEEK_FEEDBACK_ARROW_TRAVEL_PX * (index + 1)
        } else {
            -DOUBLE_TAP_SEEK_FEEDBACK_ARROW_TRAVEL_PX * (index + 1)
        }
        arrowView.setTextColor(accentColor)
        arrowView.animate()
            .alpha(1f)
            .translationX(0f)
            .setStartDelay(index * DOUBLE_TAP_SEEK_FEEDBACK_ARROW_STAGGER_MS)
            .setDuration(DOUBLE_TAP_SEEK_FEEDBACK_ARROW_ANIMATION_MS)
            .withEndAction {
                arrowView.animate()
                    .alpha(DOUBLE_TAP_SEEK_FEEDBACK_ARROW_IDLE_ALPHA)
                    .setDuration(DOUBLE_TAP_SEEK_FEEDBACK_ARROW_FADE_MS)
                    .start()
            }
            .start()
    }
    (container.getTag(PLAYER_VIEW_HIDE_RUNNABLE_TAG) as? Runnable)?.let(container::removeCallbacks)
    container.animate().cancel()
    container.visibility = View.VISIBLE
    container.bringToFront()
    container.alpha = 0f
    container.translationX = if (direction == DoubleTapSeekDirection.Backward) {
        -travelPx.toFloat()
    } else {
        travelPx.toFloat()
    }
    container.scaleX = 0.96f
    container.scaleY = 0.96f
    container.animate()
        .alpha(1f)
        .translationX(0f)
        .scaleX(1f)
        .scaleY(1f)
        .setDuration(DOUBLE_TAP_SEEK_FEEDBACK_ENTER_MS)
        .start()
    val hideRunnable = Runnable {
        container.animate()
            .alpha(0f)
            .translationX(
                if (direction == DoubleTapSeekDirection.Backward) {
                    -(travelPx / 2f)
                } else {
                    travelPx / 2f
                },
            )
            .setDuration(DOUBLE_TAP_SEEK_FEEDBACK_EXIT_MS)
            .withEndAction {
                container.visibility = View.GONE
            }
            .start()
    }
    container.setTag(PLAYER_VIEW_HIDE_RUNNABLE_TAG, hideRunnable)
    container.postDelayed(hideRunnable, DOUBLE_TAP_SEEK_FEEDBACK_VISIBLE_MS)
}

private fun PlayerView.syncControllerLayout(
    isFullscreenLandscape: Boolean,
    overlayInsetPx: Int,
    centerButtonSizePx: Int,
    playPauseButtonSizePx: Int,
    bottomButtonSizePx: Int,
    buttonPaddingPx: Int,
    centerSpacingPx: Int,
    bottomSpacingPx: Int,
    centerControlsPaddingPx: Int,
    bottomBarHeightPx: Int,
    bottomBarHorizontalPaddingPx: Int,
    bottomBarVerticalPaddingPx: Int,
    progressHeightPx: Int,
    progressBottomMarginPx: Int,
    timePaddingPx: Int,
) {
    val bottomBar = findViewById<FrameLayout>(androidx.media3.ui.R.id.exo_bottom_bar)
    val timeGroup = findViewById<LinearLayout>(androidx.media3.ui.R.id.exo_time)
    val centerControls = findViewById<LinearLayout>(androidx.media3.ui.R.id.exo_center_controls)
    val basicControls = findViewById<LinearLayout>(androidx.media3.ui.R.id.exo_basic_controls)
    val minimalControls = findViewById<LinearLayout>(androidx.media3.ui.R.id.exo_minimal_controls)
    val topControls = findViewById<LinearLayout>(androidx.media3.ui.R.id.exo_top_controls)
    val progressPlaceholder = findViewById<View>(androidx.media3.ui.R.id.exo_progress_placeholder)
    val progress = findViewById<View>(androidx.media3.ui.R.id.exo_progress)
    val centerButtons = listOf(
        androidx.media3.ui.R.id.exo_prev,
        androidx.media3.ui.R.id.exo_rew,
        androidx.media3.ui.R.id.exo_play_pause,
        androidx.media3.ui.R.id.exo_ffwd,
        androidx.media3.ui.R.id.exo_next,
    ).mapNotNull { findViewById<View>(it) }
    val bottomButtons = listOf(
        androidx.media3.ui.R.id.exo_subtitle,
        androidx.media3.ui.R.id.exo_settings,
        androidx.media3.ui.R.id.exo_fullscreen,
        androidx.media3.ui.R.id.exo_overflow_show,
        androidx.media3.ui.R.id.exo_overflow_hide,
        androidx.media3.ui.R.id.exo_repeat_toggle,
        androidx.media3.ui.R.id.exo_shuffle,
        androidx.media3.ui.R.id.exo_vr,
        androidx.media3.ui.R.id.exo_minimal_fullscreen,
    ).mapNotNull { findViewById<View>(it) }
    val controllerViews = buildList {
        addAll(listOfNotNull(bottomBar, timeGroup, centerControls, basicControls, minimalControls, topControls, progressPlaceholder, progress))
        centerControls?.children?.forEach(::add)
        basicControls?.children?.forEach(::add)
        minimalControls?.children?.forEach(::add)
        topControls?.children?.forEach(::add)
        addAll(centerButtons)
        addAll(bottomButtons)
    }.distinct()
    controllerViews.forEach(View::captureLayoutSnapshotIfNeeded)
    if (!isFullscreenLandscape) {
        controllerViews.forEach(View::restoreLayoutSnapshotIfNeeded)
        centerControls?.translationY = 0f
        return
    }
    bottomBar?.apply {
        setHeightPx(bottomBarHeightPx)
        setPadding(
            bottomBarHorizontalPaddingPx,
            bottomBarVerticalPaddingPx,
            bottomBarHorizontalPaddingPx,
            bottomBarVerticalPaddingPx,
        )
    }
    timeGroup?.setPadding(timePaddingPx, timeGroup.paddingTop, timePaddingPx, timeGroup.paddingBottom)
    centerControls?.apply {
        setPadding(
            centerControlsPaddingPx,
            centerControlsPaddingPx,
            centerControlsPaddingPx,
            centerControlsPaddingPx,
        )
        translationY = -(bottomBarHeightPx * FULLSCREEN_LANDSCAPE_CENTER_CONTROLS_VERTICAL_SHIFT_FRACTION)
        children.forEach { child -> child.setHorizontalMargins(centerSpacingPx / 2) }
    }
    basicControls?.children?.forEach { child -> child.setHorizontalMargins(bottomSpacingPx / 2) }
    minimalControls?.children?.forEach { child -> child.setHorizontalMargins(bottomSpacingPx / 2) }
    topControls?.apply {
        setPadding(overlayInsetPx, overlayInsetPx, overlayInsetPx, 0)
        children.forEach { child -> child.setHorizontalMargins(bottomSpacingPx / 2) }
    }
    progressPlaceholder?.setHeightAndBottomMarginPx(progressHeightPx, progressBottomMarginPx)
    progress?.setHeightAndBottomMarginPx(progressHeightPx, progressBottomMarginPx)
    centerButtons.forEach { button ->
        button.setSquareSizePx(
            if (button.id == androidx.media3.ui.R.id.exo_play_pause) {
                playPauseButtonSizePx
            } else {
                centerButtonSizePx
            },
        )
        if (button is ImageButton) {
            button.setPadding(buttonPaddingPx, buttonPaddingPx, buttonPaddingPx, buttonPaddingPx)
        }
    }
    bottomButtons.forEach { button ->
        button.setSquareSizePx(bottomButtonSizePx)
        if (button is ImageButton) {
            button.setPadding(buttonPaddingPx, buttonPaddingPx, buttonPaddingPx, buttonPaddingPx)
        }
    }
}

internal fun View.captureLayoutSnapshotIfNeeded() {
    if (getTag(PLAYER_VIEW_LAYOUT_SNAPSHOT_TAG) != null) {
        return
    }
    val marginLayoutParams = layoutParams as? ViewGroup.MarginLayoutParams
    setTag(
        PLAYER_VIEW_LAYOUT_SNAPSHOT_TAG,
        ViewLayoutSnapshot(
            width = layoutParams?.width,
            height = layoutParams?.height,
            leftMargin = marginLayoutParams?.leftMargin,
            topMargin = marginLayoutParams?.topMargin,
            rightMargin = marginLayoutParams?.rightMargin,
            bottomMargin = marginLayoutParams?.bottomMargin,
            paddingLeft = paddingLeft,
            paddingTop = paddingTop,
            paddingRight = paddingRight,
            paddingBottom = paddingBottom,
        ),
    )
}

internal fun View.restoreLayoutSnapshotIfNeeded() {
    val snapshot = getTag(PLAYER_VIEW_LAYOUT_SNAPSHOT_TAG) as? ViewLayoutSnapshot ?: return
    layoutParams?.let { params ->
        snapshot.width?.let { params.width = it }
        snapshot.height?.let { params.height = it }
        if (params is ViewGroup.MarginLayoutParams) {
            params.leftMargin = snapshot.leftMargin ?: params.leftMargin
            params.topMargin = snapshot.topMargin ?: params.topMargin
            params.rightMargin = snapshot.rightMargin ?: params.rightMargin
            params.bottomMargin = snapshot.bottomMargin ?: params.bottomMargin
        }
        layoutParams = params
    }
    setPadding(snapshot.paddingLeft, snapshot.paddingTop, snapshot.paddingRight, snapshot.paddingBottom)
}

private fun View.setHorizontalMargins(horizontalMarginPx: Int) {
    val params = layoutParams as? ViewGroup.MarginLayoutParams ?: return
    params.leftMargin = horizontalMarginPx
    params.rightMargin = horizontalMarginPx
    layoutParams = params
}

private fun View.setHeightAndBottomMarginPx(heightPx: Int, bottomMarginPx: Int) {
    setHeightPx(heightPx)
    val params = layoutParams as? ViewGroup.MarginLayoutParams ?: return
    params.bottomMargin = bottomMarginPx
    layoutParams = params
}

private fun View.setHeightPx(heightPx: Int) {
    val params = layoutParams ?: return
    params.height = heightPx
    layoutParams = params
}

private fun View.setSquareSizePx(sizePx: Int) {
    val params = layoutParams ?: return
    params.width = sizePx
    params.height = sizePx
    layoutParams = params
}

private fun doubleTapSeekFeedbackContainerTag(direction: DoubleTapSeekDirection): String =
    when (direction) {
        DoubleTapSeekDirection.Backward -> DOUBLE_TAP_SEEK_FEEDBACK_BACKWARD_CONTAINER_TAG
        DoubleTapSeekDirection.Forward -> DOUBLE_TAP_SEEK_FEEDBACK_FORWARD_CONTAINER_TAG
    }

private fun doubleTapSeekFeedbackArrowRowTag(direction: DoubleTapSeekDirection): String =
    when (direction) {
        DoubleTapSeekDirection.Backward -> DOUBLE_TAP_SEEK_FEEDBACK_BACKWARD_ARROW_ROW_TAG
        DoubleTapSeekDirection.Forward -> DOUBLE_TAP_SEEK_FEEDBACK_FORWARD_ARROW_ROW_TAG
    }

private fun doubleTapSeekFeedbackLabelTag(direction: DoubleTapSeekDirection): String =
    when (direction) {
        DoubleTapSeekDirection.Backward -> DOUBLE_TAP_SEEK_FEEDBACK_BACKWARD_LABEL_TAG
        DoubleTapSeekDirection.Forward -> DOUBLE_TAP_SEEK_FEEDBACK_FORWARD_LABEL_TAG
    }

internal fun doubleTapSeekFeedbackSideMarginPx(
    surfaceWidthPx: Int,
    insetPx: Int,
): Int = maxOf(insetPx, (surfaceWidthPx * DOUBLE_TAP_SEEK_FEEDBACK_SIDE_MARGIN_FRACTION).roundToInt())

internal fun doubleTapSeekFeedbackTopMarginPx(
    surfaceHeightPx: Int,
    insetPx: Int,
): Int = maxOf(insetPx, (surfaceHeightPx * DOUBLE_TAP_SEEK_FEEDBACK_TOP_MARGIN_FRACTION).roundToInt())

private data class RemotePlaybackStatus(
    val title: String,
    val badgeBody: String,
    val detailsBody: String,
)

private data class ViewLayoutSnapshot(
    val width: Int?,
    val height: Int?,
    val leftMargin: Int?,
    val topMargin: Int?,
    val rightMargin: Int?,
    val bottomMargin: Int?,
    val paddingLeft: Int,
    val paddingTop: Int,
    val paddingRight: Int,
    val paddingBottom: Int,
)

private val REMOTE_PLAYBACK_BADGE_CORNER_RADIUS = 18.dp
private val REMOTE_PLAYBACK_BADGE_STROKE_WIDTH = 1.dp
private val REMOTE_PLAYBACK_BADGE_HORIZONTAL_PADDING = 14.dp
private val REMOTE_PLAYBACK_BADGE_VERTICAL_PADDING = 10.dp
private val REMOTE_PLAYBACK_BADGE_MAX_WIDTH = 240.dp
private val DOUBLE_TAP_SEEK_FEEDBACK_CORNER_RADIUS = 20.dp
private val DOUBLE_TAP_SEEK_FEEDBACK_STROKE_WIDTH = 1.dp
private val DOUBLE_TAP_SEEK_FEEDBACK_HORIZONTAL_PADDING = 18.dp
private val DOUBLE_TAP_SEEK_FEEDBACK_VERTICAL_PADDING = 14.dp
private val DOUBLE_TAP_SEEK_FEEDBACK_ARROW_SPACING = 6.dp
private val DOUBLE_TAP_SEEK_FEEDBACK_TRAVEL = 24.dp
private val FULLSCREEN_LANDSCAPE_CENTER_BUTTON_SIZE = 88.dp
private val FULLSCREEN_LANDSCAPE_PLAY_PAUSE_BUTTON_SIZE = 112.dp
private val FULLSCREEN_LANDSCAPE_BOTTOM_BUTTON_SIZE = 60.dp
private val FULLSCREEN_LANDSCAPE_BUTTON_PADDING = 12.dp
private val FULLSCREEN_LANDSCAPE_CENTER_BUTTON_SPACING = 28.dp
private val FULLSCREEN_LANDSCAPE_BOTTOM_BUTTON_SPACING = 16.dp
private val FULLSCREEN_LANDSCAPE_CENTER_CONTROLS_PADDING = 20.dp
private val FULLSCREEN_LANDSCAPE_BOTTOM_BAR_HEIGHT = 88.dp
private val FULLSCREEN_LANDSCAPE_BOTTOM_BAR_HORIZONTAL_PADDING = 24.dp
private val FULLSCREEN_LANDSCAPE_BOTTOM_BAR_VERTICAL_PADDING = 12.dp
private val FULLSCREEN_LANDSCAPE_PROGRESS_HEIGHT = 44.dp
private val FULLSCREEN_LANDSCAPE_PROGRESS_BOTTOM_MARGIN = 72.dp
private val FULLSCREEN_LANDSCAPE_TIME_PADDING = 14.dp
private val PLAYER_HISTORY_RESERVED_CHROME_HEIGHT = 144.dp
private val PLAYER_VIEW_LAYOUT_SNAPSHOT_TAG = R.id.player_view_layout_snapshot_tag
private val PLAYER_VIEW_HIDE_RUNNABLE_TAG = R.id.player_view_hide_runnable_tag
private const val DEFAULT_DOUBLE_TAP_SEEK_SECONDS = 10L
private const val DOUBLE_TAP_SEEK_FEEDBACK_VISIBLE_MS = 840L
private const val DOUBLE_TAP_SEEK_FEEDBACK_ENTER_MS = 180L
private const val DOUBLE_TAP_SEEK_FEEDBACK_EXIT_MS = 220L
private const val DOUBLE_TAP_SEEK_FEEDBACK_ARROW_COUNT = 3
private const val DOUBLE_TAP_SEEK_FEEDBACK_ARROW_STAGGER_MS = 80L
private const val DOUBLE_TAP_SEEK_FEEDBACK_ARROW_ANIMATION_MS = 180L
private const val DOUBLE_TAP_SEEK_FEEDBACK_ARROW_FADE_MS = 180L
private const val DOUBLE_TAP_SEEK_FEEDBACK_ARROW_IDLE_ALPHA = 0.72f
private const val DOUBLE_TAP_SEEK_FEEDBACK_ARROW_TRAVEL_PX = 14f
private const val DOUBLE_TAP_SEEK_FEEDBACK_SIDE_MARGIN_FRACTION = 0.22f
private const val DOUBLE_TAP_SEEK_FEEDBACK_TOP_MARGIN_FRACTION = 0.24f
private const val FULLSCREEN_LANDSCAPE_CENTER_CONTROLS_VERTICAL_SHIFT_FRACTION = 0.18f
private const val REMOTE_PLAYBACK_BADGE_CONTAINER_TAG = "looktube.remote_playback_badge"
private const val REMOTE_PLAYBACK_BADGE_TITLE_TAG = "looktube.remote_playback_badge_title"
private const val REMOTE_PLAYBACK_BADGE_BODY_TAG = "looktube.remote_playback_badge_body"
private const val DOUBLE_TAP_SEEK_FEEDBACK_BACKWARD_CONTAINER_TAG = "looktube.double_tap_seek_feedback.backward"
private const val DOUBLE_TAP_SEEK_FEEDBACK_FORWARD_CONTAINER_TAG = "looktube.double_tap_seek_feedback.forward"
private const val DOUBLE_TAP_SEEK_FEEDBACK_BACKWARD_ARROW_ROW_TAG = "looktube.double_tap_seek_feedback.backward.arrow_row"
private const val DOUBLE_TAP_SEEK_FEEDBACK_FORWARD_ARROW_ROW_TAG = "looktube.double_tap_seek_feedback.forward.arrow_row"
private const val DOUBLE_TAP_SEEK_FEEDBACK_BACKWARD_LABEL_TAG = "looktube.double_tap_seek_feedback.backward.label"
private const val DOUBLE_TAP_SEEK_FEEDBACK_FORWARD_LABEL_TAG = "looktube.double_tap_seek_feedback.forward.label"
