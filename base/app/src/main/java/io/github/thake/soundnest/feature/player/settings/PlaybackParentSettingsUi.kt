/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.player.settings

import android.content.Context
import androidx.compose.ui.res.stringResource
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.ui.settings.M3SectionScope
import io.github.thake.soundnest.core.ui.settings.M3SettingsPalette
import io.github.thake.soundnest.core.ui.settings.M3SettingsSwitchRow
import io.github.thake.soundnest.feature.parental.settings.FilterMode
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore

/**
 * Parent Controls Dashboard toggle row for Autoplay, Smart Shuffle & Radio.
 */
fun M3SectionScope.m3PlaybackParentRow(
    context: Context,
    policy: ParentalPolicy,
    palette: M3SettingsPalette,
) {
    row {
        M3SettingsSwitchRow(
            title = stringResource(R.string.parent_autoplay_title),
            detail = if (policy.filterMode == FilterMode.STRICT_ALLOWLIST) {
                stringResource(R.string.parent_autoplay_strict_disabled)
            } else {
                stringResource(R.string.parent_autoplay_subtitle)
            },
            checked = policy.effectiveAllowAutoplayAndRadio,
            enabled = policy.filterMode != FilterMode.STRICT_ALLOWLIST,
            palette = palette,
            onCheckedChange = { allow ->
                ParentalPolicyStore.update(context) {
                    it.copy(playback = it.playback.copy(allowAutoplayAndRadio = allow))
                }
            },
        )
    }
}
