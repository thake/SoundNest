/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.spokenword.audible.data

import io.github.thake.soundnest.feature.spokenword.audible.settings.AudibleCredentialStore

import io.github.thake.soundnest.audible.*

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudibleStorageTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AudibleCredentialStore.clear(context)
        AudibleCatalogCache.clear(context)
        AudibleProgressStore.clearAll(context)
    }

    @Test
    fun `credential store encrypts tokens at rest, survives reload, and wipes cleanly`() {
        assertFalse(AudibleCredentialStore.isConnected(context))
        assertNull(AudibleCredentialStore.loadTokens(context))

        val secretTokens = AudibleSessionTokens(
            marketplace = AudibleMarketplace.DE,
            deviceSerial = "SERIAL1234567890",
            adpToken = "SUPER_SECRET_ADP_TOKEN_XYZ",
            devicePrivateKeyPem = "-----BEGIN RSA PRIVATE KEY-----\nSECRET_KEY_MATERIAL\n-----END RSA PRIVATE KEY-----",
            refreshToken = "SECRET_REFRESH_TOKEN_999",
            accessToken = "SECRET_ACCESS_TOKEN_111",
            customerName = "Mama Account",
        )
        AudibleCredentialStore.save(context, secretTokens, connectedAtMs = 1_700_000_000_000L)

        assertTrue(AudibleCredentialStore.isConnected(context))
        val summary = AudibleCredentialStore.summary(context)
        assertNotNull(summary)
        assertEquals(AudibleMarketplace.DE, summary!!.marketplace)
        assertEquals("Mama Account", summary.customerName)
        assertEquals(1_700_000_000_000L, summary.connectedAtMs)

        // Verify raw SharedPreferences never contain plaintext secrets
        val rawPrefsDump = context.getSharedPreferences(AudibleCredentialStore.PREFS_NAME, Context.MODE_PRIVATE)
            .all.values.joinToString(" ")
        assertFalse("Plaintext adpToken leaked in prefs", "SUPER_SECRET_ADP_TOKEN_XYZ" in rawPrefsDump)
        assertFalse("Plaintext private key leaked in prefs", "SECRET_KEY_MATERIAL" in rawPrefsDump)
        assertFalse("Plaintext refresh token leaked in prefs", "SECRET_REFRESH_TOKEN_999" in rawPrefsDump)

        // Simulate cold process restart
        AudibleCredentialStore.resetMemoryForTests()
        val reloaded = AudibleCredentialStore.loadTokens(context)
        assertEquals(secretTokens, reloaded)
        assertEquals(AudibleMarketplace.DE, AudibleCredentialStore.summaryFlow.value?.marketplace)

        // Wipe on disconnect
        AudibleCredentialStore.clear(context)
        assertFalse(AudibleCredentialStore.isConnected(context))
        assertNull(AudibleCredentialStore.loadTokens(context))
        assertNull(AudibleCredentialStore.summaryFlow.value)
    }

    @Test
    fun `catalog cache persists books and per-ASIN chapter tables across cold restarts`() {
        val books = listOf(
            AudibleBook(
                asin = "B08G9PRS1K",
                title = "Project Hail Mary",
                authors = listOf("Andy Weir"),
                narrators = listOf("Ray Porter"),
                runtimeLengthMs = 58_200_000L,
                coverUrl = "https://example.com/phm.jpg",
            ),
            AudibleBook(
                asin = "B07KIDSBOOK",
                title = "Die Schule der magischen Tiere",
                authors = listOf("Margit Auer"),
                runtimeLengthMs = 12_900_000L,
            ),
        )
        val chapters = listOf(
            AudibleChapter(0, "Chapter 1", 0L, 600_000L),
            AudibleChapter(1, "Chapter 2", 600_000L, 720_000L),
        )

        AudibleCatalogCache.saveBooks(context, books)
        AudibleCatalogCache.saveChapters(context, "B08G9PRS1K", chapters)

        AudibleCatalogCache.resetMemoryForTests()
        assertEquals(2, AudibleCatalogCache.books(context).size)
        assertEquals("Project Hail Mary", AudibleCatalogCache.book(context, "B08G9PRS1K")?.title)
        assertEquals(chapters, AudibleCatalogCache.chapters(context, "B08G9PRS1K"))
        assertNull(AudibleCatalogCache.chapters(context, "B07KIDSBOOK"))
    }

    @Test
    fun `progress store persists local-only book position and finished chapter flags`() {
        assertNull(AudibleProgressStore.get(context, "B08G9PRS1K"))

        AudibleProgressStore.savePosition(
            context = context,
            asin = "B08G9PRS1K",
            positionMs = 645_000L,
            chapterIndex = 1,
            nowMs = 1000L,
        )
        AudibleProgressStore.setChapterFinished(
            context = context,
            asin = "B08G9PRS1K",
            chapterIndex = 0,
            finished = true,
            nowMs = 2000L,
        )

        AudibleProgressStore.resetMemoryForTests()
        val loaded = AudibleProgressStore.get(context, "B08G9PRS1K")
        assertNotNull(loaded)
        assertEquals(645_000L, loaded!!.positionMs)
        assertEquals(1, loaded.chapterIndex)
        assertEquals(setOf(0), loaded.finishedChapters)
        assertEquals(2000L, loaded.updatedAtMs)

        // Unmark finished chapter
        AudibleProgressStore.setChapterFinished(context, "B08G9PRS1K", 0, finished = false, nowMs = 3000L)
        assertTrue(AudibleProgressStore.get(context, "B08G9PRS1K")!!.finishedChapters.isEmpty())

        // Reset book progress
        AudibleProgressStore.resetBook(context, "B08G9PRS1K")
        assertNull(AudibleProgressStore.get(context, "B08G9PRS1K"))
    }
}
