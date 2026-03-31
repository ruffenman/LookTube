package com.looktube.app

import android.content.Context
import com.looktube.data.CaptionDataStore
import com.looktube.model.CaptionGenerationPhase
import com.looktube.model.VideoCaptionData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class SharedPreferencesCaptionDataStore(
    context: Context,
) : CaptionDataStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val captionDataState = MutableStateFlow(readAll())

    override val captionData: StateFlow<Map<String, VideoCaptionData>> = captionDataState.asStateFlow()

    override fun upsert(data: VideoCaptionData) {
        persist(
            captionDataState.value.toMutableMap().apply {
                put(data.videoId, data)
            },
        )
    }

    override fun remove(videoId: String) {
        persist(
            captionDataState.value.toMutableMap().apply {
                remove(videoId)
            },
        )
    }

    override fun clear() {
        preferences.edit().remove(KEY_CAPTION_DATA_JSON).apply()
        captionDataState.value = emptyMap()
    }

    private fun persist(dataByVideoId: Map<String, VideoCaptionData>) {
        val json = JSONObject()
        dataByVideoId.forEach { (videoId, data) ->
            json.put(
                videoId,
                JSONObject().apply {
                    put(KEY_VIDEO_ID, data.videoId)
                    put(KEY_UPDATED_AT_EPOCH_MILLIS, data.updatedAtEpochMillis)
                    put(KEY_LAST_PHASE, data.lastPhase.name)
                    put(KEY_LAST_MESSAGE, data.lastMessage)
                    put(KEY_HAS_SAVED_CAPTION_TRACK, data.hasSavedCaptionTrack)
                    put(KEY_CAPTION_TRACK_PATH, data.captionTrackPath ?: JSONObject.NULL)
                    put(KEY_ENGINE_ID, data.engineId ?: JSONObject.NULL)
                },
            )
        }
        preferences.edit()
            .putString(KEY_CAPTION_DATA_JSON, json.toString())
            .apply()
        captionDataState.value = dataByVideoId
    }

    private fun readAll(): Map<String, VideoCaptionData> {
        val raw = preferences.getString(KEY_CAPTION_DATA_JSON, null).orEmpty()
        if (raw.isBlank()) {
            return emptyMap()
        }
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        return buildMap {
            root.keys().forEach { key ->
                val item = root.optJSONObject(key) ?: return@forEach
                val lastPhase = item.optString(KEY_LAST_PHASE)
                    .takeIf(String::isNotBlank)
                    ?.let { phaseName ->
                        CaptionGenerationPhase.entries.firstOrNull { it.name == phaseName }
                    }
                    ?: CaptionGenerationPhase.Error
                put(
                    key,
                    VideoCaptionData(
                        videoId = item.optString(KEY_VIDEO_ID).ifBlank { key },
                        updatedAtEpochMillis = item.optLong(KEY_UPDATED_AT_EPOCH_MILLIS, 0L),
                        lastPhase = lastPhase,
                        lastMessage = item.optString(KEY_LAST_MESSAGE),
                        hasSavedCaptionTrack = item.optBoolean(KEY_HAS_SAVED_CAPTION_TRACK, false),
                        captionTrackPath = item.optNullableString(KEY_CAPTION_TRACK_PATH),
                        engineId = item.optNullableString(KEY_ENGINE_ID),
                    ),
                )
            }
        }
    }

    private companion object {
        private const val PREFERENCES_NAME = "looktube.caption.data"
        private const val KEY_CAPTION_DATA_JSON = "caption_data_json"
        private const val KEY_VIDEO_ID = "video_id"
        private const val KEY_UPDATED_AT_EPOCH_MILLIS = "updated_at_epoch_millis"
        private const val KEY_LAST_PHASE = "last_phase"
        private const val KEY_LAST_MESSAGE = "last_message"
        private const val KEY_HAS_SAVED_CAPTION_TRACK = "has_saved_caption_track"
        private const val KEY_CAPTION_TRACK_PATH = "caption_track_path"
        private const val KEY_ENGINE_ID = "engine_id"
    }
}

private fun JSONObject.optNullableString(key: String): String? =
    if (isNull(key)) {
        null
    } else {
        optString(key).takeIf(String::isNotBlank)
    }
