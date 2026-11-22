/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.core.playback

import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Typed user-facing playback alerts emitted by playback controllers ([SongPlayer], [LocalPlayer])
 * and rendered by the UI layer (see `PlaybackAlertHost` in `App.kt`), keeping playback logic
 * decoupled from Android's `Toast` widget.
 */
sealed interface PlaybackAlert {
    val longDuration: Boolean

    data class Resource(
        @StringRes val resId: Int,
        override val longDuration: Boolean = true,
    ) : PlaybackAlert

    data class FormattedResource(
        @StringRes val resId: Int,
        val arg: String,
        override val longDuration: Boolean = true,
    ) : PlaybackAlert
}

object PlaybackAlertBus {
    private val _alerts = MutableSharedFlow<PlaybackAlert>(extraBufferCapacity = 16)
    val alerts: SharedFlow<PlaybackAlert> = _alerts.asSharedFlow()

    fun emit(alert: PlaybackAlert): Boolean = _alerts.tryEmit(alert)

    fun emitResource(@StringRes resId: Int, longDuration: Boolean = true): Boolean =
        emit(PlaybackAlert.Resource(resId = resId, longDuration = longDuration))

    fun emitFormatted(@StringRes resId: Int, arg: String, longDuration: Boolean = true): Boolean =
        emit(PlaybackAlert.FormattedResource(resId = resId, arg = arg, longDuration = longDuration))
}
