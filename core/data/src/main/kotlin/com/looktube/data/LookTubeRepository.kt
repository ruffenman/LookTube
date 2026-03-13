package com.looktube.data

import com.looktube.model.AccountSession
import com.looktube.model.AuthMode
import com.looktube.model.PlaybackProgress
import com.looktube.model.VideoSummary
import kotlinx.coroutines.flow.StateFlow

interface LookTubeRepository {
    val accountSession: StateFlow<AccountSession>
    val videos: StateFlow<List<VideoSummary>>
    val selectedVideoId: StateFlow<String?>
    val playbackProgress: StateFlow<Map<String, PlaybackProgress>>

    suspend fun bootstrap()

    fun selectAuthMode(mode: AuthMode)

    fun selectVideo(videoId: String)
}
