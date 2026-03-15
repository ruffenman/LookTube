package com.looktube.data
import com.looktube.database.PlaybackBookmarkStore

import com.looktube.model.AccountSession
import com.looktube.model.AuthMode
import com.looktube.model.FeedConfiguration
import com.looktube.model.LibrarySyncState
import com.looktube.model.PersistedFeedConfiguration
import com.looktube.model.PersistedLibrarySnapshot
import com.looktube.model.PlaybackProgress
import com.looktube.model.SyncPhase
import com.looktube.model.VideoSummary
import com.looktube.model.toRuntime
import com.looktube.network.FeedSyncException
import com.looktube.network.VideoFeedRequest
import com.looktube.network.VideoFeedService
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
    private val videoFeedService: VideoFeedService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LookTubeRepository {
    private val accountSessionState = MutableStateFlow(
        AccountSession(
            isSignedIn = false,
            accountLabel = null,
            authMode = null,
            notes = "Paste a Giant Bomb Premium RSS URL to replace the seeded library. Username and password are optional fallback fields.",
        ),
    )
    private val feedConfigurationState = MutableStateFlow(
        FeedConfiguration(
            authMode = null,
            feedUrl = "",
            username = "",
            password = "",
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
    private var hasSuccessfulFeedSync = false

    override val accountSession: StateFlow<AccountSession> = accountSessionState.asStateFlow()
    override val feedConfiguration: StateFlow<FeedConfiguration> = feedConfigurationState.asStateFlow()
    override val librarySyncState: StateFlow<LibrarySyncState> = syncState.asStateFlow()
    override val videos: StateFlow<List<VideoSummary>> = videosState.asStateFlow()
    override val selectedVideoId: StateFlow<String?> = selectedVideoIdState.asStateFlow()
    override val playbackProgress: StateFlow<Map<String, PlaybackProgress>> = playbackBookmarkStore.progressSnapshots

    override suspend fun bootstrap() {
        if (videosState.value.isNotEmpty()) {
            return
        }

        val persistedConfiguration = feedConfigurationStore.persistedConfiguration.value
        feedConfigurationState.value = persistedConfiguration.toRuntime(password = "")
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
                    message = "Loaded ${persistedSnapshot.videos.size} synced videos from local cache. Sync again to refresh.",
                    lastSuccessfulSyncSummary = persistedSnapshot.lastSuccessfulSyncSummary
                        ?: "Cached Premium feed with ${persistedSnapshot.videos.size} items.",
                ),
            )
        } else {
            videosState.value = seededVideos
            selectedVideoIdState.value = null
            publishStatus(initialStatusFor(feedConfigurationState.value))
        }
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

    override suspend fun selectAuthMode(mode: AuthMode) {
        feedConfigurationStore.setAuthMode(mode)
        feedConfigurationState.value = feedConfigurationState.value.copy(authMode = mode)
        publishStatus(statusAfterConfigurationChange(feedConfigurationState.value))
    }

    override suspend fun updateFeedUrl(feedUrl: String) {
        feedConfigurationStore.setFeedUrl(feedUrl)
        feedConfigurationState.value = feedConfigurationState.value.copy(feedUrl = feedUrl)
        publishStatus(statusAfterConfigurationChange(feedConfigurationState.value))
    }

    override suspend fun updateUsername(username: String) {
        feedConfigurationStore.setUsername(username)
        feedConfigurationState.value = feedConfigurationState.value.copy(username = username)
        publishStatus(statusAfterConfigurationChange(feedConfigurationState.value))
    }

    override fun updatePassword(password: String) {
        feedConfigurationState.value = feedConfigurationState.value.copy(password = password)
        publishStatus(statusAfterConfigurationChange(feedConfigurationState.value))
    }
    override suspend fun signInToPremiumFeed() {
        if (feedConfigurationState.value.authMode != AuthMode.CredentialedFeed) {
            feedConfigurationStore.setAuthMode(AuthMode.CredentialedFeed)
            hasSuccessfulFeedSync = false
            feedConfigurationState.value = feedConfigurationState.value.copy(authMode = AuthMode.CredentialedFeed)
        }
        refreshLibrary()
    }

    override suspend fun signOut() {
        hasSuccessfulFeedSync = false
        feedConfigurationStore.setAuthMode(null)
        feedConfigurationStore.setUsername("")
        syncedLibraryStore.clear()
        playbackBookmarkStore.clear()
        feedConfigurationState.value = feedConfigurationState.value.copy(
            authMode = null,
            username = "",
            password = "",
        )
        videosState.value = seededVideos
        selectedVideoIdState.value = null
        publishStatus(
            LibrarySyncState(
                phase = SyncPhase.Idle,
                message = "Cleared synced library data. Feed URL was preserved for the next sign-in.",
                lastSuccessfulSyncSummary = null,
            ),
        )
    }

    override suspend fun refreshLibrary() {
        val configuration = feedConfigurationState.value
        when {
            configuration.authMode == null -> {
                publishStatus(
                    LibrarySyncState(
                        phase = SyncPhase.Error,
                        message = "Sign in to Giant Bomb Premium before syncing the library.",
                        lastSuccessfulSyncSummary = syncState.value.lastSuccessfulSyncSummary,
                    ),
                )
                return
            }
            configuration.authMode == AuthMode.SessionCookie -> {
                publishStatus(
                    LibrarySyncState(
                        phase = SyncPhase.Error,
                        message = "Session-cookie mode is planned but not implemented yet. Use credentialed feed mode for this spike.",
                        lastSuccessfulSyncSummary = syncState.value.lastSuccessfulSyncSummary,
                    ),
                )
                return
            }
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
                        username = configuration.username,
                        password = configuration.password,
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
            syncedLibraryStore.save(
                PersistedLibrarySnapshot(
                    feedUrl = configuration.feedUrl,
                    videos = syncedVideos,
                    lastSuccessfulSyncSummary = syncSummary,
                ),
            )
            publishStatus(
                LibrarySyncState(
                    phase = SyncPhase.Success,
                    message = "Synced ${syncedVideos.size} videos from the configured Premium feed.",
                    lastSuccessfulSyncSummary = syncSummary,
                ),
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

    override fun selectVideo(videoId: String) {
        selectedVideoIdState.value = videoId
    }

    private fun publishStatus(status: LibrarySyncState) {
        syncState.value = status
        val configuration = feedConfigurationState.value
        accountSessionState.value = AccountSession(
            isSignedIn = hasSuccessfulFeedSync,
            accountLabel = configuration.username.takeIf(String::isNotBlank) ?: configuration.feedUrl.takeIf(String::isNotBlank)?.let { "Copied Premium feed" },
            authMode = configuration.authMode,
            notes = buildString {
                append(status.message)
                if (configuration.password.isNotBlank()) {
                    append(" Password is stored for the current app session only.")
                }
            },
        )
    }

    private fun initialStatusFor(configuration: FeedConfiguration): LibrarySyncState =
        when {
            configuration.authMode == AuthMode.SessionCookie -> LibrarySyncState(
                phase = SyncPhase.Idle,
                message = "Session-cookie mode is planned but not implemented yet. Credentialed feed mode is the active sync path.",
                lastSuccessfulSyncSummary = syncState.value.lastSuccessfulSyncSummary,
            )
            configuration.feedUrl.isBlank() -> LibrarySyncState(
                phase = SyncPhase.Idle,
                message = "Paste a Giant Bomb Premium RSS URL copied from the feeds page to replace the seeded library.",
                lastSuccessfulSyncSummary = syncState.value.lastSuccessfulSyncSummary,
            )
            configuration.username.isBlank() && configuration.password.isBlank() -> LibrarySyncState(
                phase = SyncPhase.Idle,
                message = "Saved feed URL detected. Sign in to sync it. Username and password are optional for copied feed URLs that already include access keys.",
                lastSuccessfulSyncSummary = syncState.value.lastSuccessfulSyncSummary,
            )
            configuration.password.isBlank() -> LibrarySyncState(
                phase = SyncPhase.Idle,
                message = "Saved feed URL and username loaded. If this feed still requires basic auth, enter the password for this app session; otherwise sign in now.",
                lastSuccessfulSyncSummary = syncState.value.lastSuccessfulSyncSummary,
            )
            else -> LibrarySyncState(
                phase = SyncPhase.Idle,
                message = "Feed settings are ready. Sign in to sync the Premium feed.",
                lastSuccessfulSyncSummary = syncState.value.lastSuccessfulSyncSummary,
            )
        }

    companion object {
        val seededVideos = listOf(
            VideoSummary(
                id = "premium-quick-look-1",
                title = "Premium Quick Look Spike",
                description = "Thin vertical slice placeholder for authenticated premium playback.",
                isPremium = true,
                feedCategory = "Premium",
                playbackUrl = null,
                seriesTitle = "Quick Look",
            ),
            VideoSummary(
                id = "latest-premium-2",
                title = "Latest Premium Feed Baseline",
                description = "Library baseline used to validate list rendering and player handoff.",
                isPremium = true,
                feedCategory = "Latest Premium",
                playbackUrl = null,
                seriesTitle = "Latest Premium",
            ),
        )
    }
}

interface FeedConfigurationStore {
    val persistedConfiguration: StateFlow<PersistedFeedConfiguration>

    suspend fun setAuthMode(mode: AuthMode?)

    suspend fun setFeedUrl(feedUrl: String)

    suspend fun setUsername(username: String)
}
