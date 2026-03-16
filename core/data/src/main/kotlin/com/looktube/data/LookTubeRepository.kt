package com.looktube.data

import com.looktube.model.AccountSession
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
    suspend fun signInToPremiumFeed()
    suspend fun clearSyncedData()

    suspend fun refreshLibrary()

    fun selectVideo(videoId: String)
}
