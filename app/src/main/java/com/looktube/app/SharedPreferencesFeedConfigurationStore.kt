package com.looktube.app

import android.content.Context
import com.looktube.data.FeedConfigurationStore
import com.looktube.model.AuthMode
import com.looktube.model.PersistedFeedConfiguration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SharedPreferencesFeedConfigurationStore(
    context: Context,
) : FeedConfigurationStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val persistedConfigurationState = MutableStateFlow(readFromDisk())

    override val persistedConfiguration: StateFlow<PersistedFeedConfiguration> =
        persistedConfigurationState.asStateFlow()

    override suspend fun setAuthMode(mode: AuthMode?) {
        preferences.edit()
            .putString(KEY_AUTH_MODE, mode?.name)
            .apply()
        persistedConfigurationState.value = readFromDisk()
    }

    override suspend fun setFeedUrl(feedUrl: String) {
        preferences.edit()
            .putString(KEY_FEED_URL, feedUrl)
            .apply()
        persistedConfigurationState.value = readFromDisk()
    }

    override suspend fun setUsername(username: String) {
        preferences.edit()
            .putString(KEY_USERNAME, username)
            .apply()
        persistedConfigurationState.value = readFromDisk()
    }

    private fun readFromDisk(): PersistedFeedConfiguration =
        PersistedFeedConfiguration(
            authMode = preferences.getString(KEY_AUTH_MODE, null)?.let(AuthMode::valueOf),
            feedUrl = preferences.getString(KEY_FEED_URL, "").orEmpty(),
            username = preferences.getString(KEY_USERNAME, "").orEmpty(),
        )

    companion object {
        private const val PREFERENCES_NAME = "looktube.feed.config"
        private const val KEY_AUTH_MODE = "auth_mode"
        private const val KEY_FEED_URL = "feed_url"
        private const val KEY_USERNAME = "username"
    }
}
