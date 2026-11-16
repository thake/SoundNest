package io.github.thake.soundnest.data.api

import io.github.thake.soundnest.data.entity.Lyrics

/**
 * Romanizations for the lines of lyrics their source left in another script than Latin without one.
 * What the source gave stays: only the lines left empty are filled.
 */
object LyricsRomanize {
    private val NEUTRAL = setOf(Character.UnicodeScript.LATIN, Character.UnicodeScript.COMMON, Character.UnicodeScript.INHERITED)

    /** Whether [text] has a letter of another script than Latin: what a romanization is for. */
    private fun needs(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (Character.isLetter(cp) && Character.UnicodeScript.of(cp) !in NEUTRAL) return true
            i += Character.charCount(cp)
        }
        return false
    }

    /** The lines of [lyrics] a romanization is asked for: index → text. */
    fun wanted(lyrics: Lyrics): Map<Int, String> = buildMap {
        lyrics.lines.forEachIndexed { i, line ->
            if (line.romanization.isNullOrBlank() && needs(line.text)) put(i, line.text)
        }
    }

    /**
     * [lyrics] with [found] (index → romanization) on its lines that have none and need one; a
     * romanization that only repeats its line is left out, as a source's is. The same instance when
     * nothing is added.
     */
    fun apply(lyrics: Lyrics, found: Map<Int, String>): Lyrics {
        if (found.isEmpty()) return lyrics
        var added = false
        val lines = lyrics.lines.mapIndexed { i, line ->
            val ro = found[i]?.takeIf {
                line.romanization.isNullOrBlank() && needs(line.text) && LyricsMatch.key(it) != LyricsMatch.key(line.text)
            }
            if (ro == null) line else line.copy(romanization = ro).also { added = true }
        }
        return if (added) lyrics.copy(lines = lines) else lyrics
    }
}
