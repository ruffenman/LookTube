package com.looktube.data

import com.looktube.database.InMemoryPlaybackBookmarkStore
import com.looktube.database.InMemoryVideoEngagementStore
import com.looktube.data.GeneratedCaptionDocument
import com.looktube.data.LocalCaptionEngineRegistry
import com.looktube.data.LocalCaptionGenerationRequest
import com.looktube.data.VideoCaptionStore
import com.looktube.model.DefaultLocalCaptionModel
import com.looktube.model.LocalCaptionModelState
import com.looktube.model.LocalCaptionEngine
import com.looktube.model.WhisperCppLocalCaptionEngine
import com.looktube.model.ManualWatchState
import com.looktube.model.PersistedFeedConfiguration
import com.looktube.model.PersistedLibrarySnapshot
import com.looktube.model.SyncPhase
import com.looktube.model.VideoCaptionData
import com.looktube.model.VideoCaptionTrack
import com.looktube.model.VideoSummary
import com.looktube.network.RssVideoFeedParser
import com.looktube.network.VideoFeedRequest
import com.looktube.network.VideoFeedService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigurableLookTubeRepositoryTest {
    @Test
    fun bootstrapSchedulesBackgroundRefreshWhenFeedUrlExists() = runTest {
        val scheduler = FakeLibraryRefreshScheduler()
        val repository = ConfigurableLookTubeRepository(
            feedConfigurationStore = FakeFeedConfigurationStore(
                PersistedFeedConfiguration(
                    feedUrl = "https://example.com/feed.xml",
                ),
            ),
            syncedLibraryStore = FakeSyncedLibraryStore(),
            playbackBookmarkStore = InMemoryPlaybackBookmarkStore(),
            videoEngagementStore = InMemoryVideoEngagementStore(),
            videoFeedService = FakeVideoFeedService(),
            libraryRefreshScheduler = scheduler,
        )

        repository.bootstrap()

        assertEquals(1, scheduler.scheduleCount)
        assertEquals(0, scheduler.cancelCount)
    }
    @Test
    fun bootstrapLoadsPersistedFeedUrlAndStartsWithEmptyLibraryUntilSync() = runTest {
        val store = FakeFeedConfigurationStore(
            PersistedFeedConfiguration(
                feedUrl = "https://example.com/feed.xml",
            ),
        )
        val repository = ConfigurableLookTubeRepository(
            feedConfigurationStore = store,
            syncedLibraryStore = FakeSyncedLibraryStore(),
            playbackBookmarkStore = InMemoryPlaybackBookmarkStore(),
            videoEngagementStore = InMemoryVideoEngagementStore(),
            videoFeedService = FakeVideoFeedService(),
        )

        repository.bootstrap()

        assertEquals("https://example.com/feed.xml", repository.feedConfiguration.value.feedUrl)
        assertTrue(repository.videos.value.isEmpty())
        assertEquals(SyncPhase.Idle, repository.librarySyncState.value.phase)
    }

    @Test
    fun refreshLoadsConfiguredFeedWhenUrlOnlyModeIsReady() = runTest {
        val store = FakeFeedConfigurationStore()
        val repository = ConfigurableLookTubeRepository(
            feedConfigurationStore = store,
            syncedLibraryStore = FakeSyncedLibraryStore(),
            playbackBookmarkStore = InMemoryPlaybackBookmarkStore(),
            videoEngagementStore = InMemoryVideoEngagementStore(),
            videoFeedService = FakeVideoFeedService(),
        )

        repository.bootstrap()
        repository.updateFeedUrl("https://example.com/premium.xml")
        repository.signInToPremiumFeed()

        assertEquals(SyncPhase.Success, repository.librarySyncState.value.phase)
        assertEquals(1, repository.videos.value.size)
        assertEquals("live-1", repository.videos.value.single().id)
        assertTrue(repository.accountSession.value.isSignedIn)
        assertEquals("Copied Premium feed", repository.accountSession.value.accountLabel)
    }

    @Test
    fun refreshPassesConfiguredFeedUrlToFeedService() = runTest {
        val store = FakeFeedConfigurationStore()
        val recordingService = RecordingVideoFeedService()
        val repository = ConfigurableLookTubeRepository(
            feedConfigurationStore = store,
            syncedLibraryStore = FakeSyncedLibraryStore(),
            playbackBookmarkStore = InMemoryPlaybackBookmarkStore(),
            videoEngagementStore = InMemoryVideoEngagementStore(),
            videoFeedService = recordingService,
        )

        repository.bootstrap()
        repository.updateFeedUrl("https://example.com/premium.xml")
        repository.signInToPremiumFeed()

        assertEquals("https://example.com/premium.xml", recordingService.lastRequest?.feedUrl)
    }

    @Test
    fun updateFeedUrlCancelsBackgroundRefreshWhenCleared() = runTest {
        val scheduler = FakeLibraryRefreshScheduler()
        val repository = ConfigurableLookTubeRepository(
            feedConfigurationStore = FakeFeedConfigurationStore(),
            syncedLibraryStore = FakeSyncedLibraryStore(),
            playbackBookmarkStore = InMemoryPlaybackBookmarkStore(),
            videoEngagementStore = InMemoryVideoEngagementStore(),
            videoFeedService = FakeVideoFeedService(),
            libraryRefreshScheduler = scheduler,
        )

        repository.bootstrap()
        repository.updateFeedUrl("https://example.com/premium.xml")
        repository.updateFeedUrl("")

        assertEquals(1, scheduler.scheduleCount)
        assertEquals(2, scheduler.cancelCount)
    }

    @Test
    fun clearSyncedDataKeepsSavedFeedUrlReadyForResync() = runTest {
        val store = FakeFeedConfigurationStore()
        val repository = ConfigurableLookTubeRepository(
            feedConfigurationStore = store,
            syncedLibraryStore = FakeSyncedLibraryStore(),
            playbackBookmarkStore = InMemoryPlaybackBookmarkStore(),
            videoEngagementStore = InMemoryVideoEngagementStore(),
            videoFeedService = FakeVideoFeedService(),
        )

        repository.bootstrap()
        repository.updateFeedUrl("https://example.com/premium.xml")
        repository.signInToPremiumFeed()
        repository.clearSyncedData()

        assertFalse(repository.accountSession.value.isSignedIn)
        assertEquals("https://example.com/premium.xml", repository.feedConfiguration.value.feedUrl)
        assertTrue(repository.videos.value.isEmpty())
        assertTrue(repository.librarySyncState.value.message.contains("Saved feed URL"))
    }

    @Test
    fun refreshStoresParsedPublishedDatesFromRealFeedShape() = runTest {
        val store = FakeFeedConfigurationStore()
        val syncedLibraryStore = FakeSyncedLibraryStore()
        val repository = ConfigurableLookTubeRepository(
            feedConfigurationStore = store,
            syncedLibraryStore = syncedLibraryStore,
            playbackBookmarkStore = InMemoryPlaybackBookmarkStore(),
            videoEngagementStore = InMemoryVideoEngagementStore(),
            videoFeedService = ParserBackedVideoFeedService(
                """
                    <rss version="2.0">
                        <channel>
                            <item>
                                <guid>video-newest</guid>
                                <title>Game Mess Mornings 3/23/26</title>
                                <description>Newest item.</description>
                                <category>Premium</category>
                                <pubDate>Mon, 23 Mar 2026 10:24:59 PST</pubDate>
                                <enclosure url="https://video.example.com/video-newest.mp4" />
                            </item>
                            <item>
                                <guid>video-older</guid>
                                <title>Game Mess Mornings 3/20/26</title>
                                <description>Older item.</description>
                                <category>Premium</category>
                                <pubDate>Fri, 20 Mar 2026 08:00:00 PST</pubDate>
                                <enclosure url="https://video.example.com/video-older.mp4" />
                            </item>
                        </channel>
                    </rss>
                """.trimIndent(),
            ),
        )

        repository.bootstrap()
        repository.updateFeedUrl("https://example.com/premium.xml")
        repository.signInToPremiumFeed()

        val savedSnapshot = syncedLibraryStore.persistedSnapshot.value

        assertEquals(SyncPhase.Success, repository.librarySyncState.value.phase)
        assertEquals(2, savedSnapshot?.videos?.size)
        assertTrue(savedSnapshot?.videos?.all { it.publishedAtEpochMillis != null } == true)
    }

    @Test
    fun selectionAndManualWatchStateUpdateEngagementAndClearWithSyncedData() = runTest {
        val repository = ConfigurableLookTubeRepository(
            feedConfigurationStore = FakeFeedConfigurationStore(),
            syncedLibraryStore = FakeSyncedLibraryStore(),
            playbackBookmarkStore = InMemoryPlaybackBookmarkStore(),
            videoEngagementStore = InMemoryVideoEngagementStore(),
            videoFeedService = FakeVideoFeedService(),
        )

        repository.bootstrap()
        repository.updateFeedUrl("https://example.com/premium.xml")
        repository.signInToPremiumFeed()
        repository.selectVideo("live-1")
        repository.setManualWatchState("live-1", ManualWatchState.Watched)

        assertEquals(
            ManualWatchState.Watched,
            repository.videoEngagement.value["live-1"]?.manualWatchState,
        )
        assertTrue(repository.videoEngagement.value["live-1"]?.lastPlayedAtEpochMillis != null)

        repository.clearSyncedData()

        assertTrue(repository.videoEngagement.value.isEmpty())
    }

    @Test
    fun inspectVideoDoesNotRecordPlaybackHistory() = runTest {
        val repository = ConfigurableLookTubeRepository(
            feedConfigurationStore = FakeFeedConfigurationStore(),
            syncedLibraryStore = FakeSyncedLibraryStore(),
            playbackBookmarkStore = InMemoryPlaybackBookmarkStore(),
            videoEngagementStore = InMemoryVideoEngagementStore(),
            videoFeedService = FakeVideoFeedService(),
        )

        repository.bootstrap()
        repository.updateFeedUrl("https://example.com/premium.xml")
        repository.signInToPremiumFeed()
        repository.inspectVideo("live-1")

        assertEquals("live-1", repository.selectedVideoId.value)
        assertTrue(repository.videoEngagement.value["live-1"]?.lastPlayedAtEpochMillis == null)
    }

    @Test
    fun noteAppOpenedAwardsOnePointOnlyOncePerLocalDay() = runTest {
        val store = FakeFeedConfigurationStore()
        val repository = ConfigurableLookTubeRepository(
            feedConfigurationStore = store,
            syncedLibraryStore = FakeSyncedLibraryStore(),
            playbackBookmarkStore = InMemoryPlaybackBookmarkStore(),
            videoEngagementStore = InMemoryVideoEngagementStore(),
            videoFeedService = FakeVideoFeedService(),
            currentTimeMillisProvider = { 1_744_761_600_000L },
        )

        repository.bootstrap()
        repository.noteAppOpened()
        repository.noteAppOpened()

        assertEquals(1, repository.feedConfiguration.value.dailyOpenPointCount)
        assertEquals(1_744_761_600_000L, repository.feedConfiguration.value.lastOpenedAtEpochMillis)
    }

    @Test
    fun consumeLaunchIntroMessageAdvancesDeckAndReshufflesAfterFullPass() = runTest {
        val repository = ConfigurableLookTubeRepository(
            feedConfigurationStore = FakeFeedConfigurationStore(
                PersistedFeedConfiguration(
                    feedUrl = "https://example.com/feed.xml",
                    launchIntroMessageDeckSeed = 17L,
                    launchIntroMessageDeckIndex = 0,
                ),
            ),
            syncedLibraryStore = FakeSyncedLibraryStore(),
            playbackBookmarkStore = InMemoryPlaybackBookmarkStore(),
            videoEngagementStore = InMemoryVideoEngagementStore(),
            videoFeedService = FakeVideoFeedService(),
            currentTimeMillisProvider = { 1_744_761_600_000L },
        )

        repository.bootstrap()
        repository.consumeLaunchIntroMessage(deckSize = 3)
        assertEquals(1, repository.feedConfiguration.value.launchIntroMessageDeckIndex)
        assertEquals(17L, repository.feedConfiguration.value.launchIntroMessageDeckSeed)

        repository.consumeLaunchIntroMessage(deckSize = 3)
        repository.consumeLaunchIntroMessage(deckSize = 3)

        assertEquals(0, repository.feedConfiguration.value.launchIntroMessageDeckIndex)
        assertEquals(1_744_761_600_000L, repository.feedConfiguration.value.launchIntroMessageDeckSeed)
    }

    @Test
    fun refreshAutoGeneratesCaptionsForNewVideosWhenEnabled() = runTest {
        val captionStore = RecordingVideoCaptionStore()
        val repository = ConfigurableLookTubeRepository(
            feedConfigurationStore = FakeFeedConfigurationStore(
                PersistedFeedConfiguration(
                    feedUrl = "https://example.com/feed.xml",
                    autoGenerateCaptionsForNewVideos = true,
                ),
            ),
            syncedLibraryStore = FakeSyncedLibraryStore(
                PersistedLibrarySnapshot(
                    feedUrl = "https://example.com/feed.xml",
                    videos = listOf(video(id = "live-app-1")),
                    lastSuccessfulSyncSummary = "Loaded 1 item.",
                ),
            ),
            playbackBookmarkStore = InMemoryPlaybackBookmarkStore(),
            videoEngagementStore = InMemoryVideoEngagementStore(),
            videoFeedService = MultiVideoFeedService(),
            localCaptionEngineRegistry = FakeLocalCaptionEngineRegistry(),
            videoCaptionStore = captionStore,
        )

        repository.bootstrap()
        repository.refreshLibrary()

        assertEquals(listOf("live-app-2"), captionStore.savedVideoIds)
    }

    @Test
    fun generateAndDeleteCaptionDataUpdatesTracksAndMetadata() = runTest {
        val captionStore = RecordingVideoCaptionStore()
        val captionDataStore = RecordingCaptionDataStore()
        val repository = ConfigurableLookTubeRepository(
            feedConfigurationStore = FakeFeedConfigurationStore(),
            syncedLibraryStore = FakeSyncedLibraryStore(),
            playbackBookmarkStore = InMemoryPlaybackBookmarkStore(),
            videoEngagementStore = InMemoryVideoEngagementStore(),
            videoFeedService = FakeVideoFeedService(),
            localCaptionEngineRegistry = FakeLocalCaptionEngineRegistry(),
            videoCaptionStore = captionStore,
            captionDataStore = captionDataStore,
        )

        repository.bootstrap()
        repository.updateFeedUrl("https://example.com/premium.xml")
        repository.signInToPremiumFeed()
        repository.generateCaptions("live-1")

        assertTrue(repository.videoCaptions.value.containsKey("live-1"))
        assertTrue(repository.captionData.value["live-1"]?.hasSavedCaptionTrack == true)

        repository.deleteCaptionData("live-1")

        assertFalse(repository.videoCaptions.value.containsKey("live-1"))
        assertFalse(repository.captionData.value.containsKey("live-1"))
        assertFalse(repository.captionGenerationStatus.value.containsKey("live-1"))
    }
}

