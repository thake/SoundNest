/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.parental

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

enum class AppSurface(val requiresParentPin: Boolean) {
    // Kid-accessible settings & controls (no PIN required)
    KID_APPEARANCE(requiresParentPin = false),
    KID_AUDIO(requiresParentPin = false),
    KID_SLEEP_TIMER(requiresParentPin = false),

    // Parent-PIN-locked surfaces
    PARENT_CONTROLS(requiresParentPin = true),
    ACCOUNTS_AND_LOGOUT(requiresParentPin = true),
    HIDDEN_SONGS_AND_BANS(requiresParentPin = true),
    DIAGNOSTIC_EXPORT(requiresParentPin = true),
}

enum class PinSetupResult {
    Success,
    InvalidFormat,
    AlreadyConfigured,
}

enum class PinChangeResult {
    Success,
    InvalidCurrentPin,
    InvalidNewPinFormat,
    LockedOut,
}

sealed class PinVerifyResult {
    data object Verified : PinVerifyResult()
    data object NotConfigured : PinVerifyResult()
    data class InvalidPin(val failedAttempts: Int, val remainingBeforeCooldown: Int) : PinVerifyResult()
    data class LockedOut(val remainingMs: Long) : PinVerifyResult()
}

data class PinPromptRequest(
    val id: Long,
    val surface: AppSurface,
    val onAuthorized: () -> Unit,
)

/**
 * Manages Parent PIN security, PBKDF2-HMAC-SHA256 + Android Keystore envelope storage,
 * persisted exponential brute-force backoff, optional biometric unlock, and the 5-minute Admin Session.
 */
object ParentSecurityManager {
    internal const val PREFS_NAME = "soundnest_parent_security"
    private const val KEY_SALT = "pin_salt"
    private const val KEY_HASH_BLOB = "pin_hash_blob"
    private const val KEY_HASH_WRAPPED = "pin_hash_wrapped"
    private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
    private const val KEY_LOCKED_UNTIL_MS = "locked_until_ms"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    internal const val KEYSTORE_ALIAS = "soundnest_parent_pin"

    const val MIN_PIN_LENGTH = 4
    const val MAX_PIN_LENGTH = 128
    const val ADMIN_SESSION_TTL_MS = 5 * 60_000L
    private const val PBKDF2_ITERATIONS = 65_536
    private const val PBKDF2_KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val GCM_TAG_BITS = 128
    private const val FREE_ATTEMPTS_BEFORE_COOLDOWN = 4

    private val COOLDOWN_STEPS_MS = longArrayOf(
        30_000L,      // 5th failure: 30s
        60_000L,      // 6th failure: 1m
        120_000L,     // 7th failure: 2m
        300_000L,     // 8th failure: 5m
        900_000L,     // 9th+ failure: 15m
    )

    @Volatile
    internal var clockMs: () -> Long = { System.currentTimeMillis() }

    @Volatile
    private var adminUnlockedUntilMs: Long = 0L

    private val _sessionTick = MutableStateFlow(0L)
    val sessionTick: StateFlow<Long> = _sessionTick

    private val _pendingPrompt = MutableStateFlow<PinPromptRequest?>(null)
    val pendingPrompt: StateFlow<PinPromptRequest?> = _pendingPrompt

    private var nextPromptId = 1L
    private val secureRandom = SecureRandom()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isValidPinFormat(pin: String): Boolean =
        pin.length in MIN_PIN_LENGTH..MAX_PIN_LENGTH && pin.none { it == '\n' || it == '\r' }

    fun isPinConfigured(context: Context): Boolean {
        val p = prefs(context)
        return !p.getString(KEY_SALT, null).isNullOrBlank() &&
            !p.getString(KEY_HASH_BLOB, null).isNullOrBlank()
    }

    fun isAdminUnlocked(context: Context): Boolean {
        if (!isPinConfigured(context)) return false
        val now = clockMs()
        if (now < lockedUntilMs(context)) return false
        return now < adminUnlockedUntilMs
    }

    fun adminRemainingMs(context: Context): Long {
        if (!isAdminUnlocked(context)) return 0L
        return (adminUnlockedUntilMs - clockMs()).coerceAtLeast(0L)
    }

