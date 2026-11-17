/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.spokenword.audible.playback


import io.github.thake.soundnest.audible.*

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.drm.ExoMediaDrm
import androidx.media3.exoplayer.drm.MediaDrmCallback
import androidx.media3.exoplayer.drm.MediaDrmCallbackException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID

/**
 * Media3 [MediaDrmCallback] bridging Android's hardware/software Widevine CDM (`MediaDrm`)
 * to Audible's ADP-signed `/1.0/content/<asin>/licenserequest` endpoint.
 *
 * Security & legal invariant: raw content decryption keys never enter app memory;
 * the opaque Widevine license blob is passed directly back to Android `MediaDrm`.
 */
@OptIn(UnstableApi::class)
class AudibleMediaDrmCallback(
    private val asin: String,
    private val apiClient: AudibleApiClient,
    private val http: OkHttpClient,
) : MediaDrmCallback {

    override fun executeProvisionRequest(
        uuid: UUID,
        request: ExoMediaDrm.ProvisionRequest,
    ): ByteArray {
        val separator = if ('?' in request.defaultUrl) '&' else '?'
        val url = request.defaultUrl + "${separator}signedRequest=" + String(request.data, Charsets.UTF_8)
        return try {
            val req = Request.Builder()
                .url(url)
                .post(ByteArray(0).toRequestBody("application/octet-stream".toMediaType()))
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IOException("Widevine provisioning failed (HTTP ${resp.code})")
                }
                resp.body.bytes()
            }
        } catch (e: Exception) {
            throw drmException(url, e)
        }
    }

    override fun executeKeyRequest(
        uuid: UUID,
        request: ExoMediaDrm.KeyRequest,
    ): ByteArray {
        return try {
            runBlocking {
                apiClient.exchangeWidevineChallenge(asin = asin, challengeBytes = request.data)
            }
        } catch (e: Exception) {
            throw drmException(request.licenseServerUrl, e)
        }
    }

    private fun drmException(url: String, cause: Throwable): MediaDrmCallbackException {
        val uri = runCatching { Uri.parse(url) }.getOrDefault(Uri.EMPTY)
        return MediaDrmCallbackException(
            DataSpec(uri),
            uri,
            emptyMap(),
            0L,
            cause,
        )
    }
}
