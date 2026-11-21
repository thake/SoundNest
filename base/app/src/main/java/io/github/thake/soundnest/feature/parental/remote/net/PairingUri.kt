/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.remote.net

import java.net.Inet4Address
import java.net.InetAddress
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

data class PairingInvite(
    val childId: String,
    val host: String,
    val port: Int,
    val code: String,
    val deviceName: String,
)

/**
 * Encodes and parses Child Device pairing QR payloads:
 * `soundnest-pair://v1?id=<childId>&host=<ip>&port=<port>&code=<6digits>&name=<urlEncodedName>`
 */
object PairingUri {
    private const val SCHEME = "soundnest-pair"
    private const val HOST_VERSION = "v1"

    fun encode(invite: PairingInvite): String {
        val params = listOf(
            "id" to invite.childId,
            "host" to invite.host,
            "port" to invite.port.toString(),
            "code" to invite.code,
            "name" to invite.deviceName,
        ).joinToString("&") { (k, v) ->
            "$k=${URLEncoder.encode(v, "UTF-8")}"
        }
        return "$SCHEME://$HOST_VERSION?$params"
    }

    fun parse(raw: String, allowLoopback: Boolean = false): PairingInvite? {
        val trimmed = raw.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        if (!SCHEME.equals(uri.scheme, ignoreCase = true)) return null
        if (!HOST_VERSION.equals(uri.host, ignoreCase = true)) return null
        val query = uri.rawQuery ?: return null
        val params = query.split("&").mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) null
            else part.substring(0, idx) to runCatching {
                URLDecoder.decode(part.substring(idx + 1), "UTF-8")
            }.getOrDefault("")
        }.toMap()

        val id = params["id"]?.trim().orEmpty()
        val host = params["host"]?.trim().orEmpty()
        val port = params["port"]?.toIntOrNull() ?: return null
        val code = params["code"]?.filter { it.isDigit() }.orEmpty()
        val name = params["name"]?.trim().orEmpty().ifEmpty { "Child Device" }

        if (id.isEmpty() || host.isEmpty()) return null
        if (port !in 1..65535) return null
        if (code.length != 6) return null
        if (!isValidLanHost(host, allowLoopback)) return null

        return PairingInvite(
            childId = id,
            host = host,
            port = port,
            code = code,
            deviceName = name.take(40),
        )
    }

    /** Only permits RFC1918 private IPv4 LAN addresses (or loopback in tests). */
    fun isValidLanHost(host: String, allowLoopback: Boolean = false): Boolean {
        val addr = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return false
        if (allowLoopback && addr.isLoopbackAddress) return true
        return addr is Inet4Address && addr.isSiteLocalAddress
    }
}
