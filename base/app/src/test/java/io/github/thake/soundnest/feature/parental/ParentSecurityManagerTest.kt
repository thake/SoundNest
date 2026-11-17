/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental

import io.github.thake.soundnest.feature.parental.security.*

import android.content.Context
import io.github.thake.soundnest.feature.accounts.ui.Entry
import io.github.thake.soundnest.feature.accounts.ui.Onboarding
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ParentSecurityManagerTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private var fakeNowMs: Long = 1_700_000_000_000L

    @Before
    fun setUp() {
        fakeNowMs = 1_700_000_000_000L
        ParentSecurityManager.resetForTests(context)
        ParentSecurityManager.clockMs = { fakeNowMs }
    }

    @After
    fun tearDown() {
        ParentSecurityManager.resetForTests(context)
    }

    @Test
    fun `uses SoundNest keystore alias and preferences namespace`() {
        assertEquals("soundnest_parent_pin", ParentSecurityManager.KEYSTORE_ALIAS)
        assertEquals("feature_parent_security", ParentSecurityManager.PREFS_NAME)
    }

    @Test
    fun `fails closed on first launch requiring PARENT_SETUP before SETUP or APP`() {
        assertFalse(ParentSecurityManager.isPinConfigured(context))
        assertFalse(ParentSecurityManager.isAdminUnlocked(context))

        // Signed-in Free or Premium account without a Parent PIN MUST go to Entry.PARENT_SETUP, never Entry.SETUP or Entry.APP
        assertEquals(
            Entry.PARENT_SETUP,
            Onboarding.entry(
                spotifySignedIn = true,
                setupNeeded = true,
                connecting = false,
                parentPinConfigured = false,
            ),
        )
        assertEquals(
            Entry.PARENT_SETUP,
            Onboarding.entry(
                spotifySignedIn = true,
                setupNeeded = false,
                connecting = false,
                parentPinConfigured = false,
            ),
        )
        assertFalse(Onboarding.lookOfferShown(Entry.PARENT_SETUP, false, false, false))
    }

    @Test
    fun `enforces 4-character minimum PIN or password hashes with PBKDF2 and refuses unauthenticated overwrite`() {
        assertEquals(4, ParentSecurityManager.MIN_PIN_LENGTH)
        assertEquals(128, ParentSecurityManager.MAX_PIN_LENGTH)

        // Rejects < 4 chars, > MAX_PIN_LENGTH chars, or multiline control characters
        assertEquals(PinSetupResult.InvalidFormat, ParentSecurityManager.setupInitialPin(context, ""))
        assertEquals(PinSetupResult.InvalidFormat, ParentSecurityManager.setupInitialPin(context, "123"))
        assertEquals(PinSetupResult.InvalidFormat, ParentSecurityManager.setupInitialPin(context, "abc"))
        assertEquals(PinSetupResult.InvalidFormat, ParentSecurityManager.setupInitialPin(context, "ab\ncd"))
        assertEquals(PinSetupResult.InvalidFormat, ParentSecurityManager.setupInitialPin(context, "a".repeat(129)))
        assertFalse(ParentSecurityManager.isPinConfigured(context))

        // Accepts 4-digit PIN and hashes with PBKDF2 (never storing plaintext in SharedPreferences)
        assertEquals(
            PinSetupResult.Success,
            ParentSecurityManager.setupInitialPin(context, "2468"),
        )
        assertTrue(ParentSecurityManager.isPinConfigured(context))
        assertTrue(ParentSecurityManager.isAdminUnlocked(context))

        val rawPrefsDump = context
            .getSharedPreferences(ParentSecurityManager.PREFS_NAME, Context.MODE_PRIVATE)
            .all
            .values
            .joinToString("|")
        assertFalse("Plaintext PIN must never appear in SharedPreferences", rawPrefsDump.contains("2468"))

        // Cannot overwrite initial PIN/password once configured without knowing current secret
        ParentSecurityManager.lockNow(context)
        assertEquals(
            PinSetupResult.AlreadyConfigured,
            ParentSecurityManager.setupInitialPin(context, "newPass!"),
        )
        assertEquals(
            io.github.thake.soundnest.feature.parental.security.PinChangeResult.InvalidCurrentPin,
            ParentSecurityManager.changePin(context, currentPin = "0000", newPin = "newPass!"),
        )
        assertEquals(
            io.github.thake.soundnest.feature.parental.security.PinChangeResult.InvalidNewPinFormat,
            ParentSecurityManager.changePin(context, currentPin = "2468", newPin = "abc"),
        )

        // Supports changing from a 4-digit PIN to a full password with symbols, spaces, and Unicode
        val password = "S0undN3st! horse 🔑"
        assertEquals(
            io.github.thake.soundnest.feature.parental.security.PinChangeResult.Success,
            ParentSecurityManager.changePin(context, currentPin = "2468", newPin = password),
        )
        val rawPrefsAfterChange = context
            .getSharedPreferences(ParentSecurityManager.PREFS_NAME, Context.MODE_PRIVATE)
            .all
            .values
            .joinToString("|")
        assertFalse("Plaintext password must never appear in SharedPreferences", rawPrefsAfterChange.contains("S0undN3st"))

        ParentSecurityManager.lockNow(context)
        assertTrue(ParentSecurityManager.verifyPin(context, "2468") is PinVerifyResult.InvalidPin)
        assertEquals(PinVerifyResult.Verified, ParentSecurityManager.verifyPin(context, password))
    }

    @Test
    fun `enforces persisted exponential brute-force backoff across restarts`() {
        ParentSecurityManager.setupInitialPin(context, "135790")
        ParentSecurityManager.lockNow(context)

        // First 4 wrong attempts do not trigger lockout yet
        for (attempt in 1..4) {
            val res = ParentSecurityManager.verifyPin(context, "000000")
            assertTrue("Expected InvalidPin on attempt $attempt but got $res", res is PinVerifyResult.InvalidPin)
        }

        // 5th wrong attempt triggers 30-second lockout
        val fifth = ParentSecurityManager.verifyPin(context, "000000")
        assertTrue("Expected LockedOut on 5th wrong attempt but got $fifth", fifth is PinVerifyResult.LockedOut)
        assertEquals(30_000L, (fifth as PinVerifyResult.LockedOut).remainingMs)

        // Even the CORRECT PIN is rejected while locked out, and cooldown survives in-memory reset (app restart)
        fakeNowMs += 10_000L
        ParentSecurityManager.simulateProcessRestart()
        val duringLockout = ParentSecurityManager.verifyPin(context, "135790")
        assertTrue(duringLockout is PinVerifyResult.LockedOut)
        assertEquals(20_000L, (duringLockout as PinVerifyResult.LockedOut).remainingMs)
        assertFalse(ParentSecurityManager.isAdminUnlocked(context))

        // Biometric callback is also rejected during active PIN brute-force lockout
        ParentSecurityManager.setBiometricEnabled(context, true)
        assertFalse(ParentSecurityManager.onBiometricSucceeded(context))

        // After 30s expires, a 6th wrong attempt escalates lockout to 60s
        fakeNowMs += 20_001L
        val sixth = ParentSecurityManager.verifyPin(context, "111111")
        assertTrue(sixth is PinVerifyResult.LockedOut)
        assertEquals(60_000L, (sixth as PinVerifyResult.LockedOut).remainingMs)

        // After 60s expires, valid PIN succeeds and resets failure counter
        fakeNowMs += 60_001L
        assertEquals(PinVerifyResult.Verified, ParentSecurityManager.verifyPin(context, "135790"))
        assertTrue(ParentSecurityManager.isAdminUnlocked(context))
        assertEquals(0, ParentSecurityManager.failedAttempts(context))
    }

    @Test
    fun `auto-locks Admin Session after 5 minutes and supports explicit lockNow and biometric unlock`() {
        ParentSecurityManager.setupInitialPin(context, "654321")
        assertTrue(ParentSecurityManager.isAdminUnlocked(context))

        // Still unlocked at 4m 59s
        fakeNowMs += 4 * 60_000L + 59_000L
        assertTrue(ParentSecurityManager.isAdminUnlocked(context))

        // Auto-locked at 5m 00s
        fakeNowMs += 1_000L
        assertFalse(ParentSecurityManager.isAdminUnlocked(context))

        // Biometric unlock only works when parent explicitly enabled biometric unlock
        assertFalse(ParentSecurityManager.isBiometricEnabled(context))
        assertFalse(ParentSecurityManager.onBiometricSucceeded(context))
        ParentSecurityManager.setBiometricEnabled(context, true)
        assertTrue(ParentSecurityManager.onBiometricSucceeded(context))
        assertTrue(ParentSecurityManager.isAdminUnlocked(context))

        // Explicit Lock Now immediately revokes Admin Session
        ParentSecurityManager.lockNow(context)
        assertFalse(ParentSecurityManager.isAdminUnlocked(context))
    }

    @Test
    fun `separates Kid-accessible settings from Parent-PIN-locked surfaces`() {
        ParentSecurityManager.setupInitialPin(context, "112233")
        ParentSecurityManager.lockNow(context)

        // Kid surfaces NEVER require PIN
        assertTrue(ParentSecurityManager.canAccessWithoutPrompt(context, AppSurface.KID_APPEARANCE))
        assertTrue(ParentSecurityManager.canAccessWithoutPrompt(context, AppSurface.KID_AUDIO))
        assertTrue(ParentSecurityManager.canAccessWithoutPrompt(context, AppSurface.KID_SLEEP_TIMER))

        var kidActionRan = false
        ParentSecurityManager.runProtected(context, AppSurface.KID_SLEEP_TIMER) {
            kidActionRan = true
        }
        assertTrue(kidActionRan)
        assertEquals(null, ParentSecurityManager.pendingPrompt.value)

        // Parent surfaces ARE blocked while Admin Session is locked, and trigger pendingPrompt instead of running
        val lockedSurfaces = listOf(
            AppSurface.PARENT_CONTROLS,
            AppSurface.ACCOUNTS_AND_LOGOUT,
            AppSurface.HIDDEN_SONGS_AND_BANS,
            AppSurface.DIAGNOSTIC_EXPORT,
        )
        for (surface in lockedSurfaces) {
            assertFalse(
                "Surface $surface must be locked when Admin Session is inactive",
                ParentSecurityManager.canAccessWithoutPrompt(context, surface),
            )
            var protectedRan = false
            ParentSecurityManager.runProtected(context, surface) {
                protectedRan = true
            }
            assertFalse("Protected action for $surface must not run before PIN verification", protectedRan)
            assertNotEquals(null, ParentSecurityManager.pendingPrompt.value)

            // Completing the prompt with the right PIN runs the queued action and clears the prompt
            assertEquals(PinVerifyResult.Verified, ParentSecurityManager.submitPromptPin(context, "112233"))
            assertTrue("Protected action for $surface must run after valid PIN", protectedRan)
            assertEquals(null, ParentSecurityManager.pendingPrompt.value)

            // Lock again to test the next protected surface
            ParentSecurityManager.lockNow(context)
        }
    }
}
