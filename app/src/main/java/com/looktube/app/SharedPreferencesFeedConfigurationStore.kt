package com.looktube.app

import android.content.Context
import android.content.SharedPreferences
import com.looktube.data.FeedConfigurationStore
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


    override suspend fun setFeedUrl(feedUrl: String) {
        writeToDisk(persistedConfigurationState.value.copy(feedUrl = feedUrl))
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
            feedUrl = "",
        )
        if (persistSecure(legacyConfiguration)) {
            clearLegacyEntries()
        }
        return legacyConfiguration
    }

    private fun readLegacyFromDisk(): PersistedFeedConfiguration? {
        val hasLegacyValues = preferences.all.keys.any { key ->
            key != KEY_ENCRYPTED_PAYLOAD
        }
        if (!hasLegacyValues) {
            return null
        }
        return PersistedFeedConfiguration(
            feedUrl = preferences.getString(KEY_FEED_URL, "").orEmpty(),
        )
    }

    private fun writeToDisk(configuration: PersistedFeedConfiguration) {
        if (persistSecure(configuration)) {
            clearLegacyEntries()
            return
        }
        preferences.edit()
            .remove(KEY_ENCRYPTED_PAYLOAD)
            .putString(KEY_FEED_URL, configuration.feedUrl)
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
        val legacyKeys = preferences.all.keys.filter { key ->
            key != KEY_ENCRYPTED_PAYLOAD
        }
        preferences.edit().apply {
            legacyKeys.forEach(::remove)
            apply()
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "looktube.feed.config"
        private const val KEYSTORE_ALIAS = "looktube.feed.config.aes"
        private const val KEY_AUTH_MODE = "auth_mode"
        private const val KEY_FEED_URL = "feed_url"
        private const val KEY_ENCRYPTED_PAYLOAD = "encrypted_payload_v1"
        private const val SERIALIZED_SEPARATOR = "|"

        private fun serializePersistedFeedConfiguration(configuration: PersistedFeedConfiguration): String =
            listOf(
                configuration.feedUrl,
            ).joinToString(SERIALIZED_SEPARATOR) { field ->
                Base64.getEncoder().encodeToString(field.toByteArray(UTF_8))
            }

        private fun deserializePersistedFeedConfigurationOrNull(
            serializedConfiguration: String,
        ): PersistedFeedConfiguration? {
            val parts = serializedConfiguration.split(SERIALIZED_SEPARATOR)
            if (parts.size !in setOf(1, 2, 3, 4, 5)) {
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
            val isLegacyPayload = decodedParts.size >= 3
            val fieldOffset = if (isLegacyPayload) 1 else 0
            return PersistedFeedConfiguration(
                feedUrl = decodedParts[fieldOffset].orEmpty(),
            )
        }
    }
}

private fun SecurePayloadCipher.encryptOrNull(plaintext: String): String? =
    runCatching { encrypt(plaintext) }.getOrNull()

private fun SecurePayloadCipher.decryptOrNull(ciphertext: String): String? =
    runCatching { decrypt(ciphertext) }.getOrNull()
