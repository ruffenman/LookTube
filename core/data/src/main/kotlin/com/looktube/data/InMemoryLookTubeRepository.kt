package com.looktube.data

import com.looktube.model.AccountSession
import com.looktube.model.AuthMode
import com.looktube.model.PlaybackProgress
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
    private val videosState = MutableStateFlow(emptyList<VideoSummary>())
    private val selectedVideoIdState = MutableStateFlow<String?>(null)
    private val playbackProgressState = MutableStateFlow(emptyMap<String, PlaybackProgress>())

    override val accountSession: StateFlow<AccountSession> = accountSessionState.asStateFlow()
    override val videos: StateFlow<List<VideoSummary>> = videosState.asStateFlow()
    override val selectedVideoId: StateFlow<String?> = selectedVideoIdState.asStateFlow()
    override val playbackProgress: StateFlow<Map<String, PlaybackProgress>> = playbackProgressState.asStateFlow()

    override suspend fun bootstrap() {
        if (videosState.value.isNotEmpty()) {
            return
        }

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

        videosState.value = seededVideos
        selectedVideoIdState.value = seededVideos.first().id
        playbackProgressState.value = mapOf(
            seededVideos.first().id to PlaybackProgress(
                videoId = seededVideos.first().id,
                positionSeconds = 372,
                durationSeconds = 1_820,
            ),
        )
    }

    override fun selectAuthMode(mode: AuthMode) {
        accountSessionState.value = accountSessionState.value.copy(
            authMode = mode,
            notes = when (mode) {
                AuthMode.SessionCookie -> "Spike session-cookie sign-in via a browser-backed flow and persist only the minimum session state."
                AuthMode.CredentialedFeed -> "Spike credentialed feed access first to confirm Premium video RSS reliability."
            },
        )
    }

    override fun selectVideo(videoId: String) {
        selectedVideoIdState.value = videoId
    }
}
