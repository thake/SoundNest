/* SPDX-License-Identifier: GPL-3.0-only */
package com.music.spotui.ui.screens

import app.myspoti.spotify.PremiumGateDecision
import com.music.spotui.data.preferences.Look
import com.music.spotui.data.preferences.LookPref
import com.music.spotui.di.Graph
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnboardingTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private fun prefs(name: String) = app.getSharedPreferences(name, 0)

    @Before fun setUp() {
        Graph::class.java.getDeclaredField("app").apply { isAccessible = true }.set(null, app)
        prefs("myspoti_prefs").edit().clear().commit()
        prefs("settings_prefs").edit().clear().commit()
        LookPref.clearOffered(app)
    }

    @After fun tearDown() { LookPref.set(app, Look.CLASSIC) }

    @Test fun `new install requires Spotify sign-in then setup`() {
        assertEquals(Entry.WELCOME, Onboarding.entry(spotifySignedIn = false, setupNeeded = true, connecting = false))
        assertEquals(Entry.CONNECTING, Onboarding.entry(spotifySignedIn = false, setupNeeded = true, connecting = true))
        assertEquals(Entry.SETUP, Onboarding.entry(spotifySignedIn = true, setupNeeded = true, connecting = false))
        assertEquals(listOf(STEP_INTRO, STEP_LOOK, STEP_QUALITY, STEP_PERMISSIONS), Setup.steps(app))
    }

    @Test fun `signed-in user with completed setup goes straight to app`() {
        prefs("myspoti_prefs").edit().putBoolean("SetupDone", true).commit()
        com.music.spotui.data.preferences.setQualityChosen(app)
        LookPref.setChosen(app)
        assertFalse(Setup.needed(app))
        assertEquals(Entry.APP, Onboarding.entry(spotifySignedIn = true, setupNeeded = false, connecting = false))
    }

    @Test fun `new look popup never follows onboarding`() {
        assertFalse(LookPref.offered)
        assertFalse(Onboarding.lookOfferShown(Entry.SETUP, false, false, LookPref.offered))
        assertFalse(Onboarding.lookOfferShown(Entry.WELCOME, false, false, LookPref.offered))
        LookPref.setChosen(app)
        assertTrue(LookPref.offered)
        assertFalse(Onboarding.lookOfferShown(Entry.APP, false, false, LookPref.offered))
        assertTrue(LookPref.isOffered(app))
    }

    @Test fun `existing user who never saw popup gets it once`() {
        LookPref.clearOffered(app)
        assertTrue(Onboarding.lookOfferShown(Entry.APP, false, false, LookPref.offered))
        assertFalse(Onboarding.lookOfferShown(Entry.APP, false, true, LookPref.offered))
        LookPref.setOffered(app)
        assertFalse(Onboarding.lookOfferShown(Entry.APP, false, false, LookPref.offered))
    }

    @Test fun `free account is blocked with PREMIUM_REQUIRED and unverified account stays CONNECTING`() {
        assertEquals(
            Entry.PREMIUM_REQUIRED,
            Onboarding.entry(
                spotifySignedIn = true,
                setupNeeded = false,
                connecting = false,
                premiumDecision = PremiumGateDecision.PremiumRequired,
            ),
        )
        assertEquals(
            Entry.PREMIUM_REQUIRED,
            Onboarding.entry(
                spotifySignedIn = true,
                setupNeeded = true,
                connecting = false,
                premiumDecision = PremiumGateDecision.PremiumRequired,
            ),
        )
        assertEquals(
            Entry.CONNECTING,
            Onboarding.entry(
                spotifySignedIn = true,
                setupNeeded = false,
                connecting = false,
                premiumDecision = PremiumGateDecision.Checking,
            ),
        )
        assertFalse(Onboarding.lookOfferShown(Entry.PREMIUM_REQUIRED, false, false, false))
    }
}
