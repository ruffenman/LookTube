package com.looktube.model

data class FeedConfiguration(
    val feedUrl: String,
    val autoGenerateCaptionsForNewVideos: Boolean = false,
    val dailyOpenPointCount: Int = 0,
    val launchIntroQuoteDeckSeed: Long = 1L,
    val launchIntroQuoteDeckIndex: Int = 0,
)
