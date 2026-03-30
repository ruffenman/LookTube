package com.looktube.data

import com.looktube.database.InMemoryPlaybackBookmarkStore
import com.looktube.database.InMemoryVideoEngagementStore
import com.looktube.model.ManualWatchState
import com.looktube.model.PersistedFeedConfiguration
import com.looktube.model.PersistedLibrarySnapshot
import com.looktube.model.SyncPhase
import com.looktube.model.VideoSummary
import com.looktube.network.RssVideoFeedParser
import com.looktube.network.VideoFeedRequest
import com.looktube.network.VideoFeedService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
}

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

    override suspend fun setFeedUrl(feedUrl: String) {
        state.value = state.value.copy(feedUrl = feedUrl)
    }
}

private class FakeSyncedLibraryStore : SyncedLibraryStore {
    private val state = MutableStateFlow<PersistedLibrarySnapshot?>(null)

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

private class ParserBackedVideoFeedService(
    private val xml: String,
) : VideoFeedService {
    private val parser = RssVideoFeedParser()

    override fun loadVideos(request: VideoFeedRequest): List<VideoSummary> = parser.parse(xml)
}
