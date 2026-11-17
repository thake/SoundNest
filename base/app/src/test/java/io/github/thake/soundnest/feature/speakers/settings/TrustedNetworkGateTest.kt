/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.speakers.settings

import io.github.thake.soundnest.feature.parental.settings.*

import android.content.Context
import io.github.thake.soundnest.core.playback.SongPlayer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrustedNetworkGateTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    private val selfPhone = SongPlayer.ConnectDevice(
        id = "phone-self",
        name = "This Phone",
        type = "SMARTPHONE",
        isSelf = true,
        isActive = false,
    )
    private val kidsEcho = SongPlayer.ConnectDevice(
        id = "echo-kids",
        name = "Kids Room Echo",
        type = "SPEAKER",
        isSelf = false,
        isActive = false,
    )
    private val parentsEcho = SongPlayer.ConnectDevice(
        id = "echo-parents",
        name = "Master Bedroom Echo",
        type = "SPEAKER",
        isSelf = false,
        isActive = false,
    )
    private val kitchenSonos = SongPlayer.ConnectDevice(
        id = "sonos-kitchen",
        name = "Kitchen Sonos",
        type = "SPEAKER",
        isSelf = false,
        isActive = false,
    )

    @Before
    fun setUp() {
        TrustedNetworkGate.resetForTests(context)
    }

    @After
    fun tearDown() {
        TrustedNetworkGate.resetForTests(context)
    }

    @Test
    fun `fails closed on unconfigured or untrusted Wi-Fi and cellular hiding cloud Alexa speakers and blocking LanLogin`() {
        val allDevices = listOf(selfPhone, kidsEcho, parentsEcho)

        // 1. Default policy: requireTrustedNetwork = true, no home Wi-Fi configured yet -> fail closed even on Wi-Fi
        TrustedNetworkGate.snapshotOverride = {
            NetworkSnapshot(onWifi = true, ssid = "\"SomePublicCafe\"", bssid = "11:22:33:44:55:66")
        }
        assertFalse(TrustedNetworkGate.isNetworkTrusted(context))
        assertFalse(TrustedNetworkGate.canProvisionLanDevices(context))
        assertEquals(listOf(selfPhone), TrustedNetworkGate.filterConnectDevices(context, allDevices))

        // 2. Configure Home Wi-Fi, then test on cellular (onWifi = false) -> blocked
        TrustedNetworkGate.addTrustedWifi(context, ssid = "\"HomeSweetHome\"", bssid = "AA:BB:CC:DD:EE:FF")
        TrustedNetworkGate.snapshotOverride = {
            NetworkSnapshot(onWifi = false, ssid = null, bssid = null)
        }
        assertFalse(TrustedNetworkGate.isNetworkTrusted(context))
        assertFalse(TrustedNetworkGate.canProvisionLanDevices(context))
        assertEquals(listOf(selfPhone), TrustedNetworkGate.filterConnectDevices(context, allDevices))
        assertFalse(TrustedNetworkGate.isDeviceAllowed(context, kidsEcho.id, isSelf = false))
        assertTrue(TrustedNetworkGate.isDeviceAllowed(context, selfPhone.id, isSelf = true))

        // 3. Different Wi-Fi network (friend's house / hotel) -> cloud Connect AND LanLogin blocked
        TrustedNetworkGate.snapshotOverride = {
            NetworkSnapshot(onWifi = true, ssid = "HotelGuestWiFi", bssid = "99:88:77:66:55:44")
        }
        assertFalse(TrustedNetworkGate.isNetworkTrusted(context))
        assertFalse(TrustedNetworkGate.canProvisionLanDevices(context))
        assertEquals(listOf(selfPhone), TrustedNetworkGate.filterConnectDevices(context, allDevices))
    }

    @Test
    fun `matches Home Wi-Fi by normalized SSID or BSSID or trusted LAN speaker fingerprint when SSID is masked`() {
        val allDevices = listOf(selfPhone, kidsEcho, kitchenSonos)
        TrustedNetworkGate.addTrustedWifi(context, ssid = "FamilyHome_5G", bssid = null)
        TrustedNetworkGate.addTrustedLanFingerprint(context, "sonos-kitchen")

        // Case A: Android returns quoted SSID with matching name (case-insensitive)
        TrustedNetworkGate.snapshotOverride = {
            NetworkSnapshot(onWifi = true, ssid = "\"familyhome_5g\"", bssid = "02:00:00:00:00:00")
        }
        assertTrue(TrustedNetworkGate.isNetworkTrusted(context))
        assertTrue(TrustedNetworkGate.canProvisionLanDevices(context))
        assertEquals(allDevices, TrustedNetworkGate.filterConnectDevices(context, allDevices))

        // Case B: Android masks SSID as <unknown ssid> (no location permission), but trusted LAN speaker is discovered on Wi-Fi
        TrustedNetworkGate.snapshotOverride = {
            NetworkSnapshot(
                onWifi = true,
                ssid = "<unknown ssid>",
                bssid = "02:00:00:00:00:00",
                discoveredLanDeviceIds = setOf("sonos-kitchen"),
            )
        }
        assertTrue(TrustedNetworkGate.shouldProbeLanForFingerprint(context))
        assertTrue(TrustedNetworkGate.isNetworkTrusted(context))
        assertTrue(TrustedNetworkGate.canProvisionLanDevices(context))
        assertEquals(allDevices, TrustedNetworkGate.filterConnectDevices(context, allDevices))

        // Case C: SSID masked AND no trusted LAN speaker present -> stays untrusted and blocks LanLogin provisioning
        TrustedNetworkGate.snapshotOverride = {
            NetworkSnapshot(
                onWifi = true,
                ssid = "<unknown ssid>",
                bssid = "02:00:00:00:00:00",
                discoveredLanDeviceIds = setOf("stranger-hotel-speaker"),
            )
        }
        assertFalse(TrustedNetworkGate.isNetworkTrusted(context))
        assertFalse(TrustedNetworkGate.canProvisionLanDevices(context))
        assertEquals(listOf(selfPhone), TrustedNetworkGate.filterConnectDevices(context, allDevices))
    }

    @Test
    fun `enforces per-speaker hidden list strict speaker allowlist and allowJam default false`() {
        val allDevices = listOf(selfPhone, kidsEcho, parentsEcho, kitchenSonos)
        TrustedNetworkGate.addTrustedWifi(context, ssid = "HomeNet", bssid = "aa:bb:cc:11:22:33")
        TrustedNetworkGate.snapshotOverride = {
            NetworkSnapshot(onWifi = true, ssid = "HomeNet", bssid = "aa:bb:cc:11:22:33")
        }

        // Default Jam policy for kids is disabled (false)
        assertFalse(TrustedNetworkGate.isJamAllowed(context))
        TrustedNetworkGate.setJamAllowed(context, true)
        assertTrue(TrustedNetworkGate.isJamAllowed(context))

        // Hide parents' bedroom Echo while keeping other home speakers visible
        TrustedNetworkGate.setDeviceHidden(context, parentsEcho.id, hidden = true)
        assertEquals(
            listOf(selfPhone, kidsEcho, kitchenSonos),
            TrustedNetworkGate.filterConnectDevices(context, allDevices),
        )
        assertFalse(TrustedNetworkGate.isDeviceAllowed(context, parentsEcho.id, isSelf = false))

        // Switch to strict speaker allowlist mode: only kidsEcho (and selfPhone) allowed
        TrustedNetworkGate.setSpeakerVisibilityMode(context, SpeakerVisibilityMode.ONLY_ALLOWED)
        TrustedNetworkGate.setDeviceAllowedInStrictMode(context, kidsEcho.id, allowed = true)
        assertEquals(
            listOf(selfPhone, kidsEcho),
            TrustedNetworkGate.filterConnectDevices(context, allDevices),
        )
        assertTrue(TrustedNetworkGate.isDeviceAllowed(context, kidsEcho.id, isSelf = false))
        assertFalse(TrustedNetworkGate.isDeviceAllowed(context, kitchenSonos.id, isSelf = false))
        assertTrue(TrustedNetworkGate.isDeviceAllowed(context, selfPhone.id, isSelf = true))
    }

    @Test
    fun `LanLogin start refuses Zeroconf token provisioning when network is untrusted`() {
        io.github.thake.soundnest.core.di.Graph::class.java.getDeclaredField("app").apply { isAccessible = true }.set(null, context)
        TrustedNetworkGate.addTrustedWifi(context, ssid = "HomeWifi", bssid = null)
        TrustedNetworkGate.snapshotOverride = {
            NetworkSnapshot(onWifi = true, ssid = "CafePublicWifi", bssid = "de:ad:be:ef:00:01")
        }

        val untrustedSpeaker = io.github.thake.soundnest.feature.speakers.connect.LanDevice(
            service = "CafeSonos",
            host = java.net.InetAddress.getByName("192.168.1.50"),
            port = 1400,
            cpath = "/spotifyzc",
            info = io.github.thake.soundnest.feature.speakers.connect.ZeroconfInfo(
                deviceId = "cafe-sonos-1",
                name = "Cafe Sonos",
                clientId = "client123",
                tokenType = "authorization_code",
                version = "2.10.0",
                activeUser = "",
                brand = "Sonos",
                model = "One",
                publicKey = "PUBKEY123",
            ),
        )
        io.github.thake.soundnest.feature.speakers.connect.LanLogin.start(untrustedSpeaker)
        assertEquals(null, io.github.thake.soundnest.feature.speakers.connect.LanLogin.busy.value)
    }

    @Test
    fun `zero-permission LinkProperties LAN fingerprint trusts cloud Alexa networks on Android 10+ when SSID is masked`() {
        val allDevices = listOf(selfPhone, kidsEcho, parentsEcho)

        // Simulate router LAN 192.168.178.1/24 (fritz.box) with phone IP .42, NO location permission (<unknown ssid>), and NO mDNS speakers (cloud Echo only)
        val phoneAddr1 = java.net.InetAddress.getByName("192.168.178.42") as java.net.Inet4Address
        val prefix1 = TrustedNetworkGate.ipv4NetworkPrefix(phoneAddr1, 24)
        assertEquals("192.168.178.0/24", prefix1)

        val homeLan1 = TrustedNetworkGate.buildLanNetworkFingerprint(
            gateways = listOf("192.168.178.1"),
            subnets = listOf(prefix1),
            dnsServers = listOf("192.168.178.1"),
            domains = "fritz.box",
        )!!
        assertEquals("Home LAN (192.168.178.1 · fritz.box)", homeLan1.second)

        TrustedNetworkGate.snapshotOverride = {
            NetworkSnapshot(
                onWifi = true,
                ssid = "<unknown ssid>",
                bssid = "02:00:00:00:00:00",
                lanNetworkFingerprint = homeLan1.first,
                lanNetworkLabel = homeLan1.second,
                discoveredLanDeviceIds = emptySet(),
            )
        }

        // Before trusting: fails closed
        assertFalse(TrustedNetworkGate.isNetworkTrusted(context))
        assertEquals(listOf(selfPhone), TrustedNetworkGate.filterConnectDevices(context, allDevices))

        // Parent taps "Trust Current Home Wi-Fi & Visible Speakers": succeeds with zero location permission!
        assertTrue(TrustedNetworkGate.trustCurrentNetwork(context))
        assertTrue(TrustedNetworkGate.isNetworkTrusted(context))
        assertEquals(allDevices, TrustedNetworkGate.filterConnectDevices(context, allDevices))
        assertEquals(
            "Home LAN (192.168.178.1 · fritz.box)",
            TrustedNetworkGate.displayLabelForFingerprint(context, homeLan1.first),
        )

        // Phone renews DHCP lease to .99 on the same router -> subnet prefix stays 192.168.178.0/24 -> still trusted!
        val phoneAddr2 = java.net.InetAddress.getByName("192.168.178.99") as java.net.Inet4Address
        val homeLanRenewed = TrustedNetworkGate.buildLanNetworkFingerprint(
            gateways = listOf("192.168.178.1"),
            subnets = listOf(TrustedNetworkGate.ipv4NetworkPrefix(phoneAddr2, 24)),
            dnsServers = listOf("192.168.178.1"),
            domains = "fritz.box",
        )!!
        TrustedNetworkGate.snapshotOverride = {
            NetworkSnapshot(
                onWifi = true,
                ssid = "<unknown ssid>",
                bssid = "02:00:00:00:00:00",
                lanNetworkFingerprint = homeLanRenewed.first,
                lanNetworkLabel = homeLanRenewed.second,
            )
        }
        assertTrue(TrustedNetworkGate.isNetworkTrusted(context))

        // Child visits another Wi-Fi network (192.168.1.1 / home.arpa) -> untrusted, hides remote Echo speakers!
        val friendLan = TrustedNetworkGate.buildLanNetworkFingerprint(
            gateways = listOf("192.168.1.1"),
            subnets = listOf("192.168.1.0/24"),
            dnsServers = listOf("192.168.1.1"),
            domains = "home.arpa",
        )!!
        TrustedNetworkGate.snapshotOverride = {
            NetworkSnapshot(
                onWifi = true,
                ssid = "<unknown ssid>",
                bssid = "02:00:00:00:00:00",
                lanNetworkFingerprint = friendLan.first,
                lanNetworkLabel = friendLan.second,
            )
        }
        assertFalse(TrustedNetworkGate.isNetworkTrusted(context))
        assertEquals(listOf(selfPhone), TrustedNetworkGate.filterConnectDevices(context, allDevices))
    }

    @Test
    fun `preserves original SSID casing in displayLabelForSsid while matching case-insensitively`() {
        TrustedNetworkGate.addTrustedWifi(context, ssid = "\"FamilyHome_5G\"", bssid = null)
        assertEquals(setOf("familyhome_5g"), TrustedNetworkGate.trustedSsids(context))
        assertEquals("FamilyHome_5G", TrustedNetworkGate.displayLabelForSsid(context, "familyhome_5g"))
    }
}
