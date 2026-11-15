/* SPDX-License-Identifier: GPL-3.0-only */
package app.myspoti.spotify

import kotlinx.serialization.json.JsonElement

/**
 * Result of verifying whether the signed-in Spotify account is on an ad-free Premium tier.
 */
enum class PremiumGateDecision {
    /** Paid ad-free Spotify tier (Premium, Family, Duo, Student). */
    Allowed,

    /** Account plan is not yet known; wait before entering playback/app screens. */
    Checking,

    /** Free / Open / ad-supported Spotify tier — blocked in Orpheon Kids. */
    PremiumRequired,
}

/**
 * Enforces that Orpheon Kids only operates with ad-free Spotify Premium accounts.
 */
object PremiumAccountGate {
    /** Evaluates a raw `accountAttributes` GraphQL JSON response. */
    fun evaluateAnswer(answer: JsonElement?): PremiumGateDecision = when (AccountPlan.fromAnswer(answer)) {
        true -> PremiumGateDecision.Allowed
        false -> PremiumGateDecision.PremiumRequired
        null -> PremiumGateDecision.Checking
    }

    /**
     * Evaluates access from live [AccountPlan] state and any persisted profile plan.
     * Live plan always overrides stale cached state (e.g. downgraded or upgraded subscription).
     */
    fun evaluate(
        signedIn: Boolean,
        livePremium: Boolean? = AccountPlan.premium.value,
        cachedPremium: Boolean? = null,
    ): PremiumGateDecision {
        if (!signedIn) return PremiumGateDecision.Checking
        return when (livePremium ?: cachedPremium) {
            true -> PremiumGateDecision.Allowed
            false -> PremiumGateDecision.PremiumRequired
            null -> PremiumGateDecision.Checking
        }
    }

    /** True only when the account is verified (live or cached) as ad-free Spotify Premium. */
    fun canPlay(
        livePremium: Boolean? = AccountPlan.premium.value,
        cachedPremium: Boolean? = null,
    ): Boolean = evaluate(signedIn = true, livePremium = livePremium, cachedPremium = cachedPremium) == PremiumGateDecision.Allowed
}
