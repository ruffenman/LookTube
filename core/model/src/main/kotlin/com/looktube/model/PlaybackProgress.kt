package com.looktube.model

data class PlaybackProgress(
    val videoId: String,
    val positionSeconds: Long,
    val durationSeconds: Long,
)
