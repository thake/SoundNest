/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.player.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.thake.soundnest.R
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
        title = stringResource(R.string.parent_autoplay_title),
        subtitle = if (policy.filterMode == FilterMode.STRICT_ALLOWLIST) {
            stringResource(R.string.parent_autoplay_strict_disabled)
        } else {
            stringResource(R.string.parent_autoplay_subtitle)
        },
        checked = policy.effectiveAllowAutoplayAndRadio,
        enabled = policy.filterMode != FilterMode.STRICT_ALLOWLIST,
    ) { allow ->
        ParentalPolicyStore.update(context) {
            it.copy(playback = it.playback.copy(allowAutoplayAndRadio = allow))
        }
    }
}
