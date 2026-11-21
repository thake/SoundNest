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

data class ParentTransferInvite(
    val sourceParentId: String,
    val host: String,
    val port: Int,
    val code: String,
    val deviceName: String,
)

/**
 * Encodes and parses Child Device pairing QR payloads:
 * `soundnest-pair://v1?id=<childId>&host=<ip>&port=<port>&code=<6digits>&name=<urlEncodedName>`
 * and Parent-to-Parent transfer QR payloads:
 * `soundnest-parent-transfer://v1?id=<sourceParentId>&host=<ip>&port=<port>&code=<6digits>&name=<urlEncodedName>`
 */
object PairingUri {
    private const val SCHEME = "soundnest-pair"
    private const val TRANSFER_SCHEME = "soundnest-parent-transfer"
    private const val HOST_VERSION = "v1"

    fun encode(invite: PairingInvite): String =
        encodeWithScheme(SCHEME, invite.childId, invite.host, invite.port, invite.code, invite.deviceName)

    fun encodeParentTransfer(invite: ParentTransferInvite): String =
        encodeWithScheme(TRANSFER_SCHEME, invite.sourceParentId, invite.host, invite.port, invite.code, invite.deviceName)

    private fun encodeWithScheme(
        scheme: String,
        id: String,
        host: String,
        port: Int,
        code: String,
        deviceName: String,
    ): String {
        val params = listOf(
            "id" to id,
            "host" to host,
            "port" to port.toString(),
            "code" to code,
            "name" to deviceName,
        ).joinToString("&") { (k, v) ->
            "$k=${URLEncoder.encode(v, "UTF-8")}"
        }
        return "$scheme://$HOST_VERSION?$params"
    }

    fun parse(raw: String, allowLoopback: Boolean = false): PairingInvite? {
        val parsed = parseWithScheme(raw, SCHEME, "Child Device", allowLoopback) ?: return null
        return PairingInvite(
            childId = parsed.first,
            host = parsed.second,
            port = parsed.third,
            code = parsed.fourth,
            deviceName = parsed.fifth,
        )
    }

    fun parseParentTransfer(raw: String, allowLoopback: Boolean = false): ParentTransferInvite? {
        val parsed = parseWithScheme(raw, TRANSFER_SCHEME, "Parent Device", allowLoopback) ?: return null
        return ParentTransferInvite(
            sourceParentId = parsed.first,
            host = parsed.second,
            port = parsed.third,
            code = parsed.fourth,
            deviceName = parsed.fifth,
        )
    }

    private data class ParsedFields(
        val first: String,
        val second: String,
        val third: Int,
        val fourth: String,
        val fifth: String,
    )

    private fun parseWithScheme(
        raw: String,
        expectedScheme: String,
        defaultName: String,
        allowLoopback: Boolean,
    ): ParsedFields? {
        val trimmed = raw.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        if (!expectedScheme.equals(uri.scheme, ignoreCase = true)) return null
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
        val name = params["name"]?.trim().orEmpty().ifEmpty { defaultName }

        if (id.isEmpty() || host.isEmpty()) return null
        if (port !in 1..65535) return null
        if (code.length != 6) return null
        if (!isValidLanHost(host, allowLoopback)) return null

        return ParsedFields(id, host, port, code, name.take(40))
    }

    /** Only permits RFC1918 private IPv4 LAN addresses (or loopback in tests). */
    fun isValidLanHost(host: String, allowLoopback: Boolean = false): Boolean {
        val addr = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return false
        if (allowLoopback && addr.isLoopbackAddress) return true
        return addr is Inet4Address && addr.isSiteLocalAddress
    }
}
