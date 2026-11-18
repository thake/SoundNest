/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.library.hiddensongs.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.preferences.SettingsEntry
import io.github.thake.soundnest.core.ui.navigation.Routes
import io.github.thake.soundnest.core.ui.navigation.navigateSingleTop
import io.github.thake.soundnest.core.ui.settings.M3SectionScope
import io.github.thake.soundnest.core.ui.settings.M3SettingsNavRow
import io.github.thake.soundnest.core.ui.settings.M3SettingsPalette

/**
 * Settings row and Settings Search entry for Hidden Songs.
 */
object HiddenSongsSettingsUi {
    val settingsEntries: List<SettingsEntry> = listOf(
        SettingsEntry(
            title = R.string.hidden_songs_title,
            detail = R.string.hidden_songs_detail,
            classicPlace = "6",
            immersivePage = "Library",
        ),
    )

    fun M3SectionScope.m3LibraryRow(
        navController: NavController,
        palette: M3SettingsPalette,
    ) {
        row {
            M3SettingsNavRow(
                icon = Icons.Rounded.VisibilityOff,
                title = stringResource(R.string.hidden_songs_title),
                detail = stringResource(R.string.hidden_songs_detail),
                palette = palette,
                onClick = { navController.navigateSingleTop(Routes.HiddenSongs.route) },
            )
        }
    }
}
