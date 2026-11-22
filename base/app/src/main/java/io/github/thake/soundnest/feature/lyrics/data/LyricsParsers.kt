/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.lyrics.data

import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.Base64
import java.util.zip.InflaterInputStream

/**
 * Pure, stateless parsers for synced lyrics formats (LRC, Kugou KRC, and Apple Music TTML).
 */
object LyricsParsers {

    private val spaces = Regex("\\s+")
    private val lrcTag = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val krcKey = intArrayOf(64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206, 210, 110, 105)
    private val krcLine = Regex("""^\[(\d+),(\d+)](.*)$""")
    private val krcWord = Regex("""<(\d+),(\d+),\d+>([^<]*)""")

    /** "v1" -> 1, "v2" -> 2; Apple's "v1000" (everyone) -> 0; anything else is unknown. */
    fun voiceOf(agent: String?): Int? =
        agent?.trim()?.removePrefix("v")?.toIntOrNull()?.takeIf { it >= 0 }?.let { if (it >= 1000) 0 else it }

    /** Backing vocals as plain text: "(Woo-hoo)" -> "Woo-hoo". */
    fun backingText(s: String): String? =
        s.replace(spaces, " ").trim().removeSurrounding("(", ")").trim().ifEmpty { null }

    /** TTML times: "11.859", "1:02.345" or "00:01:02.345" -> ms (-1 if invalid). */
    fun clockMs(v: String?): Long {
        if (v.isNullOrBlank()) return -1L
        val parts = v.trim().removeSuffix("s").split(':')
        var ms = 0.0
        for (part in parts) ms = ms * 60 + (part.toDoubleOrNull() ?: return -1L)
        return (ms * 1000).toLong()
    }

    fun parseClockMs(v: String?): Long = clockMs(v).coerceAtLeast(0L)

