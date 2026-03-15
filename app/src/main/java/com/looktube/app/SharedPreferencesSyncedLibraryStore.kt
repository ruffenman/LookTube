package com.looktube.app

import android.content.Context
import com.looktube.data.SyncedLibraryStore
import com.looktube.model.PersistedLibrarySnapshot
import com.looktube.model.VideoSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class SharedPreferencesSyncedLibraryStore(
    context: Context,
) : SyncedLibraryStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val snapshotState = MutableStateFlow(readSnapshot())

    override val persistedSnapshot: StateFlow<PersistedLibrarySnapshot?> = snapshotState.asStateFlow()

    override suspend fun save(snapshot: PersistedLibrarySnapshot) {
        preferences.edit()
            .putString(KEY_SNAPSHOT_JSON, snapshot.toJson().toString())
            .apply()
        snapshotState.value = snapshot
    }

    override suspend fun clear() {
        preferences.edit().remove(KEY_SNAPSHOT_JSON).apply()
        snapshotState.value = null
    }

    private fun readSnapshot(): PersistedLibrarySnapshot? {
        val raw = preferences.getString(KEY_SNAPSHOT_JSON, null).orEmpty()
        if (raw.isBlank()) {
            return null
        }
        return runCatching { JSONObject(raw).toSnapshot() }.getOrNull()
    }

    companion object {
        private const val PREFERENCES_NAME = "looktube.synced.library"
        private const val KEY_SNAPSHOT_JSON = "snapshot_json"
        private const val KEY_FEED_URL = "feed_url"
        private const val KEY_LAST_SUCCESSFUL_SYNC_SUMMARY = "last_successful_sync_summary"
        private const val KEY_VIDEOS = "videos"
        private const val KEY_ID = "id"
        private const val KEY_TITLE = "title"
        private const val KEY_DESCRIPTION = "description"
        private const val KEY_IS_PREMIUM = "is_premium"
        private const val KEY_FEED_CATEGORY = "feed_category"
        private const val KEY_PLAYBACK_URL = "playback_url"
        private const val KEY_SERIES_TITLE = "series_title"
        private const val KEY_THUMBNAIL_URL = "thumbnail_url"
        private const val KEY_PUBLISHED_AT_EPOCH_MILLIS = "published_at_epoch_millis"
        private const val KEY_DURATION_SECONDS = "duration_seconds"
    }

    private fun PersistedLibrarySnapshot.toJson(): JSONObject =
        JSONObject().apply {
            put(KEY_FEED_URL, feedUrl)
            put(KEY_LAST_SUCCESSFUL_SYNC_SUMMARY, lastSuccessfulSyncSummary ?: JSONObject.NULL)
            put(
                KEY_VIDEOS,
                JSONArray().apply {
                    videos.forEach { video ->
                        put(video.toJson())
                    }
                },
            )
        }

    private fun JSONObject.toSnapshot(): PersistedLibrarySnapshot =
        PersistedLibrarySnapshot(
            feedUrl = optString(KEY_FEED_URL),
            videos = optJSONArray(KEY_VIDEOS)?.toVideos().orEmpty(),
            lastSuccessfulSyncSummary = optNullableString(KEY_LAST_SUCCESSFUL_SYNC_SUMMARY),
        )

    private fun JSONArray.toVideos(): List<VideoSummary> =
        buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                add(item.toVideo())
            }
        }

    private fun VideoSummary.toJson(): JSONObject =
        JSONObject().apply {
            put(KEY_ID, id)
            put(KEY_TITLE, title)
            put(KEY_DESCRIPTION, description)
            put(KEY_IS_PREMIUM, isPremium)
            put(KEY_FEED_CATEGORY, feedCategory)
            put(KEY_PLAYBACK_URL, playbackUrl ?: JSONObject.NULL)
            put(KEY_SERIES_TITLE, seriesTitle ?: JSONObject.NULL)
            put(KEY_THUMBNAIL_URL, thumbnailUrl ?: JSONObject.NULL)
            put(KEY_PUBLISHED_AT_EPOCH_MILLIS, publishedAtEpochMillis ?: JSONObject.NULL)
            put(KEY_DURATION_SECONDS, durationSeconds ?: JSONObject.NULL)
        }

    private fun JSONObject.toVideo(): VideoSummary =
        VideoSummary(
            id = optString(KEY_ID),
            title = optString(KEY_TITLE),
            description = optString(KEY_DESCRIPTION),
            isPremium = optBoolean(KEY_IS_PREMIUM),
            feedCategory = optString(KEY_FEED_CATEGORY),
            playbackUrl = optNullableString(KEY_PLAYBACK_URL),
            seriesTitle = optNullableString(KEY_SERIES_TITLE),
            thumbnailUrl = optNullableString(KEY_THUMBNAIL_URL),
            publishedAtEpochMillis = optNullableLong(KEY_PUBLISHED_AT_EPOCH_MILLIS),
            durationSeconds = optNullableLong(KEY_DURATION_SECONDS),
        )

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (isNull(key)) null else optLong(key)
}
