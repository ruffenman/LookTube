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

val VideoSummary.displaySeriesTitle: String
    get() = seriesTitle?.takeIf(String::isNotBlank) ?: feedCategory.ifBlank { "More videos" }

val VideoSummary.seriesGroupingKey: String
    get() = displaySeriesTitle
        .trim()
        .lowercase()
        .replace("&", "and")
        .replace("'", "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("\\b(the|premium|show|series)\\b"), " ")
        .replace(Regex("\\blooks\\b"), "look")
        .replace(Regex("\\s+"), " ")
        .trim()

fun VideoSummary.bestDurationSeconds(progress: PlaybackProgress?): Long? =
    durationSeconds ?: progress?.durationSeconds?.takeIf { it > 0 }
