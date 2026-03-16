package com.looktube.model

data class FeedConfiguration(
    val feedUrl: String,
    val username: String,
    val password: String,
    val rememberPassword: Boolean,
)
