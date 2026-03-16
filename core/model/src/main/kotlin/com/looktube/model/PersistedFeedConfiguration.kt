package com.looktube.model

data class PersistedFeedConfiguration(
    val authMode: AuthMode?,
    val feedUrl: String,
    val username: String,
    val rememberedPassword: String,
    val rememberPassword: Boolean,
)

fun PersistedFeedConfiguration.toRuntime(): FeedConfiguration =
    FeedConfiguration(
        authMode = authMode,
        feedUrl = feedUrl,
        username = username,
        password = rememberedPassword,
        rememberPassword = rememberPassword,
    )
