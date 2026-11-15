/* SPDX-License-Identifier: GPL-3.0-only */
package app.myspoti.spotify

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PremiumAccountGateTest {
    private fun fixture(name: String): JsonElement =
        Json.parseToJsonElement(javaClass.getResourceAsStream("/fixtures/$name")!!.bufferedReader().use { it.readText() })

    private fun answer(product: String?, ads: Boolean?): JsonElement {
        val fields = listOfNotNull(
            product?.let { "\"product\":\"$it\"" },
            "\"attributes\":" + (ads?.let { "{\"ads\":$it}" } ?: "{}"),
        )
        return Json.parseToJsonElement("""{"data":{"me":{"account":{${fields.joinToString(",")}}}}}""")
    }

    @AfterEach
    fun resetPlan() = AccountPlan.set(null)

    @Test
    fun `premium account fixture is allowed`() {
        assertEquals(PremiumGateDecision.Allowed, PremiumAccountGate.evaluateAnswer(fixture("account-premium.json")))
    }

    @Test
    fun `free account fixture is blocked with PremiumRequired`() {
        assertEquals(PremiumGateDecision.PremiumRequired, PremiumAccountGate.evaluateAnswer(fixture("account-free.json")))
    }

    @Test
    fun `paid ad-free tiers are allowed while free or ad-supported tiers require Premium`() {
        for (tier in listOf("PREMIUM", "FAMILY", "DUO", "STUDENT")) {
            assertEquals(PremiumGateDecision.Allowed, PremiumAccountGate.evaluateAnswer(answer(tier, false)), tier)
        }
        for (tier in listOf("FREE", "OPEN")) {
            assertEquals(PremiumGateDecision.PremiumRequired, PremiumAccountGate.evaluateAnswer(answer(tier, false)), tier)
            assertEquals(PremiumGateDecision.PremiumRequired, PremiumAccountGate.evaluateAnswer(answer(tier, true)), tier)
        }
        assertEquals(PremiumGateDecision.PremiumRequired, PremiumAccountGate.evaluateAnswer(answer("PREMIUM", true)))
    }

    @Test
    fun `missing or malformed accountAttributes stays in Checking state`() {
        assertEquals(PremiumGateDecision.Checking, PremiumAccountGate.evaluateAnswer(null))
        assertEquals(PremiumGateDecision.Checking, PremiumAccountGate.evaluateAnswer(answer(null, false)))
        assertEquals(PremiumGateDecision.Checking, PremiumAccountGate.evaluateAnswer(answer("PREMIUM", null)))
    }

    @Test
    fun `evaluate uses cached plan until live plan arrives and live plan always wins`() {
        assertEquals(
            PremiumGateDecision.Checking,
            PremiumAccountGate.evaluate(signedIn = true, livePremium = null, cachedPremium = null),
        )
        assertEquals(
            PremiumGateDecision.Allowed,
            PremiumAccountGate.evaluate(signedIn = true, livePremium = null, cachedPremium = true),
        )
        assertEquals(
            PremiumGateDecision.PremiumRequired,
            PremiumAccountGate.evaluate(signedIn = true, livePremium = null, cachedPremium = false),
        )
        // Downgraded account: live false overrides stale cached true.
        assertEquals(
            PremiumGateDecision.PremiumRequired,
            PremiumAccountGate.evaluate(signedIn = true, livePremium = false, cachedPremium = true),
        )
        // Upgraded account: live true overrides stale cached false.
        assertEquals(
            PremiumGateDecision.Allowed,
            PremiumAccountGate.evaluate(signedIn = true, livePremium = true, cachedPremium = false),
        )
    }

    @Test
    fun `canPlay blocks free and unverified accounts while allowing verified or cached Premium`() {
        assertFalse(PremiumAccountGate.canPlay(livePremium = false, cachedPremium = true))
        assertFalse(PremiumAccountGate.canPlay(livePremium = null, cachedPremium = false))
        assertFalse(PremiumAccountGate.canPlay(livePremium = null, cachedPremium = null))
        assertTrue(PremiumAccountGate.canPlay(livePremium = true, cachedPremium = null))
        assertTrue(PremiumAccountGate.canPlay(livePremium = null, cachedPremium = true))
    }
}
