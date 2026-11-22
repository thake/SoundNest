/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.filter

import java.text.Normalizer

/**
 * Stateless anti-evasion text matcher for parental keyword and regex rules.
 *
 * Handles:
 * - NFKD Unicode decomposition + diacritic stripping (`éxplïcît` -> `explicit`)
 * - Leetspeak folding (`@`->`a`, `$`->`s`, `0`->`o`, `1`/`!`->`i`, `3`->`e`, `4`->`a`, `5`->`s`, `7`->`t`)
 * - Vowel-masked profanity matching (`f@ck` -> `fuck`, `sh*t` -> `shit`)
 * - Plain numeric word matching (so `"1989"` still matches without digit-folding)
 * - Cached case-insensitive regex compilation (silently ignoring invalid user regexes)
 */
object BlockedTextMatcher {

    private val DIACRITICS = Regex("\\p{Mn}+")
    private val NON_WORD_SPACES = Regex("[^\\p{L}\\p{N}]+")
    private val MULTI_SPACE = Regex("\\s+")
    private val CENSORED_TOKEN_SPLIT = Regex("[^\\p{L}\\p{N}@*\$!]+")
    private const val VOWELS = "aeiou"

    @Volatile
    private var cachedRegexSource: Set<String> = emptySet()

    @Volatile
    private var cachedCompiledRegexes: List<Regex> = emptyList()

    private fun compiledBlockedRegexes(patterns: Set<String>): List<Regex> {
        if (patterns.isEmpty()) return emptyList()
        if (patterns == cachedRegexSource) return cachedCompiledRegexes
        val compiled = patterns.mapNotNull { pat ->
            runCatching { Regex(pat, RegexOption.IGNORE_CASE) }.getOrNull()
        }
        cachedRegexSource = patterns.toSet()
        cachedCompiledRegexes = compiled
        return compiled
    }

    /** Strips accents/diacritics and lowercases while preserving digits/punctuation for regex checks. */
    fun stripDiacriticsLower(raw: String): String {
        if (raw.isBlank()) return ""
        val decomposed = Normalizer.normalize(raw, Normalizer.Form.NFKD)
        return DIACRITICS.replace(decomposed, "").lowercase()
    }

    private fun foldLeetChar(ch: Char): Char = when (ch) {
        '@' -> 'a'
        '$' -> 's'
        '0' -> 'o'
        '1', '!' -> 'i'
        '3' -> 'e'
        '4' -> 'a'
        '5' -> 's'
        '7' -> 't'
        else -> ch
    }

    /**
     * Normalizes text for anti-evasion keyword matching:
     * NFKD decomposition + diacritic stripping + leetspeak folding + single-space word separation.
     */
    fun normalizeForKeywords(raw: String): String {
        if (raw.isBlank()) return ""
        val base = stripDiacriticsLower(raw)
        val sb = StringBuilder(base.length)
        for (ch in base) sb.append(foldLeetChar(ch))
        return NON_WORD_SPACES.replace(sb.toString(), " ").replace(MULTI_SPACE, " ").trim()
    }

    /** Plain word-tokenized lowercase without digit-folding so numeric keywords like `"1989"` also match. */
    private fun normalizePlainWords(raw: String): String {
        if (raw.isBlank()) return ""
        return NON_WORD_SPACES.replace(stripDiacriticsLower(raw), " ").replace(MULTI_SPACE, " ").trim()
    }

    /** Matches words where `@` or `*` masks a vowel (e.g. `f@ck` -> `fuck`, `sh*t` -> `shit`). */
    private fun matchesCensoredWord(rawToken: String, normKeyword: String): Boolean {
        if (normKeyword.length < 3 || rawToken.length != normKeyword.length) return false
        if ('@' !in rawToken && '*' !in rawToken) return false
        var wildcards = 0
        for (i in rawToken.indices) {
            val rawCh = rawToken[i]
            val kwCh = normKeyword[i]
            if (foldLeetChar(rawCh) == kwCh) continue
            if ((rawCh == '@' || rawCh == '*') && kwCh in VOWELS) {
                wildcards++
                continue
            }
            return false
        }
        return wildcards > 0 && (normKeyword.length - wildcards) >= 2
    }

    fun matches(
        blockedKeywords: Set<String>,
        blockedRegexPatterns: Set<String>,
        vararg fields: String,
    ): Boolean {
        if (blockedKeywords.isEmpty() && blockedRegexPatterns.isEmpty()) return false
        val nonBlank = fields.filter { it.isNotBlank() }
        if (nonBlank.isEmpty()) return false

        if (blockedKeywords.isNotEmpty()) {
            for (field in nonBlank) {
                val stripped = stripDiacriticsLower(field)
                val rawTokens = CENSORED_TOKEN_SPLIT.split(stripped).filter { it.isNotEmpty() }
                val foldedPadded = " ${normalizeForKeywords(field)} "
                val plainPadded = " ${normalizePlainWords(field)} "
                for (kw in blockedKeywords) {
                    val normKw = normalizeForKeywords(kw)
                    val plainKw = normalizePlainWords(kw)
                    if (normKw.isNotEmpty() && foldedPadded.contains(" $normKw ")) return true
                    if (plainKw.isNotEmpty() && plainPadded.contains(" $plainKw ")) return true
                    if (normKw.length >= 3 && !normKw.contains(' ') && rawTokens.any { matchesCensoredWord(it, normKw) }) {
                        return true
                    }
                }
            }
        }

        if (blockedRegexPatterns.isNotEmpty()) {
            val compiled = compiledBlockedRegexes(blockedRegexPatterns)
            for (field in nonBlank) {
                val stripped = stripDiacriticsLower(field)
                val folded = normalizeForKeywords(field)
                for (rx in compiled) {
                    if (rx.containsMatchIn(field) || rx.containsMatchIn(stripped) || rx.containsMatchIn(folded)) {
                        return true
                    }
                }
            }
        }
        return false
    }
}
