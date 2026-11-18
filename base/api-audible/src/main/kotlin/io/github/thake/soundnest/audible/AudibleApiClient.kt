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

    suspend fun fetchChapters(asin: String): List<AudibleChapter> = withContext(Dispatchers.IO) {
        val cleanAsin = asin.trim()
        require(cleanAsin.isNotEmpty()) { "ASIN must not be blank" }
        val tokens = requireTokens()
        val url = "${baseUrl(tokens)}/1.0/content/$cleanAsin/metadata".toHttpUrl().newBuilder()
            .addQueryParameter("response_groups", "chapter_info,content_reference")
            .addQueryParameter("chapter_titles_type", "Flat")
            .addQueryParameter("quality", "High")
            .build()
            .toString()

        val req = buildSignedRequest(tokens, "GET", url)
        http.newCall(req).execute().use { resp ->
            val raw = resp.body.string()
            if (!resp.isSuccessful) {
                throw AudibleApiException(resp.code, "Audible chapter metadata request failed (HTTP ${resp.code})")
            }
            val root = json.parseToJsonElement(raw).jsonObject
            val chapterInfo = root["content_metadata"]?.jsonObject?.get("chapter_info")?.jsonObject
                ?: root["content_license"]?.jsonObject?.get("content_metadata")?.jsonObject?.get("chapter_info")?.jsonObject
                ?: root["chapter_info"]?.jsonObject
            parseChapterInfo(chapterInfo)
        }
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
            put("chapter_titles_type", "Flat")
            put("tenant_id", "Audible")
            put("spatial", false)
            put("response_groups", "chapter_info,content_reference,certificate,content_url")
            put("supported_media_features", buildJsonObject {
                put("chapter_titles_type", "Flat")
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
                val detail = extractSafeErrorDetail(raw)
                throw AudibleApiException(
                    resp.code,
                    buildString {
                        append("Audible stream manifest request failed (HTTP ${resp.code})")
                        if (!detail.isNullOrBlank()) append(": $detail")
                    },
                )
            }
            val root = json.parseToJsonElement(raw).jsonObject
            val licenseObj = root["content_license"]?.jsonObject
                ?: throw AudibleApiException(resp.code, "Missing content_license in licenserequest response")
            val statusCode = licenseObj["status_code"]?.jsonPrimitive?.contentOrNull?.trim()
            if (!statusCode.isNullOrBlank() && !statusCode.equals("Granted", ignoreCase = true)) {
                val detail = extractLicenseDenialDetail(licenseObj)
                throw AudibleApiException(
                    resp.code,
                    buildString {
                        append("Audible stream license denied (status_code=$statusCode)")
                        if (!detail.isNullOrBlank()) append(": $detail")
                    },
                )
            }

            val drmType = licenseObj["drm_type"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "Widevine"
            val metadata = licenseObj["content_metadata"]?.jsonObject
            val licenseResponseStr = licenseObj["license_response"]?.jsonPrimitive?.contentOrNull
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

            val contentUrlFromMetadata = when (val cuEl = metadata?.get("content_url")) {
                is JsonObject -> {
                    cuEl["streaming_url"]?.jsonPrimitive?.contentOrNull
                        ?: cuEl["offline_url"]?.jsonPrimitive?.contentOrNull
                        ?: cuEl["url"]?.jsonPrimitive?.contentOrNull
                }
                is JsonPrimitive -> cuEl.contentOrNull
                else -> null
            }?.trim()?.takeIf { it.isNotEmpty() }

            val manifestUrl = contentUrlFromMetadata
                ?: when {
                    licenseResponseStr != null && (
                        licenseResponseStr.startsWith("https://", ignoreCase = true) ||
                            licenseResponseStr.startsWith("http://", ignoreCase = true)
                        ) -> licenseResponseStr
                    licenseResponseStr != null && licenseResponseStr.startsWith("<MPD", ignoreCase = true) ->
                        "data:application/dash+xml;base64," + Base64.getEncoder().encodeToString(licenseResponseStr.encodeToByteArray())
                    else -> null
                }
                ?: throw AudibleApiException(
                    resp.code,
                    "Missing manifest URL in licenserequest response (status_code=${statusCode ?: "unknown"}, drm_type=$drmType)",
                )

            val chapters = parseChapterInfo(metadata?.get("chapter_info")?.jsonObject)

            // Widevine streaming grants put the service certificate protobuf in `certificate`
            // while `license_response` carries the signed CloudFront manifest URL.
            val explicitCertB64 = licenseObj["certificate"]?.jsonPrimitive?.contentOrNull
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            val certB64 = explicitCertB64 ?: licenseResponseStr?.takeIf {
                !it.startsWith("http://", ignoreCase = true) &&
                    !it.startsWith("https://", ignoreCase = true) &&
                    !it.startsWith("<")
            }
            val certBytes = certB64?.let {
                runCatching { Base64.getMimeDecoder().decode(it) }.getOrNull()
            }
            val acr = licenseObj["acr"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: metadata?.get("content_reference")?.jsonObject?.get("acr")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

            AudibleStreamGrant(
                asin = cleanAsin,
                manifestUrl = manifestUrl,
                drmType = drmType,
                chapters = chapters,
                serverCertificate = certBytes,
                acr = acr,
            )
        }
    }

    internal fun parseChapterInfo(chapterInfo: JsonObject?): List<AudibleChapter> {
        if (chapterInfo == null) return emptyList()
        val rawChapters = chapterInfo["chapters"] as? JsonArray ?: return emptyList()
        val totalRuntimeMs = extractLongMs(chapterInfo, "runtime_length_ms", "runtime_length_sec")

        data class RawNode(val title: String, val startMs: Long, val lengthMs: Long)

        fun collectNodes(elements: JsonArray, parentTitle: String = ""): List<RawNode> {
            val out = ArrayList<RawNode>()
            for (el in elements) {
                val ch = el as? JsonObject ?: continue
                val startMs = extractLongMs(ch, "start_offset_ms", "start_offset_sec").coerceAtLeast(0L)
                val lenMs = extractLongMs(ch, "length_ms", "length_sec")
                    .let { if (it > 0L) it else extractLongMs(ch, "runtime_length_ms", "runtime_length_sec") }
                    .coerceAtLeast(0L)
                val rawTitle = ch["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val nested = (ch["chapters"] as? JsonArray)?.takeIf { it.isNotEmpty() }

                if (nested != null) {
                    val childPrefix = when {
                        rawTitle.isNotEmpty() && parentTitle.isNotEmpty() && !rawTitle.startsWith(parentTitle, ignoreCase = true) ->
                            "$parentTitle - $rawTitle"
                        rawTitle.isNotEmpty() -> rawTitle
                        else -> parentTitle
                    }
                    val children = collectNodes(nested, childPrefix)
                    val firstChildStart = children.firstOrNull()?.startMs
                    if (firstChildStart != null && startMs < firstChildStart) {
                        val gapMs = firstChildStart - startMs
                        val introLen = if (lenMs > 0L) minOf(lenMs, gapMs) else gapMs
                        out += RawNode(
                            title = childPrefix,
                            startMs = startMs,
                            lengthMs = introLen.coerceAtLeast(0L),
                        )
                        // Children after a spoken part-intro keep their own chapter titles cleanly
                        out += collectNodes(nested, "")
                    } else if (children.isNotEmpty()) {
                        out += children
                    } else {
                        out += RawNode(title = childPrefix, startMs = startMs, lengthMs = lenMs)
                    }
                } else {
                    val effectiveTitle = when {
                        rawTitle.isNotEmpty() && parentTitle.isNotEmpty() && !rawTitle.startsWith(parentTitle, ignoreCase = true) ->
                            "$parentTitle - $rawTitle"
                        rawTitle.isNotEmpty() -> rawTitle
                        else -> parentTitle
                    }
                    out += RawNode(title = effectiveTitle, startMs = startMs, lengthMs = lenMs)
                }
            }
            return out
        }

        val sorted = collectNodes(rawChapters).sortedBy { it.startMs }
        if (sorted.isEmpty()) return emptyList()

        return sorted.mapIndexed { idx, node ->
            val nextStart = sorted.getOrNull(idx + 1)?.startMs
            val resolvedLen = when {
                node.lengthMs > 0L -> node.lengthMs
                nextStart != null && nextStart > node.startMs -> nextStart - node.startMs
                totalRuntimeMs > node.startMs -> totalRuntimeMs - node.startMs
                else -> 0L
            }
            AudibleChapter(
                index = idx,
                title = node.title.ifBlank { "Chapter ${idx + 1}" },
                startOffsetMs = node.startMs,
                lengthMs = resolvedLen.coerceAtLeast(0L),
            )
        }
    }

    private fun extractLongMs(obj: JsonObject, msKey: String, secKey: String): Long {
        val msVal = (obj[msKey] as? JsonPrimitive)?.let { prim ->
            prim.longOrNull ?: prim.contentOrNull?.trim()?.toLongOrNull()
        }
        if (msVal != null) return msVal
        val secVal = (obj[secKey] as? JsonPrimitive)?.let { prim ->
            prim.longOrNull ?: prim.contentOrNull?.trim()?.toLongOrNull()
        }
        return secVal?.times(1_000L) ?: 0L
    }

    suspend fun exchangeWidevineChallenge(asin: String, challengeBytes: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        val cleanAsin = asin.trim()
        require(cleanAsin.isNotEmpty()) { "ASIN must not be blank" }
        val tokens = requireTokens()
        val url = "${baseUrl(tokens)}/1.0/content/$cleanAsin/drmlicense"
        val challengeB64 = Base64.getEncoder().encodeToString(challengeBytes)

        val bodyObj = buildJsonObject {
            put("asin", cleanAsin)
            put("consumption_type", "Streaming")
            put("drm_type", "Widevine")
            put("tenant_id", "Audible")
            put("licenseChallenge", challengeB64)
        }

        val req = buildSignedRequest(tokens, "POST", url, bodyObj.toString())
        http.newCall(req).execute().use { resp ->
            val raw = resp.body.string()
            if (!resp.isSuccessful) {
                val detail = extractSafeErrorDetail(raw)
                throw AudibleApiException(
                    resp.code,
                    buildString {
                        append("Widevine license exchange failed (HTTP ${resp.code})")
                        if (!detail.isNullOrBlank()) append(": $detail")
                    },
                )
            }
            val root = json.parseToJsonElement(raw).jsonObject
            val licenseB64 = root["license"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                ?: root["drm_license"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                ?: root["content_license"]?.jsonObject?.get("license_response")?.jsonPrimitive?.contentOrNull
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() && !it.startsWith("http", ignoreCase = true) }
            if (licenseB64 == null) {
                val errMsg = root["message"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                    ?: root["reason"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                throw AudibleApiException(
                    resp.code,
                    buildString {
                        append("Missing license in drmlicense response")
                        if (!errMsg.isNullOrBlank()) append(": ${errMsg.take(200)}")
                    },
                )
            }
            Base64.getMimeDecoder().decode(licenseB64)
        }
    }

    private fun extractSafeErrorDetail(raw: String): String? {
        val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        val msg = (obj["message"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        val reason = (obj["reason"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        return listOfNotNull(msg, reason).distinct().joinToString(" — ").take(200).ifBlank { null }
    }

    private fun extractLicenseDenialDetail(licenseObj: JsonObject): String? {
        val msg = (licenseObj["message"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        val reasons = (licenseObj["license_denial_reasons"] as? JsonArray)?.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            (obj["message"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                ?: (obj["validationType"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        }?.takeIf { it.isNotEmpty() }?.joinToString("; ")
        return listOfNotNull(reasons, msg).distinct().joinToString(" — ").take(200).ifBlank { null }
    }
}
