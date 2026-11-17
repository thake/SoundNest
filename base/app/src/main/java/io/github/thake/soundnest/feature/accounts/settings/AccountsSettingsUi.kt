/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.accounts.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.core.preferences.SettingsEntry
import io.github.thake.soundnest.core.ui.classic.AppText
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveGroupRow
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersivePage
import io.github.thake.soundnest.core.ui.immersive.pages.immersiveGroup
import io.github.thake.soundnest.feature.accounts.session.SessionActions
import io.github.thake.soundnest.feature.accounts.ui.AccountsList
import io.github.thake.soundnest.feature.parental.security.AppSurface
import io.github.thake.soundnest.feature.parental.security.ParentSecurityManager
import io.github.thake.soundnest.core.ui.classic.Section

/**
 * PIN-protected Classic & Immersive Settings UI surfaces and Settings Search entry for Accounts.
 */
object AccountsSettingsUi {
    val settingsEntries: List<SettingsEntry> = listOf(
        SettingsEntry(
            title = R.string.accounts_title,
            classicPlace = "5",
            immersivePage = "Accounts",
        ),
    )

    @Composable
    fun ClassicAccountsTabContent(onLockedRedirect: () -> Unit) {
        val ctx = LocalContext.current
        val secTick by ParentSecurityManager.sessionTick.collectAsStateWithLifecycle()
        if (!ParentSecurityManager.canAccessWithoutPrompt(ctx, AppSurface.ACCOUNTS_AND_LOGOUT) && secTick >= 0L) {
            LaunchedEffect(secTick) { onLockedRedirect() }
        } else {
            Section(R.string.accounts_title)
            AccountsList()
            Text(
                stringResource(R.string.connect_device_menu_logout),
                color = AppText,
                fontSize = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        ParentSecurityManager.runProtected(
                            ctx,
                            AppSurface.ACCOUNTS_AND_LOGOUT,
                        ) { SessionActions.logout(Graph.app) }
                    }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            )
        }
    }

    @Composable
    fun ImmersiveAccountsPage(back: () -> Unit) {
        val ctx = LocalContext.current
        val secTick by ParentSecurityManager.sessionTick.collectAsStateWithLifecycle()
        if (!ParentSecurityManager.canAccessWithoutPrompt(ctx, AppSurface.ACCOUNTS_AND_LOGOUT) && secTick >= 0L) {
            LaunchedEffect(secTick) { back() }
            return
        }
        ImmersivePage(title = stringResource(R.string.accounts_title), onBack = back) {
            immersiveGroup("accounts") {
                row { AccountsList() }
            }
            immersiveGroup("sign-out") {
                row {
                    ImmersiveGroupRow(
                        stringResource(R.string.connect_device_menu_logout),
                        destructive = true,
                        onClick = {
                            ParentSecurityManager.runProtected(
                                ctx,
                                AppSurface.ACCOUNTS_AND_LOGOUT,
                            ) { SessionActions.logout(Graph.app) }
                        },
                    )
                }
            }
        }
    }
}
