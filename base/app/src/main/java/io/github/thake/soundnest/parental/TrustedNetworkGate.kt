/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.parental

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import io.github.thake.soundnest.connect.CustomSpeakerNames
import io.github.thake.soundnest.connect.LanDiscovery
import io.github.thake.soundnest.di.SongPlayer
import java.net.Inet4Address

data class NetworkSnapshot(
    val onWifi: Boolean,
    val ssid: String? = null,
    val bssid: String? = null,
    val lanNetworkFingerprint: String? = null,
    val lanNetworkLabel: String? = null,
    val discoveredLanDeviceIds: Set<String> = emptySet(),
)

enum class SpeakerVisibilityMode {
    ALLOW_ALL_EXCEPT_HIDDEN,
    ONLY_ALLOWED,
}

/**
 * Enforces Trusted Home Network boundaries for cloud Spotify Connect / Amazon Alexa speakers
 * and local Zeroconf (`LanDiscovery` / `LanLogin` `addUser`) token provisioning.
 *
 * Uses **Zero-Permission LAN & Speaker Fingerprinting**:
 * - Requires zero location permissions (`ACCESS_FINE_LOCATION` is never requested).
 * - Fingerprints the active Wi-Fi LAN using `ConnectivityManager.getLinkProperties` (default IPv4
 *   gateway, subnet prefix, local DNS servers, and DHCP domain) combined with discovered mDNS
 *   Spotify Connect speaker IDs (`LanDiscovery`) plus legacy/imported SSID or BSSID rules.
 * - Defaults fail closed for children's safety (`requireTrustedNetwork = true`, `allowJam = false`).
 */
object TrustedNetworkGate {
    private const val PREFS = "parental_trusted_network"
    private const val KEY_REQUIRE_TRUSTED = "require_trusted_network"
    private const val KEY_SSIDS = "trusted_ssids"
    private const val KEY_SSID_LABELS = "trusted_ssid_labels"
    private const val KEY_BSSIDS = "trusted_bssids"
    private const val KEY_LAN_FINGERPRINTS = "trusted_lan_fingerprints"
    private const val KEY_LAN_LABELS = "trusted_lan_labels"
    private const val KEY_VISIBILITY_MODE = "speaker_visibility_mode"
    private const val KEY_HIDDEN_DEVICES = "hidden_device_ids"
    private const val KEY_ALLOWED_DEVICES = "allowed_device_ids"
    private const val KEY_ALLOW_JAM = "allow_jam"

    const val LAN_NET_PREFIX = "lan-net:"
    private const val LABEL_SEP = "\t"

    private val PLACEHOLDER_BSSIDS = setOf("02:00:00:00:00:00", "00:00:00:00:00:00")

    @Volatile
    internal var snapshotOverride: ((Context) -> NetworkSnapshot)? = null

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun cleanDisplaySsid(raw: String?): String? {
        val cleaned = raw?.trim()?.removeSurrounding("\"")?.trim() ?: return null
        if (cleaned.isEmpty() || cleaned.equals("<unknown ssid>", ignoreCase = true)) return null
        return cleaned
    }

    fun normalizeSsid(raw: String?): String? = cleanDisplaySsid(raw)?.lowercase()

    fun normalizeBssid(raw: String?): String? {
        val cleaned = raw?.trim()?.lowercase() ?: return null
        if (cleaned.isEmpty() || cleaned in PLACEHOLDER_BSSIDS) return null
        return cleaned
    }

    internal fun ipv4NetworkPrefix(address: Inet4Address, prefixLength: Int): String {
        val clamped = prefixLength.coerceIn(0, 32)
        val rawBytes = address.address
        val ipLong = ((rawBytes[0].toLong() and 0xFF) shl 24) or
            ((rawBytes[1].toLong() and 0xFF) shl 16) or
            ((rawBytes[2].toLong() and 0xFF) shl 8) or
            (rawBytes[3].toLong() and 0xFF)
        val mask = if (clamped == 0) 0L else ((-1L shl (32 - clamped)) and 0xFFFFFFFFL)
        val net = ipLong and mask
        val a = (net ushr 24) and 0xFF
        val b = (net ushr 16) and 0xFF
        val c = (net ushr 8) and 0xFF
        val d = net and 0xFF
        return "$a.$b.$c.$d/$clamped"
    }

