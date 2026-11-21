/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.accounts.ui

/** What the main activity puts on screen. */
enum class Entry {
    /** Explicit Parent Controller Hub (manages paired child devices on Wi-Fi without Spotify login). */
    PARENT_HUB,
    /** Fail-closed mandatory Parent PIN setup before any child setup or app access. */
    PARENT_SETUP,
    /** The one-time steps after the Spotify account is in place ([SetupScreen]). */
    SETUP,
    /** The app's pages. */
    APP,
    /** Back from Spotify's sign-in page, waiting for the session. */
    CONNECTING,
    /** The first screen of an app with no Spotify account in place. */
    WELCOME,
}

/**
 * Spotify onboarding flow (supporting both Free and Premium accounts), gated fail-closed by [parentPinConfigured].
 */
object Onboarding {
    fun entry(
        spotifySignedIn: Boolean,
        setupNeeded: Boolean,
        connecting: Boolean,
        parentPinConfigured: Boolean = true,
        isParentController: Boolean = false,
    ): Entry = when {
        !spotifySignedIn && isParentController -> Entry.PARENT_HUB
        spotifySignedIn && !parentPinConfigured -> Entry.PARENT_SETUP
        spotifySignedIn && setupNeeded -> Entry.SETUP
        spotifySignedIn -> Entry.APP
        connecting -> Entry.CONNECTING
        else -> Entry.WELCOME
    }

    /**
     * The "new look" popup, for an existing user who never saw it: never over the onboarding, nor
     * after it (its look step covers it, [io.github.thake.soundnest.feature.appearance.settings.LookPref.setChosen]).
     */
    fun lookOfferShown(entry: Entry, showLook: Boolean, showingLogin: Boolean, offered: Boolean): Boolean =
        entry == Entry.APP && !showLook && !showingLogin && !offered
}
