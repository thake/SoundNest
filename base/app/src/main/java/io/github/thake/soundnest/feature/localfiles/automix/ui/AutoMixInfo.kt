/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.localfiles.automix.ui

import io.github.thake.soundnest.R
import io.github.thake.soundnest.feature.localfiles.automix.engine.AutoMix

/**
 * The sentence saying why the next transition is not a mix ([AutoMix.whyNot]), for the Transitions
 * page and "Stats for nerds"; null when it is mixed, still being analysed, or AutoMix is off.
 * The player shows nothing more than the missing mix zone on its seek bar.
 */
fun autoMixReasonText(reason: AutoMix.Reason?): Int? = when (reason) {
    null, AutoMix.Reason.Off -> null
    AutoMix.Reason.SpotifyStream -> R.string.automix_reason_spotify
    AutoMix.Reason.Cast -> R.string.automix_reason_cast
    AutoMix.Reason.Podcast -> R.string.automix_reason_podcast
    AutoMix.Reason.Album -> R.string.automix_reason_album
    AutoMix.Reason.MobileData -> R.string.automix_reason_mobile_data
    AutoMix.Reason.Frugal -> R.string.automix_reason_frugal
    AutoMix.Reason.NotAnalyzed -> R.string.automix_reason_not_analyzed
}
