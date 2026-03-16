package com.looktube.app

import android.content.Context
import android.content.SharedPreferences
import com.looktube.data.FeedConfigurationStore
import com.looktube.model.AuthMode
import com.looktube.model.PersistedFeedConfiguration
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SharedPreferencesFeedConfigurationStore internal constructor(
    private val preferences: SharedPreferences,
    private val securePayloadCipher: SecurePayloadCipher,
) : FeedConfigurationStore {
    constructor(context: Context) : this(
        preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
        securePayloadCipher = AndroidKeystoreSecurePayloadCipher(KEYSTORE_ALIAS),
    )

    private val persistedConfigurationState = MutableStateFlow(readFromDisk())

    override val persistedConfiguration: StateFlow<PersistedFeedConfiguration> =
        persistedConfigurationState.asStateFlow()

    override suspend fun setAuthMode(mode: AuthMode?) {
        writeToDisk(persistedConfigurationState.value.copy(authMode = mode))
        persistedConfigurationState.value = readFromDisk()
    }

    override suspend fun setFeedUrl(feedUrl: String) {
        writeToDisk(persistedConfigurationState.value.copy(feedUrl = feedUrl))
        persistedConfigurationState.value = readFromDisk()
    }

    override suspend fun setUsername(username: String) {
        writeToDisk(persistedConfigurationState.value.copy(username = username))
        persistedConfigurationState.value = readFromDisk()
    }

    override suspend fun setRememberPassword(rememberPassword: Boolean) {
        writeToDisk(
            persistedConfigurationState.value.copy(
                rememberedPassword = if (rememberPassword) {
                    persistedConfigurationState.value.rememberedPassword
                } else {
                    ""
                },
                rememberPassword = rememberPassword,
            ),
        )
        persistedConfigurationState.value = readFromDisk()
    }

    override suspend fun setRememberedPassword(password: String) {
        writeToDisk(
            persistedConfigurationState.value.copy(
                rememberedPassword = if (persistedConfigurationState.value.rememberPassword) {
                    password
                } else {
                    ""
                },
            ),
        )
        persistedConfigurationState.value = readFromDisk()
    }

    private fun readFromDisk(): PersistedFeedConfiguration {
        val encryptedPayload = preferences.getString(KEY_ENCRYPTED_PAYLOAD, null)
        if (!encryptedPayload.isNullOrBlank()) {
            securePayloadCipher.decryptOrNull(encryptedPayload)
                ?.let(::deserializePersistedFeedConfigurationOrNull)
                ?.let { return it }
        }

        val legacyConfiguration = readLegacyFromDisk() ?: return PersistedFeedConfiguration(
            authMode = null,
            feedUrl = "",
            username = "",
            rememberedPassword = "",
            rememberPassword = false,
        )
        if (persistSecure(legacyConfiguration)) {
            clearLegacyEntries()
        }
        return legacyConfiguration
    }

    private fun readLegacyFromDisk(): PersistedFeedConfiguration? {
        val hasLegacyValues = preferences.contains(KEY_AUTH_MODE) ||
            preferences.contains(KEY_FEED_URL) ||
            preferences.contains(KEY_USERNAME)
        if (!hasLegacyValues) {
            return null
        }
        return PersistedFeedConfiguration(
            authMode = preferences.getString(KEY_AUTH_MODE, null)
                ?.takeIf(String::isNotBlank)
                ?.let { value -> runCatching { AuthMode.valueOf(value) }.getOrNull() },
            feedUrl = preferences.getString(KEY_FEED_URL, "").orEmpty(),
            username = preferences.getString(KEY_USERNAME, "").orEmpty(),
            rememberedPassword = "",
            rememberPassword = false,
        )
    }

    private fun writeToDisk(configuration: PersistedFeedConfiguration) {
        if (persistSecure(configuration)) {
            clearLegacyEntries()
            return
        }
        preferences.edit()
            .remove(KEY_ENCRYPTED_PAYLOAD)
            .putString(KEY_AUTH_MODE, configuration.authMode?.name)
            .putString(KEY_FEED_URL, configuration.feedUrl)
            .putString(KEY_USERNAME, configuration.username)
            .apply()
    }

    private fun persistSecure(configuration: PersistedFeedConfiguration): Boolean {
        val encryptedPayload = securePayloadCipher.encryptOrNull(serializePersistedFeedConfiguration(configuration))
            ?: return false
        preferences.edit()
            .putString(KEY_ENCRYPTED_PAYLOAD, encryptedPayload)
            .apply()
        return true
    }

    private fun clearLegacyEntries() {
        preferences.edit()
            .remove(KEY_AUTH_MODE)
            .remove(KEY_FEED_URL)
            .remove(KEY_USERNAME)
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "looktube.feed.config"
        private const val KEYSTORE_ALIAS = "looktube.feed.config.aes"
        private const val KEY_AUTH_MODE = "auth_mode"
        private const val KEY_FEED_URL = "feed_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_ENCRYPTED_PAYLOAD = "encrypted_payload_v1"
        private const val SERIALIZED_SEPARATOR = "|"

        private fun serializePersistedFeedConfiguration(configuration: PersistedFeedConfiguration): String =
            listOf(
                configuration.authMode?.name.orEmpty(),
                configuration.feedUrl,
                configuration.username,
                configuration.rememberedPassword.takeIf { configuration.rememberPassword }.orEmpty(),
                configuration.rememberPassword.toString(),
            ).joinToString(SERIALIZED_SEPARATOR) { field ->
                Base64.getEncoder().encodeToString(field.toByteArray(UTF_8))
            }

        private fun deserializePersistedFeedConfigurationOrNull(
            serializedConfiguration: String,
        ): PersistedFeedConfiguration? {
            val parts = serializedConfiguration.split(SERIALIZED_SEPARATOR)
            if (parts.size != 3 && parts.size != 5) {
                return null
            }
            val decodedParts = parts.map { encodedField ->
                runCatching {
                    String(Base64.getDecoder().decode(encodedField), UTF_8)
                }.getOrNull()
            }
            if (decodedParts.any { it == null }) {
                return null
            }
            return PersistedFeedConfiguration(
                authMode = decodedParts[0]
                    ?.takeIf(String::isNotBlank)
                    ?.let { value -> runCatching { AuthMode.valueOf(value) }.getOrNull() },
                feedUrl = decodedParts[1].orEmpty(),
                username = decodedParts[2].orEmpty(),
                rememberedPassword = if (decodedParts.size >= 5 && decodedParts[4].toBoolean()) {
                    decodedParts[3].orEmpty()
                } else {
                    ""
                },
                rememberPassword = decodedParts.getOrNull(4)?.toBoolean() == true,
            )
        }
    }
}

private fun SecurePayloadCipher.encryptOrNull(plaintext: String): String? =
    runCatching { encrypt(plaintext) }.getOrNull()

private fun SecurePayloadCipher.decryptOrNull(ciphertext: String): String? =
    runCatching { decrypt(ciphertext) }.getOrNull()
