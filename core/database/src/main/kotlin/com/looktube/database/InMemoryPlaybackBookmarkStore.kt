package com.looktube.database

import com.looktube.model.PlaybackProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InMemoryPlaybackBookmarkStore : PlaybackBookmarkStore {
    private val progressByVideoId = mutableMapOf<String, PlaybackProgress>()
    private val progressState = MutableStateFlow<Map<String, PlaybackProgress>>(emptyMap())

    override val progressSnapshots: StateFlow<Map<String, PlaybackProgress>> = progressState.asStateFlow()

    override fun read(videoId: String): PlaybackProgress? = progressByVideoId[videoId]

    override fun write(progress: PlaybackProgress) {
        progressByVideoId[progress.videoId] = progress
        progressState.value = progressByVideoId.toMap()
    }

    override fun clear() {
        progressByVideoId.clear()
        progressState.value = emptyMap()
    }
}
