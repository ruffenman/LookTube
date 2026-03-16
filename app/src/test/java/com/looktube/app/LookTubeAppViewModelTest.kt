package com.looktube.app

import com.looktube.data.ConfigurableLookTubeRepository
import com.looktube.data.FeedConfigurationStore
import com.looktube.data.SyncedLibraryStore
import com.looktube.database.InMemoryPlaybackBookmarkStore
import com.looktube.model.PersistedFeedConfiguration
import com.looktube.model.PersistedLibrarySnapshot
import com.looktube.model.SyncPhase
import com.looktube.model.VideoSummary
import com.looktube.network.VideoFeedRequest
import com.looktube.network.VideoFeedService
import com.looktube.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LookTubeAppViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun bootstrapsSampleFeedWithoutTreatingItAsSignedIn() = runTest {
        val repository = ConfigurableLookTubeRepository(
            feedConfigurationStore = FakeFeedConfigurationStore(),
            syncedLibraryStore = FakeSyncedLibraryStore(),
            playbackBookmarkStore = InMemoryPlaybackBookmarkStore(),
            videoFeedService = FakeVideoFeedService(),
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val viewModel = LookTubeAppViewModel(repository)
        advanceUntilIdle()

        assertTrue(viewModel.videos.value.isNotEmpty())
        assertFalse(viewModel.accountSession.value.isSignedIn)
    }

    @Test
    fun refreshesConfiguredFeedThroughViewModel() = runTest {
        val repository = ConfigurableLookTubeRepository(
            feedConfigurationStore = FakeFeedConfigurationStore(),
            syncedLibraryStore = FakeSyncedLibraryStore(),
            playbackBookmarkStore = InMemoryPlaybackBookmarkStore(),
            videoFeedService = FakeVideoFeedService(),
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val viewModel = LookTubeAppViewModel(repository)
        advanceUntilIdle()

        viewModel.updateFeedUrl("https://example.com/feed.xml")
        viewModel.signInToPremiumFeed()
        advanceUntilIdle()

        assertEquals(SyncPhase.Success, viewModel.librarySyncState.value.phase)
        assertEquals("live-app-1", viewModel.videos.value.single().id)
    }

    @Test
    fun clearSyncedDataKeepsSavedFeedUrlReadyForResync() = runTest {
        val repository = ConfigurableLookTubeRepository(
            feedConfigurationStore = FakeFeedConfigurationStore(),
            syncedLibraryStore = FakeSyncedLibraryStore(),
            playbackBookmarkStore = InMemoryPlaybackBookmarkStore(),
            videoFeedService = FakeVideoFeedService(),
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val viewModel = LookTubeAppViewModel(repository)
        advanceUntilIdle()

        viewModel.updateFeedUrl("https://example.com/feed.xml")
        viewModel.signInToPremiumFeed()
        advanceUntilIdle()

        viewModel.clearSyncedData()
        advanceUntilIdle()

        assertFalse(viewModel.accountSession.value.isSignedIn)
        assertEquals("https://example.com/feed.xml", viewModel.feedConfiguration.value.feedUrl)
        assertEquals("premium-quick-look-1", viewModel.videos.value.first().id)
        assertTrue(viewModel.librarySyncState.value.message.contains("Saved feed URL"))
    }
}

private class FakeFeedConfigurationStore : FeedConfigurationStore {
    private val state = MutableStateFlow(
        PersistedFeedConfiguration(
            feedUrl = "",
        ),
    )

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
                id = "live-app-1",
                title = "App-level sync result",
                description = "Loaded via the fake app test service.",
                isPremium = true,
                feedCategory = "Premium",
                playbackUrl = "https://video.example.com/live-app-1.m3u8",
            ),
        )
    }
}