    internal fun buildLanNetworkFingerprint(
        gateways: List<String>,
        subnets: List<String>,
        dnsServers: List<String>,
        domains: String?,
    ): Pair<String, String>? {
        val gw = gateways.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
        val sub = subnets.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
        val dns = dnsServers.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
        val dom = domains?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        if (gw.isEmpty() && sub.isEmpty() && dns.isEmpty() && dom == null) return null

        val fingerprint = "${LAN_NET_PREFIX}gw=${gw.joinToString(",")}|sub=${sub.joinToString(",")}|dns=${dns.joinToString(",")}|dom=${dom.orEmpty()}"
        val summaryParts = buildList {
            when {
                gw.isNotEmpty() -> add(gw.first())
                sub.isNotEmpty() -> add(sub.first())
                dns.isNotEmpty() -> add(dns.first())
            }
            if (dom != null) add(dom)
        }
        val label = if (summaryParts.isEmpty()) "Home LAN" else "Home LAN (${summaryParts.joinToString(" · ")})"
        return fingerprint to label
    }

    internal fun fingerprintFromLinkProperties(linkProperties: LinkProperties?): Pair<String, String>? {
        if (linkProperties == null) return null
        val gateways = runCatching {
            linkProperties.routes
                .filter { route ->
                    val gw = route.gateway
                    route.isDefaultRoute && gw is Inet4Address && !gw.isAnyLocalAddress && !gw.isLoopbackAddress
                }
                .mapNotNull { it.gateway?.hostAddress }
        }.getOrDefault(emptyList())

        val subnets = runCatching {
            linkProperties.linkAddresses.mapNotNull { la ->
                val addr = la.address as? Inet4Address ?: return@mapNotNull null
                if (addr.isLoopbackAddress || addr.isLinkLocalAddress) return@mapNotNull null
                ipv4NetworkPrefix(addr, la.prefixLength)
            }
        }.getOrDefault(emptyList())

        val dnsServers = runCatching {
            linkProperties.dnsServers
                .filterIsInstance<Inet4Address>()
                .filter { !it.isLoopbackAddress }
                .mapNotNull { it.hostAddress }
        }.getOrDefault(emptyList())

        val domains = runCatching { linkProperties.domains }.getOrNull()
        return buildLanNetworkFingerprint(gateways, subnets, dnsServers, domains)
    }

    private fun readLabelMap(context: Context, key: String): Map<String, String> =
        prefs(context).getStringSet(key, emptySet()).orEmpty().mapNotNull { entry ->
            val idx = entry.indexOf(LABEL_SEP)
            if (idx <= 0 || idx >= entry.length - 1) null
            else entry.substring(0, idx) to entry.substring(idx + 1)
        }.toMap()

    private fun putLabelEntry(editor: android.content.SharedPreferences.Editor, current: Map<String, String>, key: String, id: String, label: String?) {
        val cleaned = label?.replace(LABEL_SEP, " ")?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val next = current.toMutableMap().apply { put(id, cleaned) }
        editor.putStringSet(key, next.entries.map { "${it.key}$LABEL_SEP${it.value}" }.toSet())
    }

    private fun removeLabelEntry(editor: android.content.SharedPreferences.Editor, current: Map<String, String>, key: String, id: String) {
        if (id !in current) return
        val next = current - id
        editor.putStringSet(key, next.entries.map { "${it.key}$LABEL_SEP${it.value}" }.toSet())
    }

    fun isRequireTrustedNetwork(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REQUIRE_TRUSTED, true)

    fun setRequireTrustedNetwork(context: Context, required: Boolean) {
        prefs(context).edit().putBoolean(KEY_REQUIRE_TRUSTED, required).apply()
    }

    fun trustedSsids(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_SSIDS, emptySet()).orEmpty().mapNotNull(::normalizeSsid).toSet()

    fun displayLabelForSsid(context: Context, normalizedOrRawSsid: String): String {
        val norm = normalizeSsid(normalizedOrRawSsid) ?: return normalizedOrRawSsid
        return readLabelMap(context, KEY_SSID_LABELS)[norm] ?: norm
    }

    fun trustedBssids(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_BSSIDS, emptySet()).orEmpty().mapNotNull(::normalizeBssid).toSet()

