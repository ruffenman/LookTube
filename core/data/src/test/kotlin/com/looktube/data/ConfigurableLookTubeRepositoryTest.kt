package com.looktube.data

import com.looktube.database.InMemoryPlaybackBookmarkStore
import com.looktube.model.AuthMode
import com.looktube.model.PersistedFeedConfiguration
import com.looktube.model.PersistedLibrarySnapshot
import com.looktube.model.SyncPhase
import com.looktube.model.VideoSummary
import com.looktube.network.VideoFeedRequest
import com.looktube.network.VideoFeedService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigurableLookTubeRepositoryTest {
    @Test
    fun bootstrapLoadsPersistedSettingsAndKeepsSeededFallback() = runTest {
        val store = FakeFeedConfigurationStore(
            PersistedFeedConfiguration(
                authMode = AuthMode.CredentialedFeed,
                feedUrl = "https://example.com/feed.xml",
                username = "jorge",
            ),
        )
        val repository = ConfigurableLookTubeRepository(
            feedConfigurationStore = store,
            syncedLibraryStore = FakeSyncedLibraryStore(),
            playbackBookmarkStore = InMemoryPlaybackBookmarkStore(),
            videoFeedService = FakeVideoFeedService(),
        )

        repository.bootstrap()

        assertEquals("https://example.com/feed.xml", repository.feedConfiguration.value.feedUrl)
        assertEquals("jorge", repository.feedConfiguration.value.username)
        assertTrue(repository.videos.value.isNotEmpty())
        assertEquals(SyncPhase.Idle, repository.librarySyncState.value.phase)
    }

    @Test
    fun refreshLoadsConfiguredFeedWhenUrlOnlyModeIsReady() = runTest {
        val store = FakeFeedConfigurationStore()
        val repository = ConfigurableLookTubeRepository(
            feedConfigurationStore = store,
            syncedLibraryStore = FakeSyncedLibraryStore(),
            playbackBookmarkStore = InMemoryPlaybackBookmarkStore(),
            videoFeedService = FakeVideoFeedService(),
        )

        repository.bootstrap()
        repository.selectAuthMode(AuthMode.CredentialedFeed)
        repository.updateFeedUrl("https://example.com/premium.xml")
        repository.refreshLibrary()

        assertEquals(SyncPhase.Success, repository.librarySyncState.value.phase)
        assertEquals(1, repository.videos.value.size)
        assertEquals("live-1", repository.videos.value.single().id)
        assertTrue(repository.accountSession.value.isSignedIn)
    }

    @Test
    fun refreshStillPassesOptionalCredentialsToFeedService() = runTest {
        val store = FakeFeedConfigurationStore()
        val recordingService = RecordingVideoFeedService()
        val repository = ConfigurableLookTubeRepository(
            feedConfigurationStore = store,
            syncedLibraryStore = FakeSyncedLibraryStore(),
            playbackBookmarkStore = InMemoryPlaybackBookmarkStore(),
            videoFeedService = recordingService,
        )

        repository.bootstrap()
        repository.selectAuthMode(AuthMode.CredentialedFeed)
        repository.updateFeedUrl("https://example.com/premium.xml")
        repository.updateUsername("jorge")
        repository.updatePassword("session-secret")
        repository.refreshLibrary()

        assertEquals("https://example.com/premium.xml", recordingService.lastRequest?.feedUrl)
        assertEquals("jorge", recordingService.lastRequest?.username)
        assertEquals("session-secret", recordingService.lastRequest?.password)
    }

    @Test
    fun refreshSurfacesUnsupportedSessionCookieMode() = runTest {
        val repository = ConfigurableLookTubeRepository(
            feedConfigurationStore = FakeFeedConfigurationStore(),
            syncedLibraryStore = FakeSyncedLibraryStore(),
            playbackBookmarkStore = InMemoryPlaybackBookmarkStore(),
            videoFeedService = FakeVideoFeedService(),
        )

        repository.bootstrap()
        repository.selectAuthMode(AuthMode.SessionCookie)
        repository.refreshLibrary()

        assertEquals(SyncPhase.Error, repository.librarySyncState.value.phase)
        assertTrue(repository.librarySyncState.value.message.contains("not implemented"))
    }
}

private class FakeFeedConfigurationStore(
    initialConfiguration: PersistedFeedConfiguration = PersistedFeedConfiguration(
        authMode = null,
        feedUrl = "",
        username = "",
    ),
) : FeedConfigurationStore {
    private val state = MutableStateFlow(initialConfiguration)

    override val persistedConfiguration: StateFlow<PersistedFeedConfiguration> = state.asStateFlow()

    override suspend fun setAuthMode(mode: AuthMode?) {
        state.value = state.value.copy(authMode = mode)
    }

    override suspend fun setFeedUrl(feedUrl: String) {
        state.value = state.value.copy(feedUrl = feedUrl)
    }

    override suspend fun setUsername(username: String) {
        state.value = state.value.copy(username = username)
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