    fun failedAttempts(context: Context): Int =
        prefs(context).getInt(KEY_FAILED_ATTEMPTS, 0)

    fun lockedUntilMs(context: Context): Long =
        prefs(context).getLong(KEY_LOCKED_UNTIL_MS, 0L)

    fun lockoutRemainingMs(context: Context): Long =
        (lockedUntilMs(context) - clockMs()).coerceAtLeast(0L)

    fun setupInitialPin(context: Context, pin: String): PinSetupResult {
        if (isPinConfigured(context)) return PinSetupResult.AlreadyConfigured
        if (!isValidPinFormat(pin)) return PinSetupResult.InvalidFormat
        storeNewPin(context, pin)
        grantAdminSession()
        return PinSetupResult.Success
    }

    fun changePin(context: Context, currentPin: String, newPin: String): PinChangeResult {
        return when (verifyPin(context, currentPin)) {
            is PinVerifyResult.LockedOut -> PinChangeResult.LockedOut
            is PinVerifyResult.InvalidPin, PinVerifyResult.NotConfigured -> PinChangeResult.InvalidCurrentPin
            PinVerifyResult.Verified -> {
                if (!isValidPinFormat(newPin)) return PinChangeResult.InvalidNewPinFormat
                storeNewPin(context, newPin)
                grantAdminSession()
                PinChangeResult.Success
            }
        }
    }

    fun verifyPin(context: Context, candidatePin: String): PinVerifyResult {
        if (!isPinConfigured(context)) return PinVerifyResult.NotConfigured
        val now = clockMs()
        val p = prefs(context)
        val lockedUntil = p.getLong(KEY_LOCKED_UNTIL_MS, 0L)
        if (now < lockedUntil) {
            return PinVerifyResult.LockedOut(remainingMs = lockedUntil - now)
        }

        val saltB64 = p.getString(KEY_SALT, null) ?: return PinVerifyResult.NotConfigured
        val blobB64 = p.getString(KEY_HASH_BLOB, null) ?: return PinVerifyResult.NotConfigured
        val wrapped = p.getBoolean(KEY_HASH_WRAPPED, false)

        val salt = runCatching { Base64.decode(saltB64, Base64.NO_WRAP) }.getOrNull()
            ?: return PinVerifyResult.NotConfigured
        val storedBlob = runCatching { Base64.decode(blobB64, Base64.NO_WRAP) }.getOrNull()
            ?: return PinVerifyResult.NotConfigured
        val expectedHash = if (wrapped) unwrapFromKeystore(storedBlob) else storedBlob

        val matched = if (expectedHash != null && candidatePin.isNotEmpty()) {
            val candidateHash = derivePbkdf2(candidatePin, salt)
            MessageDigest.isEqual(expectedHash, candidateHash)
        } else {
            false
        }

        if (matched) {
            p.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKED_UNTIL_MS, 0L)
                .commit()
            grantAdminSession()
            return PinVerifyResult.Verified
        }

