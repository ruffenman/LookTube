package com.looktube.data

import com.looktube.model.AccountSession
import com.looktube.model.AuthMode
import com.looktube.model.FeedConfiguration
import com.looktube.model.LibrarySyncState
import com.looktube.model.PersistedFeedConfiguration
import com.looktube.model.PlaybackProgress
import com.looktube.model.SyncPhase
import com.looktube.model.VideoSummary
import com.looktube.model.toRuntime
import com.looktube.network.FeedSyncException
import com.looktube.network.VideoFeedRequest
import com.looktube.network.VideoFeedService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConfigurableLookTubeRepository(
    private val feedConfigurationStore: FeedConfigurationStore,
    private val videoFeedService: VideoFeedService,
) : LookTubeRepository {
    private val accountSessionState = MutableStateFlow(
        AccountSession(
            isSignedIn = false,
            accountLabel = null,
            authMode = null,
            notes = "Configure a Premium feed to replace the seeded library.",
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
            message = "Configure a Giant Bomb Premium feed URL, choose auth mode, then sync.",
        ),
    )
    private val videosState = MutableStateFlow(emptyList<VideoSummary>())
    private val selectedVideoIdState = MutableStateFlow<String?>(null)
    private val playbackProgressState = MutableStateFlow(emptyMap<String, PlaybackProgress>())
    private var hasSuccessfulCredentialedSync = false

    override val accountSession: StateFlow<AccountSession> = accountSessionState.asStateFlow()
    override val feedConfiguration: StateFlow<FeedConfiguration> = feedConfigurationState.asStateFlow()
    override val librarySyncState: StateFlow<LibrarySyncState> = syncState.asStateFlow()
    override val videos: StateFlow<List<VideoSummary>> = videosState.asStateFlow()
    override val selectedVideoId: StateFlow<String?> = selectedVideoIdState.asStateFlow()
    override val playbackProgress: StateFlow<Map<String, PlaybackProgress>> = playbackProgressState.asStateFlow()

    override suspend fun bootstrap() {
        if (videosState.value.isNotEmpty()) {
            return
        }

        val persistedConfiguration = feedConfigurationStore.persistedConfiguration.value
        feedConfigurationState.value = persistedConfiguration.toRuntime(password = "")
        videosState.value = seededVideos
        selectedVideoIdState.value = seededVideos.firstOrNull()?.id
        playbackProgressState.value = seededPlaybackProgress
        publishStatus(initialStatusFor(feedConfigurationState.value))
    }

    override suspend fun selectAuthMode(mode: AuthMode) {
        feedConfigurationStore.setAuthMode(mode)
        feedConfigurationState.value = feedConfigurationState.value.copy(authMode = mode)
        publishStatus(initialStatusFor(feedConfigurationState.value))
    }

    override suspend fun updateFeedUrl(feedUrl: String) {
        feedConfigurationStore.setFeedUrl(feedUrl)
        feedConfigurationState.value = feedConfigurationState.value.copy(feedUrl = feedUrl)
        publishStatus(initialStatusFor(feedConfigurationState.value))
    }

    override suspend fun updateUsername(username: String) {
        feedConfigurationStore.setUsername(username)
        feedConfigurationState.value = feedConfigurationState.value.copy(username = username)
        publishStatus(initialStatusFor(feedConfigurationState.value))
    }

    override fun updatePassword(password: String) {
        feedConfigurationState.value = feedConfigurationState.value.copy(password = password)
        publishStatus(initialStatusFor(feedConfigurationState.value))
    }

    override suspend fun refreshLibrary() {
        val configuration = feedConfigurationState.value
        when {
            configuration.authMode == null -> {
                publishStatus(
                    LibrarySyncState(
                        phase = SyncPhase.Error,
                        message = "Choose an auth mode before syncing the library.",
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
            configuration.username.isBlank() || configuration.password.isBlank() -> {
                publishStatus(
                    LibrarySyncState(
                        phase = SyncPhase.Error,
                        message = "Enter both username and password before syncing the credentialed feed.",
                        lastSuccessfulSyncSummary = syncState.value.lastSuccessfulSyncSummary,
                    ),
                )
                return
            }
        }

        publishStatus(
            LibrarySyncState(
                phase = SyncPhase.Refreshing,
                message = "Syncing the configured Premium feed...",
                lastSuccessfulSyncSummary = syncState.value.lastSuccessfulSyncSummary,
            ),
        )

        try {
            val syncedVideos = videoFeedService.loadVideos(
                VideoFeedRequest(
                    feedUrl = configuration.feedUrl,
                    username = configuration.username,
                    password = configuration.password,
                ),
            )
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
                selectedVideoIdState.value = syncedVideos.first().id
            }
            hasSuccessfulCredentialedSync = true
            publishStatus(
                LibrarySyncState(
                    phase = SyncPhase.Success,
                    message = "Synced ${syncedVideos.size} videos from the configured credentialed feed.",
                    lastSuccessfulSyncSummary = "Credentialed feed sync loaded ${syncedVideos.size} items.",
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
            isSignedIn = hasSuccessfulCredentialedSync,
            accountLabel = configuration.username.takeIf(String::isNotBlank),
            authMode = configuration.authMode,
            notes = buildString {
                append(status.message)
                if (configuration.password.isBlank()) {
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
                message = "Configure a Giant Bomb Premium feed URL to replace the seeded library.",
                lastSuccessfulSyncSummary = syncState.value.lastSuccessfulSyncSummary,
            )
            configuration.username.isBlank() -> LibrarySyncState(
                phase = SyncPhase.Idle,
                message = "Saved feed URL detected. Enter the Premium username to continue.",
                lastSuccessfulSyncSummary = syncState.value.lastSuccessfulSyncSummary,
            )
            configuration.password.isBlank() -> LibrarySyncState(
                phase = SyncPhase.Idle,
                message = "Saved feed settings loaded. Enter the password for this app session and sync.",
                lastSuccessfulSyncSummary = syncState.value.lastSuccessfulSyncSummary,
            )
            else -> LibrarySyncState(
                phase = SyncPhase.Idle,
                message = "Configured feed is ready to sync.",
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
            ),
            VideoSummary(
                id = "latest-premium-2",
                title = "Latest Premium Feed Baseline",
                description = "Library baseline used to validate list rendering and player handoff.",
                isPremium = true,
                feedCategory = "Latest Premium",
                playbackUrl = null,
            ),
        )

        val seededPlaybackProgress = mapOf(
            seededVideos.first().id to PlaybackProgress(
                videoId = seededVideos.first().id,
                positionSeconds = 372,
                durationSeconds = 1_820,
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
