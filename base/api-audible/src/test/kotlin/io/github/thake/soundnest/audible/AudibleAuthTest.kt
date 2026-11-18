/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.audible

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AudibleAuthTest {

    @Test
    fun `computes RFC 7636 S256 PKCE challenge accurately`() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            AudibleAuth.s256Challenge(verifier),
        )
    }

    @Test
    fun `builds regional sign-in URL with PKCE S256 and hex-encoded client_id`() {
        val session = AudibleAuth.createPkceSession(
            marketplace = AudibleMarketplace.DE,
            verifierBytes = ByteArray(32) { (it + 1).toByte() },
            serialHex = "0123456789ABCDEF0123456789ABCDEF",
        )
        val url = AudibleAuth.buildSignInUrl(session)

        assertTrue(url.startsWith("https://www.amazon.de/ap/signin?"))
        assertTrue("openid.oa2.response_type=code" in url)
        assertTrue("openid.oa2.code_challenge_method=S256" in url)
        assertTrue("openid.oa2.code_challenge=${session.codeChallenge}" in url)
        assertTrue("marketPlaceId=${AudibleMarketplace.DE.marketplaceId}" in url)
        assertTrue("https%3A%2F%2Fwww.amazon.de%2Fap%2Fmaplanding" in url)
        val expectedClientHex = ("0123456789ABCDEF0123456789ABCDEF#" + AudibleAuth.AUDIBLE_DEVICE_TYPE)
            .encodeToByteArray()
            .joinToString("") { "%02x".format(it) }
        assertTrue("device%3A$expectedClientHex" in url || "device:$expectedClientHex" in url)
    }

    @Test
    fun `parses maplanding redirect authorization code and ignores intermediate pages`() {
        assertEquals(
            AudibleAuth.MapLandingResult.NotMapLanding,
            AudibleAuth.parseRedirectUrl("https://www.amazon.de/ap/signin?ie=UTF8"),
        )
        assertEquals(
            AudibleAuth.MapLandingResult.Authorized("ANhJ-auth-code-123"),
            AudibleAuth.parseRedirectUrl(
                "https://www.audible.de/ap/maplanding?openid.oa2.authorization_code=ANhJ-auth-code-123&openid.mode=id_res"
            ),
        )
        val err = AudibleAuth.parseRedirectUrl(
            "https://www.audible.de/ap/maplanding?openid.error=access_denied&error_description=User+cancelled"
        )
        assertTrue(err is AudibleAuth.MapLandingResult.Error)
        assertEquals("access_denied", (err as AudibleAuth.MapLandingResult.Error).errorCode)
    }

    @Test
    fun `registers virtual device without requesting website_cookies`() = runTest {
        var capturedUrl = ""
        var capturedBody = ""
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val req = chain.request()
            capturedUrl = req.url.toString()
            val buf = Buffer()
            req.body?.writeTo(buf)
            capturedBody = buf.readUtf8()
            val jsonResp = """
                {
                  "response": {
                    "success": {
                      "extensions": {
                        "customer_info": { "name": "Parent Account" }
                      },
                      "tokens": {
                        "bearer": {
                          "access_token": "Atna|access-123",
                          "refresh_token": "Atnr|refresh-456"
                        },
                        "mac_dms": {
                          "adp_token": "adp-token-xyz",
                          "device_private_key": "-----BEGIN RSA PRIVATE KEY-----\nMIIB...\n-----END RSA PRIVATE KEY-----"
                        }
                      }
                    }
                  }
                }
            """.trimIndent()
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(jsonResp.toResponseBody("application/json".toMediaType()))
                .build()
        }.build()

        val session = AudibleAuth.createPkceSession(
            marketplace = AudibleMarketplace.DE,
            serialHex = "AABBCCDDEEFF00112233445566778899",
        )
        val tokens = AudibleAuth.registerDevice(http, session, "ANhJ-code-999")

        assertEquals("https://api.amazon.de/auth/register", capturedUrl)
        val parsedReq = Json.parseToJsonElement(capturedBody).jsonObject
        val requestedTokenTypes = parsedReq["requested_token_type"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("bearer", "mac_dms"), requestedTokenTypes)
        assertFalse("website_cookies" in requestedTokenTypes, "Must never request persistent Amazon website cookies")

        assertEquals(AudibleMarketplace.DE, tokens.marketplace)
        assertEquals("adp-token-xyz", tokens.adpToken)
        assertTrue(tokens.devicePrivateKeyPem.startsWith("-----BEGIN RSA PRIVATE KEY-----"))
        assertEquals("Atnr|refresh-456", tokens.refreshToken)
        assertEquals("Atna|access-123", tokens.accessToken)
        assertEquals("Parent Account", tokens.customerName)
    }

    @Test
    fun `deregisters virtual device cleanly on sign-out`() = runTest {
        var authHeader = ""
        var deregisterUrl = ""
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val req = chain.request()
            deregisterUrl = req.url.toString()
            authHeader = req.header("Authorization").orEmpty()
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"response":{"success":{}}}""".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()

        val tokens = AudibleSessionTokens(
            marketplace = AudibleMarketplace.UK,
            deviceSerial = "AABBCCDDEEFF00112233445566778899",
            adpToken = "adp",
            devicePrivateKeyPem = "pem",
            refreshToken = "refresh",
            accessToken = "Atna|uk-token",
        )
        assertTrue(AudibleAuth.deregisterDevice(http, tokens))
        assertEquals("https://api.amazon.co.uk/auth/deregister", deregisterUrl)
        assertEquals("Bearer Atna|uk-token", authHeader)
    }
}
