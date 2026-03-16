package com.looktube.model

data class PersistedFeedConfiguration(
    val feedUrl: String,
    val username: String,
    val rememberedPassword: String,
    val rememberPassword: Boolean,
)

fun PersistedFeedConfiguration.toRuntime(): FeedConfiguration =
    FeedConfiguration(
        feedUrl = feedUrl,
        username = username,
        password = rememberedPassword,
        rememberPassword = rememberPassword,
    )
