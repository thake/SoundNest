/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.library.hiddensongs.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.preferences.SettingsEntry
import io.github.thake.soundnest.core.ui.classic.AppMuted
import io.github.thake.soundnest.core.ui.classic.AppText
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveGroupRow
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveGroupScope
import io.github.thake.soundnest.core.ui.navigation.Routes
import io.github.thake.soundnest.feature.parental.security.AppSurface
import io.github.thake.soundnest.feature.parental.security.ParentSecurityManager

/**
 * PIN-protected Kid Settings rows (Classic & Immersive) and Settings Search entry for Hidden Songs.
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

    @Composable
    fun ClassicLibraryRow(navController: NavController) {
        val ctx = LocalContext.current
        Column(
            Modifier
                .fillMaxWidth()
                .clickable {
                    ParentSecurityManager.runProtected(
                        ctx,
                        AppSurface.HIDDEN_SONGS_AND_BANS,
                    ) { navController.navigate(Routes.HiddenSongs.route) }
                }
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text(stringResource(R.string.hidden_songs_title), color = AppText, fontSize = 16.sp)
            Text(stringResource(R.string.hidden_songs_detail), color = AppMuted, fontSize = 12.sp)
        }
    }

    internal fun ImmersiveGroupScope.immersiveLibraryRow(ctx: Context, navController: NavController) {
        row {
            ImmersiveGroupRow(
                stringResource(R.string.hidden_songs_title),
                detail = stringResource(R.string.hidden_songs_detail),
                chevron = true,
                onClick = {
                    ParentSecurityManager.runProtected(
                        ctx,
                        AppSurface.HIDDEN_SONGS_AND_BANS,
                    ) { navController.navigate(Routes.HiddenSongs.route) }
                },
            )
        }
    }
}
