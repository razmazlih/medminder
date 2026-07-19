package com.medminder.app.core.security

import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object SyncCryptoPrimitives {
    private const val AES_KEY_SIZE_BYTES = 32
    private const val GCM_TAG_SIZE_BITS = 128
    private const val GCM_IV_SIZE_BYTES = 12
    private const val HMAC_ALGO = "HmacSHA256"
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"

    data class EncryptedPayload(
        val iv: ByteArray,
        val ciphertext: ByteArray
    )

    fun deriveDataKey(syncCode: String): ByteArray {
        val ikm = syncCode.trim().toByteArray(StandardCharsets.UTF_8)
        val salt = "medminder-sync-salt-v1".toByteArray(StandardCharsets.UTF_8)
        val info = "medminder-sync-data-key".toByteArray(StandardCharsets.UTF_8)
        return hkdfSha256(ikm = ikm, salt = salt, info = info, length = AES_KEY_SIZE_BYTES)
    }

    fun encryptAesGcm(plaintext: ByteArray, key: ByteArray, aad: ByteArray): EncryptedPayload {
        val iv = ByteArray(GCM_IV_SIZE_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_SIZE_BITS, iv))
        cipher.updateAAD(aad)
        return EncryptedPayload(iv = iv, ciphertext = cipher.doFinal(plaintext))
    }

    fun decryptAesGcm(ciphertext: ByteArray, iv: ByteArray, key: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_SIZE_BITS, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }

    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val prkMac = Mac.getInstance(HMAC_ALGO)
        prkMac.init(SecretKeySpec(salt, HMAC_ALGO))
        val prk = prkMac.doFinal(ikm)

        val okm = ByteArray(length)
        var generated = 0
        var counter: Byte = 1
        var previous = ByteArray(0)
        while (generated < length) {
            val mac = Mac.getInstance(HMAC_ALGO)
            mac.init(SecretKeySpec(prk, HMAC_ALGO))
            mac.update(previous)
            mac.update(info)
            mac.update(counter)
            previous = mac.doFinal()
            val toCopy = minOf(previous.size, length - generated)
            previous.copyInto(okm, generated, 0, toCopy)
            generated += toCopy
            counter = (counter + 1).toByte()
        }
        return okm
    }
}
