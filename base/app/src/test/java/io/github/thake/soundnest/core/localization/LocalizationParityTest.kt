/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.core.localization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory

class LocalizationParityTest {

    private fun findProjectFile(relativeResPath: String): File {
        val candidates = listOf(
            File("src/main/res", relativeResPath),
            File("app/src/main/res", relativeResPath),
            File("base/app/src/main/res", relativeResPath),
            File("../base/app/src/main/res", relativeResPath),
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Could not find $relativeResPath in candidates: $candidates")
    }

    private data class ParsedStrings(
        val strings: Map<String, String>,
        val plurals: Map<String, Map<String, String>>,
    )

    private fun parseStringsFile(file: File): ParsedStrings {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(file)
        doc.documentElement.normalize()

        val strings = mutableMapOf<String, String>()
        val stringNodes = doc.getElementsByTagName("string")
        for (i in 0 until stringNodes.length) {
            val node = stringNodes.item(i) as Element
            val name = node.getAttribute("name")
            val translatable = node.getAttribute("translatable")
            if (translatable != "false" && name.isNotBlank()) {
                strings[name] = node.textContent
            }
        }

        val plurals = mutableMapOf<String, Map<String, String>>()
        val pluralsNodes = doc.getElementsByTagName("plurals")
        for (i in 0 until pluralsNodes.length) {
            val node = pluralsNodes.item(i) as Element
            val name = node.getAttribute("name")
            val items = mutableMapOf<String, String>()
            val itemNodes = node.getElementsByTagName("item")
            for (j in 0 until itemNodes.length) {
                val item = itemNodes.item(j) as Element
                val quantity = item.getAttribute("quantity")
                items[quantity] = item.textContent
            }
            plurals[name] = items
        }

        return ParsedStrings(strings, plurals)
    }

    private fun extractPlaceholders(text: String): List<String> {
        val pattern = Pattern.compile("%(?:\\d+\\$)?[a-zA-Z%]")
        val matcher = pattern.matcher(text)
        val list = mutableListOf<String>()
        while (matcher.find()) {
            val match = matcher.group()
            if (match != "%%") {
                list.add(match)
            }
        }
        return list.sorted()
    }

    @Test
    fun `soundnest_strings has 100 percent key parity between default and german`() {
        val defaultFile = findProjectFile("values/soundnest_strings.xml")
        val deFile = findProjectFile("values-de/soundnest_strings.xml")

        val defaultData = parseStringsFile(defaultFile)
        val deData = parseStringsFile(deFile)

        val missingInDe = defaultData.strings.keys - deData.strings.keys
        val extraInDe = deData.strings.keys - defaultData.strings.keys

        assertTrue("Keys in default soundnest_strings missing in values-de: $missingInDe", missingInDe.isEmpty())
        assertTrue("Keys in values-de soundnest_strings missing in default: $extraInDe", extraInDe.isEmpty())
        assertEquals("Total string count should match", defaultData.strings.size, deData.strings.size)
    }

    @Test
    fun `soundnest_strings plurals have 100 percent parity between default and german`() {
        val defaultFile = findProjectFile("values/soundnest_strings.xml")
        val deFile = findProjectFile("values-de/soundnest_strings.xml")

        val defaultData = parseStringsFile(defaultFile)
        val deData = parseStringsFile(deFile)

        val missingPluralsInDe = defaultData.plurals.keys - deData.plurals.keys
        val extraPluralsInDe = deData.plurals.keys - defaultData.plurals.keys

        assertTrue("Plurals in default missing in values-de: $missingPluralsInDe", missingPluralsInDe.isEmpty())
        assertTrue("Plurals in values-de missing in default: $extraPluralsInDe", extraPluralsInDe.isEmpty())
        assertEquals("Total plurals count should match", defaultData.plurals.size, deData.plurals.size)

        for ((key, defaultItems) in defaultData.plurals) {
            val deItems = deData.plurals[key] ?: error("Missing plural in de: $key")
            assertTrue("Plural $key missing 'one' quantity in de", deItems.containsKey("one"))
            assertTrue("Plural $key missing 'other' quantity in de", deItems.containsKey("other"))
        }
    }

    @Test
    fun `placeholder formats match between default and german strings`() {
        val defaultFile = findProjectFile("values/soundnest_strings.xml")
        val deFile = findProjectFile("values-de/soundnest_strings.xml")

        val defaultData = parseStringsFile(defaultFile)
        val deData = parseStringsFile(deFile)

        val mismatchedKeys = mutableListOf<String>()

        for ((key, enText) in defaultData.strings) {
            val deText = deData.strings[key] ?: continue
            val enPlaceholders = extractPlaceholders(enText)
            val dePlaceholders = extractPlaceholders(deText)
            if (enPlaceholders != dePlaceholders) {
                mismatchedKeys.add("$key (EN: $enPlaceholders vs DE: $dePlaceholders)")
            }
        }

        assertTrue("Placeholder mismatches found: \n${mismatchedKeys.joinToString("\n")}", mismatchedKeys.isEmpty())
    }
}
