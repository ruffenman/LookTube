package com.looktube.data

import com.looktube.database.InMemoryPlaybackBookmarkStore
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigurableLookTubeRepositoryTest {
    @Test
    fun bootstrapLoadsPersistedSettingsAndKeepsSeededFallback() = runTest {
        val store = FakeFeedConfigurationStore(
            PersistedFeedConfiguration(
                feedUrl = "https://example.com/feed.xml",
                username = "jorge",
                rememberedPassword = "remembered-secret",
                rememberPassword = true,
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
        assertEquals("remembered-secret", repository.feedConfiguration.value.password)
        assertTrue(repository.feedConfiguration.value.rememberPassword)
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
        repository.updateFeedUrl("https://example.com/premium.xml")
        repository.signInToPremiumFeed()

        assertEquals(SyncPhase.Success, repository.librarySyncState.value.phase)
        assertEquals(1, repository.videos.value.size)
        assertEquals("live-1", repository.videos.value.single().id)
        assertTrue(repository.accountSession.value.isSignedIn)
        assertEquals("Copied Premium feed", repository.accountSession.value.accountLabel)
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
        repository.updateFeedUrl("https://example.com/premium.xml")
        repository.updateUsername("jorge")
        repository.setRememberPassword(true)
        repository.updatePassword("session-secret")
        repository.signInToPremiumFeed()

        assertEquals("https://example.com/premium.xml", recordingService.lastRequest?.feedUrl)
        assertEquals("jorge", recordingService.lastRequest?.username)
        assertEquals("session-secret", recordingService.lastRequest?.password)
        assertEquals("session-secret", store.persistedConfiguration.value.rememberedPassword)
        assertTrue(store.persistedConfiguration.value.rememberPassword)
    }

    @Test
    fun clearSyncedDataKeepsSavedCredentialsReadyForResync() = runTest {
        val store = FakeFeedConfigurationStore()
        val repository = ConfigurableLookTubeRepository(
            feedConfigurationStore = store,
            syncedLibraryStore = FakeSyncedLibraryStore(),
            playbackBookmarkStore = InMemoryPlaybackBookmarkStore(),
            videoFeedService = FakeVideoFeedService(),
        )

        repository.bootstrap()
        repository.updateFeedUrl("https://example.com/premium.xml")
        repository.updateUsername("jorge")
        repository.setRememberPassword(true)
        repository.updatePassword("remembered-secret")
        repository.signInToPremiumFeed()
        repository.clearSyncedData()

        assertFalse(repository.accountSession.value.isSignedIn)
        assertEquals("jorge", repository.feedConfiguration.value.username)
        assertEquals("remembered-secret", repository.feedConfiguration.value.password)
        assertTrue(repository.feedConfiguration.value.rememberPassword)
        assertEquals("premium-quick-look-1", repository.videos.value.first().id)
        assertTrue(repository.librarySyncState.value.message.contains("optional fallback details"))
    }

    @Test
    fun forgetSavedCredentialsClearsPersistedAuthInputs() = runTest {
        val store = FakeFeedConfigurationStore()
        val repository = ConfigurableLookTubeRepository(
            feedConfigurationStore = store,
            syncedLibraryStore = FakeSyncedLibraryStore(),
            playbackBookmarkStore = InMemoryPlaybackBookmarkStore(),
            videoFeedService = FakeVideoFeedService(),
        )

        repository.bootstrap()
        repository.updateFeedUrl("https://example.com/premium.xml")
        repository.updateUsername("jorge")
        repository.setRememberPassword(true)
        repository.updatePassword("remembered-secret")
        repository.forgetSavedCredentials()

        assertEquals("", repository.feedConfiguration.value.username)
        assertEquals("", repository.feedConfiguration.value.password)
        assertFalse(repository.feedConfiguration.value.rememberPassword)
        assertEquals("", store.persistedConfiguration.value.username)
        assertEquals("", store.persistedConfiguration.value.rememberedPassword)
        assertFalse(store.persistedConfiguration.value.rememberPassword)
        assertEquals("premium-quick-look-1", repository.videos.value.first().id)
    }
}

private class FakeFeedConfigurationStore(
    initialConfiguration: PersistedFeedConfiguration = PersistedFeedConfiguration(
        feedUrl = "",
        username = "",
        rememberedPassword = "",
        rememberPassword = false,
    ),
) : FeedConfigurationStore {
    private val state = MutableStateFlow(initialConfiguration)

    override val persistedConfiguration: StateFlow<PersistedFeedConfiguration> = state.asStateFlow()

    override suspend fun setFeedUrl(feedUrl: String) {
        state.value = state.value.copy(feedUrl = feedUrl)
    }

    override suspend fun setUsername(username: String) {
        state.value = state.value.copy(username = username)
    }

    override suspend fun setRememberPassword(rememberPassword: Boolean) {
        state.value = state.value.copy(
            rememberedPassword = if (rememberPassword) state.value.rememberedPassword else "",
            rememberPassword = rememberPassword,
        )
    }

    override suspend fun setRememberedPassword(password: String) {
        state.value = state.value.copy(
            rememberedPassword = if (state.value.rememberPassword) password else "",
        )
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
