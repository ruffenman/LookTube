package com.looktube.model

data class PersistedFeedConfiguration(
    val authMode: AuthMode?,
    val feedUrl: String,
    val username: String,
)

fun PersistedFeedConfiguration.toRuntime(password: String): FeedConfiguration =
    FeedConfiguration(
        authMode = authMode,
        feedUrl = feedUrl,
        username = username,
        password = password,
    )
