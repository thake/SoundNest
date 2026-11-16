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
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64

class AudibleApiClientTest {

    private val testKeyPem: String by lazy {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val b64 = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(kp.private.encoded)
        "-----BEGIN PRIVATE KEY-----\n$b64\n-----END PRIVATE KEY-----"
    }

    private fun testTokens() = AudibleSessionTokens(
        marketplace = AudibleMarketplace.DE,
        deviceSerial = "00112233445566778899AABBCCDDEEFF",
        adpToken = "adp-test-token",
        devicePrivateKeyPem = testKeyPem,
        refreshToken = "refresh-token",
    )

    private val fixedClock = Clock.fixed(Instant.parse("2026-11-16T22:45:00Z"), ZoneOffset.UTC)

    @Test
    fun `fetches paginated library with ADP signature headers and maps metadata cleanly`() = runTest {
        val requestedPaths = mutableListOf<String>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val req = chain.request()
            assertNotNull(req.header("x-adp-token"))
            assertEquals("SHA256withRSA:1.0", req.header("x-adp-alg"))
            assertTrue(req.header("x-adp-signature").orEmpty().endsWith(":2026-11-16T22:45:00Z"))
            val page = req.url.queryParameter("page")?.toInt() ?: 1
            requestedPaths += "${req.url.encodedPath}?page=$page"

            val body = if (page == 1) {
                """
                {
                  "items": [
                    {
                      "asin": "B08G9PRS1K",
                      "title": "Project Hail Mary",
                      "subtitle": "A Novel",
                      "authors": [{"name": "Andy Weir"}],
                      "narrators": [{"name": "Ray Porter"}],
                      "series": [{"title": "Hail Mary", "sequence": "1"}],
                      "runtime_length_min": 970,
                      "product_images": {"500": "https://m.media-amazon.com/images/I/500.jpg", "1024": "https://m.media-amazon.com/images/I/1024.jpg"},
                      "merchandising_summary": "<p>Ryland Grace is the sole survivor on a desperate mission.</p>",
                      "issue_date": "2021-05-04",
                      "is_adult_product": false,
                      "content_delivery_type": "MultiPartBook"
                    },
                    {
                      "asin": "B09ADULT99",
                      "title": "Adult Mystery",
                      "authors": [{"name": "Jane Doe"}],
                      "narrators": [{"name": "John Smith"}],
                      "runtime_length_min": 420,
                      "product_images": {"500": "https://m.media-amazon.com/images/I/adult.jpg"},
                      "is_adult_product": true,
                      "content_delivery_type": "SinglePartBook"
                    }
                  ]
                }
                """.trimIndent()
            } else {
                """
                {
                  "items": [
                    {
                      "asin": "B07KIDSBOOK",
                      "title": "Die Schule der magischen Tiere",
                      "authors": [{"name": "Margit Auer"}],
                      "narrators": [{"name": "Robert Missler"}],
                      "runtime_length_min": 215,
                      "product_images": {"500": "https://m.media-amazon.com/images/I/schule.jpg"},
                      "is_adult_product": false
                    }
                  ]
                }
                """.trimIndent()
            }
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }.build()

        val client = AudibleApiClient(http = http, tokensProvider = ::testTokens, clock = fixedClock)
        val books = client.fetchLibrary(pageSize = 2)

        assertEquals(listOf("/1.0/library?page=1", "/1.0/library?page=2"), requestedPaths)
        assertEquals(3, books.size)

        val first = books[0]
        assertEquals("B08G9PRS1K", first.asin)
        assertEquals("Project Hail Mary", first.title)
        assertEquals(listOf("Andy Weir"), first.authors)
        assertEquals(listOf("Ray Porter"), first.narrators)
        assertEquals("Hail Mary", first.seriesTitle)
        assertEquals("1", first.seriesSequence)
        assertEquals(970L * 60_000L, first.runtimeLengthMs)
        assertEquals("https://m.media-amazon.com/images/I/1024.jpg", first.coverUrl)
        assertEquals("Ryland Grace is the sole survivor on a desperate mission.", first.description)
        assertFalse(first.isAdultProduct)

