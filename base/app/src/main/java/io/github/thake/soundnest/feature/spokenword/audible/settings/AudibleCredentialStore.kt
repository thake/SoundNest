/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.spokenword.audible.settings

import io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer

import io.github.thake.soundnest.audible.*

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.thake.soundnest.core.preferences.featurePrefs
import io.github.thake.soundnest.core.util.writeBytesSafely
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class AudibleAccountSummary(
    val marketplace: AudibleMarketplace,
    val customerName: String?,
    val connectedAtMs: Long,
)

/**
 * Hardware-backed AES-256-GCM storage for Audible virtual-device credentials.
 * Never exported by [io.github.thake.soundnest.feature.parental.settings.ParentalConfigBackup].
 */
object AudibleCredentialStore {
    internal const val PREFS_NAME = "feature_audible_credentials"
    private const val KEY_CIPHERTEXT = "tokens_gcm_blob"
    private const val KEY_MARKETPLACE = "marketplace_code"
    private const val KEY_CUSTOMER_NAME = "customer_name"
    private const val KEY_CONNECTED_AT = "connected_at_ms"
    private const val KEYSTORE_ALIAS = "soundnest_audible_credentials"
    private const val GCM_TAG_BITS = 128

    private val json = Json { ignoreUnknownKeys = true }
    private val secureRandom = SecureRandom()

    private val _summaryFlow = MutableStateFlow<AudibleAccountSummary?>(null)
    val summaryFlow: StateFlow<AudibleAccountSummary?> = _summaryFlow.asStateFlow()

    @Volatile
    private var cachedTokens: AudibleSessionTokens? = null

    @Volatile
    private var initialized = false

    private fun prefs(context: Context) =
        context.applicationContext.featurePrefs("audible_credentials")

    private fun fallbackKeyFile(context: Context): File =
        File(context.applicationContext.filesDir, "audible/.credential_wrap_key")

    private fun getOrCreateSecretKey(context: Context): SecretKey {
        val hwKey = runCatching {
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
                .setKeySize(256)
                .build()
            keyGen.init(spec)
            keyGen.generateKey()
        }.getOrNull()
        if (hwKey != null) return hwKey

        // JVM / Robolectric fallback where AndroidKeyStore hardware provider is absent:
        // still encrypts with real 256-bit AES-GCM using an isolated binary key file outside SharedPreferences.
        val file = fallbackKeyFile(context)
        if (file.exists()) {
            val raw = runCatching { file.readBytes() }.getOrNull()
            if (raw != null && raw.size == 32) return SecretKeySpec(raw, "AES")
        }
        val raw = ByteArray(32).also(secureRandom::nextBytes)
        writeBytesSafely(file, raw)
        return SecretKeySpec(raw, "AES")
    }

    private fun encrypt(context: Context, plaintext: ByteArray): ByteArray {
        val key = getOrCreateSecretKey(context)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return ByteArray(1 + iv.size + ciphertext.size).also { out ->
            out[0] = iv.size.toByte()
            System.arraycopy(iv, 0, out, 1, iv.size)
            System.arraycopy(ciphertext, 0, out, 1 + iv.size, ciphertext.size)
        }
    }

    private fun decrypt(context: Context, blob: ByteArray): ByteArray? = runCatching {
        if (blob.size < 2) return null
        val ivLen = blob[0].toInt() and 0xFF
        if (ivLen <= 0 || blob.size <= 1 + ivLen) return null
        val key = getOrCreateSecretKey(context)
        val iv = blob.copyOfRange(1, 1 + ivLen)
        val ciphertext = blob.copyOfRange(1 + ivLen, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.doFinal(ciphertext)
    }.getOrNull()

    @Synchronized
    private fun ensureLoaded(context: Context) {
        if (initialized) return
        val p = prefs(context)
        val encoded = p.getString(KEY_CIPHERTEXT, null)
        if (!encoded.isNullOrBlank()) {
            val decrypted = runCatching { Base64.decode(encoded, Base64.NO_WRAP) }
                .getOrNull()
                ?.let { decrypt(context, it) }
            val parsed = decrypted?.let {
                runCatching { json.decodeFromString<AudibleSessionTokens>(it.decodeToString()) }.getOrNull()
            }
            if (parsed != null) {
                cachedTokens = parsed
                val connectedAt = p.getLong(KEY_CONNECTED_AT, 0L)
                _summaryFlow.value = AudibleAccountSummary(
                    marketplace = parsed.marketplace,
                    customerName = parsed.customerName ?: p.getString(KEY_CUSTOMER_NAME, null),
                    connectedAtMs = connectedAt,
                )
            }
        }
        initialized = true
    }

    @Synchronized
    fun save(context: Context, tokens: AudibleSessionTokens, connectedAtMs: Long = System.currentTimeMillis()) {
        val rawBytes = json.encodeToString(tokens).encodeToByteArray()
        val encrypted = encrypt(context, rawBytes)
        prefs(context).edit()
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_MARKETPLACE, tokens.marketplace.code)
            .putString(KEY_CUSTOMER_NAME, tokens.customerName)
            .putLong(KEY_CONNECTED_AT, connectedAtMs)
            .commit()
        cachedTokens = tokens
        _summaryFlow.value = AudibleAccountSummary(
            marketplace = tokens.marketplace,
            customerName = tokens.customerName,
            connectedAtMs = connectedAtMs,
        )
        initialized = true
    }

    @Synchronized
    fun loadTokens(context: Context): AudibleSessionTokens? {
        ensureLoaded(context)
        return cachedTokens
    }

    @Synchronized
    fun summary(context: Context): AudibleAccountSummary? {
        ensureLoaded(context)
        return _summaryFlow.value
    }

    @Synchronized
    fun isConnected(context: Context): Boolean {
        ensureLoaded(context)
        return cachedTokens != null
    }

    @Synchronized
    fun isConnectedOrLoaded(): Boolean {
        if (initialized) return cachedTokens != null
        val app = runCatching { io.github.thake.soundnest.core.di.Graph.app }.getOrNull() ?: return false
        return isConnected(app)
    }

    @Synchronized
    fun clear(context: Context) {
        prefs(context).edit().clear().commit()
        runCatching {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (ks.containsAlias(KEYSTORE_ALIAS)) ks.deleteEntry(KEYSTORE_ALIAS)
        }
        runCatching { fallbackKeyFile(context).delete() }
        cachedTokens = null
        _summaryFlow.value = null
        initialized = true
        runCatching { AudiblePlayer.stop() }
    }

    @Synchronized
    internal fun resetMemoryForTests() {
        cachedTokens = null
        _summaryFlow.value = null
        initialized = false
    }
}
