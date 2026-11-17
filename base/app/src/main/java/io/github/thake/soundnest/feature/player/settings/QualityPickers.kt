package io.github.thake.soundnest.feature.player.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.ui.classic.AppAccent
import io.github.thake.soundnest.core.ui.classic.AppMuted
import io.github.thake.soundnest.core.ui.classic.AppText
import io.github.thake.soundnest.core.ui.immersive.Immersive
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveChoiceRow
import io.github.thake.soundnest.core.ui.navigation.isImmersive
import io.github.thake.soundnest.spotify.AccountPlan

/** The streaming levels of the Web Player, one choice; saved and applied at once. */
@Composable
fun StreamingQualityPicker(modifier: Modifier = Modifier, cellular: Boolean = false, title: Int = R.string.quality_streaming_title) {
    val ctx = LocalContext.current
    var current by remember(cellular) { mutableStateOf(getStreamingQuality(ctx, cellular)) }
    val premium by AccountPlan.premium.collectAsState()
    QualityGroup(
        title = stringResource(title),
        detail = stringResource(R.string.quality_streaming_detail),
        options = listOfNotNull(
            Option(StreamingQuality.AUTO, R.string.quality_streaming_auto, R.string.quality_streaming_auto_detail),
            Option(StreamingQuality.LOW, R.string.quality_streaming_low, R.string.quality_streaming_low_detail),
            Option(StreamingQuality.NORMAL, R.string.quality_streaming_normal, R.string.quality_streaming_normal_detail),
            Option(StreamingQuality.HIGH, R.string.quality_streaming_high, R.string.quality_streaming_high_detail),
            Option(StreamingQuality.VERY_HIGH, R.string.quality_streaming_very_high, R.string.quality_streaming_very_high_detail)
                .takeIf { premium == true || current == StreamingQuality.VERY_HIGH },
        ),
        selected = current,
        onSelect = { current = it; setStreamingQuality(ctx, it, cellular) },
        modifier = modifier,
    )
}

private class Option<T>(val value: T, val label: Int, val detail: Int)

@Composable
private fun <T> QualityGroup(
    title: String, detail: String, options: List<Option<T>>, selected: T,
    onSelect: (T) -> Unit, modifier: Modifier = Modifier,
) {
    if (isImmersive()) {
        Column(modifier.fillMaxWidth().padding(horizontal = 20.dp).selectableGroup()) {
            Text(title, style = Immersive.Headline, modifier = Modifier.padding(top = 4.dp))
            Text(detail, style = Immersive.Caption.copy(color = Color.White.copy(alpha = 0.72f)), modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
            Column(Modifier.fillMaxWidth().clip(Immersive.Card).background(Immersive.Raised.copy(alpha = 0.92f))) {
                options.forEachIndexed { i, o ->
                    ImmersiveChoiceRow(stringResource(o.label), selected = o.value == selected, detail = stringResource(o.detail), onClick = { onSelect(o.value) })
                    if (i < options.lastIndex) Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(0.5.dp).background(Immersive.Separator))
                }
            }
        }
        return
    }
    Column(modifier.fillMaxWidth().selectableGroup()) {
        Text(title, color = AppText, fontSize = 17.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        Text(detail, color = AppMuted, fontSize = 13.sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp))
        for (o in options) {
            val on = o.value == selected
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(o.value) })
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                RadioButton(selected = on, onClick = null, modifier = Modifier.padding(horizontal = 8.dp),
                    colors = RadioButtonDefaults.colors(selectedColor = AppAccent, unselectedColor = AppMuted))
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(stringResource(o.label), color = if (on) AppAccent else AppText, fontSize = 16.sp)
                    Text(stringResource(o.detail), color = AppMuted, fontSize = 12.sp)
                }
            }
        }
    }
}
