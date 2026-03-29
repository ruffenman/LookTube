package com.looktube.data

import com.looktube.model.AccountSession
import com.looktube.model.CaptionGenerationStatus
import com.looktube.model.FeedConfiguration
import com.looktube.model.LibrarySyncState
import com.looktube.model.LocalCaptionModelState
import com.looktube.model.VideoEngagementRecord
import com.looktube.model.ManualWatchState
import com.looktube.model.PlaybackProgress
import com.looktube.model.VideoCaptionTrack
import com.looktube.model.VideoSummary
import kotlinx.coroutines.flow.StateFlow

interface LookTubeRepository {
    val accountSession: StateFlow<AccountSession>
    val feedConfiguration: StateFlow<FeedConfiguration>
    val librarySyncState: StateFlow<LibrarySyncState>
    val videos: StateFlow<List<VideoSummary>>
    val selectedVideoId: StateFlow<String?>
    val playbackProgress: StateFlow<Map<String, PlaybackProgress>>
    val videoEngagement: StateFlow<Map<String, VideoEngagementRecord>>
    val localCaptionModelState: StateFlow<LocalCaptionModelState>
    val videoCaptions: StateFlow<Map<String, VideoCaptionTrack>>
    val captionGenerationStatus: StateFlow<Map<String, CaptionGenerationStatus>>

    suspend fun bootstrap()

    suspend fun updateFeedUrl(feedUrl: String)
    suspend fun signInToPremiumFeed()
    suspend fun clearSyncedData()
    suspend fun downloadLocalCaptionModel()

    suspend fun refreshLibrary()
    suspend fun generateCaptions(videoId: String)

    fun selectVideo(videoId: String)
    fun setManualWatchState(videoId: String, manualWatchState: ManualWatchState)
}