    fun trustedLanFingerprints(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_LAN_FINGERPRINTS, emptySet()).orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun displayLabelForFingerprint(context: Context, fingerprint: String): String {
        val id = fingerprint.trim()
        readLabelMap(context, KEY_LAN_LABELS)[id]?.let { return it }
        CustomSpeakerNames.getCustomName(context, id)?.let { return "Speaker: $it" }
        if (id.startsWith(LAN_NET_PREFIX)) {
            val fields = id.removePrefix(LAN_NET_PREFIX).split('|').associate { part ->
                val eq = part.indexOf('=')
                if (eq > 0) part.substring(0, eq) to part.substring(eq + 1) else part to ""
            }
            val gw = fields["gw"]?.split(',')?.firstOrNull { it.isNotBlank() }
            val sub = fields["sub"]?.split(',')?.firstOrNull { it.isNotBlank() }
            val dom = fields["dom"]?.takeIf { it.isNotBlank() }
            val parts = listOfNotNull(gw ?: sub, dom)
            return if (parts.isEmpty()) "Home LAN" else "Home LAN (${parts.joinToString(" · ")})"
        }
        val liveName = runCatching {
            LanDiscovery.devices.firstOrNull { it.info.deviceId == id }?.info?.name?.takeIf { it.isNotBlank() }
        }.getOrNull()
        return if (liveName != null) "Speaker: $liveName" else "Speaker ($id)"
    }

    fun addTrustedWifi(context: Context, ssid: String?, bssid: String?) {
        val p = prefs(context)
        val ssids = p.getStringSet(KEY_SSIDS, emptySet()).orEmpty().mapNotNull(::normalizeSsid).toMutableSet()
        val bssids = p.getStringSet(KEY_BSSIDS, emptySet()).orEmpty().mapNotNull(::normalizeBssid).toMutableSet()
        val ssidLabels = readLabelMap(context, KEY_SSID_LABELS)
        val normSsid = normalizeSsid(ssid)
        val displaySsid = cleanDisplaySsid(ssid)
        normSsid?.let(ssids::add)
        normalizeBssid(bssid)?.let(bssids::add)
        val editor = p.edit().putStringSet(KEY_SSIDS, ssids).putStringSet(KEY_BSSIDS, bssids)
        if (normSsid != null && displaySsid != null) {
            putLabelEntry(editor, ssidLabels, KEY_SSID_LABELS, normSsid, displaySsid)
        }
        editor.apply()
    }

    fun removeTrustedSsid(context: Context, ssid: String) {
        val target = normalizeSsid(ssid) ?: return
        val updated = trustedSsids(context) - target
        val ssidLabels = readLabelMap(context, KEY_SSID_LABELS)
        val editor = prefs(context).edit().putStringSet(KEY_SSIDS, updated)
        removeLabelEntry(editor, ssidLabels, KEY_SSID_LABELS, target)
        editor.apply()
    }

    fun removeTrustedBssid(context: Context, bssid: String) {
        val target = normalizeBssid(bssid) ?: return
        val updated = trustedBssids(context) - target
        prefs(context).edit().putStringSet(KEY_BSSIDS, updated).apply()
    }

    fun addTrustedLanFingerprint(context: Context, deviceOrNetworkId: String, label: String? = null) {
        val id = deviceOrNetworkId.trim().takeIf { it.isNotEmpty() } ?: return
        val updated = trustedLanFingerprints(context) + id
        val labels = readLabelMap(context, KEY_LAN_LABELS)
        val editor = prefs(context).edit().putStringSet(KEY_LAN_FINGERPRINTS, updated)
        putLabelEntry(editor, labels, KEY_LAN_LABELS, id, label)
        editor.apply()
    }

    fun removeTrustedLanFingerprint(context: Context, deviceOrNetworkId: String) {
        val id = deviceOrNetworkId.trim().takeIf { it.isNotEmpty() } ?: return
        val updated = trustedLanFingerprints(context) - id
        val labels = readLabelMap(context, KEY_LAN_LABELS)
        val editor = prefs(context).edit().putStringSet(KEY_LAN_FINGERPRINTS, updated)
        removeLabelEntry(editor, labels, KEY_LAN_LABELS, id)
        editor.apply()
    }

    /**
     * Captures the currently connected Wi-Fi LAN (via zero-permission `LinkProperties` gateway/subnet/DNS/domain,
     * any visible mDNS speaker IDs, and SSID/BSSID if unmasked by OS) as Trusted Home Network.
     */
    fun trustCurrentNetwork(context: Context): Boolean {
        val snap = currentSnapshot(context)
        if (!snap.onWifi) return false
        val hasWifiId = normalizeSsid(snap.ssid) != null || normalizeBssid(snap.bssid) != null
        val hasLanNet = !snap.lanNetworkFingerprint.isNullOrBlank()
        val hasLanSpeakers = snap.discoveredLanDeviceIds.isNotEmpty()
        if (!hasWifiId && !hasLanNet && !hasLanSpeakers) return false

        if (hasWifiId) {
            addTrustedWifi(context, snap.ssid, snap.bssid)
        }
        snap.lanNetworkFingerprint?.takeIf { it.isNotBlank() }?.let { netFp ->
            addTrustedLanFingerprint(context, netFp, snap.lanNetworkLabel)
        }
        snap.discoveredLanDeviceIds.forEach { speakerId ->
            val customOrLive = CustomSpeakerNames.getCustomName(context, speakerId)
                ?: runCatching { LanDiscovery.devices.firstOrNull { it.info.deviceId == speakerId }?.info?.name?.takeIf(String::isNotBlank) }.getOrNull()
            addTrustedLanFingerprint(context, speakerId, customOrLive?.let { "Speaker: $it" })
        }
        return true
    }

