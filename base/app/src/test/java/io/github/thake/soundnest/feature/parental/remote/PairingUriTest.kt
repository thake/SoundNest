/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.remote

import io.github.thake.soundnest.feature.parental.remote.net.PairingInvite
import io.github.thake.soundnest.feature.parental.remote.net.PairingUri
import io.github.thake.soundnest.feature.parental.remote.net.ParentTransferInvite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PairingUriTest {

    @Test
    fun encodeAndParseRoundTripOnPrivateLan() {
        val invite = PairingInvite(
            childId = "child-uuid-1234",
            host = "192.168.1.42",
            port = 51234,
            code = "048917",
            deviceName = "Emma's Tablet",
        )
        val encoded = PairingUri.encode(invite)
        val parsed = PairingUri.parse(encoded)

        assertNotNull(parsed)
        assertEquals(invite, parsed)
    }

    @Test
    fun rejectsPublicIpInvalidCodeAndBadPort() {
        // Public IP (not RFC1918 LAN) must be rejected
        val publicIpUri = "soundnest-pair://v1?id=c1&host=8.8.8.8&port=12345&code=123456&name=Kid"
        assertNull(PairingUri.parse(publicIpUri))

        // Loopback rejected unless explicitly allowed for tests
        val loopbackUri = "soundnest-pair://v1?id=c1&host=127.0.0.1&port=12345&code=123456&name=Kid"
        assertNull(PairingUri.parse(loopbackUri, allowLoopback = false))
        assertNotNull(PairingUri.parse(loopbackUri, allowLoopback = true))

        // Invalid 5-digit code
        val shortCode = "soundnest-pair://v1?id=c1&host=192.168.1.10&port=12345&code=12345&name=Kid"
        assertNull(PairingUri.parse(shortCode))

        // Invalid port
        val badPort = "soundnest-pair://v1?id=c1&host=192.168.1.10&port=70000&code=123456&name=Kid"
        assertNull(PairingUri.parse(badPort))

        // Wrong scheme
        assertNull(PairingUri.parse("https://example.com/pair"))
    }

    @Test
    fun parentTransferUriEncodeAndParseRoundTrip() {
        val invite = ParentTransferInvite(
            sourceParentId = "parent-papa-1",
            host = "192.168.178.128",
            port = 44556,
            code = "904215",
            deviceName = "Papa's Phone",
        )
        val encoded = PairingUri.encodeParentTransfer(invite)
        assertNull(PairingUri.parse(encoded))
        val parsed = PairingUri.parseParentTransfer(encoded)
        assertNotNull(parsed)
        assertEquals(invite, parsed)

        // Rejects public IP & bad code
        assertNull(PairingUri.parseParentTransfer("soundnest-parent-transfer://v1?id=p1&host=1.1.1.1&port=1234&code=123456&name=Papa"))
        assertNull(PairingUri.parseParentTransfer("soundnest-parent-transfer://v1?id=p1&host=192.168.1.2&port=1234&code=123&name=Papa"))
        assertNotNull(PairingUri.parseParentTransfer("soundnest-parent-transfer://v1?id=p1&host=127.0.0.1&port=1234&code=123456&name=Papa", allowLoopback = true))
    }
}
