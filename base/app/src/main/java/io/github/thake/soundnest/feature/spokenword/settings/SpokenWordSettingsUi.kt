/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.spokenword.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.preferences.SettingsEntry
import io.github.thake.soundnest.core.ui.settings.M3SectionScope
import io.github.thake.soundnest.core.ui.settings.M3SettingsPalette
import io.github.thake.soundnest.core.ui.settings.M3SettingsSwitchRow
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy

/**
 * Settings rows and Settings Search entry for spoken-word headset skip,
 * gated on `policy.spokenWord.allowPodcasts || policy.spokenWord.allowAnyAudiobooks`.
 */
object SpokenWordSettingsUi {
    val settingsEntries: List<SettingsEntry> = listOf(
        SettingsEntry(
            title = R.string.settings_headset_skip_title,
            detail = R.string.settings_headset_skip_detail,
            classicPlace = "0",
            immersivePage = "Playback",
            isVisible = { it.spokenWord.allowPodcasts || it.spokenWord.allowAnyAudiobooks },
        ),
    )

    fun M3SectionScope.m3ControlsRow(
        context: Context,
        policy: ParentalPolicy,
        palette: M3SettingsPalette,
    ) {
        if (!policy.spokenWord.allowPodcasts && !policy.spokenWord.allowAnyAudiobooks) return
        row {
            var headsetSkip by remember { mutableStateOf(SpokenWordSettings.skipsOnEpisodes(context)) }
            M3SettingsSwitchRow(
                title = stringResource(R.string.settings_headset_skip_title),
                detail = stringResource(R.string.settings_headset_skip_detail),
                checked = headsetSkip,
                palette = palette,
                onCheckedChange = {
                    headsetSkip = it
                    SpokenWordSettings.setSkipsOnEpisodes(context, it)
                },
            )
        }
    }
}