private class FakeLocalCaptionEngineRegistry : LocalCaptionEngineRegistry {
    private val modelStateFlow = MutableStateFlow(
        LocalCaptionModelState(
            model = DefaultLocalCaptionModel,
            localPath = "D:/fake/model.bin",
        ),
    )

    override val availableEngines: StateFlow<List<LocalCaptionEngine>> =
        MutableStateFlow(listOf(WhisperCppLocalCaptionEngine)).asStateFlow()
    override val selectedEngine: StateFlow<LocalCaptionEngine> =
        MutableStateFlow(WhisperCppLocalCaptionEngine).asStateFlow()
    override val modelState: StateFlow<LocalCaptionModelState> = modelStateFlow.asStateFlow()

    override suspend fun downloadSelectedModel() = Unit

    override suspend fun generate(
        request: LocalCaptionGenerationRequest,
        onProgress: (com.looktube.model.CaptionGenerationStatus) -> Unit,
    ): GeneratedCaptionDocument = GeneratedCaptionDocument(
        webVtt = "WEBVTT\n",
        languageTag = "en-US",
        label = "English (generated)",
        engineId = WhisperCppLocalCaptionEngine.id,
    )

    override fun selectEngine(engineId: String) = Unit
}

private class RecordingVideoCaptionStore : VideoCaptionStore {
    private val state = MutableStateFlow(emptyMap<String, VideoCaptionTrack>())
    val savedVideoIds = mutableListOf<String>()

