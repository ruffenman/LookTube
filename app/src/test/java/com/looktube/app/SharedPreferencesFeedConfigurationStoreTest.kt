package com.looktube.app

import android.content.Context
import android.content.SharedPreferences
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
                launchIntroQuoteDeckSeed = 88L,
                launchIntroQuoteDeckIndex = 5,
            ),
        )

        assertEquals("https://www.giantbomb.com/feeds/premium-videos/?token=extended-secret", store.persistedConfiguration.value.feedUrl)
        assertTrue(store.persistedConfiguration.value.autoGenerateCaptionsForNewVideos)
        assertEquals(7, store.persistedConfiguration.value.dailyOpenPointCount)
        assertEquals(20_177L, store.persistedConfiguration.value.lastOpenedLocalEpochDay)
        assertEquals(88L, store.persistedConfiguration.value.launchIntroQuoteDeckSeed)
        assertEquals(5, store.persistedConfiguration.value.launchIntroQuoteDeckIndex)
    }

    private fun createPreferences(nameSuffix: String): SharedPreferences {
        val context = RuntimeEnvironment.getApplication().applicationContext as Context
        return context.getSharedPreferences("looktube.feed.config.test.$nameSuffix", Context.MODE_PRIVATE).apply {
            edit().clear().apply()
        }
    }
}

private class PrefixingSecurePayloadCipher : SecurePayloadCipher {
    override fun encrypt(plaintext: String): String = "enc:${plaintext.reversed()}"

    override fun decrypt(ciphertext: String): String = ciphertext.removePrefix("enc:").reversed()
}

private class FailingSecurePayloadCipher : SecurePayloadCipher {
    override fun encrypt(plaintext: String): String = error("Simulated secure storage failure")

    override fun decrypt(ciphertext: String): String = error("Simulated secure storage failure")
}
