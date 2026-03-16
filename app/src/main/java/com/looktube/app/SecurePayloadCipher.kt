package com.looktube.app

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.charset.StandardCharsets.UTF_8
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal interface SecurePayloadCipher {
    fun encrypt(plaintext: String): String

    fun decrypt(ciphertext: String): String
}

internal class AndroidKeystoreSecurePayloadCipher(
    private val keyAlias: String,
) : SecurePayloadCipher {
    override fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encryptedBytes = cipher.doFinal(plaintext.toByteArray(UTF_8))
        return buildString {
            append(Base64.getEncoder().encodeToString(cipher.iv))
            append(PAYLOAD_SEPARATOR)
            append(Base64.getEncoder().encodeToString(encryptedBytes))
        }
    }

    override fun decrypt(ciphertext: String): String {
        val payloadParts = ciphertext.split(PAYLOAD_SEPARATOR, limit = 2)
        require(payloadParts.size == 2) { "Encrypted payload is malformed." }
        val iv = Base64.getDecoder().decode(payloadParts[0])
        val encryptedBytes = Base64.getDecoder().decode(payloadParts[1])
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv),
        )
        return String(cipher.doFinal(encryptedBytes), UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setKeySize(KEY_SIZE_BITS)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    private companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PAYLOAD_SEPARATOR = ":"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val KEY_SIZE_BITS = 256
    }
}
