package io.github.thake.soundnest.core.playback

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioReconnectControllerTest {

    @Test
    fun classifiesRetryableNetworkAnd5xxErrorsOnly() {
        assertTrue(RadioReconnectController.isRetryableNetworkError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, null))
        assertTrue(RadioReconnectController.isRetryableNetworkError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, null))
        assertTrue(RadioReconnectController.isRetryableNetworkError(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, null))
        assertTrue(RadioReconnectController.isRetryableNetworkError(PlaybackException.ERROR_CODE_TIMEOUT, null))
        assertTrue(RadioReconnectController.isRetryableNetworkError(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 503))
        assertTrue(RadioReconnectController.isRetryableNetworkError(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 500))

        assertFalse(RadioReconnectController.isRetryableNetworkError(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 404))
        assertFalse(RadioReconnectController.isRetryableNetworkError(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 403))
        assertFalse(RadioReconnectController.isRetryableNetworkError(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, null))
        assertFalse(RadioReconnectController.isRetryableNetworkError(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, null))
    }

    @Test
    fun backsOffExponentiallyUpToMaxRetriesThenGivesUpAndResets() {
        val controller = RadioReconnectController(maxTries = 5)

        val expected = listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L)
        expected.forEachIndexed { idx, delayMs ->
            val attempt = controller.nextAttempt()
            assertEquals(idx + 1, attempt?.attemptNumber)
            assertEquals(delayMs, attempt?.delayMs)
        }

        // 6th drop exceeds maxTries -> gives up (returns null) and resets counter
        assertNull(controller.nextAttempt())
        assertEquals(0, controller.retries)
    }

    @Test
    fun resetRestoresFullRetryBudget() {
        val controller = RadioReconnectController(maxTries = 5)
        controller.nextAttempt()
        controller.nextAttempt()
        assertEquals(2, controller.retries)

        controller.reset()
        assertEquals(0, controller.retries)
        assertEquals(1, controller.nextAttempt()?.attemptNumber)
    }
}
