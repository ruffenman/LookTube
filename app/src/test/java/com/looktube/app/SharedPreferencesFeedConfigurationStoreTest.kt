package com.looktube.app

import android.content.Context
import android.content.SharedPreferences
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharedPreferencesFeedConfigurationStoreTest {
    @Test
    fun persistsEncryptedPayloadInsteadOfLegacyPlaintextKeys() = runTest {
        val preferences = createPreferences("secure-write")
        val store = SharedPreferencesFeedConfigurationStore(
            preferences = preferences,
            securePayloadCipher = PrefixingSecurePayloadCipher(),
        )

        store.save(
            com.looktube.model.PersistedFeedConfiguration(
                feedUrl = "https://www.giantbomb.com/feeds/premium-videos/?token=super-secret",
            ),
        )

        assertEquals("https://www.giantbomb.com/feeds/premium-videos/?token=super-secret", store.persistedConfiguration.value.feedUrl)
        assertEquals(1, preferences.all.size)
        assertFalse(preferences.all.values.single().toString().contains("super-secret"))
        assertFalse(preferences.contains("feed_url"))
    }

    @Test
    fun migratesLegacyPlaintextFeedUrlIntoEncryptedPayloadOnInit() {
        val preferences = createPreferences("migrate-legacy")
        preferences.edit()
            .putString("feed_url", "https://www.giantbomb.com/feeds/premium-videos/?token=legacy-secret")
            .apply()

        val store = SharedPreferencesFeedConfigurationStore(
            preferences = preferences,
            securePayloadCipher = PrefixingSecurePayloadCipher(),
        )

        assertEquals("https://www.giantbomb.com/feeds/premium-videos/?token=legacy-secret", store.persistedConfiguration.value.feedUrl)
        assertEquals(1, preferences.all.size)
        assertFalse(preferences.contains("feed_url"))
    }

    @Test
    fun fallsBackToLegacyPreferencesWhenSecureCipherUnavailable() = runTest {
        val preferences = createPreferences("cipher-failure")
        val store = SharedPreferencesFeedConfigurationStore(
            preferences = preferences,
            securePayloadCipher = FailingSecurePayloadCipher(),
        )

        store.save(
            com.looktube.model.PersistedFeedConfiguration(
                feedUrl = "https://www.giantbomb.com/feeds/premium-videos/?token=fallback-secret",
            ),
        )

        assertEquals("https://www.giantbomb.com/feeds/premium-videos/?token=fallback-secret", store.persistedConfiguration.value.feedUrl)
        assertTrue(preferences.contains("feed_url"))
        assertFalse(preferences.all.keys.any { it.contains("encrypted") })
    }

    @Test
    fun ignoresBooleanOnlyEncryptedPayloadRestoredFromLegacyState() {
        val preferences = createPreferences("boolean-only-payload")
        preferences.edit()
            .putString("encrypted_payload_v1", encryptForTest("false"))
            .apply()

        val store = SharedPreferencesFeedConfigurationStore(
            preferences = preferences,
            securePayloadCipher = PrefixingSecurePayloadCipher(),
        )

        assertEquals("", store.persistedConfiguration.value.feedUrl)
        assertFalse(store.persistedConfiguration.value.autoGenerateCaptionsForNewVideos)
    }

    @Test
    fun migratesEncryptedPayloadWithLegacyBooleanPrefixWithoutPromotingItToFeedUrl() {
        val preferences = createPreferences("legacy-boolean-prefix")
        preferences.edit()
            .putString(
                "encrypted_payload_v1",
                encryptForTest(
                    listOf(
                        "false",
                        "https://www.giantbomb.com/feeds/premium-videos/?token=migrated-secret",
                        "true",
                        "7",
                        "20177",
                        "88",
                        "5",
                    ).joinToString("|") { field ->
                        Base64.getEncoder().encodeToString(field.toByteArray(UTF_8))
                    },
                ),
            )
            .apply()

        val store = SharedPreferencesFeedConfigurationStore(
            preferences = preferences,
            securePayloadCipher = PrefixingSecurePayloadCipher(),
        )

        assertEquals("https://www.giantbomb.com/feeds/premium-videos/?token=migrated-secret", store.persistedConfiguration.value.feedUrl)
        assertTrue(store.persistedConfiguration.value.autoGenerateCaptionsForNewVideos)
        assertEquals(7, store.persistedConfiguration.value.dailyOpenPointCount)
        assertEquals(20_177L, store.persistedConfiguration.value.lastOpenedLocalEpochDay)
        assertEquals(88L, store.persistedConfiguration.value.launchIntroMessageDeckSeed)
        assertEquals(5, store.persistedConfiguration.value.launchIntroMessageDeckIndex)
        assertEquals(null, store.persistedConfiguration.value.lastOpenedAtEpochMillis)
    }

    @Test
    fun persistsAutoCaptionAndDailyOpenFieldsInEncryptedPayload() = runTest {
        val preferences = createPreferences("extended-fields")
        val store = SharedPreferencesFeedConfigurationStore(
            preferences = preferences,
            securePayloadCipher = PrefixingSecurePayloadCipher(),
        )

        store.save(
            com.looktube.model.PersistedFeedConfiguration(
                feedUrl = "https://www.giantbomb.com/feeds/premium-videos/?token=extended-secret",
                autoGenerateCaptionsForNewVideos = true,
                dailyOpenPointCount = 7,
                lastOpenedLocalEpochDay = 20_177L,
                launchIntroMessageDeckSeed = 88L,
                launchIntroMessageDeckIndex = 5,
                lastOpenedAtEpochMillis = 1_744_761_600_000L,
            ),
        )

        assertEquals("https://www.giantbomb.com/feeds/premium-videos/?token=extended-secret", store.persistedConfiguration.value.feedUrl)
        assertTrue(store.persistedConfiguration.value.autoGenerateCaptionsForNewVideos)
        assertEquals(7, store.persistedConfiguration.value.dailyOpenPointCount)
        assertEquals(20_177L, store.persistedConfiguration.value.lastOpenedLocalEpochDay)
        assertEquals(88L, store.persistedConfiguration.value.launchIntroMessageDeckSeed)
        assertEquals(5, store.persistedConfiguration.value.launchIntroMessageDeckIndex)
        assertEquals(1_744_761_600_000L, store.persistedConfiguration.value.lastOpenedAtEpochMillis)
    }

    private fun createPreferences(nameSuffix: String): SharedPreferences {
        val context = RuntimeEnvironment.getApplication().applicationContext as Context
        return context.getSharedPreferences("looktube.feed.config.test.$nameSuffix", Context.MODE_PRIVATE).apply {
            edit().clear().apply()
        }
    }

    private fun encryptForTest(plaintext: String): String = "enc:${plaintext.reversed()}"
}

private class PrefixingSecurePayloadCipher : SecurePayloadCipher {
    override fun encrypt(plaintext: String): String = "enc:${plaintext.reversed()}"

    override fun decrypt(ciphertext: String): String = ciphertext.removePrefix("enc:").reversed()
}

private class FailingSecurePayloadCipher : SecurePayloadCipher {
    override fun encrypt(plaintext: String): String = error("Simulated secure storage failure")

    override fun decrypt(ciphertext: String): String = error("Simulated secure storage failure")
}
