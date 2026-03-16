package com.looktube.data

import com.looktube.model.AccountSession
import com.looktube.model.AuthMode
import com.looktube.model.FeedConfiguration
import com.looktube.model.LibrarySyncState
import com.looktube.model.PlaybackProgress
import com.looktube.model.VideoSummary
import kotlinx.coroutines.flow.StateFlow

interface LookTubeRepository {
    val accountSession: StateFlow<AccountSession>
    val feedConfiguration: StateFlow<FeedConfiguration>
    val librarySyncState: StateFlow<LibrarySyncState>
    val videos: StateFlow<List<VideoSummary>>
    val selectedVideoId: StateFlow<String?>
    val playbackProgress: StateFlow<Map<String, PlaybackProgress>>

    suspend fun bootstrap()

    suspend fun updateFeedUrl(feedUrl: String)

    suspend fun updateUsername(username: String)
    suspend fun updatePassword(password: String)
    suspend fun setRememberPassword(rememberPassword: Boolean)
    suspend fun signInToPremiumFeed()
    suspend fun clearSyncedData()
    suspend fun forgetSavedCredentials()

    suspend fun refreshLibrary()

    fun selectVideo(videoId: String)
}
