package com.looktube.model

data class PersistedFeedConfiguration(
    val feedUrl: String,
)

fun PersistedFeedConfiguration.toRuntime(): FeedConfiguration =
    FeedConfiguration(
        feedUrl = feedUrl,
    )
