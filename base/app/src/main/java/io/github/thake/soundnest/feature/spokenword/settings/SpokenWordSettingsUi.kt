/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.spokenword.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.preferences.SettingsEntry
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveGroupScope
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveSwitchRow
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.core.ui.classic.OptionRow

/**
 * Kid Settings rows (Classic & Immersive) and Settings Search entry for spoken-word headset skip,
 * gated on `policy.spokenWord.allowPodcasts || policy.spokenWord.allowAudiobooks`.
 */
object SpokenWordSettingsUi {
    val settingsEntries: List<SettingsEntry> = listOf(
        SettingsEntry(
            title = R.string.settings_headset_skip_title,
            detail = R.string.settings_headset_skip_detail,
            classicPlace = "0",
            immersivePage = "Playback",
            isVisible = { it.spokenWord.allowPodcasts || it.spokenWord.allowAudiobooks },
        ),
    )

    @Composable
    fun ClassicControlsRow(policy: ParentalPolicy) {
        if (!policy.spokenWord.allowPodcasts && !policy.spokenWord.allowAudiobooks) return
        val ctx = LocalContext.current
        var headsetSkip by remember { mutableStateOf(SpokenWordSettings.skipsOnEpisodes(ctx)) }
        OptionRow(
            label = stringResource(R.string.settings_headset_skip_title),
            detail = stringResource(R.string.settings_headset_skip_detail),
            checked = headsetSkip,
            onChecked = {
                headsetSkip = it
                SpokenWordSettings.setSkipsOnEpisodes(ctx, it)
            },
        )
    }

    internal fun ImmersiveGroupScope.immersiveControlsRow(context: Context, policy: ParentalPolicy) {
        if (!policy.spokenWord.allowPodcasts && !policy.spokenWord.allowAudiobooks) return
        row {
            var headsetSkip by remember { mutableStateOf(SpokenWordSettings.skipsOnEpisodes(context)) }
            ImmersiveSwitchRow(
                stringResource(R.string.settings_headset_skip_title),
                checked = headsetSkip,
                onChecked = {
                    headsetSkip = it
                    SpokenWordSettings.setSkipsOnEpisodes(context, it)
                },
                detail = stringResource(R.string.settings_headset_skip_detail),
            )
        }
    }
}
