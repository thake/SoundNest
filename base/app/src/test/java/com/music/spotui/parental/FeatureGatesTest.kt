/* SPDX-License-Identifier: GPL-3.0-only */
package com.music.spotui.parental

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.music.spotui.data.api.LibraryEdits
import com.music.spotui.data.api.SpotifySync
import com.music.spotui.data.entity.LibraryEntry
import com.music.spotui.data.local.LocalMusic
import com.music.spotui.data.local.LocalPlaylists
import com.music.spotui.data.local.LocalTrack
import com.music.spotui.di.Graph
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FeatureGatesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        Graph::class.java.getDeclaredField("app").apply {
            isAccessible = true
            set(null, context)
        }
        LocalPlaylists.init(context)
        LocalPlaylists.playlists.clear()
        ParentalPolicyStore.resetForTests(context)
        TrustedNetworkGate.resetForTests(context)
        ParentSecurityManager.resetForTests(context)
    }

    @After
    fun tearDown() {
        ParentalPolicyStore.resetForTests(context)
        TrustedNetworkGate.resetForTests(context)
        ParentSecurityManager.resetForTests(context)
    }

    @Test
    fun `default feature gates fail safe for kid profile`() {
        assertFalse(FeatureGates.canUsePodcasts(context))
        assertFalse(FeatureGates.canUseAudiobooks(context))
        assertFalse(FeatureGates.canUseLiveRadio(context))
        assertFalse(FeatureGates.canUseLocalFiles(context))
        assertFalse(FeatureGates.canUseJam(context))
        assertTrue(FeatureGates.canShowLyrics(context))
        assertFalse(FeatureGates.canUseGeniusMeanings(context))
        assertFalse(FeatureGates.canUseExtraLyricSources(context))
        assertTrue(FeatureGates.canUseAutoplayAndRadio(context))
        assertTrue(FeatureGates.canModifySpotifyLibrary(context))
    }

    @Test
    fun `strict allowlist automatically disables autoplay radio and recommendations even if toggle was true`() {
        ParentalPolicyStore.update(context) {
            it.copy(
                filterMode = FilterMode.STRICT_ALLOWLIST,
                allowAutoplayAndRadio = true,
            )
        }
        assertFalse(FeatureGates.canUseAutoplayAndRadio(context))

        ParentalPolicyStore.update(context) {
            it.copy(
                filterMode = FilterMode.FILTERED_OPEN,
                allowAutoplayAndRadio = false,
            )
        }
        assertFalse(FeatureGates.canUseAutoplayAndRadio(context))
    }

    @Test
    fun `local files gate suppresses MediaStore reads and permission requests when disabled`() = runBlocking {
        assertFalse(FeatureGates.canUseLocalFiles(context))
        assertFalse(FeatureGates.shouldRequestAudioStoragePermission(context))
        assertTrue(LocalMusic.tracks(context, refresh = true).isEmpty())

        ParentalPolicyStore.update(context) { it.copy(allowLocalFiles = true) }
        assertTrue(FeatureGates.canUseLocalFiles(context))
        assertTrue(FeatureGates.shouldRequestAudioStoragePermission(context))
    }

    @Test
    fun `jam toggle stays synchronized with TrustedNetworkGate`() {
        assertFalse(FeatureGates.canUseJam(context))
        assertFalse(TrustedNetworkGate.isJamAllowed(context))

        ParentalPolicyStore.update(context) { it.copy(allowJam = true) }
        assertTrue(TrustedNetworkGate.isJamAllowed(context))
        assertTrue(FeatureGates.canUseJam(context))

        ParentalPolicyStore.update(context) { it.copy(allowJam = false) }
        assertFalse(TrustedNetworkGate.isJamAllowed(context))
        assertFalse(FeatureGates.canUseJam(context))
    }

    @Test
    fun `readOnlySpotifyLibrary blocks remote Spotify mutations while keeping LocalPlaylists writable`() {
        ParentalPolicyStore.update(context) { it.copy(readOnlySpotifyLibrary = true) }
        assertFalse(FeatureGates.canModifySpotifyLibrary(context))

        // Remote LibraryEdits.run must immediately refuse without running the write block
        var writeExecuted = false
        var callbackResult: Boolean? = null
        val dummyEdit = LibraryEdits.Rename("spotify:playlist:1111111111111111111111", "Hacked Name")
        LibraryEdits.run(
            context = context,
            edit = dummyEdit,
            write = { writeExecuted = true },
            onDone = { ok -> callbackResult = ok },
        )
        assertFalse(writeExecuted)
        assertEquals(false, callbackResult)

        // SpotifySync.addTrackToPlaylist must immediately refuse
        var syncResult: Boolean? = null
        SpotifySync.addTrackToPlaylist(context, "1111111111111111111111", "2222222222222222222222") { ok ->
            syncResult = ok
        }
        assertEquals(false, syncResult)

        // LocalPlaylists on-device playlists remain 100% writable for the child
        val created = LocalPlaylists.create("My Kid Mix")
        LocalPlaylists.add(
            created.id,
            LocalTrack(
                id = 1L,
                uri = Uri.parse("content://media/external/audio/media/1"),
                title = "Kid Song",
                artist = "Kid Artist",
                album = "Kid Album",
                durationMs = 180_000L,
                artUri = Uri.EMPTY,
            ),
        )
        val stored = LocalPlaylists.list()
        assertEquals(1, stored.size)
        assertEquals("My Kid Mix", stored.first().name)
        assertEquals(1, stored.first().items.size)
    }
}
