/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.accounts.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.thake.soundnest.BuildConfig
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.ui.look.bottomBarsSpace
import io.github.thake.soundnest.core.ui.immersive.Immersive
import io.github.thake.soundnest.core.ui.immersive.shell.ImmersiveLargeTitle
import io.github.thake.soundnest.core.ui.immersive.shell.ImmersiveTopBar
import io.github.thake.soundnest.core.ui.immersive.shell.immersiveChromeScroll
import io.github.thake.soundnest.core.ui.immersive.shell.immersiveHazeSource
import io.github.thake.soundnest.core.ui.immersive.shell.immersiveTopContentPadding
import io.github.thake.soundnest.core.ui.navigation.isImmersive
import io.github.thake.soundnest.core.ui.classic.AppAccent
import io.github.thake.soundnest.core.ui.classic.AppBackground
import io.github.thake.soundnest.core.ui.classic.AppMuted
import io.github.thake.soundnest.core.ui.classic.AppText
import io.github.thake.soundnest.core.ui.classic.tone


/**
 * Full-screen Material Design 3 Switch Account page wrapping [AccountsList] so switching, adding,
 * or removing Spotify profiles does not stack modal bottom sheets.
 */
@Composable
fun AccountsScreen(navController: NavController) {
    val immersive = isImmersive()
    val title = stringResource(R.string.accounts_title)
    val pageBg = if (immersive) Immersive.Page else AppBackground
    val cardBg = if (immersive) Immersive.Raised else tone(0xFF202020)
    val listState = rememberLazyListState()

    Box(Modifier.fillMaxSize().background(pageBg)) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = if (immersive) immersiveTopContentPadding() else 0.dp,
                bottom = if (immersive) Immersive.BottomChrome else bottomBarsSpace() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .then(if (immersive) Modifier.immersiveHazeSource().immersiveChromeScroll() else Modifier.statusBarsPadding().navigationBarsPadding()),
        ) {
            if (immersive) {
                item(key = "accounts:large-title") {
                    ImmersiveLargeTitle(title = title)
                }
            } else {
                item(key = "accounts:topbar") {
                    ClassicSubScreenTopBar(title = title, onBack = { navController.popBackStack() })
                }
            }

            item(key = "accounts:card") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = cardBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .then(if (immersive) Modifier.border(1.dp, Immersive.GlassEdge, RoundedCornerShape(20.dp)) else Modifier),
                ) {
                    Box(Modifier.padding(vertical = 8.dp)) {
                        AccountsList(onLeave = { navController.popBackStack() })
                    }
                }
            }
        }

        if (immersive) {
            ImmersiveTopBar(
                title = title,
                listState = listState,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun ClassicSubScreenTopBar(title: String, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onBack),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.accessibility_navigate_back),
                tint = AppText,
            )
        }
        Text(
            text = title,
            color = AppText,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).padding(end = 16.dp),
        )
    }
}
