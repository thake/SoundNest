/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.audible

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object AudibleAuth {
    const val AUDIBLE_DEVICE_TYPE = "A2CZJZGLK2JJVM"
    private const val APP_NAME = "Audible"
    private const val APP_VERSION = "3.74.0"

    private val secureRandom = SecureRandom()
    private val json = Json { ignoreUnknownKeys = true }
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    fun createPkceSession(
        marketplace: AudibleMarketplace,
        verifierBytes: ByteArray? = null,
        serialHex: String? = null,
    ): AudiblePkceSession {
        val rawVerifier = verifierBytes ?: ByteArray(32).also(secureRandom::nextBytes)
        val codeVerifier = Base64.getUrlEncoder().withoutPadding().encodeToString(rawVerifier)
        val serial = serialHex ?: ByteArray(16).also(secureRandom::nextBytes).joinToString("") { "%02X".format(it) }
        return AudiblePkceSession(
            marketplace = marketplace,
            deviceSerial = serial,
            codeVerifier = codeVerifier,
            codeChallenge = s256Challenge(codeVerifier),
        )
    }

    fun s256Challenge(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.encodeToByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun buildClientIdHex(deviceSerial: String): String =
        "$deviceSerial#$AUDIBLE_DEVICE_TYPE".encodeToByteArray().joinToString("") { "%02x".format(it) }

    fun buildSignInUrl(session: AudiblePkceSession): String {
        val m = session.marketplace
        val returnTo = "https://www.${m.audibleDomain}/ap/maplanding"
        val assocHandle = "amzn_audible_ios_${m.code}"
        val clientId = "device:${buildClientIdHex(session.deviceSerial)}"
        return "https://www.${m.amazonDomain}/ap/signin".toHttpUrl().newBuilder()
            .addQueryParameter("openid.oa2.response_type", "code")
            .addQueryParameter("openid.oa2.code_challenge_method", "S256")
            .addQueryParameter("openid.oa2.code_challenge", session.codeChallenge)
            .addQueryParameter("openid.return_to", returnTo)
            .addQueryParameter("openid.assoc_handle", assocHandle)
            .addQueryParameter("openid.identity", "http://specs.openid.net/auth/2.0/identifier_select")
            .addQueryParameter("pageId", "amzn_audible_ios")
            .addQueryParameter("accountStatusPolicy", "P1")
            .addQueryParameter("openid.claimed_id", "http://specs.openid.net/auth/2.0/identifier_select")
            .addQueryParameter("openid.mode", "checkid_setup")
            .addQueryParameter("openid.ns.oa2", "http://www.amazon.com/ap/ext/oauth/2")
            .addQueryParameter("openid.oa2.client_id", clientId)
            .addQueryParameter("openid.ns.pape", "http://specs.openid.net/extensions/pape/1.0")
            .addQueryParameter("marketPlaceId", m.marketplaceId)
            .addQueryParameter("openid.oa2.scope", "device_auth_access")
            .addQueryParameter("forceMobileLayout", "true")
            .addQueryParameter("openid.ns", "http://specs.openid.net/auth/2.0")
            .addQueryParameter("openid.pape.max_auth_age", "0")
            .build()
            .toString()
    }

    sealed class MapLandingResult {
        data class Authorized(val authorizationCode: String) : MapLandingResult()
        data class Error(val errorCode: String, val description: String?) : MapLandingResult()
        data object NotMapLanding : MapLandingResult()
    }

    fun parseRedirectUrl(url: String): MapLandingResult {
        val parsed = url.toHttpUrlOrNull() ?: return MapLandingResult.NotMapLanding
        if (!parsed.encodedPath.startsWith("/ap/maplanding")) return MapLandingResult.NotMapLanding
        val code = parsed.queryParameter("openid.oa2.authorization_code")
        if (!code.isNullOrBlank()) return MapLandingResult.Authorized(code)
        val err = parsed.queryParameter("openid.error") ?: parsed.queryParameter("error")
        if (!err.isNullOrBlank()) {
            return MapLandingResult.Error(err, parsed.queryParameter("error_description"))
        }
        return MapLandingResult.Error("missing_authorization_code", "Redirected to maplanding without authorization_code")
    }

    suspend fun registerDevice(
        http: OkHttpClient,
        session: AudiblePkceSession,
        authorizationCode: String,
        registerBaseUrlOverride: String? = null,
    ): AudibleSessionTokens = withContext(Dispatchers.IO) {
        val m = session.marketplace
        val baseUrl = (registerBaseUrlOverride ?: "https://api.${m.amazonDomain}").trimEnd('/')
        val payload = buildJsonObject {
            // Security invariant: request only bearer and mac_dms; never request website_cookies.
            put("requested_token_type", buildJsonArray {
                add(kotlinx.serialization.json.JsonPrimitive("bearer"))
                add(kotlinx.serialization.json.JsonPrimitive("mac_dms"))
            })
            put("cookies", buildJsonObject {
                put("website_cookies", buildJsonArray {})
                put("domain", ".${m.amazonDomain}")
            })
            put("registration_data", buildJsonObject {
                put("domain", "Device")
                put("app_version", APP_VERSION)
                put("device_type", AUDIBLE_DEVICE_TYPE)
                put("device_name", "%FIRST_NAME%'s%DUPE_STRATEGY_1ST%SoundNest")
                put("os_version", "15.0.0")
                put("device_serial", session.deviceSerial)
                put("device_model", "iPhone")
                put("app_name", APP_NAME)
                put("software_version", "35602678")
            })
            put("auth_data", buildJsonObject {
                put("client_id", buildClientIdHex(session.deviceSerial))
                put("authorization_code", authorizationCode)
                put("code_verifier", session.codeVerifier)
                put("code_algorithm", "SHA-256")
                put("client_domain", "DeviceLegacy")
            })
            put("requested_extensions", buildJsonArray {
                add(kotlinx.serialization.json.JsonPrimitive("device_info"))
                add(kotlinx.serialization.json.JsonPrimitive("customer_info"))
            })
        }

        val req = Request.Builder()
            .url("$baseUrl/auth/register")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .header("Accept", "application/json")
            .build()

        http.newCall(req).execute().use { resp ->
            val bodyStr = resp.body.string()
            if (!resp.isSuccessful) {
                throw AudibleApiException(resp.code, "Audible device registration failed (HTTP ${resp.code})")
            }
            val root = json.parseToJsonElement(bodyStr).jsonObject
            val success = root["response"]?.jsonObject?.get("success")?.jsonObject
                ?: throw AudibleApiException(resp.code, "Missing response.success in device registration")
            val tokensObj = success["tokens"]?.jsonObject
                ?: throw AudibleApiException(resp.code, "Missing tokens in device registration")
            val macDms = tokensObj["mac_dms"]?.jsonObject
                ?: throw AudibleApiException(resp.code, "Missing mac_dms tokens in device registration")
            val bearer = tokensObj["bearer"]?.jsonObject
            val customerName = success["extensions"]
                ?.jsonObject?.get("customer_info")
                ?.jsonObject?.get("name")
                ?.jsonPrimitive?.contentOrNull

            val adpToken = macDms["adp_token"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: throw AudibleApiException(resp.code, "Missing adp_token")
            val privateKeyPem = macDms["device_private_key"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: throw AudibleApiException(resp.code, "Missing device_private_key")
            val refreshToken = bearer?.get("refresh_token")?.jsonPrimitive?.contentOrNull.orEmpty()
            val accessToken = bearer?.get("access_token")?.jsonPrimitive?.contentOrNull.orEmpty()

            AudibleSessionTokens(
                marketplace = m,
                deviceSerial = session.deviceSerial,
                adpToken = adpToken,
                devicePrivateKeyPem = privateKeyPem,
                refreshToken = refreshToken,
                accessToken = accessToken,
                customerName = customerName,
            )
        }
    }

    suspend fun deregisterDevice(
        http: OkHttpClient,
        tokens: AudibleSessionTokens,
        deregisterBaseUrlOverride: String? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        val m = tokens.marketplace
        val baseUrl = (deregisterBaseUrlOverride ?: "https://api.${m.amazonDomain}").trimEnd('/')
        val accessToken = tokens.accessToken.ifBlank {
            refreshAccessToken(http, tokens, baseUrl) ?: return@withContext false
        }
        val payload = buildJsonObject {
            put("deregister_all_existing_accounts", false)
        }
        val req = Request.Builder()
            .url("$baseUrl/auth/deregister")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $accessToken")
            .build()
        runCatching {
            http.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private fun refreshAccessToken(
        http: OkHttpClient,
        tokens: AudibleSessionTokens,
        baseUrl: String,
    ): String? {
        if (tokens.refreshToken.isBlank()) return null
        val payload = buildJsonObject {
            put("app_name", APP_NAME)
            put("app_version", APP_VERSION)
            put("source_token", tokens.refreshToken)
            put("requested_token_type", "access_token")
            put("source_token_type", "refresh_token")
        }
        val req = Request.Builder()
            .url("$baseUrl/auth/token")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .header("Accept", "application/json")
            .build()
        return runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val obj: JsonObject = json.parseToJsonElement(resp.body.string()).jsonObject
                obj["access_token"]?.jsonPrimitive?.contentOrNull
            }
        }.getOrNull()
    }
}
