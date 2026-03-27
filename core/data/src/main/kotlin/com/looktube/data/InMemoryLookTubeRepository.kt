package com.looktube.data

import com.looktube.model.AccountSession
import com.looktube.model.FeedConfiguration
import com.looktube.model.LibrarySyncState
import com.looktube.model.ManualWatchState
import com.looktube.model.PlaybackProgress
import com.looktube.model.SyncPhase
import com.looktube.model.VideoEngagementRecord
import com.looktube.model.VideoSummary
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

    override val accountSession: StateFlow<AccountSession> = accountSessionState.asStateFlow()
    override val feedConfiguration: StateFlow<FeedConfiguration> = feedConfigurationState.asStateFlow()
    override val librarySyncState: StateFlow<LibrarySyncState> = syncState.asStateFlow()
    override val videos: StateFlow<List<VideoSummary>> = videosState.asStateFlow()
    override val selectedVideoId: StateFlow<String?> = selectedVideoIdState.asStateFlow()
    override val playbackProgress: StateFlow<Map<String, PlaybackProgress>> = playbackProgressState.asStateFlow()
    override val videoEngagement: StateFlow<Map<String, VideoEngagementRecord>> = videoEngagementState.asStateFlow()

    override suspend fun bootstrap() {
        if (videosState.value.isNotEmpty()) {
            return
        }

        val seededVideos = ConfigurableLookTubeRepository.seededVideos

        videosState.value = seededVideos
        selectedVideoIdState.value = null
        playbackProgressState.value = emptyMap()
        videoEngagementState.value = emptyMap()
    }

    override suspend fun updateFeedUrl(feedUrl: String) {
        feedConfigurationState.value = feedConfigurationState.value.copy(feedUrl = feedUrl)
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
            message = "Cleared synced data. Seeded content is active until the next refresh.",
        )
        videosState.value = ConfigurableLookTubeRepository.seededVideos
        selectedVideoIdState.value = null
        playbackProgressState.value = emptyMap()
        videoEngagementState.value = emptyMap()
    }

    override suspend fun refreshLibrary() {
        syncState.value = LibrarySyncState(
            phase = SyncPhase.Success,
            message = "In-memory repository does not require refresh; seeded content remains active.",
            lastSuccessfulSyncSummary = "Seeded content is active.",
        )
    }

    override fun selectVideo(videoId: String) {
        selectedVideoIdState.value = videoId
        videoEngagementState.value = videoEngagementState.value.toMutableMap().apply {
            val existingRecord = get(videoId) ?: VideoEngagementRecord(videoId = videoId)
            put(videoId, existingRecord.copy(lastPlayedAtEpochMillis = System.currentTimeMillis()))
        }
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
