/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.core.playback

import android.app.Application
import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WebPlayerSelfHealerTest {

    private lateinit var context: Context
    private var nowMs = 1_000_000L
    private var reloadCount = 0

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        WebPlayerSelfHealer.resetForTests()
        nowMs = 1_000_000L
        reloadCount = 0

        WebPlayerSelfHealer.clockMs = { nowMs }
        WebPlayerSelfHealer.isSignedIn = { true }
        WebPlayerSelfHealer.isParentController = { false }
        WebPlayerSelfHealer.isDataSaverActive = { false }
        WebPlayerSelfHealer.isPlayingNow = { false }
        WebPlayerSelfHealer.isApiReady = { true }
        WebPlayerSelfHealer.isNetworkValidated = { true }
        WebPlayerSelfHealer.hasPlayerState = { false }
        WebPlayerSelfHealer.listDevicesCount = { 0 }
        WebPlayerSelfHealer.probeAudioInfoOk = { false }
        WebPlayerSelfHealer.triggerReload = { reloadCount++ }
    }

    @After
    fun tearDown() {
        WebPlayerSelfHealer.resetForTests()
    }

    @Test
    fun reloadsWhenWedgedWithEmptyDevicesNullStateAndHangingAudioInfo() = runBlocking {
        val healed = WebPlayerSelfHealer.checkAndHealIfWedged(context, "play_not_taken")
        assertTrue(healed)
        assertEquals(1, reloadCount)
    }

    @Test
    fun skipsReloadWhenHealthyOrOfflineOrPlayingOrDataSaver() = runBlocking {
        // Healthy: has devices
        WebPlayerSelfHealer.listDevicesCount = { 3 }
        assertFalse(WebPlayerSelfHealer.checkAndHealIfWedged(context, "play_not_taken"))

        // Healthy: has player state
        WebPlayerSelfHealer.listDevicesCount = { 0 }
        WebPlayerSelfHealer.hasPlayerState = { true }
        assertFalse(WebPlayerSelfHealer.checkAndHealIfWedged(context, "play_not_taken"))

        // Offline
        WebPlayerSelfHealer.hasPlayerState = { false }
        WebPlayerSelfHealer.isNetworkValidated = { false }
        assertFalse(WebPlayerSelfHealer.checkAndHealIfWedged(context, "play_not_taken"))

        // Actively playing
        WebPlayerSelfHealer.isNetworkValidated = { true }
        WebPlayerSelfHealer.isPlayingNow = { true }
        assertFalse(WebPlayerSelfHealer.checkAndHealIfWedged(context, "play_not_taken"))

        // Data saver active
        WebPlayerSelfHealer.isPlayingNow = { false }
        WebPlayerSelfHealer.isDataSaverActive = { true }
        assertFalse(WebPlayerSelfHealer.checkAndHealIfWedged(context, "play_not_taken"))

        // Foreground resume when audioInfo still answers quickly -> alive, don't reload speculatively
        WebPlayerSelfHealer.isDataSaverActive = { false }
        WebPlayerSelfHealer.probeAudioInfoOk = { true }
        assertFalse(WebPlayerSelfHealer.checkAndHealIfWedged(context, "foreground_resume"))

        assertEquals(0, reloadCount)
    }

    @Test
    fun enforcesCooldownAndWindowBudgetAndResetsOnHealthy() = runBlocking {
        // 1st reload succeeds
        assertTrue(WebPlayerSelfHealer.checkAndHealIfWedged(context, "transport_timeout"))
        assertEquals(1, reloadCount)

        // Within 60s cooldown -> blocked
        nowMs += 30_000L
        assertFalse(WebPlayerSelfHealer.checkAndHealIfWedged(context, "transport_timeout"))
        assertEquals(1, reloadCount)

        // 2nd reload after cooldown
        nowMs += 35_000L
        assertTrue(WebPlayerSelfHealer.checkAndHealIfWedged(context, "transport_timeout"))
        assertEquals(2, reloadCount)

        // 3rd reload after cooldown
        nowMs += 65_000L
        assertTrue(WebPlayerSelfHealer.checkAndHealIfWedged(context, "transport_timeout"))
        assertEquals(3, reloadCount)

        // 4th reload within 10-minute window -> blocked by budget cap
        nowMs += 65_000L
        assertFalse(WebPlayerSelfHealer.checkAndHealIfWedged(context, "transport_timeout"))
        assertEquals(3, reloadCount)

        // Once healthy state is observed, budget resets
        WebPlayerSelfHealer.recordHealthy()
        assertTrue(WebPlayerSelfHealer.checkAndHealIfWedged(context, "transport_timeout"))
        assertEquals(4, reloadCount)
    }

    @Test
    fun retriesFailedInitialLoadImmediatelyOnForegroundAndHeal() = runBlocking {
        var retried = 0
        WebPlayerSelfHealer.isApiReady = { false }
        WebPlayerSelfHealer.triggerRetryFailedLoad = { retried++; true }

        WebPlayerSelfHealer.onForeground(context)
        assertEquals(1, retried)

        assertTrue(WebPlayerSelfHealer.checkAndHealIfWedged(context, "transport_timeout"))
        assertEquals(2, retried)
        assertEquals(0, reloadCount)
    }
}
