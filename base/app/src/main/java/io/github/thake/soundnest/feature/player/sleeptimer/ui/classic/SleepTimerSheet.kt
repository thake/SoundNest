package io.github.thake.soundnest.feature.player.sleeptimer.ui.classic


import io.github.thake.soundnest.core.ui.look.LookSheet
import io.github.thake.soundnest.core.ui.classic.tone
import io.github.thake.soundnest.core.ui.classic.AppText
import io.github.thake.soundnest.core.ui.classic.AppMuted

import androidx.compose.ui.res.stringResource
import io.github.thake.soundnest.R

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Delete
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import io.github.thake.soundnest.core.ui.navigation.isImmersive
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import io.github.thake.soundnest.core.model.SongsModel
import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.feature.player.sleeptimer.playback.SleepTimerController
import io.github.thake.soundnest.core.ui.classic.ExplicitBadge

private val Green: Color get() = io.github.thake.soundnest.core.ui.classic.AppAccent
private val SheetColor: androidx.compose.ui.graphics.Color get() = tone(0xFF282828)


// ── Sleep timer ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(onDismiss: () -> Unit) {
    val active = SleepTimerController.sleepEndsAt.value != null
    io.github.thake.soundnest.core.ui.look.LookSheet(onDismissRequest = onDismiss, containerColor = SheetColor) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.context_menu_sleep_timer),
                color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            val endOfTrack = stringResource(R.string.context_menu_sleep_timer_end_of_track)
            val options = buildList {
                for (m in listOf(5, 10, 15, 30, 45)) add(stringResource(R.string.sleep_minutes, m) to m)
                add(stringResource(R.string.mute_chat_duration_one_hour) to 60)
                add(endOfTrack to 0)
            }
            options.forEach { (label, minutes) ->
                Text(
                    label,
                    color = AppText, fontSize = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { SleepTimerController.setSleepTimer(minutes); onDismiss() }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                )
            }
            if (active) {
                Text(
                    stringResource(R.string.context_menu_sleep_timer_turn_off),
                    color = Green, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { SleepTimerController.setSleepTimer(null); onDismiss() }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                )
            }
        }
    }
}