        val attempts = p.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        adminUnlockedUntilMs = 0L
        return if (attempts > FREE_ATTEMPTS_BEFORE_COOLDOWN) {
            val stepIdx = (attempts - FREE_ATTEMPTS_BEFORE_COOLDOWN - 1).coerceIn(0, COOLDOWN_STEPS_MS.lastIndex)
            val cooldown = COOLDOWN_STEPS_MS[stepIdx]
            p.edit()
                .putInt(KEY_FAILED_ATTEMPTS, attempts)
                .putLong(KEY_LOCKED_UNTIL_MS, now + cooldown)
                .commit()
            bumpTick()
            PinVerifyResult.LockedOut(remainingMs = cooldown)
        } else {
            p.edit()
                .putInt(KEY_FAILED_ATTEMPTS, attempts)
                .putLong(KEY_LOCKED_UNTIL_MS, 0L)
                .commit()
            bumpTick()
            PinVerifyResult.InvalidPin(
                failedAttempts = attempts,
                remainingBeforeCooldown = (FREE_ATTEMPTS_BEFORE_COOLDOWN + 1 - attempts).coerceAtLeast(0),
            )
        }
    }

    fun isBiometricEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BIOMETRIC_ENABLED, false)

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
        bumpTick()
    }

    fun isDeviceBiometricAvailable(context: Context): Boolean = runCatching {
        val bm = context.getSystemService(BiometricManager::class.java) ?: return false
        val res = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
        } else {
            @Suppress("DEPRECATION")
            bm.canAuthenticate()
        }
        res == BiometricManager.BIOMETRIC_SUCCESS
    }.getOrDefault(false)

    fun onBiometricSucceeded(context: Context): Boolean {
        if (!isPinConfigured(context) || !isBiometricEnabled(context)) return false
        if (clockMs() < lockedUntilMs(context)) return false
        prefs(context).edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKED_UNTIL_MS, 0L)
            .commit()
        grantAdminSession()
        val req = _pendingPrompt.value
        if (req != null) {
            _pendingPrompt.value = null
            req.onAuthorized()
        }
        return true
    }

    fun lockNow(@Suppress("UNUSED_PARAMETER") context: Context) {
        adminUnlockedUntilMs = 0L
        bumpTick()
    }

    fun canAccessWithoutPrompt(context: Context, surface: AppSurface): Boolean {
        if (!surface.requiresParentPin) return true
        return isAdminUnlocked(context)
    }

    fun runProtected(context: Context, surface: AppSurface, onAuthorized: () -> Unit) {
        if (canAccessWithoutPrompt(context, surface)) {
            if (surface.requiresParentPin) grantAdminSession()
            onAuthorized()
            return
        }
        _pendingPrompt.value = PinPromptRequest(
            id = nextPromptId++,
            surface = surface,
            onAuthorized = onAuthorized,
        )
    }

    fun submitPromptPin(context: Context, candidatePin: String): PinVerifyResult {
        val result = verifyPin(context, candidatePin)
        if (result == PinVerifyResult.Verified) {
            val req = _pendingPrompt.value
            _pendingPrompt.value = null
            req?.onAuthorized()
        }
        return result
    }

    fun dismissPrompt() {
        _pendingPrompt.value = null
    }

    private fun grantAdminSession() {
        adminUnlockedUntilMs = clockMs() + ADMIN_SESSION_TTL_MS
        bumpTick()
    }

    private fun bumpTick() {
        _sessionTick.value = _sessionTick.value + 1L
    }

    private fun storeNewPin(context: Context, pin: String) {
        val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
        val rawHash = derivePbkdf2(pin, salt)
        val wrapped = wrapWithKeystoreOrNull(rawHash)
        val blobToStore = wrapped ?: rawHash
        prefs(context).edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH_BLOB, Base64.encodeToString(blobToStore, Base64.NO_WRAP))
            .putBoolean(KEY_HASH_WRAPPED, wrapped != null)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKED_UNTIL_MS, 0L)
            .commit()
    }

    private fun derivePbkdf2(pin: String, salt: ByteArray): ByteArray {
        val chars = pin.toCharArray()
        return try {
            val spec = PBEKeySpec(chars, salt, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS)
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            chars.fill('\u0000')
        }
    }

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
            .setKeySize(256)
            .build()
        keyGen.init(spec)
        keyGen.generateKey()
    }.getOrNull()

    private fun wrapWithKeystoreOrNull(plainHash: ByteArray): ByteArray? = runCatching {
        val key = getOrCreateKeystoreSecretKey() ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plainHash)
        ByteArray(1 + iv.size + ciphertext.size).also { out ->
            out[0] = iv.size.toByte()
            System.arraycopy(iv, 0, out, 1, iv.size)
            System.arraycopy(ciphertext, 0, out, 1 + iv.size, ciphertext.size)
        }
    }.getOrNull()

    private fun unwrapFromKeystore(blob: ByteArray): ByteArray? = runCatching {
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

    internal fun simulateProcessRestart() {
        adminUnlockedUntilMs = 0L
        _pendingPrompt.value = null
        bumpTick()
    }

    internal fun resetForTests(context: Context) {
        clockMs = { System.currentTimeMillis() }
        adminUnlockedUntilMs = 0L
        _pendingPrompt.value = null
        prefs(context).edit().clear().commit()
        bumpTick()
    }
}
