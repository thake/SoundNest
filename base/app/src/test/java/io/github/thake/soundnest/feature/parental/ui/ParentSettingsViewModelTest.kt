/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ParentSettingsViewModelTest {

    @Test
    fun exportBackupEmitsSuccessOnWrite() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val vm = ParentSettingsViewModel(ioDispatcher = dispatcher)
        val events = mutableListOf<ParentBackupEvent>()
        val job = launch(dispatcher) { vm.backupEvents.collect { events += it } }

        var written: String? = null
        vm.exportBackup(
            produceJson = { """{"version":1}""" },
            writeJson = { written = it },
        )
        advanceUntilIdle()

        assertEquals("""{"version":1}""", written)
        assertEquals(listOf(ParentBackupEvent.ExportSuccess), events)
        job.cancel()
    }

    @Test
    fun exportBackupEmitsFailureWhenWriteThrows() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val vm = ParentSettingsViewModel(ioDispatcher = dispatcher)
        val events = mutableListOf<ParentBackupEvent>()
        val job = launch(dispatcher) { vm.backupEvents.collect { events += it } }

        vm.exportBackup(
            produceJson = { "{}" },
            writeJson = { error("disk full") },
        )
        advanceUntilIdle()

        assertEquals(1, events.size)
        val err = events.single() as ParentBackupEvent.ExportFailed
        assertTrue(err.message.contains("disk full"))
        job.cancel()
    }

    @Test
    fun importBackupBumpsNetworkRefreshOnSuccessAndNotOnFailure() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val vm = ParentSettingsViewModel(ioDispatcher = dispatcher)
        val events = mutableListOf<ParentBackupEvent>()
        val job = launch(dispatcher) { vm.backupEvents.collect { events += it } }

        assertEquals(0, vm.networkRefresh.value)

        vm.importBackup(
            readJson = { """{"valid":true}""" },
            applyJson = { Result.success(Unit) },
        )
        advanceUntilIdle()

        assertEquals(1, vm.networkRefresh.value)
        assertEquals(listOf(ParentBackupEvent.ImportSuccess), events)

        vm.importBackup(
            readJson = { """{"bad":true}""" },
            applyJson = { Result.failure(IllegalArgumentException("corrupt")) },
        )
        advanceUntilIdle()

        assertEquals(1, vm.networkRefresh.value)
        val failed = events.last() as ParentBackupEvent.ImportFailed
        assertTrue(failed.message.contains("corrupt"))
        job.cancel()
    }

    @Test
    fun parseQuickSpotifyUriExtractsKindAndNormalizedId() {
        assertEquals("track" to "4cOdK2wGLETKBW3PvgPWqT", parseQuickSpotifyUri("spotify:track:4cOdK2wGLETKBW3PvgPWqT"))
        assertEquals(
            "playlist" to "37i9dQZF1DXcBWIGoYBM5M",
            parseQuickSpotifyUri("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=xyz"),
        )
        assertEquals("album" to "1ATL5GLyefJaxhQzSPVrLX", parseQuickSpotifyUri("https://open.spotify.com/album/1ATL5GLyefJaxhQzSPVrLX"))
        assertEquals("artist" to "06HL4z0CvFAxyc27GXpf02", parseQuickSpotifyUri("spotify:artist:06HL4z0CvFAxyc27GXpf02"))
        assertEquals("show" to "4rOoJ6Egrf8K2IrywzwOMk", parseQuickSpotifyUri("https://open.spotify.com/show/4rOoJ6Egrf8K2IrywzwOMk"))
        assertNull(parseQuickSpotifyUri("https://example.com/not-spotify"))
        assertNull(parseQuickSpotifyUri("   "))
    }
}