    /** LRC: each line is "[mm:ss.xx] text" (a line may carry multiple timestamps). */
    fun parseLrc(lrc: String): List<LyricLine> {
        val out = ArrayList<LyricLine>()
        for (raw in lrc.split("\n")) {
            val tags = lrcTag.findAll(raw).toList()
            if (tags.isEmpty()) continue
            val text = raw.substring(tags.last().range.last + 1).trim().ifEmpty { "♪" }
            for (m in tags) {
                val frac = m.groupValues[3]
                val ms = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100
                    2 -> frac.toLong() * 10
                    else -> frac.take(3).toLong()
                }
                out.add(LyricLine(m.groupValues[1].toLong() * 60_000 + m.groupValues[2].toLong() * 1_000 + ms, text))
            }
        }
        // Collapse runs of empty (instrumental) lines into one ♪.
        val sorted = out.sortedBy { it.timeMs }
        return sorted.filterIndexed { i, l -> !(l.text == "♪" && i > 0 && sorted[i - 1].text == "♪") }
    }

    /** Decrypts and inflates a Base64-encoded Kugou KRC payload. */
    fun decodeKrc(
        base64Content: String,
        base64Decode: (String) -> ByteArray = { Base64.getMimeDecoder().decode(it) },
    ): String? = runCatching {
        val raw = base64Decode(base64Content)
        if (raw.size <= 4) return null
        val x = ByteArray(raw.size - 4) { i -> (raw[i + 4].toInt() xor krcKey[i % krcKey.size]).toByte() }
        InflaterInputStream(x.inputStream()).readBytes().decodeToString()
    }.getOrNull()

    /** Parses decrypted Kugou KRC text into word-synced [Lyrics]. */
    fun parseKrc(krc: String): Lyrics? {
        val lines = krc.lines().mapNotNull { raw ->
            val m = krcLine.find(raw.trim()) ?: return@mapNotNull null
            val start = m.groupValues[1].toLong()
            val words = krcWord.findAll(m.groupValues[3]).map { w ->
                val s = start + w.groupValues[1].toLong()
                LyricWord(s, s + w.groupValues[2].toLong(), w.groupValues[3])
            }.toList()
            val text = words.joinToString("") { it.text }.trim()
            // Kugou opens with credit lines ("作词：…"): not lyrics.
            if (text.isEmpty() || text.contains('：')) null else LyricLine(start, text, words.takeIf { it.isNotEmpty() })
        }
        if (lines.size < 3) return null
        return Lyrics(lines, synced = true, provider = "Kugou")
    }

    /**
     * Apple Music's TTML: a <p> per line (its `ttm:agent` says who sings), a timed <span> per word,
     * backing vocals in a `ttm:role="x-bg"` span (kept apart from the line), and a translation and a
     * romanization either in the head (`translation` / `transliteration`, a `text for="L1"` per line,
     * the line's `*:key`) or inside the line (`x-translation` / `x-roman` spans).
     */
    fun parseTtml(
        xml: String,
        newParser: () -> XmlPullParser = { android.util.Xml.newPullParser() },
    ): Lyrics? = runCatching {
        val p = newParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(StringReader(xml))
        fun attr(name: String) = p.getAttributeValue(null, name)
        fun lineKey() = (0 until p.attributeCount)
            .firstOrNull { p.getAttributeName(it).let { n -> n == "key" || n.endsWith(":key") } }
            ?.let { p.getAttributeValue(it) }
        fun tidy(sb: CharSequence) = sb.toString().replace(spaces, " ").trim()

        val headTranslation = HashMap<String, String>()
        val headRoman = HashMap<String, String>()
        var translationLanguage: String? = null
        var headKind = 0
        var headSeenTr = false
        var headSeenRo = false
        var headKey: String? = null
        var headText = StringBuilder()
        var headSkip = 0

        val lines = ArrayList<LyricLine>()
        var lineStart = -1L
        var words: MutableList<LyricWord>? = null
        var plain = StringBuilder()
        var wordStart = -1L
        var wordEnd = 0L
        var word = StringBuilder()
        var role: String? = null
        var roleDepth = 0
        var key: String? = null
        var voice: Int? = null
        var bgWords = ArrayList<LyricWord>()
        var bgPlain = StringBuilder()
        var bgStart = -1L
        var bgEnd = 0L
        var bgWord = StringBuilder()
        var bgSpan = longArrayOf(-1, 0)
        var inTr = StringBuilder()
        var inRo = StringBuilder()
        var inBody = false

        while (true) {
            when (p.next()) {
                XmlPullParser.END_DOCUMENT -> break
                XmlPullParser.START_TAG -> when {
                    p.name == "body" -> inBody = true
                    !inBody -> when {
                        headKey != null -> if (headSkip > 0) headSkip++ else if (attr("ttm:role")?.startsWith("x-") == true) headSkip = 1
                        p.name == "translation" -> if (!headSeenTr) { headSeenTr = true; headKind = 1; translationLanguage = attr("xml:lang") } else headKind = -1
                        p.name == "transliteration" -> if (!headSeenRo) { headSeenRo = true; headKind = 2 } else headKind = -1
                        p.name == "text" && headKind > 0 -> { headKey = attr("for"); headText = StringBuilder(); headSkip = 0 }
                    }
                    role != null -> {
                        roleDepth++
                        if (role == "x-bg" && p.name == "span" && roleDepth == 2) {
                            bgStart = clockMs(attr("begin"))
                            bgEnd = clockMs(attr("end"))
                            bgWord = StringBuilder()
                        }
                    }
                    attr("ttm:role")?.startsWith("x-") == true -> {
                        role = attr("ttm:role")
                        roleDepth = 1
                        if (role == "x-bg") bgSpan = longArrayOf(clockMs(attr("begin")), clockMs(attr("end")))
                        if (role == "x-translation" && translationLanguage == null) translationLanguage = attr("xml:lang")
                    }
                    p.name == "p" -> {
                        lineStart = clockMs(attr("begin"))
                        words = ArrayList()
                        plain = StringBuilder()
                        key = lineKey()
                        voice = voiceOf(attr("ttm:agent"))
                        bgWords = ArrayList()
                        bgPlain = StringBuilder()
                        bgStart = -1
                        inTr = StringBuilder()
                        inRo = StringBuilder()
                    }
                    p.name == "span" && words != null -> {
                        wordStart = clockMs(attr("begin"))
                        wordEnd = clockMs(attr("end"))
                        word = StringBuilder()
                    }
                }
                XmlPullParser.TEXT -> {
                    val t = p.text
                    if (!inBody) {
                        if (headKey != null && headSkip == 0) headText.append(t)
                    } else if (words != null) when (role) {
                        null -> {
                            if (wordStart >= 0) word.append(t)
                            else if (words!!.isNotEmpty()) words!![words!!.lastIndex] = words!!.last().let { it.copy(text = it.text + t) }
                            plain.append(t)
                        }
                        "x-bg" -> {
                            bgPlain.append(t)
                            if (bgStart >= 0) bgWord.append(t)
                            else if (bgWords.isNotEmpty()) bgWords[bgWords.lastIndex] = bgWords.last().let { it.copy(text = it.text + t) }
                        }
                        "x-translation" -> inTr.append(t)
                        "x-roman" -> inRo.append(t)
                    }
                }
                XmlPullParser.END_TAG -> when {
                    !inBody -> when {
                        headKey != null && headSkip > 0 -> headSkip--
                        headKey != null && p.name == "text" -> {
                            val text = tidy(headText).ifEmpty { null }
                            if (text != null) (if (headKind == 1) headTranslation else headRoman)[headKey!!] = text
                            headKey = null
                        }
                        p.name == "translation" || p.name == "transliteration" -> headKind = 0
                    }
                    role != null -> {
                        if (role == "x-bg" && roleDepth == 2 && p.name == "span" && bgStart >= 0) {
                            bgWords.add(LyricWord(bgStart, bgEnd, bgWord.toString()))
                            bgStart = -1
                        }
                        roleDepth--
                        if (roleDepth == 0) {
                            if (role == "x-bg" && bgWords.isEmpty() && bgSpan[0] >= 0 && bgPlain.isNotBlank()) {
                                bgWords.add(LyricWord(bgSpan[0], bgSpan[1], bgPlain.toString()))
                            }
                            role = null
                        }
                    }
                    p.name == "span" && wordStart >= 0 -> {
                        words?.add(LyricWord(wordStart, wordEnd, word.toString()))
                        wordStart = -1
                    }
                    p.name == "p" && words != null -> {
                        var text = tidy(plain)
                        var foreground: List<LyricWord>? = words!!.takeIf { it.isNotEmpty() }
                        var back: List<LyricWord>? = bgWords.takeIf { it.isNotEmpty() }
                        var backText = backingText(tidy(bgPlain))
                        if (text.isEmpty() && backText != null) {
                            text = backText
                            foreground = back
                            back = null
                            backText = null
                        }
                        if (text.isNotEmpty() && lineStart >= 0) {
                            val tr = tidy(inTr).ifEmpty { null } ?: key?.let(headTranslation::get)
                            val ro = tidy(inRo).ifEmpty { null } ?: key?.let(headRoman::get)
                            lines += LyricLine(
                                lineStart,
                                text,
                                foreground,
                                translation = tr?.takeIf { LyricsMatch.key(it) != LyricsMatch.key(text) },
                                romanization = ro?.takeIf { LyricsMatch.key(it) != LyricsMatch.key(text) },
                                voice = voice,
                                background = backText,
                                backgroundWords = back,
                            )
                        }
                        words = null
                    }
                }
            }
        }
        if (lines.isEmpty()) null
        else Lyrics(lines, synced = true, translationLanguage = translationLanguage.takeIf { lines.any { it.translation != null } })
    }.getOrNull()
}
