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

        store.setFeedUrl("https://www.giantbomb.com/feeds/premium-videos/?token=super-secret")
        store.setUsername("jorge")
        store.setRememberPassword(true)
        store.setRememberedPassword("remembered-secret")

        assertEquals("https://www.giantbomb.com/feeds/premium-videos/?token=super-secret", store.persistedConfiguration.value.feedUrl)
        assertEquals("jorge", store.persistedConfiguration.value.username)
        assertEquals("remembered-secret", store.persistedConfiguration.value.rememberedPassword)
        assertTrue(store.persistedConfiguration.value.rememberPassword)
        assertEquals(1, preferences.all.size)
        assertFalse(preferences.all.values.single().toString().contains("super-secret"))
        assertFalse(preferences.all.values.single().toString().contains("remembered-secret"))
        assertFalse(preferences.contains("feed_url"))
        assertFalse(preferences.contains("username"))
    }

    @Test
    fun migratesLegacyPlaintextValuesIntoEncryptedPayloadOnInit() {
        val preferences = createPreferences("migrate-legacy")
        preferences.edit()
            .putString("feed_url", "https://www.giantbomb.com/feeds/premium-videos/?token=legacy-secret")
            .putString("username", "jorge")
            .apply()

        val store = SharedPreferencesFeedConfigurationStore(
            preferences = preferences,
            securePayloadCipher = PrefixingSecurePayloadCipher(),
        )

        assertEquals("https://www.giantbomb.com/feeds/premium-videos/?token=legacy-secret", store.persistedConfiguration.value.feedUrl)
        assertEquals("jorge", store.persistedConfiguration.value.username)
        assertEquals("", store.persistedConfiguration.value.rememberedPassword)
        assertFalse(store.persistedConfiguration.value.rememberPassword)
        assertEquals(1, preferences.all.size)
        assertFalse(preferences.contains("feed_url"))
        assertFalse(preferences.contains("username"))
    }

    @Test
    fun fallsBackToLegacyPreferencesWhenSecureCipherUnavailable() = runTest {
        val preferences = createPreferences("cipher-failure")
        val store = SharedPreferencesFeedConfigurationStore(
            preferences = preferences,
            securePayloadCipher = FailingSecurePayloadCipher(),
        )

        store.setFeedUrl("https://www.giantbomb.com/feeds/premium-videos/?token=fallback-secret")
        store.setUsername("jorge")
        store.setRememberPassword(true)
        store.setRememberedPassword("do-not-persist")

        assertEquals("https://www.giantbomb.com/feeds/premium-videos/?token=fallback-secret", store.persistedConfiguration.value.feedUrl)
        assertEquals("jorge", store.persistedConfiguration.value.username)
        assertEquals("", store.persistedConfiguration.value.rememberedPassword)
        assertFalse(store.persistedConfiguration.value.rememberPassword)
        assertTrue(preferences.contains("feed_url"))
        assertTrue(preferences.contains("username"))
        assertFalse(preferences.all.values.any { it.toString().contains("do-not-persist") })
        assertFalse(preferences.all.keys.any { it.contains("encrypted") })
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
