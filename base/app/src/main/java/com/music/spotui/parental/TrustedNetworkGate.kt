/* SPDX-License-Identifier: GPL-3.0-only */
package com.music.spotui.parental

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.music.spotui.connect.LanDiscovery
import com.music.spotui.di.SongPlayer

data class NetworkSnapshot(
    val onWifi: Boolean,
    val ssid: String? = null,
    val bssid: String? = null,
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
 * Defaults fail closed for children's safety:
 * - `requireTrustedNetwork = true`: cloud Connect & `LanLogin` require matching Home Wi-Fi SSID,
 *   BSSID, or a parent-trusted LAN speaker fingerprint (when Android masks SSID without location permission).
 * - `allowJam = false`: hides `JamSection` and blocks Jam session joins unless enabled by a parent.
 */
object TrustedNetworkGate {
    private const val PREFS = "parental_trusted_network"
    private const val KEY_REQUIRE_TRUSTED = "require_trusted_network"
    private const val KEY_SSIDS = "trusted_ssids"
    private const val KEY_BSSIDS = "trusted_bssids"
    private const val KEY_LAN_FINGERPRINTS = "trusted_lan_fingerprints"
    private const val KEY_VISIBILITY_MODE = "speaker_visibility_mode"
    private const val KEY_HIDDEN_DEVICES = "hidden_device_ids"
    private const val KEY_ALLOWED_DEVICES = "allowed_device_ids"
    private const val KEY_ALLOW_JAM = "allow_jam"

    private val PLACEHOLDER_BSSIDS = setOf("02:00:00:00:00:00", "00:00:00:00:00:00")

    @Volatile
    internal var snapshotOverride: ((Context) -> NetworkSnapshot)? = null

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun normalizeSsid(raw: String?): String? {
        val cleaned = raw?.trim()?.removeSurrounding("\"")?.trim() ?: return null
        if (cleaned.isEmpty() || cleaned.equals("<unknown ssid>", ignoreCase = true)) return null
        return cleaned.lowercase()
    }

    fun normalizeBssid(raw: String?): String? {
        val cleaned = raw?.trim()?.lowercase() ?: return null
        if (cleaned.isEmpty() || cleaned in PLACEHOLDER_BSSIDS) return null
        return cleaned
    }

    fun isRequireTrustedNetwork(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REQUIRE_TRUSTED, true)

    fun setRequireTrustedNetwork(context: Context, required: Boolean) {
        prefs(context).edit().putBoolean(KEY_REQUIRE_TRUSTED, required).apply()
    }

    fun trustedSsids(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_SSIDS, emptySet()).orEmpty().mapNotNull(::normalizeSsid).toSet()

    fun trustedBssids(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_BSSIDS, emptySet()).orEmpty().mapNotNull(::normalizeBssid).toSet()

    fun trustedLanFingerprints(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_LAN_FINGERPRINTS, emptySet()).orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun addTrustedWifi(context: Context, ssid: String?, bssid: String?) {
        val p = prefs(context)
        val ssids = p.getStringSet(KEY_SSIDS, emptySet()).orEmpty().toMutableSet()
        val bssids = p.getStringSet(KEY_BSSIDS, emptySet()).orEmpty().toMutableSet()
        normalizeSsid(ssid)?.let(ssids::add)
        normalizeBssid(bssid)?.let(bssids::add)
        p.edit().putStringSet(KEY_SSIDS, ssids).putStringSet(KEY_BSSIDS, bssids).apply()
    }

    fun removeTrustedSsid(context: Context, ssid: String) {
        val target = normalizeSsid(ssid) ?: return
        val updated = trustedSsids(context) - target
        prefs(context).edit().putStringSet(KEY_SSIDS, updated).apply()
    }

    fun removeTrustedBssid(context: Context, bssid: String) {
        val target = normalizeBssid(bssid) ?: return
        val updated = trustedBssids(context) - target
        prefs(context).edit().putStringSet(KEY_BSSIDS, updated).apply()
    }

    fun addTrustedLanFingerprint(context: Context, deviceId: String) {
        val id = deviceId.trim().takeIf { it.isNotEmpty() } ?: return
        val updated = trustedLanFingerprints(context) + id
        prefs(context).edit().putStringSet(KEY_LAN_FINGERPRINTS, updated).apply()
    }

    fun removeTrustedLanFingerprint(context: Context, deviceId: String) {
        val id = deviceId.trim().takeIf { it.isNotEmpty() } ?: return
        val updated = trustedLanFingerprints(context) - id
        prefs(context).edit().putStringSet(KEY_LAN_FINGERPRINTS, updated).apply()
    }

    /** Captures the currently connected Wi-Fi network (and any visible LAN speaker IDs) as Trusted Home Wi-Fi. */
    fun trustCurrentNetwork(context: Context): Boolean {
        val snap = currentSnapshot(context)
        if (!snap.onWifi) return false
        val hasWifiId = normalizeSsid(snap.ssid) != null || normalizeBssid(snap.bssid) != null
        val hasLanIds = snap.discoveredLanDeviceIds.isNotEmpty()
        if (!hasWifiId && !hasLanIds) return false
        addTrustedWifi(context, snap.ssid, snap.bssid)
        snap.discoveredLanDeviceIds.forEach { addTrustedLanFingerprint(context, it) }
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
        val lanIds = runCatching {
            LanDiscovery.devices.toList().mapNotNull { it.info.deviceId.trim().takeIf(String::isNotEmpty) }.toSet()
        }.getOrDefault(emptySet())
        return NetworkSnapshot(onWifi = true, ssid = ssid, bssid = bssid, discoveredLanDeviceIds = lanIds)
    }

    /**
     * True when connected to Wi-Fi and trusted LAN fingerprints exist, so read-only mDNS `getInfo`
     * discovery may verify whether a parent-trusted home speaker is on this LAN when Android masks SSID.
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

    internal fun resetForTests(context: Context) {
        snapshotOverride = null
        prefs(context).edit().clear().apply()
    }
}
