/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuleEntityKindTest {

    @Test
    fun resolvesAllWireKeysIncludingAliasesAndAudible() {
        assertEquals(RuleEntityKind.TRACK, RuleEntityKind.fromWireKey("track"))
        assertEquals(RuleEntityKind.TRACK, RuleEntityKind.fromWireKey("SONG"))
        assertEquals(RuleEntityKind.ALBUM, RuleEntityKind.fromWireKey("album"))
        assertEquals(RuleEntityKind.ARTIST, RuleEntityKind.fromWireKey("artist"))
        assertEquals(RuleEntityKind.PLAYLIST, RuleEntityKind.fromWireKey("playlist"))
        assertEquals(RuleEntityKind.SHOW, RuleEntityKind.fromWireKey("show"))
        assertEquals(RuleEntityKind.AUDIBLE, RuleEntityKind.fromWireKey("audible"))
        assertNull(RuleEntityKind.fromWireKey("unknown"))
        assertNull(RuleEntityKind.fromWireKey("  "))
    }

    @Test
    fun parsesSpotifyUrisAndUrlsIntoKindAndId() {
        assertEquals(
            RuleEntityKind.TRACK to "4cOdK2wGLETKBW3PvgPWqT",
            RuleEntityKind.parseSpotifyUri("spotify:track:4cOdK2wGLETKBW3PvgPWqT"),
        )
        assertEquals(
            RuleEntityKind.PLAYLIST to "37i9dQZF1DXcBWIGoYBM5M",
            RuleEntityKind.parseSpotifyUri("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=123"),
        )
        assertEquals(
            RuleEntityKind.ALBUM to "1ATL5GLyefJaxhQzSPVrLX",
            RuleEntityKind.parseSpotifyUri("https://open.spotify.com/album/1ATL5GLyefJaxhQzSPVrLX/"),
        )
        assertEquals(
            RuleEntityKind.ARTIST to "06HL4z0CvFAxyc27GXpf02",
            RuleEntityKind.parseSpotifyUri("spotify:artist:06HL4z0CvFAxyc27GXpf02"),
        )
        assertEquals(
            RuleEntityKind.SHOW to "4rOoJ6Egrf8K2IrywzwOMk",
            RuleEntityKind.parseSpotifyUri("https://open.spotify.com/show/4rOoJ6Egrf8K2IrywzwOMk"),
        )
        assertNull(RuleEntityKind.parseSpotifyUri("https://example.com/foo"))
    }
}
