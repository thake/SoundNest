/* SPDX-License-Identifier: GPL-3.0-only */
package com.music.spotui.ui.screens

import app.myspoti.spotify.PremiumGateDecision

/** What the main activity puts on screen. */
enum class Entry {
    /** Signed-in account is on Spotify Free / ad-supported tier: blocked in Orpheon Kids. */
    PREMIUM_REQUIRED,
    /** The one-time steps after the Spotify account is in place ([SetupScreen]). */
    SETUP,
    /** The app's pages. */
    APP,
    /** Back from Spotify's sign-in page, waiting for the session or plan verification. */
    CONNECTING,
    /** The first screen of an app with no Spotify account in place. */
    WELCOME,
}

/**
 * Spotify-only onboarding flow for Orpheon Kids, gated by [PremiumGateDecision].
 */
object Onboarding {
    fun entry(
        spotifySignedIn: Boolean,
        setupNeeded: Boolean,
        connecting: Boolean,
        premiumDecision: PremiumGateDecision = PremiumGateDecision.Allowed,
    ): Entry = when {
        spotifySignedIn && premiumDecision == PremiumGateDecision.PremiumRequired -> Entry.PREMIUM_REQUIRED
        spotifySignedIn && premiumDecision == PremiumGateDecision.Checking -> Entry.CONNECTING
        spotifySignedIn && setupNeeded -> Entry.SETUP
        spotifySignedIn -> Entry.APP
        connecting -> Entry.CONNECTING
        else -> Entry.WELCOME
    }

    /**
     * The "new look" popup, for an existing user who never saw it: never over the onboarding, nor
     * after it (its look step covers it, [com.music.spotui.data.preferences.LookPref.setChosen]).
     */
    fun lookOfferShown(entry: Entry, showLook: Boolean, showingLogin: Boolean, offered: Boolean): Boolean =
        entry == Entry.APP && !showLook && !showingLogin && !offered
}
