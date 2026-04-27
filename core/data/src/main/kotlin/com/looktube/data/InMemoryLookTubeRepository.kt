package com.looktube.data

import com.looktube.model.AccountSession
import com.looktube.model.CaptionGenerationStatus
import com.looktube.model.FeedConfiguration
import com.looktube.model.LibrarySyncState
import com.looktube.model.LocalCaptionEngine
import com.looktube.model.LocalCaptionModelState
import com.looktube.model.ManualWatchState
import com.looktube.model.PlaybackProgress
import com.looktube.model.SyncPhase
import com.looktube.model.VideoCaptionData
import com.looktube.model.VideoCaptionTrack
import com.looktube.model.VideoEngagementRecord
import com.looktube.model.VideoSummary
import com.looktube.model.WhisperCppLocalCaptionEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InMemoryLookTubeRepository : LookTubeRepository {
    private val accountSessionState = MutableStateFlow(
        AccountSession(
            isSignedIn = false,
            accountLabel = null,
            notes = "Feed-first playback spike pending validation against additional Giant Bomb Premium variants.",
        ),
    )
    private val feedConfigurationState = MutableStateFlow(
        FeedConfiguration(
            feedUrl = "",
        ),
    )
    private val syncState = MutableStateFlow(
        LibrarySyncState(
            phase = SyncPhase.Idle,
            message = "In-memory spike repository is active.",
        ),
    )
    private val videosState = MutableStateFlow(emptyList<VideoSummary>())
    private val selectedVideoIdState = MutableStateFlow<String?>(null)
    private val playbackProgressState = MutableStateFlow(emptyMap<String, PlaybackProgress>())
    private val videoEngagementState = MutableStateFlow(emptyMap<String, VideoEngagementRecord>())
    private val availableLocalCaptionEnginesState = MutableStateFlow(listOf(WhisperCppLocalCaptionEngine))
    private val selectedLocalCaptionEngineState = MutableStateFlow(WhisperCppLocalCaptionEngine)
    private val localCaptionModelStateFlow = MutableStateFlow(LocalCaptionModelState())
    private val videoCaptionsState = MutableStateFlow(emptyMap<String, VideoCaptionTrack>())
    private val captionDataState = MutableStateFlow(emptyMap<String, VideoCaptionData>())
    private val captionGenerationState = MutableStateFlow(emptyMap<String, CaptionGenerationStatus>())

    override val accountSession: StateFlow<AccountSession> = accountSessionState.asStateFlow()
    override val feedConfiguration: StateFlow<FeedConfiguration> = feedConfigurationState.asStateFlow()
    override val librarySyncState: StateFlow<LibrarySyncState> = syncState.asStateFlow()
    override val videos: StateFlow<List<VideoSummary>> = videosState.asStateFlow()
    override val selectedVideoId: StateFlow<String?> = selectedVideoIdState.asStateFlow()
    override val playbackProgress: StateFlow<Map<String, PlaybackProgress>> = playbackProgressState.asStateFlow()
    override val videoEngagement: StateFlow<Map<String, VideoEngagementRecord>> = videoEngagementState.asStateFlow()
    override val availableLocalCaptionEngines: StateFlow<List<LocalCaptionEngine>> = availableLocalCaptionEnginesState.asStateFlow()
    override val selectedLocalCaptionEngine: StateFlow<LocalCaptionEngine> = selectedLocalCaptionEngineState.asStateFlow()
    override val localCaptionModelState: StateFlow<LocalCaptionModelState> = localCaptionModelStateFlow.asStateFlow()
    override val videoCaptions: StateFlow<Map<String, VideoCaptionTrack>> = videoCaptionsState.asStateFlow()
    override val captionData: StateFlow<Map<String, VideoCaptionData>> = captionDataState.asStateFlow()
    override val captionGenerationStatus: StateFlow<Map<String, CaptionGenerationStatus>> = captionGenerationState.asStateFlow()

    override suspend fun bootstrap() {
        if (videosState.value.isNotEmpty()) {
            return
        }
        selectedVideoIdState.value = null
        playbackProgressState.value = emptyMap()
        videoEngagementState.value = emptyMap()
    }
    override suspend fun noteAppOpened() {
        feedConfigurationState.value = feedConfigurationState.value.copy(
            dailyOpenPointCount = feedConfigurationState.value.dailyOpenPointCount + 1,
            lastOpenedAtEpochMillis = System.currentTimeMillis(),
        )
    }

    override suspend fun consumeLaunchIntroMessage(deckSize: Int) {
        if (deckSize <= 0) {
            return
        }
        val nextIndex = feedConfigurationState.value.launchIntroMessageDeckIndex + 1
        feedConfigurationState.value = if (nextIndex >= deckSize) {
            feedConfigurationState.value.copy(
                launchIntroMessageDeckSeed = System.currentTimeMillis(),
                launchIntroMessageDeckIndex = 0,
            )
        } else {
            feedConfigurationState.value.copy(launchIntroMessageDeckIndex = nextIndex)
        }
    }

    override suspend fun updateFeedUrl(feedUrl: String) {
        feedConfigurationState.value = feedConfigurationState.value.copy(feedUrl = feedUrl)
    }
    override suspend fun updateAutoGenerateCaptionsForNewVideos(enabled: Boolean) {
        feedConfigurationState.value = feedConfigurationState.value.copy(
            autoGenerateCaptionsForNewVideos = enabled,
        )
    }
    override suspend fun signInToPremiumFeed() {
        accountSessionState.value = accountSessionState.value.copy(
            notes = "Spike feed-first Premium access first with copied feed URLs only.",
        )
        refreshLibrary()
    }

    override suspend fun clearSyncedData() {
        accountSessionState.value = accountSessionState.value.copy(
            isSignedIn = false,
            accountLabel = null,
            notes = "Cleared synced data in the in-memory spike repository.",
        )
        syncState.value = LibrarySyncState(
            phase = SyncPhase.Idle,
            message = "Cleared synced data. Sync again to load the library in this in-memory spike.",
        )
        videosState.value = emptyList()
        selectedVideoIdState.value = null
        playbackProgressState.value = emptyMap()
        videoEngagementState.value = emptyMap()
        videoCaptionsState.value = emptyMap()
        captionDataState.value = emptyMap()
        captionGenerationState.value = emptyMap()
    }

    override suspend fun downloadLocalCaptionModel() = Unit

    override suspend fun clearCaptionData() {
        videoCaptionsState.value = emptyMap()
        captionDataState.value = emptyMap()
        captionGenerationState.value = emptyMap()
    }

    override suspend fun refreshLibrary() {
        syncState.value = LibrarySyncState(
            phase = SyncPhase.Success,
            message = "In-memory repository does not fetch remote data; use the configurable repository for synced library behavior.",
            lastSuccessfulSyncSummary = "No synced library data is loaded in the in-memory spike.",
        )
    }

    override suspend fun generateCaptions(videoId: String) = Unit

    override suspend fun deleteCaptionData(videoId: String) {
        videoCaptionsState.value = videoCaptionsState.value.toMutableMap().apply {
            remove(videoId)
        }
        captionDataState.value = captionDataState.value.toMutableMap().apply {
            remove(videoId)
        }
        captionGenerationState.value = captionGenerationState.value.toMutableMap().apply {
            remove(videoId)
        }
    }

    override fun selectLocalCaptionEngine(engineId: String) {
        availableLocalCaptionEnginesState.value.firstOrNull { engine -> engine.id == engineId }
            ?.let { engine -> selectedLocalCaptionEngineState.value = engine }
    }

    override fun selectVideo(videoId: String) {
        selectedVideoIdState.value = videoId
        videoEngagementState.value = videoEngagementState.value.toMutableMap().apply {
            val existingRecord = get(videoId) ?: VideoEngagementRecord(videoId = videoId)
            put(videoId, existingRecord.copy(lastPlayedAtEpochMillis = System.currentTimeMillis()))
        }
    }

    override fun inspectVideo(videoId: String) {
        selectedVideoIdState.value = videoId
    }

    override fun setManualWatchState(videoId: String, manualWatchState: ManualWatchState) {
        videoEngagementState.value = videoEngagementState.value.toMutableMap().apply {
            val existingRecord = get(videoId) ?: VideoEngagementRecord(videoId = videoId)
            put(
                videoId,
                existingRecord.copy(
                    completedAtEpochMillis = if (manualWatchState == ManualWatchState.Watched) {
                        existingRecord.completedAtEpochMillis ?: System.currentTimeMillis()
                    } else {
                        null
                    },
                    manualWatchState = manualWatchState,
                ),
            )
        }
    }
}
