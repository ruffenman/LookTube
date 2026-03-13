package com.looktube.database

import com.looktube.model.PlaybackProgress

interface PlaybackBookmarkStore {
    fun read(videoId: String): PlaybackProgress?

    fun write(progress: PlaybackProgress)
}
