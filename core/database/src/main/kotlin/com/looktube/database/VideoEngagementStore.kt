package com.looktube.database

import com.looktube.model.ManualWatchState
import com.looktube.model.PlaybackProgress
import com.looktube.model.VideoEngagementRecord
import kotlinx.coroutines.flow.StateFlow

interface VideoEngagementStore {
    val engagementRecords: StateFlow<Map<String, VideoEngagementRecord>>
    fun read(videoId: String): VideoEngagementRecord?

    fun recordPlayback(videoId: String, playedAtEpochMillis: Long = System.currentTimeMillis())

    fun recordPlaybackProgress(
        progress: PlaybackProgress,
        recordedAtEpochMillis: Long = System.currentTimeMillis(),
    )

    fun setManualWatchState(
        videoId: String,
        manualWatchState: ManualWatchState,
        recordedAtEpochMillis: Long = System.currentTimeMillis(),
    )

    fun clear()
}
