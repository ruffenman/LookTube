package com.looktube.app

import android.content.Context
import com.looktube.database.VideoEngagementStore
import com.looktube.model.ManualWatchState
import com.looktube.model.PlaybackProgress
import com.looktube.model.VideoEngagementRecord
import com.looktube.model.isCompletedByProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class SharedPreferencesVideoEngagementStore(
    context: Context,
) : VideoEngagementStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val recordsState = MutableStateFlow(readAll())

    override val engagementRecords: StateFlow<Map<String, VideoEngagementRecord>> = recordsState.asStateFlow()

    override fun read(videoId: String): VideoEngagementRecord? = recordsState.value[videoId]

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
        preferences.edit().remove(KEY_RECORDS_JSON).apply()
        recordsState.value = emptyMap()
    }

    private fun updateRecord(
        videoId: String,
        transform: (VideoEngagementRecord) -> VideoEngagementRecord,
    ) {
        val updated = recordsState.value.toMutableMap().apply {
            put(videoId, transform(get(videoId) ?: VideoEngagementRecord(videoId = videoId)))
        }
        persist(updated)
    }

    private fun persist(recordsByVideoId: Map<String, VideoEngagementRecord>) {
        val json = JSONObject()
        recordsByVideoId.forEach { (videoId, record) ->
            json.put(
                videoId,
                JSONObject().apply {
                    put(KEY_VIDEO_ID, record.videoId)
                    put(KEY_LAST_PLAYED_AT_EPOCH_MILLIS, record.lastPlayedAtEpochMillis ?: JSONObject.NULL)
                    put(KEY_COMPLETED_AT_EPOCH_MILLIS, record.completedAtEpochMillis ?: JSONObject.NULL)
                    put(KEY_MANUAL_WATCH_STATE, record.manualWatchState?.name ?: JSONObject.NULL)
                },
            )
        }
        preferences.edit()
            .putString(KEY_RECORDS_JSON, json.toString())
            .apply()
        recordsState.value = recordsByVideoId
    }

    private fun readAll(): Map<String, VideoEngagementRecord> {
        val raw = preferences.getString(KEY_RECORDS_JSON, null).orEmpty()
        if (raw.isBlank()) {
            return emptyMap()
        }
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        return buildMap {
            root.keys().forEach { key ->
                val item = root.optJSONObject(key) ?: return@forEach
                val manualWatchState = item.optString(KEY_MANUAL_WATCH_STATE)
                    .takeIf(String::isNotBlank)
                    ?.let { stateName -> ManualWatchState.entries.firstOrNull { it.name == stateName } }
                put(
                    key,
                    VideoEngagementRecord(
                        videoId = item.optString(KEY_VIDEO_ID).ifBlank { key },
                        lastPlayedAtEpochMillis = item.optNullableLong(KEY_LAST_PLAYED_AT_EPOCH_MILLIS),
                        completedAtEpochMillis = item.optNullableLong(KEY_COMPLETED_AT_EPOCH_MILLIS),
                        manualWatchState = manualWatchState,
                    ),
                )
            }
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "looktube.video.engagement"
        private const val KEY_RECORDS_JSON = "records_json"
        private const val KEY_VIDEO_ID = "video_id"
        private const val KEY_LAST_PLAYED_AT_EPOCH_MILLIS = "last_played_at_epoch_millis"
        private const val KEY_COMPLETED_AT_EPOCH_MILLIS = "completed_at_epoch_millis"
        private const val KEY_MANUAL_WATCH_STATE = "manual_watch_state"
    }
}

private fun JSONObject.optNullableLong(key: String): Long? =
    if (isNull(key)) null else optLong(key)
