/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.spokenword.settings

import android.content.Context
import io.github.thake.soundnest.host.android.PlaybackService

/**
 * Headset skip-button behavior on spoken-word audio (podcasts and audiobooks), delegating to
 * [PlaybackService.skipsOnEpisodes] / [PlaybackService.setSkipsOnEpisodes].
 */
object SpokenWordSettings {
    fun skipsOnEpisodes(context: Context): Boolean = PlaybackService.skipsOnEpisodes(context)

    fun setSkipsOnEpisodes(context: Context, value: Boolean) {
        PlaybackService.setSkipsOnEpisodes(context, value)
    }
}