    override val captions: StateFlow<Map<String, VideoCaptionTrack>> = state.asStateFlow()

    override suspend fun saveGeneratedCaption(
        videoId: String,
        document: com.looktube.data.GeneratedCaptionDocument,
        generatedAtEpochMillis: Long,
    ): VideoCaptionTrack {
        savedVideoIds += videoId
        val track = VideoCaptionTrack(
            videoId = videoId,
            filePath = "D:/fake/$videoId.vtt",
            generatedAtEpochMillis = generatedAtEpochMillis,
            languageTag = document.languageTag,
            label = document.label,
            engineId = document.engineId,
        )
        state.update { existing -> existing + (videoId to track) }
        return track
    }

    override suspend fun delete(videoId: String) {
        state.update { existing -> existing - videoId }
        savedVideoIds.remove(videoId)
    }

    override suspend fun clear() {
        state.value = emptyMap()
        savedVideoIds.clear()
    }
}

private class RecordingCaptionDataStore : CaptionDataStore {
    private val state = MutableStateFlow(emptyMap<String, VideoCaptionData>())

    override val captionData: StateFlow<Map<String, VideoCaptionData>> = state.asStateFlow()

    override fun upsert(data: VideoCaptionData) {
        state.update { existing -> existing + (data.videoId to data) }
    }

