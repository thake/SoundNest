/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.radio.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.thake.soundnest.R
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.feature.parental.ui.PolicyToggleRow

/**
 * Parent Controls Dashboard toggle row for Live Internet Radio.
 */
@Composable
fun RadioParentSettingsRow(policy: ParentalPolicy) {
    val context = LocalContext.current
    PolicyToggleRow(
        title = stringResource(R.string.parent_radio_title),
        subtitle = stringResource(R.string.parent_radio_subtitle),
        checked = policy.radio.enabled,
    ) { enabled ->
        ParentalPolicyStore.update(context) { it.copy(radio = it.radio.copy(enabled = enabled)) }
    }
}
