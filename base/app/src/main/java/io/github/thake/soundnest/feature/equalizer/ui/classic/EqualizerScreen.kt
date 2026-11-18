/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.equalizer.ui.classic

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import io.github.thake.soundnest.feature.equalizer.ui.MaterialEqualizerScreen

/**
 * Settings > Playback > Equalizer (Classic): delegates to the unified Material Design 3 screen.
 */
@Composable
fun EqualizerScreen(navController: NavController) = MaterialEqualizerScreen(navController)
