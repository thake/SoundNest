/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.speakers.jam.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
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
        title = "Spotify Jam",
        subtitle = "Allow starting or joining shared Spotify Jam listening sessions",
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
            text = "Note: Spotify Jam also respects your Trusted Home Wi-Fi boundary when Require Trusted Home Network is enabled.",
            color = AppMuted,
            fontSize = 11.sp,
        )
    }
}
