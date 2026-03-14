package com.looktube.data

import com.looktube.model.AccountSession
import com.looktube.model.AuthMode
import com.looktube.model.FeedConfiguration
import com.looktube.model.LibrarySyncState
import com.looktube.model.PlaybackProgress
import com.looktube.model.SyncPhase
import com.looktube.model.VideoSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InMemoryLookTubeRepository : LookTubeRepository {
    private val accountSessionState = MutableStateFlow(
        AccountSession(
            isSignedIn = false,
            accountLabel = null,
            authMode = null,
            notes = "Auth spike pending validation against Giant Bomb Premium flows.",
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
            message = "In-memory spike repository is active.",
        ),
    )
    private val videosState = MutableStateFlow(emptyList<VideoSummary>())
    private val selectedVideoIdState = MutableStateFlow<String?>(null)
    private val playbackProgressState = MutableStateFlow(emptyMap<String, PlaybackProgress>())

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

        val seededVideos = ConfigurableLookTubeRepository.seededVideos

        videosState.value = seededVideos
        selectedVideoIdState.value = seededVideos.first().id
        playbackProgressState.value = ConfigurableLookTubeRepository.seededPlaybackProgress
    }


    override suspend fun selectAuthMode(mode: AuthMode) {
        feedConfigurationState.value = feedConfigurationState.value.copy(authMode = mode)
        accountSessionState.value = accountSessionState.value.copy(
            authMode = mode,
            notes = when (mode) {
                AuthMode.SessionCookie -> "Spike session-cookie sign-in via a browser-backed flow and persist only the minimum session state."
                AuthMode.CredentialedFeed -> "Spike credentialed feed access first to confirm Premium video RSS reliability."
            },
        )
    }

    override suspend fun updateFeedUrl(feedUrl: String) {
        feedConfigurationState.value = feedConfigurationState.value.copy(feedUrl = feedUrl)
    }

    override suspend fun updateUsername(username: String) {
        feedConfigurationState.value = feedConfigurationState.value.copy(username = username)
    }

    override fun updatePassword(password: String) {
        feedConfigurationState.value = feedConfigurationState.value.copy(password = password)
    }
    override suspend fun signInToPremiumFeed() {
        selectAuthMode(AuthMode.CredentialedFeed)
        refreshLibrary()
    }

    override suspend fun signOut() {
        accountSessionState.value = accountSessionState.value.copy(
            isSignedIn = false,
            accountLabel = null,
            authMode = null,
            notes = "Signed out of the in-memory spike repository.",
        )
        feedConfigurationState.value = feedConfigurationState.value.copy(
            authMode = null,
            username = "",
            password = "",
        )
        syncState.value = LibrarySyncState(
            phase = SyncPhase.Idle,
            message = "Signed out. Seeded content is active.",
        )
        videosState.value = ConfigurableLookTubeRepository.seededVideos
        selectedVideoIdState.value = ConfigurableLookTubeRepository.seededVideos.first().id
        playbackProgressState.value = ConfigurableLookTubeRepository.seededPlaybackProgress
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
    }
}
