package com.looktube.model

data class VideoSummary(
    val id: String,
    val title: String,
    val description: String,
    val isPremium: Boolean,
    val feedCategory: String,
    val playbackUrl: String?,
    val seriesTitle: String? = null,
    val thumbnailUrl: String? = null,
    val publishedAtEpochMillis: Long? = null,
    val durationSeconds: Long? = null,
)

fun VideoSummary.bestDurationSeconds(progress: PlaybackProgress?): Long? =
    durationSeconds ?: progress?.durationSeconds?.takeIf { it > 0 }
