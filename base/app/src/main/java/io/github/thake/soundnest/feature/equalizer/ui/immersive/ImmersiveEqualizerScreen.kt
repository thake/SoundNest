/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.equalizer.ui.immersive

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import io.github.thake.soundnest.feature.equalizer.ui.MaterialEqualizerScreen

/**
 * Settings > Playback > Equalizer (Immersive): delegates to the unified Material Design 3 screen.
 */
@Composable
fun ImmersiveEqualizerScreen(navController: NavController) = MaterialEqualizerScreen(navController)
