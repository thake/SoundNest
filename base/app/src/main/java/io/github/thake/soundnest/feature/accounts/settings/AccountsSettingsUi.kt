/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.accounts.settings

import android.content.Context
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.core.preferences.SettingsEntry
import io.github.thake.soundnest.core.ui.settings.M3SectionBlock
import io.github.thake.soundnest.core.ui.settings.M3SettingsNavRow
import io.github.thake.soundnest.core.ui.settings.M3SettingsPalette
import io.github.thake.soundnest.core.ui.settings.M3SettingsScaffold
import io.github.thake.soundnest.core.ui.settings.m3SettingsSection
import io.github.thake.soundnest.feature.accounts.session.SessionActions
import io.github.thake.soundnest.feature.accounts.ui.AccountBrowser
import io.github.thake.soundnest.feature.accounts.ui.AccountsList
import io.github.thake.soundnest.feature.parental.security.AppSurface
import io.github.thake.soundnest.feature.parental.security.ParentSecurityManager
import io.github.thake.soundnest.host.android.SoundNestRuntime

/**
 * PIN-protected Material Design 3 Accounts management sections shared by Settings > Accounts and
 * the Menu > Switch account page, plus Settings Search entries.
 */
object AccountsSettingsUi {
    val settingsEntries: List<SettingsEntry> = listOf(
        SettingsEntry(
            title = R.string.accounts_title,
            classicPlace = "5",
            immersivePage = "Accounts",
        ),
        SettingsEntry(
            title = R.string.account_page_title,
            detail = R.string.account_page_detail,
            classicPlace = "5",
            immersivePage = "Accounts",
        ),
    )

    @Composable
    fun M3AccountsPage(back: () -> Unit) {
        val ctx = LocalContext.current
        val secTick by ParentSecurityManager.sessionTick.collectAsStateWithLifecycle()
        if (!ParentSecurityManager.canAccessWithoutPrompt(ctx, AppSurface.ACCOUNTS_AND_LOGOUT) && secTick >= 0L) {
            LaunchedEffect(secTick) { back() }
            return
        }
        M3SettingsScaffold(
            title = stringResource(R.string.accounts_title),
            onBack = back,
        ) { palette ->
            accountsManagementSections(
                palette = palette,
                context = ctx,
                onLeave = back,
            )
        }
    }
}

/**
 * Shared Material Design 3 sections for Spotify profiles (active, saved, add account) and
 * account actions (Spotify account page + destructive log out).
 */
fun LazyListScope.accountsManagementSections(
    palette: M3SettingsPalette,
    context: Context,
    onLeave: () -> Unit,
) {
    val multiProfile = SoundNestRuntime.isStarted && SoundNestRuntime.host.multiProfile
    item(key = "accounts:profiles", contentType = "m3:accounts-profiles") {
        M3SectionBlock(
            palette = palette,
            header = stringResource(R.string.accounts_section_profiles),
            footer = stringResource(if (multiProfile) R.string.accounts_footer else R.string.accounts_single_footer),
        ) {
            AccountsList(onLeave = onLeave, showFooter = false)
        }
    }

    m3SettingsSection(
        key = "accounts:actions",
        palette = palette,
        header = context.getString(R.string.accounts_section_actions),
        hasLeadingIconBadges = true,
    ) {
        row {
            M3SettingsNavRow(
                icon = Icons.Rounded.ManageAccounts,
                title = stringResource(R.string.account_page_title),
                detail = stringResource(R.string.account_page_detail),
                palette = palette,
                externalLink = true,
                onClick = {
                    ParentSecurityManager.runProtected(context, AppSurface.ACCOUNTS_AND_LOGOUT) {
                        onLeave()
                        AccountBrowser.open(context, AccountBrowser.ACCOUNT_URL)
                    }
                },
            )
        }
        row {
            M3SettingsNavRow(
                icon = Icons.AutoMirrored.Rounded.Logout,
                title = stringResource(R.string.connect_device_menu_logout),
                palette = palette,
                destructive = true,
                showChevron = false,
                onClick = {
                    ParentSecurityManager.runProtected(context, AppSurface.ACCOUNTS_AND_LOGOUT) {
                        SessionActions.logout(Graph.app)
                    }
                },
            )
        }
    }
}
