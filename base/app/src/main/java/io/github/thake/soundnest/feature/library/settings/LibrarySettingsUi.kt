/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.library.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.preferences.SettingsEntry
import io.github.thake.soundnest.core.ui.settings.M3SettingsScaffold
import io.github.thake.soundnest.core.ui.settings.m3SettingsSection
import io.github.thake.soundnest.feature.home.whatsnew.settings.ReleaseNotifySettingsUi
import io.github.thake.soundnest.feature.library.hiddensongs.settings.HiddenSongsSettingsUi
import io.github.thake.soundnest.feature.localfiles.settings.LocalFilesSettingsUi
import io.github.thake.soundnest.feature.parental.filter.HomeSectionFilter
import io.github.thake.soundnest.feature.parental.settings.rememberParentalPolicy
import io.github.thake.soundnest.feature.radio.settings.RadioSettingsUi

/**
 * Unified Material Design 3 Library settings page composing `home.whatsnew`, `localfiles`,
 * `radio`, and `library.hiddensongs` rows + Settings Search entries.
 */
object LibrarySettingsUi {
    val settingsEntries: List<SettingsEntry> = listOf(
        *ReleaseNotifySettingsUi.settingsEntries.toTypedArray(),
        *RadioSettingsUi.settingsEntries.toTypedArray(),
        *LocalFilesSettingsUi.settingsEntries.toTypedArray(),
        *HiddenSongsSettingsUi.settingsEntries.toTypedArray(),
        SettingsEntry(
            title = R.string.settings_section_library,
            classicPlace = "6",
            immersivePage = "Library",
        ),
    )

    @Composable
    fun M3LibraryPage(navController: NavController, back: () -> Unit) {
        val ctx = LocalContext.current
        val policy = rememberParentalPolicy()
        val hasPrefs = HomeSectionFilter.isWhatsNewAllowed(policy)

        M3SettingsScaffold(
            title = stringResource(R.string.settings_section_library),
            onBack = back,
        ) { palette ->
            if (hasPrefs) {
                m3SettingsSection(
                    key = "library:prefs",
                    palette = palette,
                    header = ctx.getString(R.string.settings_library_section_preferences),
                ) {
                    with(ReleaseNotifySettingsUi) { m3LibraryRow(ctx, policy, palette) }
                }
            }
            m3SettingsSection(
                key = "library:manage",
                palette = palette,
                header = ctx.getString(R.string.settings_library_section_manage),
                hasLeadingIconBadges = true,
            ) {
                with(HiddenSongsSettingsUi) { m3LibraryRow(navController, palette) }
            }
        }
    }
}
