package io.github.thake.soundnest.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thake.soundnest.BuildConfig
import io.github.thake.soundnest.R
import io.github.thake.soundnest.ui.theme.AppMuted
import io.github.thake.soundnest.ui.theme.AppText
import io.github.thake.soundnest.ui.theme.tone

/** About sheet for SoundNest with static GPL-3.0 attribution to upstream Orpheon / Myspoti, BitChord, and Spotui. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(onDismiss: () -> Unit) {
    LookSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = tone(0xFF282828),
    ) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp)) {
                Image(
                    painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(tone(0xFF121212)),
                )
                Column(Modifier.padding(start = 14.dp)) {
                    Text("SoundNest", color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${stringResource(R.string.settings_item_version_title)} ${BuildConfig.VERSION_NAME}",
                        color = AppMuted,
                        fontSize = 13.sp,
                    )
                }
            }
            Text(
                "Parent-controlled Spotify player for children and teens (compatible with Spotify Free and Premium).",
                color = AppMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
            )
            StaticAboutRow(
                title = "Source Code (GPL-3.0)",
                detail = "github.com/thake/soundnest",
            )
            StaticAboutRow(
                title = "Upstream Attribution",
                detail = "Forked from Orpheon / Myspoti (github.com/kota-zo/Orpheon), BitChord, and Spotui under the GNU General Public License v3.0.",
            )
        }
    }
}

@Composable
private fun StaticAboutRow(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(title, color = AppText, fontSize = 16.sp)
        Text(detail, color = AppMuted, fontSize = 12.sp)
    }
}
