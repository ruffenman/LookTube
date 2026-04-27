package com.looktube.model

data class FeedConfiguration(
    val feedUrl: String,
    val autoGenerateCaptionsForNewVideos: Boolean = false,
    val dailyOpenPointCount: Int = 0,
    val lastOpenedAtEpochMillis: Long? = null,
    val launchIntroMessageDeckSeed: Long = 1L,
    val launchIntroMessageDeckIndex: Int = 0,
)
