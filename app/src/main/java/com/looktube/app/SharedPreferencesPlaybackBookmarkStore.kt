package com.looktube.app

import android.content.Context
import com.looktube.database.PlaybackBookmarkStore
import com.looktube.model.PlaybackProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class SharedPreferencesPlaybackBookmarkStore(
    context: Context,
) : PlaybackBookmarkStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val progressState = MutableStateFlow(readAll())

    override val progressSnapshots: StateFlow<Map<String, PlaybackProgress>> = progressState.asStateFlow()

    override fun read(videoId: String): PlaybackProgress? = progressState.value[videoId]

    override fun write(progress: PlaybackProgress) {
        val updated = progressState.value.toMutableMap().apply {
            put(progress.videoId, progress)
        }
        persist(updated)
    }

    override fun clear() {
        preferences.edit().remove(KEY_PROGRESS_JSON).apply()
        progressState.value = emptyMap()
    }

    private fun persist(progressByVideoId: Map<String, PlaybackProgress>) {
        val json = JSONObject()
        progressByVideoId.forEach { (videoId, progress) ->
            json.put(
                videoId,
                JSONObject().apply {
                    put(KEY_VIDEO_ID, progress.videoId)
                    put(KEY_POSITION_SECONDS, progress.positionSeconds)
                    put(KEY_DURATION_SECONDS, progress.durationSeconds)
                },
            )
        }
        preferences.edit()
            .putString(KEY_PROGRESS_JSON, json.toString())
            .apply()
        progressState.value = progressByVideoId
    }

    private fun readAll(): Map<String, PlaybackProgress> {
        val raw = preferences.getString(KEY_PROGRESS_JSON, null).orEmpty()
        if (raw.isBlank()) {
            return emptyMap()
        }
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        return buildMap {
            root.keys().forEach { key ->
                val item = root.optJSONObject(key) ?: return@forEach
                val videoId = item.optString(KEY_VIDEO_ID).ifBlank { key }
                val positionSeconds = item.optLong(KEY_POSITION_SECONDS, 0L)
                val durationSeconds = item.optLong(KEY_DURATION_SECONDS, 0L)
                put(
                    key,
                    PlaybackProgress(
                        videoId = videoId,
                        positionSeconds = positionSeconds,
                        durationSeconds = durationSeconds,
                    ),
                )
            }
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "looktube.playback.bookmarks"
        private const val KEY_PROGRESS_JSON = "progress_json"
        private const val KEY_VIDEO_ID = "video_id"
        private const val KEY_POSITION_SECONDS = "position_seconds"
        private const val KEY_DURATION_SECONDS = "duration_seconds"
    }
}
