package com.looktube.model

data class PersistedFeedConfiguration(
    val feedUrl: String,
    val autoGenerateCaptionsForNewVideos: Boolean = false,
    val dailyOpenPointCount: Int = 0,
    val lastOpenedLocalEpochDay: Long? = null,
)

fun PersistedFeedConfiguration.toRuntime(): FeedConfiguration =
    FeedConfiguration(
        feedUrl = feedUrl,
        autoGenerateCaptionsForNewVideos = autoGenerateCaptionsForNewVideos,
        dailyOpenPointCount = dailyOpenPointCount,
    )
