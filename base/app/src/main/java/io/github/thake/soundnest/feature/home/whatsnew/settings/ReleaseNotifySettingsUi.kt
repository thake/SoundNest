/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.home.whatsnew.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.preferences.SettingsEntry
import io.github.thake.soundnest.core.ui.classic.OptionRow
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveGroupScope
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveSwitchRow
import io.github.thake.soundnest.feature.parental.filter.HomeSectionFilter
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy

/**
 * Classic & Immersive Kid Settings rows ("Release Notifications") and Settings Search entry,
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

    @Composable
    fun ClassicLibraryRow(policy: ParentalPolicy) {
        if (!HomeSectionFilter.isWhatsNewAllowed(policy)) return
        val context = LocalContext.current
        OptionRow(
            label = stringResource(R.string.settings_release_notify),
            detail = stringResource(R.string.settings_release_notify_detail),
            checked = NewReleaseNotifier.on,
            onChecked = { NewReleaseNotifier.setOn(context, it) },
        )
    }

    internal fun ImmersiveGroupScope.immersiveLibraryRow(context: Context, policy: ParentalPolicy) {
        if (!HomeSectionFilter.isWhatsNewAllowed(policy)) return
        row {
            ImmersiveSwitchRow(
                stringResource(R.string.settings_release_notify),
                checked = NewReleaseNotifier.on,
                onChecked = { NewReleaseNotifier.setOn(context, it) },
                detail = stringResource(R.string.settings_release_notify_detail),
            )
        }
    }
}
