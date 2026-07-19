package com.medminder.app.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

@Singleton
class SyncCryptoManager @Inject constructor() {
    private companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val WRAP_KEY_ALIAS = "medminder_sync_wrap_key_v1"
        const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }

    fun generateSyncCode(length: Int = 32): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        return buildString(length) {
            repeat(length) {
                append(alphabet[random.nextInt(alphabet.length)])
            }
        }
    }

    fun deriveDataKey(syncCode: String): ByteArray = SyncCryptoPrimitives.deriveDataKey(syncCode)

    fun wrapKey(rawKey: ByteArray): String {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateWrapKey())
        val encrypted = cipher.doFinal(rawKey)
        val iv = cipher.iv
        return JSONObject().apply {
            put("iv", Base64.getEncoder().encodeToString(iv))
            put("data", Base64.getEncoder().encodeToString(encrypted))
        }.toString()
    }

    fun unwrapKey(wrapped: String): ByteArray {
        val obj = JSONObject(wrapped)
        val iv = Base64.getDecoder().decode(obj.getString("iv"))
        val encrypted = Base64.getDecoder().decode(obj.getString("data"))
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateWrapKey(),
            GCMParameterSpec(GCM_TAG_BITS, iv)
        )
        return cipher.doFinal(encrypted)
    }

    fun encryptPayload(plaintextJson: String, key: ByteArray, aad: String): Pair<String, String> {
        val encrypted = SyncCryptoPrimitives.encryptAesGcm(
            plaintext = plaintextJson.toByteArray(StandardCharsets.UTF_8),
            key = key,
            aad = aad.toByteArray(StandardCharsets.UTF_8)
        )
        return Base64.getEncoder().encodeToString(encrypted.iv) to
            Base64.getEncoder().encodeToString(encrypted.ciphertext)
    }

    fun decryptPayload(ciphertextBase64: String, ivBase64: String, key: ByteArray, aad: String): String {
        val plaintext = SyncCryptoPrimitives.decryptAesGcm(
            ciphertext = Base64.getDecoder().decode(ciphertextBase64),
            iv = Base64.getDecoder().decode(ivBase64),
            key = key,
            aad = aad.toByteArray(StandardCharsets.UTF_8)
        )
        return String(plaintext, StandardCharsets.UTF_8)
    }

    private fun getOrCreateWrapKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getKey(WRAP_KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                WRAP_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return keyGenerator.generateKey()
    }
}