    fun speakerVisibilityMode(context: Context): SpeakerVisibilityMode =
        runCatching {
            SpeakerVisibilityMode.valueOf(
                prefs(context).getString(KEY_VISIBILITY_MODE, SpeakerVisibilityMode.ALLOW_ALL_EXCEPT_HIDDEN.name)!!,
            )
        }.getOrDefault(SpeakerVisibilityMode.ALLOW_ALL_EXCEPT_HIDDEN)

    fun setSpeakerVisibilityMode(context: Context, mode: SpeakerVisibilityMode) {
        prefs(context).edit().putString(KEY_VISIBILITY_MODE, mode.name).apply()
    }

    fun hiddenDeviceIds(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_HIDDEN_DEVICES, emptySet()).orEmpty().toSet()

    fun setDeviceHidden(context: Context, deviceId: String, hidden: Boolean) {
        val id = deviceId.trim().takeIf { it.isNotEmpty() } ?: return
        val updated = hiddenDeviceIds(context).let { if (hidden) it + id else it - id }
        prefs(context).edit().putStringSet(KEY_HIDDEN_DEVICES, updated).apply()
    }

    fun allowedDeviceIds(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_ALLOWED_DEVICES, emptySet()).orEmpty().toSet()

    fun setDeviceAllowedInStrictMode(context: Context, deviceId: String, allowed: Boolean) {
        val id = deviceId.trim().takeIf { it.isNotEmpty() } ?: return
        val updated = allowedDeviceIds(context).let { if (allowed) it + id else it - id }
        prefs(context).edit().putStringSet(KEY_ALLOWED_DEVICES, updated).apply()
    }

    fun isJamAllowed(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ALLOW_JAM, false)

    fun setJamAllowed(context: Context, allowed: Boolean) {
        prefs(context).edit().putBoolean(KEY_ALLOW_JAM, allowed).apply()
    }