        assertTrue(books[1].isAdultProduct)
        assertEquals("Die Schule der magischen Tiere", books[2].title)
    }

    @Test
    fun `requests Widevine DASH stream manifest and chapter table without WhisperSync writes`() = runTest {
        var sentPayload = ""
        val certBytes = byteArrayOf(1, 2, 3, 4, 5)
        val certB64 = Base64.getEncoder().encodeToString(certBytes)
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val req = chain.request()
            assertEquals("POST", req.method)
            assertEquals("/1.0/content/B08G9PRS1K/licenserequest", req.url.encodedPath)
            val buf = Buffer()
            req.body?.writeTo(buf)
            sentPayload = buf.readUtf8()

            val resp = """
                {
                  "content_license": {
                    "asin": "B08G9PRS1K",
                    "drm_type": "Widevine",
                    "license_response": "$certB64",
                    "content_metadata": {
                      "content_url": {
                        "offline_url": "https://cdn.audible.de/books/B08G9PRS1K/manifest.mpd"
                      },
                      "chapter_info": {
                        "chapters": [
                          {"title": "Opening Credits", "start_offset_ms": 0, "length_ms": 18000},
                          {"title": "Chapter 1", "start_offset_ms": 18000, "length_ms": 1245000},
                          {"title": "Chapter 2", "start_offset_ms": 1263000, "length_ms": 980000}
                        ]
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
                .body(resp.toResponseBody("application/json".toMediaType()))
                .build()
        }.build()

        val client = AudibleApiClient(http = http, tokensProvider = ::testTokens, clock = fixedClock)
        val grant = client.requestStreamManifest("B08G9PRS1K")

        val reqJson = Json.parseToJsonElement(sentPayload).jsonObject
        assertEquals("Streaming", reqJson["consumption_type"]!!.jsonPrimitive.content)
        val drmTypes = reqJson["supported_media_features"]!!.jsonObject["drm_types"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertTrue("Widevine" in drmTypes)
        assertFalse("Adrm" in drmTypes, "Must not request proprietary AAXC Adrm decryption keys")

        assertEquals("https://cdn.audible.de/books/B08G9PRS1K/manifest.mpd", grant.manifestUrl)
        assertEquals("Widevine", grant.drmType)
        assertEquals(3, grant.chapters.size)
        assertEquals("Chapter 1", grant.chapters[1].title)
        assertEquals(18_000L, grant.chapters[1].startOffsetMs)
        assertEquals(1_245_000L, grant.chapters[1].lengthMs)
        assertArrayEquals(certBytes, grant.serverCertificate)
    }

    @Test
    fun `exchanges Widevine CDM challenge bytes via Base64 JSON license request`() = runTest {
        val challenge = byteArrayOf(0x08, 0x01, 0x12, 0x10, 0x77, 0x69, 0x64, 0x65)
        val expectedLicense = byteArrayOf(0x4C, 0x49, 0x43, 0x45, 0x4E, 0x53, 0x45)
        val expectedLicenseB64 = Base64.getEncoder().encodeToString(expectedLicense)
        var capturedChallengeB64 = ""

        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val req = chain.request()
            val buf = Buffer()
            req.body?.writeTo(buf)
            val bodyObj = Json.parseToJsonElement(buf.readUtf8()).jsonObject
            capturedChallengeB64 = bodyObj["drm_challenge"]?.jsonPrimitive?.content.orEmpty()

            val resp = """
                {
                  "content_license": {
                    "asin": "B08G9PRS1K",
                    "drm_type": "Widevine",
                    "license_response": "$expectedLicenseB64"
                  }
                }
            """.trimIndent()
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(resp.toResponseBody("application/json".toMediaType()))
                .build()
        }.build()

        val client = AudibleApiClient(http = http, tokensProvider = ::testTokens, clock = fixedClock)
        val licenseBytes = client.exchangeWidevineChallenge("B08G9PRS1K", challenge)

        assertEquals(Base64.getEncoder().encodeToString(challenge), capturedChallengeB64)
        assertArrayEquals(expectedLicense, licenseBytes)
    }

    @Test
    fun `throws AudibleApiException when signed out or HTTP 403 returned`() = runTest {
        val signedOutClient = AudibleApiClient(http = OkHttpClient(), tokensProvider = { null }, clock = fixedClock)
        assertThrows(AudibleApiException::class.java) {
            kotlinx.coroutines.runBlocking { signedOutClient.fetchLibrary() }
        }
    }
}
