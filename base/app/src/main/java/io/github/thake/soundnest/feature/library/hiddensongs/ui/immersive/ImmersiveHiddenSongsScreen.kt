/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.library.hiddensongs.ui.immersive

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import io.github.thake.soundnest.feature.library.hiddensongs.ui.MaterialHiddenSongsScreen

/**
 * Settings > Hidden songs (Immersive): delegates to the unified Material Design 3 screen.
 */
@Composable
fun ImmersiveHiddenSongsScreen(navController: NavController) = MaterialHiddenSongsScreen(navController)
