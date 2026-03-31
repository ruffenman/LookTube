package com.looktube.data

import com.looktube.database.PlaybackBookmarkStore
import com.looktube.database.VideoEngagementStore

import com.looktube.model.AccountSession
import com.looktube.model.CaptionGenerationPhase
import com.looktube.model.CaptionGenerationStatus
import com.looktube.model.FeedConfiguration
import com.looktube.model.LibrarySyncState
import com.looktube.model.LocalCaptionEngine
import com.looktube.model.LocalCaptionModelState
import com.looktube.model.ManualWatchState
import com.looktube.model.PersistedFeedConfiguration
import com.looktube.model.PersistedLibrarySnapshot
import com.looktube.model.PlaybackProgress
import com.looktube.model.SyncPhase
import com.looktube.model.VideoCaptionTrack
import com.looktube.model.VideoEngagementRecord
import com.looktube.model.VideoSummary
import com.looktube.model.toRuntime
import com.looktube.network.FeedSyncException
import com.looktube.network.VideoFeedRequest
import com.looktube.network.VideoFeedService
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class ConfigurableLookTubeRepository(
    private val feedConfigurationStore: FeedConfigurationStore,
    private val syncedLibraryStore: SyncedLibraryStore,
    private val playbackBookmarkStore: PlaybackBookmarkStore,
    private val videoEngagementStore: VideoEngagementStore,
    private val videoFeedService: VideoFeedService,
    private val libraryRefreshScheduler: LibraryRefreshScheduler = NoOpLibraryRefreshScheduler,
    private val localCaptionEngineRegistry: LocalCaptionEngineRegistry = NoOpLocalCaptionEngineRegistry,
    private val videoCaptionStore: VideoCaptionStore = NoOpVideoCaptionStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val currentTimeMillisProvider: () -> Long = System::currentTimeMillis,
) : LookTubeRepository {
    private val accountSessionState = MutableStateFlow(
        AccountSession(
            isSignedIn = false,
            accountLabel = null,
            notes = "Paste a Giant Bomb Premium RSS URL to load your library.",
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
            message = "Paste a Giant Bomb Premium RSS URL copied from the feeds page, then sync.",
        ),
    )
    private val videosState = MutableStateFlow(emptyList<VideoSummary>())
    private val selectedVideoIdState = MutableStateFlow<String?>(null)
    private val captionGenerationState = MutableStateFlow(emptyMap<String, CaptionGenerationStatus>())
    private var hasSuccessfulFeedSync = false

    override val accountSession: StateFlow<AccountSession> = accountSessionState.asStateFlow()
    override val feedConfiguration: StateFlow<FeedConfiguration> = feedConfigurationState.asStateFlow()
    override val librarySyncState: StateFlow<LibrarySyncState> = syncState.asStateFlow()
    override val videos: StateFlow<List<VideoSummary>> = videosState.asStateFlow()
    override val selectedVideoId: StateFlow<String?> = selectedVideoIdState.asStateFlow()
    override val playbackProgress: StateFlow<Map<String, PlaybackProgress>> = playbackBookmarkStore.progressSnapshots
    override val videoEngagement: StateFlow<Map<String, VideoEngagementRecord>> = videoEngagementStore.engagementRecords
    override val availableLocalCaptionEngines: StateFlow<List<LocalCaptionEngine>> = localCaptionEngineRegistry.availableEngines
    override val selectedLocalCaptionEngine: StateFlow<LocalCaptionEngine> = localCaptionEngineRegistry.selectedEngine
    override val localCaptionModelState: StateFlow<LocalCaptionModelState> = localCaptionEngineRegistry.modelState
    override val videoCaptions: StateFlow<Map<String, VideoCaptionTrack>> = videoCaptionStore.captions
    override val captionGenerationStatus: StateFlow<Map<String, CaptionGenerationStatus>> = captionGenerationState.asStateFlow()

    override suspend fun bootstrap() {
        if (videosState.value.isNotEmpty()) {
            return
        }

        val persistedConfiguration = feedConfigurationStore.persistedConfiguration.value
        feedConfigurationState.value = persistedConfiguration.toRuntime()
        updateBackgroundRefresh(feedConfigurationState.value.feedUrl)
        val persistedSnapshot = syncedLibraryStore.persistedSnapshot.value
        if (
            persistedSnapshot != null &&
            persistedSnapshot.feedUrl.isNotBlank() &&
            persistedSnapshot.feedUrl == feedConfigurationState.value.feedUrl &&
            persistedSnapshot.videos.isNotEmpty()
        ) {
            hasSuccessfulFeedSync = true
            videosState.value = persistedSnapshot.videos
            selectedVideoIdState.value = null
            publishStatus(
                LibrarySyncState(
                    phase = SyncPhase.Success,
                    message = "Loaded ${persistedSnapshot.videos.size} synced videos from local cache. Background refresh stays active while this feed URL is saved.",
                    lastSuccessfulSyncSummary = persistedSnapshot.lastSuccessfulSyncSummary
                        ?: "Cached Premium feed with ${persistedSnapshot.videos.size} items.",
                ),
            )
        } else {
            videosState.value = emptyList()
            selectedVideoIdState.value = null
            publishStatus(initialStatusFor(feedConfigurationState.value))
        }
    }

    override suspend fun downloadLocalCaptionModel() {
        localCaptionEngineRegistry.downloadSelectedModel()
    }

    override suspend fun noteAppOpened() {
        val persistedConfiguration = feedConfigurationStore.persistedConfiguration.value
        val currentEpochDay = Instant.ofEpochMilli(currentTimeMillisProvider())
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toEpochDay()
        if (persistedConfiguration.lastOpenedLocalEpochDay == currentEpochDay) {
            return
        }
        val updatedConfiguration = persistedConfiguration.copy(
            dailyOpenPointCount = persistedConfiguration.dailyOpenPointCount + 1,
            lastOpenedLocalEpochDay = currentEpochDay,
        )
        feedConfigurationStore.save(updatedConfiguration)
        feedConfigurationState.value = updatedConfiguration.toRuntime()
    }

    private fun statusAfterConfigurationChange(configuration: FeedConfiguration): LibrarySyncState =
        if (hasSuccessfulFeedSync && videosState.value.isNotEmpty()) {
            LibrarySyncState(
                phase = SyncPhase.Idle,
                message = "Synced library is saved on this device. Sync again to refresh, or clear synced data to remove it.",
                lastSuccessfulSyncSummary = syncState.value.lastSuccessfulSyncSummary,
            )
        } else {
            initialStatusFor(configuration)
        }

    override suspend fun updateFeedUrl(feedUrl: String) {
        val updatedConfiguration = feedConfigurationStore.persistedConfiguration.value.copy(feedUrl = feedUrl)
        feedConfigurationStore.save(updatedConfiguration)
        feedConfigurationState.value = updatedConfiguration.toRuntime()
        updateBackgroundRefresh(feedUrl)
        publishStatus(statusAfterConfigurationChange(feedConfigurationState.value))
    }

    override suspend fun updateAutoGenerateCaptionsForNewVideos(enabled: Boolean) {
        val updatedConfiguration = feedConfigurationStore.persistedConfiguration.value.copy(
            autoGenerateCaptionsForNewVideos = enabled,
        )
        feedConfigurationStore.save(updatedConfiguration)
        feedConfigurationState.value = updatedConfiguration.toRuntime()
    }
    override suspend fun signInToPremiumFeed() {
        hasSuccessfulFeedSync = false
        updateBackgroundRefresh(feedConfigurationState.value.feedUrl)
        refreshLibrary()
    }
    override suspend fun clearSyncedData() {
        hasSuccessfulFeedSync = false
        syncedLibraryStore.clear()
        playbackBookmarkStore.clear()
        videoEngagementStore.clear()
        videoCaptionStore.clear()
        videosState.value = emptyList()
        selectedVideoIdState.value = null
        captionGenerationState.value = emptyMap()
        publishStatus(
            LibrarySyncState(
                phase = SyncPhase.Idle,
                message = "Cleared synced library data. Saved feed URL is still available, and background refresh will use it the next time sync runs.",
                lastSuccessfulSyncSummary = null,
            ),
        )
    }

    override suspend fun refreshLibrary() {
        val configuration = feedConfigurationState.value
        val previousSnapshot = syncedLibraryStore.persistedSnapshot.value
        when {
            configuration.feedUrl.isBlank() -> {
                publishStatus(
                    LibrarySyncState(
                        phase = SyncPhase.Error,
                        message = "Enter a feed URL before syncing the library.",
                        lastSuccessfulSyncSummary = syncState.value.lastSuccessfulSyncSummary,
                    ),
                )
                return
            }
        }

        publishStatus(
            LibrarySyncState(
                phase = SyncPhase.Refreshing,
                message = "Syncing the configured Premium feed URL...",
                lastSuccessfulSyncSummary = syncState.value.lastSuccessfulSyncSummary,
            ),
        )

        try {
            val syncedVideos = withContext(ioDispatcher) {
                videoFeedService.loadVideos(
                    VideoFeedRequest(
                        feedUrl = configuration.feedUrl,
                    ),
                )
            }
            if (syncedVideos.isEmpty()) {
                publishStatus(
                    LibrarySyncState(
                        phase = SyncPhase.Error,
                        message = "The configured feed loaded successfully but returned no video items.",
                        lastSuccessfulSyncSummary = syncState.value.lastSuccessfulSyncSummary,
                    ),
                )
                return
            }

            videosState.value = syncedVideos
            if (selectedVideoIdState.value !in syncedVideos.map(VideoSummary::id)) {
                selectedVideoIdState.value = null
            }
            hasSuccessfulFeedSync = true
            val syncSummary = "Premium feed sync loaded ${syncedVideos.size} items."
            val latestSnapshot = PersistedLibrarySnapshot(
                feedUrl = configuration.feedUrl,
                videos = syncedVideos,
                lastSuccessfulSyncSummary = syncSummary,
            )
            syncedLibraryStore.save(latestSnapshot)
            publishStatus(
                LibrarySyncState(
                    phase = SyncPhase.Success,
                    message = "Synced ${syncedVideos.size} videos from the configured Premium feed. Background refresh will keep checking for new releases.",
                    lastSuccessfulSyncSummary = syncSummary,
                ),
            )
            autoGenerateCaptionsForNewVideos(
                newVideos = latestSnapshot.newVideosComparedTo(previousSnapshot),
                autoGenerateEnabled = configuration.autoGenerateCaptionsForNewVideos,
            )
        } catch (exception: FeedSyncException) {
            publishStatus(
                LibrarySyncState(
                    phase = SyncPhase.Error,
                    message = "Feed sync failed: ${exception.message.orEmpty().take(180)}",
                    lastSuccessfulSyncSummary = syncState.value.lastSuccessfulSyncSummary,
                ),
            )
        }
    }

    override suspend fun generateCaptions(videoId: String) {
        val video = videosState.value.firstOrNull { candidate -> candidate.id == videoId }
        if (video == null) {
            publishCaptionGenerationStatus(
                videoId = videoId,
                status = CaptionGenerationStatus(
                    phase = CaptionGenerationPhase.Error,
                    message = "Caption generation needs a selected video from the current library.",
                ),
            )
            return
        }
        val playbackUrl = video.playbackUrl
        if (playbackUrl.isNullOrBlank()) {
            publishCaptionGenerationStatus(
                videoId = videoId,
                status = CaptionGenerationStatus(
                    phase = CaptionGenerationPhase.Error,
                    message = "This item does not expose a playable URL for local caption extraction.",
                ),
            )
            return
        }
        val modelPath = localCaptionEngineRegistry.modelState.value.localPath
        if (modelPath.isNullOrBlank()) {
            publishCaptionGenerationStatus(
                videoId = videoId,
                status = CaptionGenerationStatus(
                    phase = CaptionGenerationPhase.Error,
                    message = "Download the offline caption model from Settings before generating captions.",
                ),
            )
            return
        }
        try {
            publishCaptionGenerationStatus(
                videoId = videoId,
                status = CaptionGenerationStatus(
                    phase = CaptionGenerationPhase.ExtractingAudio,
                    message = "Preparing audio for offline caption generation…",
                ),
            )
            val document = withContext(ioDispatcher) {
                localCaptionEngineRegistry.generate(
                    request = LocalCaptionGenerationRequest(
                        videoId = videoId,
                        playbackUrl = playbackUrl,
                        modelPath = modelPath,
                    ),
                    onProgress = { status ->
                        publishCaptionGenerationStatus(
                            videoId = videoId,
                            status = status,
                        )
                    },
                )
            }
            publishCaptionGenerationStatus(
                videoId = videoId,
                status = CaptionGenerationStatus(
                    phase = CaptionGenerationPhase.Saving,
                    message = "Saving generated captions on this device…",
                    progressFraction = 0.98f,
                ),
            )
            videoCaptionStore.saveGeneratedCaption(
                videoId = videoId,
                document = document,
            )
            publishCaptionGenerationStatus(
                videoId = videoId,
                status = CaptionGenerationStatus(
                    phase = CaptionGenerationPhase.Completed,
                    message = "Generated captions are ready on this device.",
                    progressFraction = 1f,
                ),
            )
        } catch (exception: Exception) {
            publishCaptionGenerationStatus(
                videoId = videoId,
                status = CaptionGenerationStatus(
                    phase = CaptionGenerationPhase.Error,
                    message = "Caption generation failed: ${exception.message.orEmpty().take(180)}",
                ),
            )
        }
    }

    override fun selectLocalCaptionEngine(engineId: String) {
        localCaptionEngineRegistry.selectEngine(engineId)
    }

    override fun selectVideo(videoId: String) {
        selectedVideoIdState.value = videoId
        videoEngagementStore.recordPlayback(videoId)
    }

    override fun setManualWatchState(videoId: String, manualWatchState: ManualWatchState) {
        videoEngagementStore.setManualWatchState(
            videoId = videoId,
            manualWatchState = manualWatchState,
        )
    }

    private fun publishStatus(status: LibrarySyncState) {
        syncState.value = status
        val configuration = feedConfigurationState.value
        accountSessionState.value = AccountSession(
            isSignedIn = hasSuccessfulFeedSync,
            accountLabel = configuration.feedUrl.takeIf(String::isNotBlank)?.let { "Copied Premium feed" },
            notes = status.message,
        )
    }

    private fun initialStatusFor(configuration: FeedConfiguration): LibrarySyncState =
        when {
            configuration.feedUrl.isBlank() -> LibrarySyncState(
                phase = SyncPhase.Idle,
                message = "Paste a Giant Bomb Premium RSS URL copied from the feeds page, then sync to load your library.",
                lastSuccessfulSyncSummary = syncState.value.lastSuccessfulSyncSummary,
            )
            else -> LibrarySyncState(
                phase = SyncPhase.Idle,
                message = "Saved feed URL detected. Sync once to load your library, then background refresh will keep it current.",
                lastSuccessfulSyncSummary = syncState.value.lastSuccessfulSyncSummary,
            )
        }

    private fun updateBackgroundRefresh(feedUrl: String) {
        if (feedUrl.isBlank()) {
            libraryRefreshScheduler.cancel()
        } else {
            libraryRefreshScheduler.schedule()
        }
    }

    private fun publishCaptionGenerationStatus(
        videoId: String,
        status: CaptionGenerationStatus,
    ) {
        captionGenerationState.value = captionGenerationState.value.toMutableMap().apply {
            put(videoId, status)
        }
    }

    private suspend fun autoGenerateCaptionsForNewVideos(
        newVideos: List<VideoSummary>,
        autoGenerateEnabled: Boolean,
    ) {
        if (!autoGenerateEnabled || newVideos.isEmpty() || !localCaptionEngineRegistry.modelState.value.isReady) {
            return
        }
        val existingCaptionIds = videoCaptionStore.captions.value.keys
        newVideos
            .filter { video -> !video.playbackUrl.isNullOrBlank() && video.id !in existingCaptionIds }
            .forEach { video ->
                runCatching { generateCaptions(video.id) }
            }
    }
}

private fun PersistedLibrarySnapshot.newVideosComparedTo(
    previousSnapshot: PersistedLibrarySnapshot?,
): List<VideoSummary> {
    if (previousSnapshot == null || previousSnapshot.feedUrl != feedUrl || previousSnapshot.videos.isEmpty()) {
        return emptyList()
    }
    val previousIds = previousSnapshot.videos.asSequence().map(VideoSummary::id).toSet()
    return videos.filterNot { video -> video.id in previousIds }
}

interface FeedConfigurationStore {
    val persistedConfiguration: StateFlow<PersistedFeedConfiguration>
    suspend fun save(configuration: PersistedFeedConfiguration)
}
