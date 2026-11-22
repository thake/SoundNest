/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedTextMatcherTest {

    @Test
    fun `stripDiacriticsLower folds accents and preserves digits`() {
        assertEquals("explicit 2026!", BlockedTextMatcher.stripDiacriticsLower("Éxplïcît 2026!"))
        assertEquals("", BlockedTextMatcher.stripDiacriticsLower("   "))
    }

    @Test
    fun `normalizeForKeywords folds leetspeak and punctuation`() {
        assertEquals("explicit shit", BlockedTextMatcher.normalizeForKeywords("3xpl1c!t \$h17"))
        assertEquals("a s o i e a s t", BlockedTextMatcher.normalizeForKeywords("@ $ 0 1 3 4 5 7"))
    }

    @Test
    fun `matches detects plain, leetspeak, numeric, and vowel-masked keywords`() {
        val keywords = setOf("explicit", "shit", "1989")

        assertTrue(BlockedTextMatcher.matches(keywords, emptySet(), "My Éxplïcît Track"))
        assertTrue(BlockedTextMatcher.matches(keywords, emptySet(), "That sh*t slaps"))
        assertTrue(BlockedTextMatcher.matches(keywords, emptySet(), "3xpl1c!t remix"))
        assertTrue(BlockedTextMatcher.matches(keywords, emptySet(), "Album 1989 Deluxe"))
        assertFalse(BlockedTextMatcher.matches(keywords, emptySet(), "Clean Kids Song"))
    }

    @Test
    fun `matches respects word boundaries for keywords`() {
        assertFalse(BlockedTextMatcher.matches(setOf("ass"), emptySet(), "Classical Music"))
        assertTrue(BlockedTextMatcher.matches(setOf("ass"), emptySet(), "Kick Ass Beat"))
    }

    @Test
    fun `matches evaluates regex patterns case-insensitively and ignores invalid regexes`() {
        val patterns = setOf("[unclosed", "parental\\s+advisory")

        assertTrue(BlockedTextMatcher.matches(emptySet(), patterns, "Song (PARENTAL  ADVISORY)"))
        assertFalse(BlockedTextMatcher.matches(emptySet(), patterns, "Bedtime Lullaby"))
    }
}
