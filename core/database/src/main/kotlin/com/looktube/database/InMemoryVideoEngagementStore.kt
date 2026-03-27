package com.looktube.database

import com.looktube.model.ManualWatchState
import com.looktube.model.PlaybackProgress
import com.looktube.model.VideoEngagementRecord
import com.looktube.model.isCompletedByProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InMemoryVideoEngagementStore : VideoEngagementStore {
    private val recordsByVideoId = mutableMapOf<String, VideoEngagementRecord>()
    private val recordsState = MutableStateFlow<Map<String, VideoEngagementRecord>>(emptyMap())

    override val engagementRecords: StateFlow<Map<String, VideoEngagementRecord>> = recordsState.asStateFlow()

    override fun read(videoId: String): VideoEngagementRecord? = recordsByVideoId[videoId]

    override fun recordPlayback(videoId: String, playedAtEpochMillis: Long) {
        updateRecord(videoId) { existingRecord ->
            existingRecord.copy(lastPlayedAtEpochMillis = playedAtEpochMillis)
        }
    }

    override fun recordPlaybackProgress(progress: PlaybackProgress, recordedAtEpochMillis: Long) {
        if (!progress.isCompletedByProgress()) {
            return
        }
        updateRecord(progress.videoId) { existingRecord ->
            existingRecord.copy(
                completedAtEpochMillis = existingRecord.completedAtEpochMillis ?: recordedAtEpochMillis,
                manualWatchState = when (existingRecord.manualWatchState) {
                    ManualWatchState.Unwatched -> null
                    else -> existingRecord.manualWatchState
                },
            )
        }
    }

    override fun setManualWatchState(
        videoId: String,
        manualWatchState: ManualWatchState,
        recordedAtEpochMillis: Long,
    ) {
        updateRecord(videoId) { existingRecord ->
            existingRecord.copy(
                completedAtEpochMillis = if (manualWatchState == ManualWatchState.Watched) {
                    existingRecord.completedAtEpochMillis ?: recordedAtEpochMillis
                } else {
                    null
                },
                manualWatchState = manualWatchState,
            )
        }
    }

    override fun clear() {
        recordsByVideoId.clear()
        recordsState.value = emptyMap()
    }

    private fun updateRecord(
        videoId: String,
        transform: (VideoEngagementRecord) -> VideoEngagementRecord,
    ) {
        val updatedRecord = transform(
            recordsByVideoId[videoId] ?: VideoEngagementRecord(videoId = videoId),
        )
        recordsByVideoId[videoId] = updatedRecord
        recordsState.value = recordsByVideoId.toMap()
    }
}
