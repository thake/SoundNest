/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.library.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.preferences.SettingsEntry
import io.github.thake.soundnest.core.ui.classic.Section
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersivePage
import io.github.thake.soundnest.core.ui.immersive.pages.immersiveGroup
import io.github.thake.soundnest.feature.home.whatsnew.settings.ReleaseNotifySettingsUi
import io.github.thake.soundnest.feature.library.hiddensongs.settings.HiddenSongsSettingsUi
import io.github.thake.soundnest.feature.localfiles.settings.LocalFilesSettingsUi
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.rememberParentalPolicy
import io.github.thake.soundnest.feature.radio.settings.RadioSettingsUi

/**
 * Classic `TAB_LIBRARY` & Immersive `LibraryPage` composing `home.whatsnew`, `localfiles`,
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
    fun ClassicLibraryTabContent(navController: NavController, policy: ParentalPolicy) {
        Section(R.string.settings_section_library)
        ReleaseNotifySettingsUi.ClassicLibraryRow(policy)
        LocalFilesSettingsUi.ClassicLibraryRow(policy)
        RadioSettingsUi.ClassicLibraryRow(policy)
        HiddenSongsSettingsUi.ClassicLibraryRow(navController)
    }

    @Composable
    fun ImmersiveLibraryPage(navController: NavController, back: () -> Unit) {
        val ctx = LocalContext.current
        val policy = rememberParentalPolicy()
        ImmersivePage(title = stringResource(R.string.settings_section_library), onBack = back) {
            immersiveGroup("library") {
                with(ReleaseNotifySettingsUi) {
                    immersiveLibraryRow(ctx, policy)
                }
                with(LocalFilesSettingsUi) {
                    immersiveLibraryRow(ctx, policy)
                }
                with(RadioSettingsUi) {
                    immersiveLibraryRow(ctx, policy)
                }
                with(HiddenSongsSettingsUi) {
                    immersiveLibraryRow(ctx, navController)
                }
            }
        }
    }
}
