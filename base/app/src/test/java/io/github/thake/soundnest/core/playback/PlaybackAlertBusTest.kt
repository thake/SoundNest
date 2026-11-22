/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.core.playback

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackAlertBusTest {

    @Test
    fun `emitResource delivers Resource alert to collectors`() = runTest {
        var received: PlaybackAlert? = null
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            received = PlaybackAlertBus.alerts.first()
        }

        assertTrue(PlaybackAlertBus.emitResource(resId = 1234, longDuration = false))
        job.join()

        assertEquals(PlaybackAlert.Resource(resId = 1234, longDuration = false), received)
    }

    @Test
    fun `emitFormatted delivers FormattedResource alert to collectors`() = runTest {
        var received: PlaybackAlert? = null
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            received = PlaybackAlertBus.alerts.first()
        }

        assertTrue(PlaybackAlertBus.emitFormatted(resId = 5678, arg = "Living Room", longDuration = true))
        job.join()

        assertEquals(
            PlaybackAlert.FormattedResource(resId = 5678, arg = "Living Room", longDuration = true),
            received,
        )
    }
}