    fun currentSnapshot(context: Context): NetworkSnapshot {
        snapshotOverride?.let { return it(context) }
        val app = context.applicationContext
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetworkSnapshot(onWifi = false)
        val active = cm.activeNetwork ?: return NetworkSnapshot(onWifi = false)
        val caps = cm.getNetworkCapabilities(active) ?: return NetworkSnapshot(onWifi = false)
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return NetworkSnapshot(onWifi = false)
        }
        val transportWifi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) caps.transportInfo as? WifiInfo else null
        @Suppress("DEPRECATION")
        val fallbackWifi = if (transportWifi == null) {
            runCatching { (app.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.connectionInfo }.getOrNull()
        } else null
        val wifi = transportWifi ?: fallbackWifi
        @Suppress("DEPRECATION")
        val ssid = runCatching { wifi?.ssid }.getOrNull()
        @Suppress("DEPRECATION")
        val bssid = runCatching { wifi?.bssid }.getOrNull()

        val linkFp = runCatching { fingerprintFromLinkProperties(cm.getLinkProperties(active)) }.getOrNull()
        val lanIds = runCatching {
            LanDiscovery.devices.toList().mapNotNull { it.info.deviceId.trim().takeIf(String::isNotEmpty) }.toSet()
        }.getOrDefault(emptySet())
        return NetworkSnapshot(
            onWifi = true,
            ssid = ssid,
            bssid = bssid,
            lanNetworkFingerprint = linkFp?.first,
            lanNetworkLabel = linkFp?.second,
            discoveredLanDeviceIds = lanIds,
        )
    }

    /**
     * True when connected to Wi-Fi and speaker fingerprints exist, so read-only mDNS `getInfo`
     * discovery may verify whether a parent-trusted home speaker is on this LAN.
     */
    fun shouldProbeLanForFingerprint(context: Context): Boolean {
        if (!isRequireTrustedNetwork(context)) return true
        val snap = currentSnapshot(context)
        return snap.onWifi && trustedLanFingerprints(context).isNotEmpty()
    }

    fun isNetworkTrusted(context: Context, snapshot: NetworkSnapshot = currentSnapshot(context)): Boolean {
        if (!isRequireTrustedNetwork(context)) return true
        if (!snapshot.onWifi) return false
        val ssids = trustedSsids(context)
        val bssids = trustedBssids(context)
        val lanPrints = trustedLanFingerprints(context)
        if (ssids.isEmpty() && bssids.isEmpty() && lanPrints.isEmpty()) return false
        val normSsid = normalizeSsid(snapshot.ssid)
        if (normSsid != null && normSsid in ssids) return true
        val normBssid = normalizeBssid(snapshot.bssid)
        if (normBssid != null && normBssid in bssids) return true
        val netPrint = snapshot.lanNetworkFingerprint?.trim()?.takeIf { it.isNotEmpty() }
        if (netPrint != null && netPrint in lanPrints) return true
        if (lanPrints.isNotEmpty() && snapshot.discoveredLanDeviceIds.any { it in lanPrints }) return true
        return false
    }

    /** `LanLogin` (`addUser` Zeroconf token provisioning) is strictly blocked unless the current network is trusted. */
    fun canProvisionLanDevices(context: Context): Boolean = isNetworkTrusted(context)

    fun isDeviceAllowed(context: Context, deviceId: String, isSelf: Boolean): Boolean {
        if (isSelf) return true
        if (!isNetworkTrusted(context)) return false
        val id = deviceId.trim()
        if (id in hiddenDeviceIds(context)) return false
        return when (speakerVisibilityMode(context)) {
            SpeakerVisibilityMode.ALLOW_ALL_EXCEPT_HIDDEN -> true
            SpeakerVisibilityMode.ONLY_ALLOWED -> id in allowedDeviceIds(context)
        }
    }

    fun filterConnectDevices(context: Context, devices: List<SongPlayer.ConnectDevice>): List<SongPlayer.ConnectDevice> {
        val snap = currentSnapshot(context)
        val trusted = isNetworkTrusted(context, snap)
        val hidden = hiddenDeviceIds(context)
        val mode = speakerVisibilityMode(context)
        val allowed = allowedDeviceIds(context)
        return devices.filter { d ->
            if (d.isSelf) true
            else if (!trusted) false
            else if (d.id in hidden) false
            else when (mode) {
                SpeakerVisibilityMode.ALLOW_ALL_EXCEPT_HIDDEN -> true
                SpeakerVisibilityMode.ONLY_ALLOWED -> d.id in allowed
            }
        }
    }

    fun replaceConfiguration(
        context: Context,
        requireTrustedNetwork: Boolean,
        ssids: Set<String>,
        bssids: Set<String>,
        lanFingerprints: Set<String>,
        visibilityMode: SpeakerVisibilityMode,
        hiddenDevices: Set<String>,
        allowedDevices: Set<String>,
        allowJam: Boolean,
    ) {
        val ssidLabels = ssids.mapNotNull { raw ->
            val norm = normalizeSsid(raw) ?: return@mapNotNull null
            val display = cleanDisplaySsid(raw) ?: return@mapNotNull null
            "${norm}$LABEL_SEP$display"
        }.toSet()
        prefs(context).edit()
            .putBoolean(KEY_REQUIRE_TRUSTED, requireTrustedNetwork)
            .putStringSet(KEY_SSIDS, ssids.mapNotNull(::normalizeSsid).toSet())
            .putStringSet(KEY_SSID_LABELS, ssidLabels)
            .putStringSet(KEY_BSSIDS, bssids.mapNotNull(::normalizeBssid).toSet())
            .putStringSet(KEY_LAN_FINGERPRINTS, lanFingerprints.map { it.trim() }.filter { it.isNotEmpty() }.toSet())
            .putString(KEY_VISIBILITY_MODE, visibilityMode.name)
            .putStringSet(KEY_HIDDEN_DEVICES, hiddenDevices.map { it.trim() }.filter { it.isNotEmpty() }.toSet())
            .putStringSet(KEY_ALLOWED_DEVICES, allowedDevices.map { it.trim() }.filter { it.isNotEmpty() }.toSet())
            .putBoolean(KEY_ALLOW_JAM, allowJam)
            .apply()
    }

    internal fun resetForTests(context: Context) {
        snapshotOverride = null
        prefs(context).edit().clear().apply()
    }
}
