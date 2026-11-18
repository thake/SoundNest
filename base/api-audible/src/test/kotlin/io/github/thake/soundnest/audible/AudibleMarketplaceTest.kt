/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.audible

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

class AudibleMarketplaceTest {

    @Test
    fun `auto defaults marketplace from device locale country`() {
        assertEquals(AudibleMarketplace.DE, AudibleMarketplace.defaultForLocale(Locale.GERMANY))
        assertEquals(AudibleMarketplace.DE, AudibleMarketplace.defaultForLocale(Locale("de", "AT")))
        assertEquals(AudibleMarketplace.DE, AudibleMarketplace.defaultForLocale(Locale("de", "CH")))
        assertEquals(AudibleMarketplace.UK, AudibleMarketplace.defaultForLocale(Locale.UK))
        assertEquals(AudibleMarketplace.UK, AudibleMarketplace.defaultForLocale(Locale("en", "IE")))
        assertEquals(AudibleMarketplace.US, AudibleMarketplace.defaultForLocale(Locale.US))
        assertEquals(AudibleMarketplace.FR, AudibleMarketplace.defaultForLocale(Locale.FRANCE))
        assertEquals(AudibleMarketplace.CA, AudibleMarketplace.defaultForLocale(Locale.CANADA))
        assertEquals(AudibleMarketplace.AU, AudibleMarketplace.defaultForLocale(Locale("en", "AU")))
        assertEquals(AudibleMarketplace.IT, AudibleMarketplace.defaultForLocale(Locale.ITALY))
        assertEquals(AudibleMarketplace.ES, AudibleMarketplace.defaultForLocale(Locale("es", "ES")))
        assertEquals(AudibleMarketplace.JP, AudibleMarketplace.defaultForLocale(Locale.JAPAN))
        assertEquals(AudibleMarketplace.IN, AudibleMarketplace.defaultForLocale(Locale("en", "IN")))
        assertEquals(AudibleMarketplace.BR, AudibleMarketplace.defaultForLocale(Locale("pt", "BR")))
    }

    @Test
    fun `falls back to US marketplace for unsupported locale countries`() {
        assertEquals(AudibleMarketplace.US, AudibleMarketplace.defaultForLocale(Locale("sv", "SE")))
        assertEquals(AudibleMarketplace.US, AudibleMarketplace.defaultForLocale(Locale.ROOT))
    }

    @Test
    fun `resolves manual override by code or tld case-insensitively`() {
        assertEquals(AudibleMarketplace.DE, AudibleMarketplace.fromCodeOrTld("de"))
        assertEquals(AudibleMarketplace.DE, AudibleMarketplace.fromCodeOrTld("DE"))
        assertEquals(AudibleMarketplace.UK, AudibleMarketplace.fromCodeOrTld("uk"))
        assertEquals(AudibleMarketplace.UK, AudibleMarketplace.fromCodeOrTld("co.uk"))
        assertEquals(AudibleMarketplace.US, AudibleMarketplace.fromCodeOrTld("com"))
        assertEquals(AudibleMarketplace.AU, AudibleMarketplace.fromCodeOrTld("com.au"))
        assertNull(AudibleMarketplace.fromCodeOrTld("invalid"))
        assertNull(AudibleMarketplace.fromCodeOrTld(null))
    }

    @Test
    fun `exposes correct regional amazon and audible domains`() {
        assertEquals("amazon.de", AudibleMarketplace.DE.amazonDomain)
        assertEquals("audible.de", AudibleMarketplace.DE.audibleDomain)
        assertEquals("https://api.audible.de", AudibleMarketplace.DE.apiBaseUrl)

        assertEquals("amazon.co.uk", AudibleMarketplace.UK.amazonDomain)
        assertEquals("audible.co.uk", AudibleMarketplace.UK.audibleDomain)
        assertEquals("https://api.audible.co.uk", AudibleMarketplace.UK.apiBaseUrl)
    }

    @Test
    fun `jp marketplace id aligns with regional registry`() {
        assertEquals("A1QAP3MOU4173J", AudibleMarketplace.JP.marketplaceId)
    }

    @Test
    fun `sign-in return_to targets amazonDomain across marketplaces`() {
        for (market in AudibleMarketplace.entries) {
            val session = AudibleAuth.createPkceSession(market)
            val url = AudibleAuth.buildSignInUrl(session)
            val expectedReturnTo = "https%3A%2F%2Fwww.${market.amazonDomain}%2Fap%2Fmaplanding"
            val forbiddenReturnTo = "https%3A%2F%2Fwww.${market.audibleDomain}%2Fap%2Fmaplanding"
            assertTrue(expectedReturnTo in url, "Expected $expectedReturnTo in $url")
            assertFalse(forbiddenReturnTo in url, "Forbidden cross-domain return_to $forbiddenReturnTo in $url")
        }
    }
}
