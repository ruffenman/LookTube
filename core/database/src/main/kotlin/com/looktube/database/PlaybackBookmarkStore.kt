package com.looktube.database

import com.looktube.model.PlaybackProgress
import kotlinx.coroutines.flow.StateFlow

interface PlaybackBookmarkStore {
    val progressSnapshots: StateFlow<Map<String, PlaybackProgress>>
    fun read(videoId: String): PlaybackProgress?

    fun write(progress: PlaybackProgress)

    fun clear()
}
