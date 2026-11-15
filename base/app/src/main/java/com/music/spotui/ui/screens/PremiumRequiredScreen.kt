/* SPDX-License-Identifier: GPL-3.0-only */
package com.music.spotui.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.spotui.ui.theme.AppAccent
import com.music.spotui.ui.theme.AppMuted
import com.music.spotui.ui.theme.AppText
import com.music.spotui.ui.theme.tone

/**
 * Blocking screen shown whenever the active signed-in Spotify account is not on an ad-free
 * Spotify Premium plan (Premium, Family, Duo, or Student).
 */
@Composable
fun PremiumRequiredScreen(
    accountName: String?,
    onSwitchOrLogout: () -> Unit,
    onRecheck: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tone(0xFF121212))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(tone(0xFF242424)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = AppAccent,
                    modifier = Modifier.size(34.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Spotify Premium Required",
                color = AppText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = buildString {
                    if (!accountName.isNullOrBlank()) {
                        append("The signed-in account (")
                        append(accountName)
                        append(") does not have an active Spotify Premium subscription.\n\n")
                    }
                    append("Orpheon Kids only works with ad-free Spotify Premium accounts (Individual, Duo, Family, or Student).")
                },
                color = AppMuted,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onSwitchOrLogout,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = AppAccent, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("Use Another Account", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onRecheck,
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth().height(46.dp),
            ) {
                Text("Check Subscription Again", color = AppText, fontSize = 14.sp)
            }
        }
    }
}
