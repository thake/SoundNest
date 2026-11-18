/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.settings.ui.classic

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import io.github.thake.soundnest.feature.settings.ui.MaterialSettingsScreen
import io.github.thake.soundnest.feature.settings.ui.immersive.SettingsPage

/**
 * Settings in the Classic look: delegates to the unified Material Design 3 Settings hub & subpages,
 * mapping legacy [initialTab] / [initialSub] arguments for callers and screenshot suites.
 */
@Composable
fun SettingsScreen(
    navController: NavController,
    initialTab: Int = -1,
    initialSub: String? = null,
) {
    val page = when {
        initialSub == "transitions" -> SettingsPage.Crossfade
        initialTab == 0 -> SettingsPage.Playback
        initialTab == 1 -> SettingsPage.Lyrics
        initialTab == 2 -> SettingsPage.Storage
        initialTab == 3 -> SettingsPage.Display
        initialTab == 4 -> SettingsPage.App
        initialTab == 5 -> SettingsPage.Accounts
        initialTab == 6 -> SettingsPage.Library
        initialTab == 7 -> SettingsPage.LocalAndRadio
        else -> SettingsPage.Root
    }
    MaterialSettingsScreen(
        navController = navController,
        initialPage = page,
    )
}
