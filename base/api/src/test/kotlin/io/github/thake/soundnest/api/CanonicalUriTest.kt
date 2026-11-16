package io.github.thake.soundnest.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CanonicalUriTest {
    @Test fun `an older playlist uri is the playlist's own`() {
        assertEquals("spotify:playlist:37i9dQZF1DX0", canonicalUri("spotify:user:spotify:playlist:37i9dQZF1DX0"))
        assertEquals(MediaKind.PLAYLIST, MediaKind.of(canonicalUri("spotify:user:someone:playlist:abc123")))
    }

    @Test fun `other uris stay as they are`() {
        for (uri in listOf("spotify:playlist:abc", "spotify:user:someone", "spotify:user:someone:collection", "spotify:user:me:folder:f1", "spotify:track:x")) {
            assertEquals(uri, canonicalUri(uri))
        }
    }

    @Test fun `audible book and chapter uris map to AUDIOBOOK and CHAPTER`() {
        assertEquals(MediaKind.AUDIOBOOK, MediaKind.of("audible:book:B08G9PRS1K"))
        assertEquals(MediaKind.CHAPTER, MediaKind.of("audible:chapter:B08G9PRS1K:0"))
        assertEquals("audible:book:B08G9PRS1K", canonicalUri("audible:book:B08G9PRS1K"))
    }
}
