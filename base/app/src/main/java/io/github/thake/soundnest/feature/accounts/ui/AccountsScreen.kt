/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.accounts.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.ui.settings.M3SettingsScaffold
import io.github.thake.soundnest.feature.accounts.settings.accountsManagementSections

/**
 * Full-screen Material Design 3 Switch Account page for both Classic and Immersive looks,
 * displaying Spotify profiles and account actions without stacking modal bottom sheets.
 */
@Composable
fun AccountsScreen(navController: NavController) {
    val context = LocalContext.current
    M3SettingsScaffold(
        title = stringResource(R.string.accounts_title),
        onBack = { navController.popBackStack() },
    ) { palette ->
        accountsManagementSections(
            palette = palette,
            context = context,
            onLeave = { navController.popBackStack() },
        )
    }
}
