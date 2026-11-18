/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.settings.ui.immersive

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import io.github.thake.soundnest.feature.settings.ui.MaterialSettingsScreen

/** Where the Settings screen is: the root hub of categories, one category, or a choice inside it. */
enum class SettingsPage { Root, Playback, Lyrics, StreamingWifi, StreamingCell, Crossfade, Storage, Data, Display, Look, Rotation, Language, LyricsLanguage, App, Accounts, Library, LocalAndRadio }

/**
 * Settings in the Immersive look: delegates to the unified Material Design 3 Settings hub & subpages.
 */
@Composable
fun ImmersiveSettingsScreen(
    navController: NavController,
    initialPage: SettingsPage = SettingsPage.Root,
) = MaterialSettingsScreen(
    navController = navController,
    initialPage = initialPage,
)