    override fun remove(videoId: String) {
        state.update { existing -> existing - videoId }
    }

    override fun clear() {
        state.value = emptyMap()
    }
}

private fun video(id: String): VideoSummary = VideoSummary(
    id = id,
    title = "Video $id",
    description = "Description for $id",
    isPremium = true,
    feedCategory = "Premium",
    playbackUrl = "https://video.example.com/$id.mp4",
)

private class FakeLibraryRefreshScheduler : LibraryRefreshScheduler {
    var scheduleCount: Int = 0
    var cancelCount: Int = 0

    override fun schedule() {
        scheduleCount += 1
    }

    override fun cancel() {
        cancelCount += 1
    }
}

private class FakeFeedConfigurationStore(
    initialConfiguration: PersistedFeedConfiguration = PersistedFeedConfiguration(
        feedUrl = "",
    ),
) : FeedConfigurationStore {
    private val state = MutableStateFlow(initialConfiguration)

    override val persistedConfiguration: StateFlow<PersistedFeedConfiguration> = state.asStateFlow()

    override suspend fun save(configuration: PersistedFeedConfiguration) {
        state.value = configuration
    }
}

private class FakeSyncedLibraryStore(
    initialSnapshot: PersistedLibrarySnapshot? = null,
) : SyncedLibraryStore {
    private val state = MutableStateFlow<PersistedLibrarySnapshot?>(initialSnapshot)

    override val persistedSnapshot: StateFlow<PersistedLibrarySnapshot?> = state.asStateFlow()

    override suspend fun save(snapshot: PersistedLibrarySnapshot) {
        state.value = snapshot
    }

    override suspend fun clear() {
        state.value = null
    }
}

