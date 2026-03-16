package com.looktube.model

data class FeedConfiguration(
    val authMode: AuthMode?,
    val feedUrl: String,
    val username: String,
    val password: String,
    val rememberPassword: Boolean,
) {
    val canAttemptCredentialedFeedSync: Boolean
        get() = authMode == AuthMode.CredentialedFeed &&
            feedUrl.isNotBlank()
}
