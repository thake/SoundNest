/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.equalizer.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.preferences.SettingsEntry
import io.github.thake.soundnest.core.ui.classic.AppMuted
import io.github.thake.soundnest.core.ui.classic.AppText
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveGroupRow
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveGroupScope
import io.github.thake.soundnest.core.ui.navigation.Routes
import io.github.thake.soundnest.feature.equalizer.ui.eqPresetLabel
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy

/**
 * Kid Settings rows (Classic & Immersive) and Settings Search entry for the local-player Equalizer,
 * gated on [EqualizerSettings.isAvailable].
 */
object EqualizerSettingsUi {
    val settingsEntries: List<SettingsEntry> = listOf(
        SettingsEntry(
            title = R.string.equalizer_title,
            detail = R.string.equalizer_settings_detail_soundnest,
            classicPlace = "0",
            immersivePage = "Playback",
            isVisible = EqualizerSettings::isAvailable,
        ),
    )

    @Composable
    fun ClassicPlaybackRow(navController: NavController, policy: ParentalPolicy) {
        if (!EqualizerSettings.isAvailable(policy)) return
        Column(
            Modifier
                .fillMaxWidth()
                .clickable { navController.navigate(Routes.Equalizer.route) }
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text(stringResource(R.string.equalizer_title), color = AppText, fontSize = 16.sp)
            Text(stringResource(R.string.equalizer_settings_detail_soundnest), color = AppMuted, fontSize = 12.sp)
        }
    }

    internal fun ImmersiveGroupScope.immersivePlaybackRow(navController: NavController, policy: ParentalPolicy) {
        if (!EqualizerSettings.isAvailable(policy)) return
        row {
            ImmersiveGroupRow(
                stringResource(R.string.equalizer_title),
                detail = stringResource(R.string.equalizer_settings_detail_soundnest),
                value = if (EqualizerSettings.enabled) stringResource(eqPresetLabel(EqualizerSettings.preset)) else null,
                chevron = true,
                onClick = { navController.navigate(Routes.Equalizer.route) },
            )
        }
    }
}
