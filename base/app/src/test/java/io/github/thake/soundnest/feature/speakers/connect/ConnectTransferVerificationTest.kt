package io.github.thake.soundnest.feature.speakers.connect

import io.github.thake.soundnest.api.Device
import io.github.thake.soundnest.core.playback.SongPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectTransferVerificationTest {
    @Test
    fun connectDeviceHoldsStructuredAliasId() {
        val dev = SongPlayer.ConnectDevice(
            id = "amzn-echo-1",
            name = "Kitchen Echo",
            type = "SPEAKER",
            isSelf = false,
            isActive = false,
            rawName = "Kitchen Echo",
            aliasId = "amzn1.alexa.kitchen",
        )
        assertEquals("amzn-echo-1", dev.id)
        assertEquals("amzn1.alexa.kitchen", dev.aliasId)
    }

    @Test
    fun selfDevicesDeduplicateToSinglePhoneRow() {
        val list = listOf(
            SongPlayer.ConnectDevice("web-player", "Web Player (Chrome)", "COMPUTER", isSelf = true, isActive = false),
            SongPlayer.ConnectDevice("native-phone", "Phone", "SMARTPHONE", isSelf = true, isActive = true),
            SongPlayer.ConnectDevice("amzn-echo-1", "Kitchen Echo", "SPEAKER", isSelf = false, isActive = false, aliasId = "amzn1"),
            SongPlayer.ConnectDevice("amzn-echo-1", "Everywhere", "SPEAKER", isSelf = false, isActive = false, aliasId = "amzn2"),
        )

        val deduplicated = list.sortedWith(compareByDescending<SongPlayer.ConnectDevice> { it.isSelf }.thenBy { it.name })
            .distinctBy { if (it.isSelf) "self" else "${it.id}:${it.aliasId.orEmpty()}" }

        assertEquals(3, deduplicated.size)
        assertEquals(1, deduplicated.count { it.isSelf })
        assertEquals(2, deduplicated.count { !it.isSelf })
    }

    @Test
    fun apiDevicePreservesAliasId() {
        val device = Device(
            id = "echo-1",
            name = "Living Room Echo",
            type = "SPEAKER",
            active = false,
            volume = 0.5,
            local = false,
            aliasId = "alias-lr",
        )
        assertEquals("echo-1", device.id)
        assertEquals("alias-lr", device.aliasId)
        assertFalse(device.local)
    }

    @Test
    fun echoDeviceMatchesAcrossAmznAndHarmonyCompositeIds() {
        val picked = SongPlayer.ConnectDevice(
            id = "28fa3b85-4e54-4429-9383-0bad54fa70e8_amzn_1",
            name = "Thorstens Echo",
            type = "SPEAKER",
            isSelf = false,
            isActive = false,
        )
        val dealerDevice = Device(
            id = "28fa3b85-4e54-4429-9383-0bad54fa70e8::a_1",
            name = "Thorstens Echo",
            type = "speaker",
            active = true,
            volume = 0.2,
            local = false,
            aliasId = "1",
        )
        val otherAlias = Device(
            id = "28fa3b85-4e54-4429-9383-0bad54fa70e8::a_2",
            name = "Everywhere",
            type = "speaker",
            active = true,
            volume = 0.2,
            local = false,
            aliasId = "2",
        )

        assertTrue(ConnectTransferMatcher.matchesDevice(picked, dealerDevice))
        assertFalse(ConnectTransferMatcher.matchesDevice(picked, otherAlias))
        assertTrue(ConnectTransferMatcher.matchesChosenRemote("28fa3b85-4e54-4429-9383-0bad54fa70e8::a_1", picked.id))
        assertEquals(ConnectTransferMatcher.dedupeKey(picked), ConnectTransferMatcher.dedupeKey(
            picked.copy(id = "28fa3b85-4e54-4429-9383-0bad54fa70e8::a_1", aliasId = "1")
        ))
    }
}
