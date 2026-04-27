package com.looktube.app
import android.content.Intent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.looktube.data.LookTubeRepository
import com.looktube.model.CaptionGenerationStatus
import com.looktube.model.VideoCaptionData
import com.looktube.heuristics.displaySeriesTitle
import com.looktube.model.FeedConfiguration
import com.looktube.model.LibrarySyncState
import com.looktube.model.LookPointsSummary
import com.looktube.model.ManualWatchState
import com.looktube.model.LocalCaptionModelState
import com.looktube.model.PlaybackProgress
import com.looktube.model.RecentPlaybackVideo
import com.looktube.model.SeriesCompletionSummary
import com.looktube.model.VideoCaptionTrack
import com.looktube.model.VideoSummary
import com.looktube.model.buildLookPointsSummary
import com.looktube.model.buildRecentPlaybackVideos
import com.looktube.model.isWatched
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LookTubeAppViewModel(
    private val repository: LookTubeRepository,
) : ViewModel() {
    val accountSession = repository.accountSession
    val feedConfiguration = repository.feedConfiguration
    val librarySyncState = repository.librarySyncState
    val videos = repository.videos
    val playbackProgress = repository.playbackProgress
    val videoEngagement = repository.videoEngagement
    val availableLocalCaptionEngines = repository.availableLocalCaptionEngines
    val selectedLocalCaptionEngine = repository.selectedLocalCaptionEngine
    val localCaptionModelState = repository.localCaptionModelState
    val videoCaptions = repository.videoCaptions
    val captionData = repository.captionData
    private val requestedPageState = MutableStateFlow<Int?>(null)
    val requestedPage: StateFlow<Int?> = requestedPageState.asStateFlow()
    private val videoSelectionModeState = MutableStateFlow(VideoSelectionMode.Passive)
    val videoSelectionMode: StateFlow<VideoSelectionMode> = videoSelectionModeState.asStateFlow()
    private val playbackSelectionRequestState = MutableStateFlow(0L)
    val playbackSelectionRequest: StateFlow<Long> = playbackSelectionRequestState.asStateFlow()
    private val previousAppOpenedAtEpochMillisState = MutableStateFlow(
        repository.feedConfiguration.value.lastOpenedAtEpochMillis,
    )
    val previousAppOpenedAtEpochMillis: StateFlow<Long?> =
        previousAppOpenedAtEpochMillisState.asStateFlow()

    val selectedVideo: StateFlow<VideoSummary?> = combine(
        repository.videos,
        repository.selectedVideoId,
    ) { videos, selectedVideoId ->
        videos.firstOrNull { it.id == selectedVideoId }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    val selectedPlaybackTarget: StateFlow<SelectedPlaybackTarget?> = combine(
        repository.videos,
        repository.selectedVideoId,
        repository.playbackProgress,
        repository.videoCaptions,
    ) { videos, selectedVideoId, progressMap, captionTracks ->
        selectedVideoId
            ?.let { selectedId ->
                videos.firstOrNull { it.id == selectedId }
                    ?.let { video ->
                        SelectedPlaybackTarget(
                            video = video,
                            playbackProgress = progressMap[selectedId],
                            captionTrack = captionTracks[selectedId],
                        )
                    }
            }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = repository.selectedVideoId.value
            ?.let { selectedId ->
                repository.videos.value.firstOrNull { it.id == selectedId }
                    ?.let { video ->
                        SelectedPlaybackTarget(
                            video = video,
                            playbackProgress = repository.playbackProgress.value[selectedId],
                            captionTrack = repository.videoCaptions.value[selectedId],
                        )
                    }
            },
    )

    val selectedVideoCaptionTrack: StateFlow<VideoCaptionTrack?> = combine(
        repository.selectedVideoId,
        repository.videoCaptions,
    ) { selectedVideoId, captionTracks ->
        selectedVideoId?.let(captionTracks::get)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = repository.selectedVideoId.value?.let(repository.videoCaptions.value::get),
    )

    val selectedCaptionGenerationStatus: StateFlow<CaptionGenerationStatus> = combine(
        repository.selectedVideoId,
        repository.captionGenerationStatus,
    ) { selectedVideoId, generationStatusMap ->
        selectedVideoId?.let(generationStatusMap::get) ?: CaptionGenerationStatus.Idle
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = repository.selectedVideoId.value
            ?.let(repository.captionGenerationStatus.value::get)
            ?: CaptionGenerationStatus.Idle,
    )

    val selectedProgress: StateFlow<PlaybackProgress?> = combine(
        repository.playbackProgress,
        repository.selectedVideoId,
    ) { progressMap, selectedVideoId ->
        selectedVideoId?.let(progressMap::get)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    val recentPlaybackVideos: StateFlow<List<RecentPlaybackVideo>> = combine(
        repository.videos,
        repository.playbackProgress,
        repository.videoEngagement,
    ) { videos, progressMap, engagementRecords ->
        buildRecentPlaybackVideos(
            videos = videos,
            playbackProgress = progressMap,
            engagementRecords = engagementRecords,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList(),
    )

    val lookPointsSummary: StateFlow<LookPointsSummary> = combine(
        repository.videos,
        repository.playbackProgress,
        repository.videoEngagement,
        repository.feedConfiguration,
    ) { videos, progressMap, engagementRecords, feedConfiguration ->
        buildLookPointsSummary(
            videos = videos,
            playbackProgress = progressMap,
            engagementRecords = engagementRecords,
            dailyOpenPointCount = feedConfiguration.dailyOpenPointCount,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = LookPointsSummary.Empty,
    )

    val seriesCompletionSummaries: StateFlow<Map<String, SeriesCompletionSummary>> = combine(
        repository.videos,
        repository.playbackProgress,
        repository.videoEngagement,
    ) { videos, progressMap, engagementRecords ->
        videos.groupBy(VideoSummary::displaySeriesTitle)
            .mapValues { (seriesTitle, seriesVideos) ->
                SeriesCompletionSummary(
                    seriesTitle = seriesTitle,
                    watchedVideoCount = seriesVideos.count { video ->
                        engagementRecords[video.id].isWatched(progressMap[video.id])
                    },
                    totalVideoCount = seriesVideos.size,
                )
            }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyMap(),
    )

    init {
        viewModelScope.launch {
            repository.bootstrap()
        }
    }

    fun updateFeedUrl(feedUrl: String) {
        viewModelScope.launch {
            repository.updateFeedUrl(feedUrl)
        }
    }

    fun noteAppOpened() {
        previousAppOpenedAtEpochMillisState.value = repository.feedConfiguration.value.lastOpenedAtEpochMillis
        viewModelScope.launch {
            repository.noteAppOpened()
        }
    }
    fun consumeLaunchIntroMessage(deckSize: Int) {
        viewModelScope.launch {
            repository.consumeLaunchIntroMessage(deckSize)
        }
    }

    fun updateAutoGenerateCaptionsForNewVideos(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateAutoGenerateCaptionsForNewVideos(enabled)
        }
    }
    fun signInToPremiumFeed() {
        viewModelScope.launch {
            repository.signInToPremiumFeed()
        }
    }
    fun clearSyncedData() {
        viewModelScope.launch {
            repository.clearSyncedData()
        }
    }

    fun clearCaptionData() {
        viewModelScope.launch {
            repository.clearCaptionData()
        }
    }

    fun downloadLocalCaptionModel() {
        viewModelScope.launch {
            repository.downloadLocalCaptionModel()
        }
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            repository.refreshLibrary()
        }
    }

    fun generateCaptions(videoId: String) {
        viewModelScope.launch {
            repository.generateCaptions(videoId)
        }
    }

    fun deleteCaptionData(videoId: String) {
        viewModelScope.launch {
            repository.deleteCaptionData(videoId)
        }
    }

    fun selectLocalCaptionEngine(engineId: String) {
        repository.selectLocalCaptionEngine(engineId)
    }

    fun handleLaunchIntent(intent: Intent?) {
        val launchIntent = intent ?: return
        launchIntent.getStringExtra(LookTubeLaunchContract.EXTRA_OPEN_VIDEO_ID)
            ?.takeIf(String::isNotBlank)
            ?.let { videoId ->
                openVideoFromLaunch(videoId)
                return
            }
        launchIntent.getIntExtra(LookTubeLaunchContract.EXTRA_TARGET_PAGE, -1)
            .takeIf { pageIndex -> pageIndex >= 0 }
            ?.let { pageIndex ->
                requestedPageState.value = pageIndex
            }
    }

    internal fun openVideoFromLaunch(videoId: String) {
        videoSelectionModeState.value = VideoSelectionMode.Preview
        repository.inspectVideo(videoId)
        requestedPageState.value = LookTubeLaunchContract.PLAYER_PAGE_INDEX
    }

    fun consumeRequestedPage(pageIndex: Int) {
        if (requestedPageState.value == pageIndex) {
            requestedPageState.value = null
        }
    }

    fun selectVideo(videoId: String) {
        videoSelectionModeState.value = VideoSelectionMode.Play
        repository.selectVideo(videoId)
        notePlaybackSelectionRequest()
    }

    fun inspectVideoInPlayer(videoId: String) {
        videoSelectionModeState.value = VideoSelectionMode.Preview
        repository.inspectVideo(videoId)
        requestedPageState.value = LookTubeLaunchContract.PLAYER_PAGE_INDEX
    }

    fun syncVideoWithPlaybackSession(videoId: String) {
        videoSelectionModeState.value = VideoSelectionMode.Passive
        repository.inspectVideo(videoId)
    }

    fun markVideoWatched(videoId: String) {
        repository.setManualWatchState(videoId, ManualWatchState.Watched)
    }

    fun markVideoUnwatched(videoId: String) {
        repository.setManualWatchState(videoId, ManualWatchState.Unwatched)
    }

    fun markVideosWatched(videoIds: List<String>) {
        videoIds.forEach(::markVideoWatched)
    }

    fun markVideosUnwatched(videoIds: List<String>) {
        videoIds.forEach(::markVideoUnwatched)
    }

    private fun notePlaybackSelectionRequest() {
        playbackSelectionRequestState.value = playbackSelectionRequestState.value + 1L
    }

    companion object {
        fun factory(repository: LookTubeRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LookTubeAppViewModel(repository) as T
                }
            }
    }
}
data class SelectedPlaybackTarget(
    val video: VideoSummary,
    val playbackProgress: PlaybackProgress?,
    val captionTrack: VideoCaptionTrack? = null,
)

enum class VideoSelectionMode {
    Play,
    Preview,
    Passive,
}
