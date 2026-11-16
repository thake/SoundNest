/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.audible

import android.app.Application
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.drm.ExoMediaDrm
import androidx.media3.exoplayer.drm.MediaDrmCallbackException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPairGenerator
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@androidx.annotation.OptIn(UnstableApi::class)
class AudibleMediaDrmCallbackTest {

    private fun generateTestPem(): String {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val b64 = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(kp.private.encoded)
        return "-----BEGIN PRIVATE KEY-----\n$b64\n-----END PRIVATE KEY-----"
    }

    @Test
    fun `executeKeyRequest signs Widevine challenge via AudibleApiClient and returns raw license bytes`() {
        val expectedLicense = byteArrayOf(0x10, 0x20, 0x30, 0x40, 0x50)
        val licenseB64 = Base64.getEncoder().encodeToString(expectedLicense)
        var capturedPath = ""
        var capturedBody = ""
        var hasAdpSignature = false

        val http = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                capturedPath = req.url.encodedPath
                hasAdpSignature = !req.header("x-adp-signature").isNullOrBlank()
                val buf = Buffer()
                req.body?.writeTo(buf)
                capturedBody = buf.readUtf8()
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """{"content_license":{"license_response":"$licenseB64"}}"""
                            .toResponseBody("application/json".toMediaType()),
                    )
                    .build()
            }
            .build()

        val tokens = AudibleSessionTokens(
            marketplace = AudibleMarketplace.DE,
            deviceSerial = "SERIAL123",
            adpToken = "ADP_TOKEN_XYZ",
            devicePrivateKeyPem = generateTestPem(),
            refreshToken = "REFRESH_XYZ",
        )
        val apiClient = AudibleApiClient(http = http, tokensProvider = { tokens })
        val callback = AudibleMediaDrmCallback(asin = "B08G9PRS1K", apiClient = apiClient, http = http)

        val challenge = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val keyRequest = ExoMediaDrm.KeyRequest(challenge, "https://license.audible.de")
        val actual = callback.executeKeyRequest(C.WIDEVINE_UUID, keyRequest)

        assertArrayEquals(expectedLicense, actual)
        assertEquals("/1.0/content/B08G9PRS1K/licenserequest", capturedPath)
        assertTrue(hasAdpSignature)
        assertTrue(capturedBody.contains(Base64.getEncoder().encodeToString(challenge)))
    }

    @Test
    fun `executeProvisionRequest posts signedRequest to Widevine provisioning URL and wraps HTTP errors in MediaDrmCallbackException`() {
        val provisionResponse = byteArrayOf(0x7A, 0x7B, 0x7C)
        var capturedUrl = ""

        val okHttp = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                capturedUrl = req.url.toString()
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(provisionResponse.toResponseBody("application/octet-stream".toMediaType()))
                    .build()
            }
            .build()

        val apiClient = AudibleApiClient(http = okHttp, tokensProvider = { null })
        val callback = AudibleMediaDrmCallback(asin = "B08G9PRS1K", apiClient = apiClient, http = okHttp)

        val provReq = ExoMediaDrm.ProvisionRequest("PROV_TOKEN".toByteArray(), "https://www.googleapis.com/certificateprovisioning/v1/devicecertificates/create?key=abc")
        val bytes = callback.executeProvisionRequest(C.WIDEVINE_UUID, provReq)
        assertArrayEquals(provisionResponse, bytes)
        assertTrue(capturedUrl.contains("signedRequest=PROV_TOKEN"))

        // Failing HTTP 500 must throw MediaDrmCallbackException
        val failingHttp = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(500)
                    .message("Server Error")
                    .body("err".toResponseBody("text/plain".toMediaType()))
                    .build()
            }
            .build()
        val failingCallback = AudibleMediaDrmCallback("B08G9PRS1K", apiClient, failingHttp)
        var threwDrmException = false
        try {
            failingCallback.executeProvisionRequest(C.WIDEVINE_UUID, provReq)
        } catch (_: MediaDrmCallbackException) {
            threwDrmException = true
        }
        assertTrue(threwDrmException)
    }
}
