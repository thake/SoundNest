/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.radio.settings

import android.content.Context
import androidx.compose.ui.res.stringResource
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.ui.settings.M3SectionScope
import io.github.thake.soundnest.core.ui.settings.M3SettingsPalette
import io.github.thake.soundnest.core.ui.settings.M3SettingsSwitchRow
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore

/**
 * Parent Controls Dashboard toggle row for Live Internet Radio.
 */
fun M3SectionScope.m3RadioParentRow(
    context: Context,
    policy: ParentalPolicy,
    palette: M3SettingsPalette,
) {
    row {
        M3SettingsSwitchRow(
            title = stringResource(R.string.parent_radio_title),
            detail = stringResource(R.string.parent_radio_subtitle),
            checked = policy.radio.enabled,
            palette = palette,
            onCheckedChange = { enabled ->
                ParentalPolicyStore.update(context) { it.copy(radio = it.radio.copy(enabled = enabled)) }
            },
        )
    }
}
