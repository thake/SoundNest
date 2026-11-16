/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.audible

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Clock
import java.util.Base64

class AudibleApiClient(
    private val http: OkHttpClient,
    private val tokensProvider: () -> AudibleSessionTokens?,
    private val apiBaseUrlOverride: String? = null,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private fun requireTokens(): AudibleSessionTokens =
        tokensProvider() ?: throw AudibleApiException(401, "Audible account is not signed in")

    private fun baseUrl(tokens: AudibleSessionTokens): String =
        (apiBaseUrlOverride ?: tokens.marketplace.apiBaseUrl).trimEnd('/')

    private fun buildSignedRequest(
        tokens: AudibleSessionTokens,
        method: String,
        fullUrl: String,
        body: String = "",
    ): Request {
        val parsed = fullUrl.toHttpUrl()
        val pathAndQuery = buildString {
            append(parsed.encodedPath)
            parsed.encodedQuery?.takeIf { it.isNotEmpty() }?.let {
                append('?')
                append(it)
            }
        }
        val signed = AdpRequestSigner.sign(
            method = method,
            pathAndQuery = pathAndQuery,
            body = body,
            adpToken = tokens.adpToken,
            privateKeyPem = tokens.devicePrivateKeyPem,
            timestamp = clock.instant(),
        )
        val builder = Request.Builder()
            .url(parsed)
            .header("Accept", "application/json")
        signed.toHeaderMap().forEach { (k, v) -> builder.header(k, v) }
        when (method.uppercase()) {
            "GET" -> builder.get()
            "POST" -> builder.post(body.toRequestBody(JSON_MEDIA))
            else -> builder.method(method.uppercase(), body.takeIf { it.isNotEmpty() }?.toRequestBody(JSON_MEDIA))
        }
        return builder.build()
    }

    suspend fun fetchLibrary(pageSize: Int = 500): List<AudibleBook> = withContext(Dispatchers.IO) {
        val tokens = requireTokens()
        val base = baseUrl(tokens)
        val clampedSize = pageSize.coerceIn(1, 1000)
        val results = LinkedHashMap<String, AudibleBook>()
        var page = 1

        while (page <= 50) {
            val url = "$base/1.0/library".toHttpUrl().newBuilder()
                .addQueryParameter("num_results", clampedSize.toString())
                .addQueryParameter("page", page.toString())
                .addQueryParameter(
                    "response_groups",
                    "contributors,media,product_attrs,product_desc,product_extended_attrs,series",
                )
                .build()
                .toString()

            val req = buildSignedRequest(tokens, "GET", url)
            val itemsCount = http.newCall(req).execute().use { resp ->
                val raw = resp.body.string()
                if (!resp.isSuccessful) {
                    throw AudibleApiException(resp.code, "Audible library fetch failed (HTTP ${resp.code})")
                }
                val root = json.parseToJsonElement(raw).jsonObject
                val items = root["items"]?.jsonArray ?: JsonArray(emptyList())
                for (el in items) {
                    val itemObj = el as? JsonObject ?: continue
                    parseLibraryBook(itemObj)?.let { book ->
                        results.putIfAbsent(book.asin, book)
                    }
                }
                items.size
            }
            if (itemsCount < clampedSize) break
            page++
        }
        results.values.toList()
    }

    private fun parseLibraryBook(obj: JsonObject): AudibleBook? {
        val asin = obj["asin"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val title = obj["title"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val deliveryType = obj["content_delivery_type"]?.jsonPrimitive?.contentOrNull
        if (deliveryType != null && deliveryType.equals("Pdf", ignoreCase = true)) return null

        val subtitle = obj["subtitle"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val authors = (obj["authors"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull?.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        val narrators = (obj["narrators"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull?.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        val firstSeries = (obj["series"] as? JsonArray)?.firstOrNull() as? JsonObject
        val seriesTitle = firstSeries?.get("title")?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        val seriesSequence = firstSeries?.get("sequence")?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        val runtimeMin = obj["runtime_length_min"]?.jsonPrimitive?.longOrNull ?: 0L
        val coverUrl = selectBestImage(obj["product_images"] as? JsonObject)
        val summaryHtml = obj["merchandising_summary"]?.jsonPrimitive?.contentOrNull
            ?: obj["publisher_summary"]?.jsonPrimitive?.contentOrNull
            ?: ""
        val releaseDate = obj["issue_date"]?.jsonPrimitive?.contentOrNull
            ?: obj["release_date"]?.jsonPrimitive?.contentOrNull
        val isAdult = obj["is_adult_product"]?.jsonPrimitive?.booleanOrNull == true

        return AudibleBook(
            asin = asin,
            title = title,
            subtitle = subtitle,
            authors = authors,
            narrators = narrators,
            seriesTitle = seriesTitle,
            seriesSequence = seriesSequence,
            runtimeLengthMs = runtimeMin.coerceAtLeast(0L) * 60_000L,
            coverUrl = coverUrl,
            description = stripHtmlTags(summaryHtml),
            releaseDate = releaseDate,
            isAdultProduct = isAdult,
        )
    }

    private fun selectBestImage(images: JsonObject?): String {
        if (images == null || images.isEmpty()) return ""
        return images.entries
            .mapNotNull { (key, value) ->
                val url = (value as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                (key.toIntOrNull() ?: 0) to url
            }
            .maxByOrNull { it.first }
            ?.second
            .orEmpty()
    }

    private fun stripHtmlTags(html: String): String {
        if (html.isBlank()) return ""
        return html
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    suspend fun requestStreamManifest(asin: String): AudibleStreamGrant = withContext(Dispatchers.IO) {
        val cleanAsin = asin.trim()
        require(cleanAsin.isNotEmpty()) { "ASIN must not be blank" }
        val tokens = requireTokens()
        val url = "${baseUrl(tokens)}/1.0/content/$cleanAsin/licenserequest"

        // Security & boundary invariant: request standard Widevine / MPEG-DASH / HLS-CMAF streaming only;
        // never request Adrm (AAXC raw key/iv) or download decryption keys, and omit WhisperSync writes.
        val bodyObj = buildJsonObject {
            put("consumption_type", "Streaming")
            put("quality", "High")
            put("use_adaptive_bit_rate", true)
            put("response_groups", "chapter_info,content_reference,certificate,content_url")
            put("supported_media_features", buildJsonObject {
                put("codecs", buildJsonArray {
                    add(JsonPrimitive("mp4a.40.2"))
                    add(JsonPrimitive("mp4a.40.42"))
                })
                put("drm_types", buildJsonArray {
                    add(JsonPrimitive("Widevine"))
                    add(JsonPrimitive("Dash"))
                    add(JsonPrimitive("HlsCmaf"))
                })
            })
        }

        val req = buildSignedRequest(tokens, "POST", url, bodyObj.toString())
        http.newCall(req).execute().use { resp ->
            val raw = resp.body.string()
            if (!resp.isSuccessful) {
                throw AudibleApiException(resp.code, "Audible stream manifest request failed (HTTP ${resp.code})")
            }
            val root = json.parseToJsonElement(raw).jsonObject
            val licenseObj = root["content_license"]?.jsonObject
                ?: throw AudibleApiException(resp.code, "Missing content_license in licenserequest response")
            val drmType = licenseObj["drm_type"]?.jsonPrimitive?.contentOrNull ?: "Widevine"
            val metadata = licenseObj["content_metadata"]?.jsonObject
            val manifestUrl = metadata?.get("content_url")?.jsonObject?.let { cu ->
                cu["offline_url"]?.jsonPrimitive?.contentOrNull
                    ?: cu["url"]?.jsonPrimitive?.contentOrNull
            }?.takeIf { it.isNotBlank() }
                ?: throw AudibleApiException(resp.code, "Missing manifest URL in content_metadata.content_url")

            val rawChapters = metadata["chapter_info"]?.jsonObject?.get("chapters") as? JsonArray
            val chapters = rawChapters?.mapIndexedNotNull { idx, el ->
                val ch = el as? JsonObject ?: return@mapIndexedNotNull null
                val startMs = ch["start_offset_ms"]?.jsonPrimitive?.longOrNull ?: 0L
                val lenMs = ch["length_ms"]?.jsonPrimitive?.longOrNull ?: 0L
                val chTitle = ch["title"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?.takeIf { it.isNotEmpty() } ?: "Chapter ${idx + 1}"
                AudibleChapter(
                    index = idx,
                    title = chTitle,
                    startOffsetMs = startMs.coerceAtLeast(0L),
                    lengthMs = lenMs.coerceAtLeast(0L),
                )
            }.orEmpty()

            val certB64 = licenseObj["license_response"]?.jsonPrimitive?.contentOrNull
                ?: licenseObj["certificate"]?.jsonPrimitive?.contentOrNull
            val certBytes = certB64?.takeIf { it.isNotBlank() }?.let {
                runCatching { Base64.getDecoder().decode(it) }.getOrNull()
            }

            AudibleStreamGrant(
                asin = cleanAsin,
                manifestUrl = manifestUrl,
                drmType = drmType,
                chapters = chapters,
                serverCertificate = certBytes,
            )
        }
    }

    suspend fun exchangeWidevineChallenge(asin: String, challengeBytes: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        val cleanAsin = asin.trim()
        require(cleanAsin.isNotEmpty()) { "ASIN must not be blank" }
        val tokens = requireTokens()
        val url = "${baseUrl(tokens)}/1.0/content/$cleanAsin/licenserequest"
        val challengeB64 = Base64.getEncoder().encodeToString(challengeBytes)

        val bodyObj = buildJsonObject {
            put("consumption_type", "Streaming")
            put("drm_type", "Widevine")
            put("drm_challenge", challengeB64)
            put("quality", "High")
            put("use_adaptive_bit_rate", true)
            put("supported_media_features", buildJsonObject {
                put("codecs", buildJsonArray {
                    add(JsonPrimitive("mp4a.40.2"))
                    add(JsonPrimitive("mp4a.40.42"))
                })
                put("drm_types", buildJsonArray {
                    add(JsonPrimitive("Widevine"))
                })
            })
        }

        val req = buildSignedRequest(tokens, "POST", url, bodyObj.toString())
        http.newCall(req).execute().use { resp ->
            val raw = resp.body.string()
            if (!resp.isSuccessful) {
                throw AudibleApiException(resp.code, "Widevine license exchange failed (HTTP ${resp.code})")
            }
            val root = json.parseToJsonElement(raw).jsonObject
            val licenseB64 = root["content_license"]
                ?.jsonObject?.get("license_response")
                ?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?: throw AudibleApiException(resp.code, "Missing content_license.license_response")
            Base64.getDecoder().decode(licenseB64)
        }
    }
}
