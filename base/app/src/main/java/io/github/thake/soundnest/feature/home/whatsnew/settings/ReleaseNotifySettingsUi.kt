/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.home.whatsnew.settings

import android.content.Context
import androidx.compose.ui.res.stringResource
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.preferences.SettingsEntry
import io.github.thake.soundnest.core.ui.settings.M3SectionScope
import io.github.thake.soundnest.core.ui.settings.M3SettingsPalette
import io.github.thake.soundnest.core.ui.settings.M3SettingsSwitchRow
import io.github.thake.soundnest.feature.parental.filter.HomeSectionFilter
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy

/**
 * Settings row ("Release Notifications") and Settings Search entry,
 * gated by [HomeSectionFilter.isWhatsNewAllowed].
 */
object ReleaseNotifySettingsUi {
    val settingsEntries: List<SettingsEntry> = listOf(
        SettingsEntry(
            title = R.string.settings_release_notify,
            detail = R.string.settings_release_notify_detail,
            classicPlace = "6",
            immersivePage = "Library",
            isVisible = { HomeSectionFilter.isWhatsNewAllowed(it) },
        ),
    )

    fun M3SectionScope.m3LibraryRow(
        context: Context,
        policy: ParentalPolicy,
        palette: M3SettingsPalette,
    ) {
        if (!HomeSectionFilter.isWhatsNewAllowed(policy)) return
        row {
            M3SettingsSwitchRow(
                title = stringResource(R.string.settings_release_notify),
                detail = stringResource(R.string.settings_release_notify_detail),
                checked = NewReleaseNotifier.on,
                palette = palette,
                onCheckedChange = { NewReleaseNotifier.setOn(context, it) },
            )
        }
    }
}
