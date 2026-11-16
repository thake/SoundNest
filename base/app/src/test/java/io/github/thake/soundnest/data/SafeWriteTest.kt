package io.github.thake.soundnest.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SafeWriteTest {
    @Test
    fun replacesTheFileWholeAndLeavesNoTemporary() {
        val dir = Files.createTempDirectory("safe").toFile()
        val f = File(dir, "index.json")
        f.writeText("old")
        writeTextSafely(f, "new")
        assertEquals("new", f.readText())
        assertFalse(File(dir, "index.json.tmp").exists())
        dir.deleteRecursively()
    }

    @Test
    fun replacesBytesWholeAndKeepsNoOldCopy() {
        val dir = Files.createTempDirectory("safe").toFile()
        val f = File(dir, "package.zip")
        f.writeBytes(byteArrayOf(1, 2))
        writeBytesSafely(f, byteArrayOf(3, 4, 5), sync = false)
        assertEquals(listOf<Byte>(3, 4, 5), f.readBytes().toList())
        assertEquals(listOf("package.zip"), dir.list()!!.toList())
        dir.deleteRecursively()
    }

    @Test
    fun createsMissingFolders() {
        val dir = Files.createTempDirectory("safe").toFile()
        val f = File(dir, "a/b/collections.json")
        writeTextSafely(f, "{}")
        assertEquals("{}", f.readText())
        dir.deleteRecursively()
    }

    @Test
    fun accountKeyIsStableOpaqueAndDistinct() {
        assertEquals(accountKeyOf("alice"), accountKeyOf("alice"))
        assertNotEquals(accountKeyOf("alice"), accountKeyOf("bob"))
        assertEquals(24, accountKeyOf("alice").length)
        assertFalse(accountKeyOf("alice").contains("alice"))
    }
}
