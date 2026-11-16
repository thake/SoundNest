/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.audible

import kotlinx.serialization.Serializable

@Serializable
data class AudiblePkceSession(
    val marketplace: AudibleMarketplace,
    val deviceSerial: String,
    val codeVerifier: String,
    val codeChallenge: String,
)

@Serializable
data class AudibleSessionTokens(
    val marketplace: AudibleMarketplace,
    val deviceSerial: String,
    val adpToken: String,
    val devicePrivateKeyPem: String,
    val refreshToken: String,
    val accessToken: String = "",
    val customerName: String? = null,
)

@Serializable
data class AudibleChapter(
    val index: Int,
    val title: String,
    val startOffsetMs: Long,
    val lengthMs: Long,
) {
    val endOffsetMs: Long get() = startOffsetMs + lengthMs
}

@Serializable
data class AudibleBook(
    val asin: String,
    val title: String,
    val subtitle: String = "",
    val authors: List<String> = emptyList(),
    val narrators: List<String> = emptyList(),
    val seriesTitle: String? = null,
    val seriesSequence: String? = null,
    val runtimeLengthMs: Long = 0L,
    val coverUrl: String = "",
    val description: String = "",
    val releaseDate: String? = null,
    val isAdultProduct: Boolean = false,
)

data class AudibleStreamGrant(
    val asin: String,
    val manifestUrl: String,
    val drmType: String,
    val chapters: List<AudibleChapter>,
    val serverCertificate: ByteArray? = null,
)

class AudibleApiException(
    val statusCode: Int,
    message: String,
    cause: Throwable? = null,
) : java.io.IOException(message, cause)
