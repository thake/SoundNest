/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.speakers.jam.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.ui.classic.AppMuted
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.feature.parental.ui.PolicyToggleRow

/**
 * Parent Controls Dashboard toggle row + Home Wi-Fi note for Spotify Jam.
 */
@Composable
fun JamParentSettingsRow(policy: ParentalPolicy) {
    val context = LocalContext.current
    PolicyToggleRow(
        title = stringResource(R.string.parent_jam_title),
        subtitle = stringResource(R.string.parent_jam_subtitle),
        checked = policy.speakers.jam.enabled,
    ) { enabled ->
        ParentalPolicyStore.update(context) {
            it.copy(
                speakers = it.speakers.copy(
                    jam = it.speakers.jam.copy(enabled = enabled),
                ),
            )
        }
    }
    if (policy.speakers.jam.enabled && policy.speakers.requireTrustedNetwork) {
        Text(
            text = stringResource(R.string.parent_jam_trusted_wifi_note),
            color = AppMuted,
            fontSize = 11.sp,
        )
    }
}
