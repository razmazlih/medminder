package com.medminder.app.core.security

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PinHasher @Inject constructor() {

    data class HashedPin(
        val saltBase64: String,
        val hashBase64: String,
        val iterations: Int
    )

    fun hash(pin: CharArray, iterations: Int = 120_000): HashedPin {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val keySpec = PBEKeySpec(pin, salt, iterations, 256)
        val hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec).encoded
        keySpec.clearPassword()
        return HashedPin(
            saltBase64 = android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP),
            hashBase64 = android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP),
            iterations = iterations
        )
    }

    fun verify(pin: CharArray, saltBase64: String, hashBase64: String, iterations: Int): Boolean {
        val salt = android.util.Base64.decode(saltBase64, android.util.Base64.NO_WRAP)
        val expected = android.util.Base64.decode(hashBase64, android.util.Base64.NO_WRAP)
        val keySpec = PBEKeySpec(pin, salt, iterations, expected.size * 8)
        val actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec).encoded
        keySpec.clearPassword()
        return actual.contentEquals(expected)
    }
}
