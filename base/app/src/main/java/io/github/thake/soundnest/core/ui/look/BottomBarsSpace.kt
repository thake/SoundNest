package io.github.thake.soundnest.core.ui.look

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Whether an outer top chrome strip (such as `ParentAdminSessionBanner`) has already applied
 * `statusBarsPadding()` above the current subtree, so direct `WindowInsets.statusBars` height
 * lookups inside that subtree return `0.dp`.
 */
val LocalTopStatusBarConsumed = compositionLocalOf { false }

/**
 * Top status bar padding remaining for the current subtree (`0.dp` when an outer banner already
 * sits above the status bar inset).
 */
@Composable
fun effectiveStatusBarTopPadding(): Dp =
    if (LocalTopStatusBarConsumed.current) 0.dp
    else WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

/**
 * The room a scrolling page leaves at its end, so its last rows can scroll above the mini
 * player, the tabs and the system navigation bar (three buttons or gesture line) instead of
 * staying under them.
 */
@Composable
fun bottomBarsSpace(): Dp = 160.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
