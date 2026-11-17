/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.player.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.thake.soundnest.feature.parental.settings.FilterMode
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.feature.parental.ui.PolicyToggleRow

/**
 * Parent Controls Dashboard toggle row for Autoplay, Smart Shuffle & Radio.
 */
@Composable
fun PlaybackParentSettingsRow(policy: ParentalPolicy) {
    val context = LocalContext.current
    PolicyToggleRow(
        title = "Autoplay, Smart Shuffle & Radio",
        subtitle = if (policy.filterMode == FilterMode.STRICT_ALLOWLIST) {
            "Automatically disabled in Strict Allowlist mode"
        } else {
            "Allow endless autoplay, Smart Shuffle, Song/Artist Radio, and Browse Categories"
        },
        checked = policy.effectiveAllowAutoplayAndRadio,
        enabled = policy.filterMode != FilterMode.STRICT_ALLOWLIST,
    ) { allow ->
        ParentalPolicyStore.update(context) {
            it.copy(playback = it.playback.copy(allowAutoplayAndRadio = allow))
        }
    }
}
