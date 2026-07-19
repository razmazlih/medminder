package com.medminder.app.core.security

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SyncCryptoPrimitivesTest {

    @Test
    fun deriveDataKeyIsDeterministicAndSized() {
        val keyA = SyncCryptoPrimitives.deriveDataKey("SYNC-CODE-12345")
        val keyB = SyncCryptoPrimitives.deriveDataKey("SYNC-CODE-12345")
        val keyC = SyncCryptoPrimitives.deriveDataKey("SYNC-CODE-ABCDE")

        assertEquals(32, keyA.size)
        assertArrayEquals(keyA, keyB)
        assertFalse(keyA.contentEquals(keyC))
    }

    @Test
    fun aesGcmRoundTripWithAad() {
        val key = SyncCryptoPrimitives.deriveDataKey("SYNC-CODE-12345")
        val plaintext = """{"name":"אקמול","dose":"1"}""".toByteArray(StandardCharsets.UTF_8)
        val aad = "dataset|MEDICATION|med-1|100".toByteArray(StandardCharsets.UTF_8)

        val encrypted = SyncCryptoPrimitives.encryptAesGcm(plaintext = plaintext, key = key, aad = aad)
        val decrypted = SyncCryptoPrimitives.decryptAesGcm(
            ciphertext = encrypted.ciphertext,
            iv = encrypted.iv,
            key = key,
            aad = aad
        )

        assertArrayEquals(plaintext, decrypted)
    }
}