private class FakeVideoFeedService : VideoFeedService {
    override fun loadVideos(request: VideoFeedRequest): List<VideoSummary> {
        return listOf(
            VideoSummary(
                id = "live-1",
                title = "Live Premium Item",
                description = "Loaded from the configured fake service.",
                isPremium = true,
                feedCategory = "Premium",
                playbackUrl = "https://video.example.com/live-1.m3u8",
            ),
        )
    }
}

private class RecordingVideoFeedService : VideoFeedService {
    var lastRequest: VideoFeedRequest? = null

    override fun loadVideos(request: VideoFeedRequest): List<VideoSummary> {
        lastRequest = request
        return FakeVideoFeedService().loadVideos(request)
    }
}

private class MultiVideoFeedService : VideoFeedService {
    override fun loadVideos(request: VideoFeedRequest): List<VideoSummary> {
        return listOf(
            VideoSummary(
                id = "live-app-1",
                title = "App-level sync result",
                description = "Loaded via the fake app test service.",
                isPremium = true,
                feedCategory = "Premium",
                playbackUrl = "https://video.example.com/live-app-1.m3u8",
                seriesTitle = "Live Show",
            ),
            VideoSummary(
                id = "live-app-2",
                title = "App-level sync result two",
                description = "Loaded via the fake app test service.",
                isPremium = true,
                feedCategory = "Premium",
                playbackUrl = "https://video.example.com/live-app-2.m3u8",
                seriesTitle = "Live Show",
            ),
        )
    }
}

private class ParserBackedVideoFeedService(
    private val xml: String,
) : VideoFeedService {
    private val parser = RssVideoFeedParser()

    override fun loadVideos(request: VideoFeedRequest): List<VideoSummary> = parser.parse(xml)
}

