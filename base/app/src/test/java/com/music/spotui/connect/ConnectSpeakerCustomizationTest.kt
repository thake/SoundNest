/* SPDX-License-Identifier: GPL-3.0-only */
package com.music.spotui.connect

import android.content.Context
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConnectSpeakerCustomizationTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `parent custom speaker nickname overrides raw Connect label while keeping rawName intact`() {
        CustomSpeakerNames.clearAll(context)
        CustomSpeakerNames.setCustomName(context, "echo-kids-1", "Leo's Bedroom Alexa")

        assertEquals("Leo's Bedroom Alexa", CustomSpeakerNames.resolve(context, "echo-kids-1", "Echo Dot-49P"))
        assertEquals("Kitchen Speaker", CustomSpeakerNames.resolve(context, "sonos-2", "Kitchen Speaker"))
        assertEquals(mapOf("echo-kids-1" to "Leo's Bedroom Alexa"), CustomSpeakerNames.all(context))

        // Blank nickname clears override and restores Spotify's resolved label
        CustomSpeakerNames.setCustomName(context, "echo-kids-1", "   ")
        assertEquals("Echo Dot-49P", CustomSpeakerNames.resolve(context, "echo-kids-1", "Echo Dot-49P"))
    }

    @Test
    fun `remote sleep timer expiration sends fallback pause and direct Connect pause when speaker stays active`() = runTest {
        var playerPauseCalled = 0
        var directDevicePauseId: String? = null

        RemoteSleepPauseCoordinator.finishRemoteSleepFade(
            remoteDeviceId = "echo-kids-1",
            pauseSlowlySucceeded = false,
            remoteStillPlaying = { true },
            sendPlayerPause = { playerPauseCalled++ },
            sendDirectDevicePause = { id -> directDevicePauseId = id; true },
        )

        assertEquals(1, playerPauseCalled)
        assertEquals("echo-kids-1", directDevicePauseId)
    }

    @Test
    fun `remote sleep timer expiration skips redundant pause when pauseSlowly already paused speaker`() = runTest {
        var playerPauseCalled = 0
        var directCalled = false

        RemoteSleepPauseCoordinator.finishRemoteSleepFade(
            remoteDeviceId = "echo-kids-1",
            pauseSlowlySucceeded = true,
            remoteStillPlaying = { false },
            sendPlayerPause = { playerPauseCalled++ },
            sendDirectDevicePause = { directCalled = true; true },
        )

        assertEquals(0, playerPauseCalled)
        assertTrue(!directCalled)
    }
}
