/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.lyrics.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.DeflaterOutputStream

class LyricsParsersTest {

    @Test
    fun `parseLrc handles multi-tag lines, fractional precision, and collapses instrumental runs`() {
        val lrc = """
            [00:10.5]Line A
            [00:05.00][00:20.250]Chorus
            [00:12.00]
            [00:14.00]
            [00:15.12]Line B
        """.trimIndent()

        val lines = LyricsParsers.parseLrc(lrc)
        assertEquals(5, lines.size)
        assertEquals(5_000L to "Chorus", lines[0].timeMs to lines[0].text)
        assertEquals(10_500L to "Line A", lines[1].timeMs to lines[1].text)
        assertEquals(12_000L to "♪", lines[2].timeMs to lines[2].text)
        assertEquals(15_120L to "Line B", lines[3].timeMs to lines[3].text)
        assertEquals(20_250L to "Chorus", lines[4].timeMs to lines[4].text)
    }

    @Test
    fun `decodeKrc and parseKrc round-trip XOR compressed Kugou payload and skip credit lines`() {
        val plainKrc = """
            [0,1000]<0,500,0>作词：<500,500,0>Author
            [1000,2000]<0,1000,0>Hello <1000,1000,0>world
            [3000,2000]<0,1000,0>Second <1000,1000,0>line
            [5000,2000]<0,1000,0>Third <1000,1000,0>line
        """.trimIndent()

        val compressed = ByteArrayOutputStream().use { out ->
            DeflaterOutputStream(out).use { it.write(plainKrc.toByteArray(Charsets.UTF_8)) }
            out.toByteArray()
        }
        val key = intArrayOf(64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206, 210, 110, 105)
        val payload = ByteArray(4 + compressed.size)
        for (i in compressed.indices) {
            payload[i + 4] = (compressed[i].toInt() xor key[i % key.size]).toByte()
        }
        val b64 = Base64.getEncoder().encodeToString(payload)

        val decoded = LyricsParsers.decodeKrc(b64)
        assertEquals(plainKrc, decoded)

        val lyrics = LyricsParsers.parseKrc(decoded!!)
        assertNotNull(lyrics)
        assertTrue(lyrics!!.synced)
        assertEquals("Kugou", lyrics.provider)
        assertEquals(3, lyrics.lines.size)
        assertEquals("Hello world", lyrics.lines[0].text)
        assertEquals(1000L, lyrics.lines[0].timeMs)
        assertEquals(2, lyrics.lines[0].words?.size)
        assertEquals(2000L, lyrics.lines[0].words?.get(1)?.startMs)
    }

    @Test
    fun `parseKrc returns null when fewer than three lyric lines exist`() {
        val krc = "[1000,1000]<0,500,0>Only one\n[2000,1000]<0,500,0>Only two"
        assertNull(LyricsParsers.parseKrc(krc))
    }

    @Test
    fun `clockMs and voiceOf parse standard formats`() {
        assertEquals(11_859L, LyricsParsers.clockMs("11.859s"))
        assertEquals(62_345L, LyricsParsers.clockMs("1:02.345"))
        assertEquals(3_662_345L, LyricsParsers.clockMs("01:01:02.345"))
        assertEquals(-1L, LyricsParsers.clockMs("invalid"))
        assertEquals(1, LyricsParsers.voiceOf("v1"))
        assertEquals(0, LyricsParsers.voiceOf("v1000"))
        assertNull(LyricsParsers.voiceOf("unknown"))
    }
}
