/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.remote

import io.github.thake.soundnest.feature.parental.remote.crypto.RemoteCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.AEADBadTagException

class RemoteCryptoTest {

    @Test
    fun ecdhSharedSecretMatchesOnBothSides() {
        val alice = RemoteCrypto.generateEcdhKeyPair()
        val bob = RemoteCrypto.generateEcdhKeyPair()

        val sharedA = RemoteCrypto.deriveEcdhSharedSecret(alice.private, bob.public.encoded)
        val sharedB = RemoteCrypto.deriveEcdhSharedSecret(bob.private, alice.public.encoded)

        assertArrayEquals(sharedA, sharedB)
        assertTrue(sharedA.size >= 32)
    }

    @Test
    fun permanentSecretDerivationIsDeterministicAndContextBound() {
        val shared = RemoteCrypto.randomBytes(32)
        val nonce = RemoteCrypto.randomBytes(16)

        val secret1 = RemoteCrypto.derivePermanentSecret(shared, nonce, "parent-1", "child-1")
        val secret2 = RemoteCrypto.derivePermanentSecret(shared, nonce, "parent-1", "child-1")
        val secretOtherChild = RemoteCrypto.derivePermanentSecret(shared, nonce, "parent-1", "child-2")

        assertEquals(32, secret1.size)
        assertArrayEquals(secret1, secret2)
        assertFalse(secret1.contentEquals(secretOtherChild))
    }

    @Test
    fun aesGcmRoundTripAndTamperRejection() {
        val key = RemoteCrypto.randomBytes(32)
        val aad = "soundnest-remote-v1|p1|c1".toByteArray()
        val plain = "hello child device".toByteArray()

        val (iv, ct) = RemoteCrypto.encryptAesGcm(key, plain, aad)
        val decrypted = RemoteCrypto.decryptAesGcm(key, iv, ct, aad)
        assertArrayEquals(plain, decrypted)

        // Modified ciphertext must fail GCM tag verification
        val tampered = ct.copyOf().also { it[0] = (it[0].toInt() xor 0xFF).toByte() }
        var threw = false
        try {
            RemoteCrypto.decryptAesGcm(key, iv, tampered, aad)
        } catch (_: AEADBadTagException) {
            threw = true
        }
        assertTrue("Tampered ciphertext must fail authentication", threw)

        // Wrong AAD must fail GCM tag verification
        var wrongAadThrew = false
        try {
            RemoteCrypto.decryptAesGcm(key, iv, ct, "soundnest-remote-v1|p1|c2".toByteArray())
        } catch (_: AEADBadTagException) {
            wrongAadThrew = true
        }
        assertTrue("Wrong AAD must fail authentication", wrongAadThrew)

        // Wrong key must fail GCM tag verification
        val otherKey = RemoteCrypto.randomBytes(32)
        var wrongKeyThrew = false
        try {
            RemoteCrypto.decryptAesGcm(otherKey, iv, ct, aad)
        } catch (_: AEADBadTagException) {
            wrongKeyThrew = true
        }
        assertTrue("Wrong key must fail authentication", wrongKeyThrew)
    }

    @Test
    fun pairingKeyDerivationIsDeterministicAndSensitiveToInputs() {
        val nonce = RemoteCrypto.randomBytes(16)
        val k1 = RemoteCrypto.derivePairingKey("482917", nonce, "child-a")
        val k2 = RemoteCrypto.derivePairingKey("482917", nonce, "child-a")
        val kWrongCode = RemoteCrypto.derivePairingKey("482918", nonce, "child-a")
        val kWrongChild = RemoteCrypto.derivePairingKey("482917", nonce, "child-b")

        assertEquals(32, k1.size)
        assertArrayEquals(k1, k2)
        assertFalse(k1.contentEquals(kWrongCode))
        assertFalse(k1.contentEquals(kWrongChild))
    }

    @Test
    fun sixDigitCodeIsAlwaysSixDigits() {
        repeat(50) {
            val code = RemoteCrypto.generateSixDigitCode()
            assertEquals(6, code.length)
            assertTrue(code.all { it.isDigit() })
        }
    }
}
