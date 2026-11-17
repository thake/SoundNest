/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.radio.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.preferences.SettingsEntry
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveGroupScope
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveSwitchRow
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.core.ui.classic.OptionRow

/**
 * Kid Settings rows (Classic & Immersive) and Settings Search entry for Live Radio.
 */
object RadioSettingsUi {
    val settingsEntries: List<SettingsEntry> = listOf(
        SettingsEntry(
            title = R.string.live_radio_settings_title,
            detail = R.string.live_radio_settings_detail,
            classicPlace = "6",
            immersivePage = "Library",
            isVisible = { it.radio.enabled },
        ),
    )

    @Composable
    fun ClassicLibraryRow(policy: ParentalPolicy) {
        if (!policy.radio.enabled) return
        val context = LocalContext.current
        OptionRow(
            label = stringResource(R.string.live_radio_settings_title),
            detail = stringResource(R.string.live_radio_settings_detail),
            checked = RadioSettings.on,
            onChecked = { RadioSettings.set(context, it) },
        )
    }

    internal fun ImmersiveGroupScope.immersiveLibraryRow(context: Context, policy: ParentalPolicy) {
        if (!policy.radio.enabled) return
        row {
            ImmersiveSwitchRow(
                stringResource(R.string.live_radio_settings_title),
                checked = RadioSettings.on,
                onChecked = { RadioSettings.set(context, it) },
                detail = stringResource(R.string.live_radio_settings_detail),
            )
        }
    }
}
