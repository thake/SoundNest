package io.github.thake.soundnest.feature.player.sleeptimer.ui.immersive


import io.github.thake.soundnest.core.ui.immersive.sheets.ImmersiveSheet
import io.github.thake.soundnest.core.ui.immersive.sheets.ImmersiveSheetOption
import io.github.thake.soundnest.core.ui.immersive.sheets.ImmersiveSheetTitle
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.thake.soundnest.R
import io.github.thake.soundnest.feature.player.sleeptimer.playback.SleepTimerController

/**
 * The sleep timer of the Immersive look: the same choices and the same [SleepTimerController.setSleepTimer]
 * call as the Classic [io.github.thake.soundnest.feature.player.sleeptimer.ui.classic.SleepTimerSheet], in the Immersive sheet kit.
 */
@Composable
fun ImmersiveSleepTimerSheet(onDismiss: () -> Unit) {
    val active = SleepTimerController.sleepEndsAt.value != null
    ImmersiveSheet(onDismiss = onDismiss) {
        ImmersiveSheetTitle(stringResource(R.string.context_menu_sleep_timer))
        val options = buildList {
            for (m in listOf(5, 10, 15, 30, 45)) add(stringResource(R.string.sleep_minutes, m) to m)
            add(stringResource(R.string.mute_chat_duration_one_hour) to 60)
            add(stringResource(R.string.context_menu_sleep_timer_end_of_track) to 0)
        }
        options.forEach { (label, minutes) ->
            ImmersiveSheetOption(label = label, selected = false) { SleepTimerController.setSleepTimer(minutes); onDismiss() }
        }
        if (active) {
            ImmersiveSheetOption(
                label = stringResource(R.string.context_menu_sleep_timer_turn_off), selected = false, danger = true,
            ) { SleepTimerController.setSleepTimer(null); onDismiss() }
        }
        Spacer(Modifier.height(12.dp))
    }
}
