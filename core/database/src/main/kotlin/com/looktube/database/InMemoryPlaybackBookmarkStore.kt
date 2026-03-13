package com.looktube.database

import com.looktube.model.PlaybackProgress

class InMemoryPlaybackBookmarkStore : PlaybackBookmarkStore {
    private val progressByVideoId = mutableMapOf<String, PlaybackProgress>()

    override fun read(videoId: String): PlaybackProgress? = progressByVideoId[videoId]

    override fun write(progress: PlaybackProgress) {
        progressByVideoId[progress.videoId] = progress
    }
}
