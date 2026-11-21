/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.remote.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cryptographic primitives for authenticated pairing and AES-256-GCM encrypted LAN control
 * between a Parent Device and paired Child Devices on the same Wi-Fi network.
 */
object RemoteCrypto {
    internal const val KEYSTORE_ALIAS = "soundnest_remote_pairing_key"
    private const val PBKDF2_ITERATIONS = 10_000
    private const val KEY_BITS = 256
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128

    private val secureRandom = SecureRandom()

    fun randomBytes(count: Int): ByteArray = ByteArray(count).also(secureRandom::nextBytes)

    fun generateSixDigitCode(): String {
        val value = secureRandom.nextInt(1_000_000)
        return value.toString().padStart(6, '0')
    }

    fun generateEcdhKeyPair(): KeyPair {
        val gen = KeyPairGenerator.getInstance("EC")
        gen.initialize(ECGenParameterSpec("secp256r1"), secureRandom)
        return gen.generateKeyPair()
    }

    fun deriveEcdhSharedSecret(privateKey: PrivateKey, peerPublicX509: ByteArray): ByteArray {
        val pub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(peerPublicX509))
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(privateKey)
        ka.doPhase(pub, true)
        return ka.generateSecret()
    }

    /**
     * Derives a 256-bit temporary pairing authentication key from the 6-digit pairing [code],
     * random pairing [nonce], and [childId].
     */
    fun derivePairingKey(code: String, nonce: ByteArray, childId: String): ByteArray {
        val salt = nonce + childId.toByteArray(Charsets.UTF_8)
        val chars = code.trim().toCharArray()
        return try {
            val spec = PBEKeySpec(chars, salt, PBKDF2_ITERATIONS, KEY_BITS)
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            chars.fill('\u0000')
        }
    }

    /**
     * HKDF-SHA256 (extract + single-block expand) deriving the permanent 32-byte per-pair
     * AES-256-GCM secret from the ECDH shared secret and pairing transcript context.
     */
    fun derivePermanentSecret(
        ecdhSharedSecret: ByteArray,
        nonce: ByteArray,
        parentId: String,
        childId: String,
    ): ByteArray {
        val prk = hmacSha256(nonce, ecdhSharedSecret)
        val info = "soundnest-remote-v1|$parentId|$childId".toByteArray(Charsets.UTF_8) + byteArrayOf(0x01)
        return hmacSha256(prk, info)
    }

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    fun verifyHmacSha256(key: ByteArray, data: ByteArray, expectedMac: ByteArray): Boolean =
        MessageDigest.isEqual(hmacSha256(key, data), expectedMac)

    /** Encrypts [plaintext] with AES-256-GCM; returns Pair(iv, ciphertextWithTag). */
    fun encryptAesGcm(key: ByteArray, plaintext: ByteArray, aad: ByteArray): Pair<ByteArray, ByteArray> {
        require(key.size == 32) { "Expected 32-byte AES-256 key" }
        val iv = randomBytes(GCM_IV_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext)
        return iv to ciphertext
    }

    /** Decrypts [ciphertextWithTag] with AES-256-GCM; throws AEADBadTagException on tamper/wrong key. */
    fun decryptAesGcm(key: ByteArray, iv: ByteArray, ciphertextWithTag: ByteArray, aad: ByteArray): ByteArray {
        require(key.size == 32) { "Expected 32-byte AES-256 key" }
        require(iv.size == GCM_IV_BYTES) { "Expected 12-byte GCM IV" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertextWithTag)
    }

    /** Wraps a secret key blob with Android Keystore AES-GCM when available (null on Robolectric/JVM). */
    fun wrapWithKeystoreOrNull(plainSecret: ByteArray): ByteArray? = runCatching {
        val key = getOrCreateKeystoreSecretKey() ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plainSecret)
        ByteArray(1 + iv.size + ciphertext.size).also { out ->
            out[0] = iv.size.toByte()
            System.arraycopy(iv, 0, out, 1, iv.size)
            System.arraycopy(ciphertext, 0, out, 1 + iv.size, ciphertext.size)
        }
    }.getOrNull()

    fun unwrapFromKeystore(blob: ByteArray): ByteArray? = runCatching {
        if (blob.size < 2) return null
        val ivLen = blob[0].toInt() and 0xFF
        if (ivLen <= 0 || blob.size <= 1 + ivLen) return null
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = (ks.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey ?: return null
        val iv = blob.copyOfRange(1, 1 + ivLen)
        val ciphertext = blob.copyOfRange(1 + ivLen, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.doFinal(ciphertext)
    }.getOrNull()

    private fun getOrCreateKeystoreSecretKey(): SecretKey? = runCatching {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = (ks.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        if (existing != null) return@runCatching existing
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BITS)
            .build()
        keyGen.init(spec)
        keyGen.generateKey()
    }.getOrNull()
}
