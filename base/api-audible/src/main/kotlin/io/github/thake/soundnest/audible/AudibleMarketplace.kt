/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.audible

import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
enum class AudibleMarketplace(
    val code: String,
    val tld: String,
    val marketplaceId: String,
    val displayName: String,
    val countryCodes: Set<String>,
) {
    US("us", "com", "AF2M0KC94RCEA", "United States (audible.com)", setOf("US")),
    DE("de", "de", "AN7V1F1VY261K", "Germany / DACH (audible.de)", setOf("DE", "AT", "CH")),
    UK("uk", "co.uk", "A2I9A3Q2GNFNGQ", "United Kingdom (audible.co.uk)", setOf("GB", "UK", "IE")),
    FR("fr", "fr", "A2728XDNODOQ8T", "France (audible.fr)", setOf("FR", "BE", "LU")),
    CA("ca", "ca", "A2CQZ5RBY40XE", "Canada (audible.ca)", setOf("CA")),
    AU("au", "com.au", "AN7EY7DTAW63G", "Australia (audible.com.au)", setOf("AU", "NZ")),
    IT("it", "it", "A2N7FU2W2BU2ZC", "Italy (audible.it)", setOf("IT")),
    ES("es", "es", "ALMIKO4SZCSAR", "Spain (audible.es)", setOf("ES")),
    JP("jp", "co.jp", "A1QAP3MOU4173J", "Japan (audible.co.jp)", setOf("JP")),
    IN("in", "in", "AJO3FBRUE6J4S", "India (audible.in)", setOf("IN")),
    BR("br", "com.br", "A10J1VAYUDTYRN", "Brazil (audible.com.br)", setOf("BR"));

    val amazonDomain: String get() = "amazon.$tld"
    val audibleDomain: String get() = "audible.$tld"
    val apiBaseUrl: String get() = "https://api.audible.$tld"

    companion object {
        fun defaultForLocale(locale: Locale = Locale.getDefault()): AudibleMarketplace {
            val country = locale.country.uppercase(Locale.ROOT)
            if (country.isBlank()) return US
            return entries.firstOrNull { country in it.countryCodes } ?: US
        }

        fun fromCodeOrTld(raw: String?): AudibleMarketplace? {
            val cleaned = raw?.trim()?.lowercase(Locale.ROOT)?.removePrefix(".")?.takeIf { it.isNotEmpty() } ?: return null
            return entries.firstOrNull { it.code == cleaned || it.tld == cleaned || it.name.lowercase(Locale.ROOT) == cleaned }
        }
    }
}
