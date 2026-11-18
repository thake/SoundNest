/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.system.ui

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
 * Full-screen Material Design 3 About page for SoundNest, preserving GPL-3.0 upstream attributions
 * to Orpheon / Myspoti, BitChord, and Spotui without stacking nested bottom sheets.
 */
@Composable
fun AboutScreen(navController: NavController) {
    val immersive = isImmersive()
    val title = stringResource(R.string.menu_about_title)
    val pageBg = if (immersive) Immersive.Page else AppBackground
    val cardBg = if (immersive) Immersive.Raised else tone(0xFF202020)
    val textColor = if (immersive) Immersive.Text else AppText
    val mutedColor = if (immersive) Immersive.TextSecondary else AppMuted
    val accentColor = if (immersive) Immersive.Accent else AppAccent
    val dividerColor = if (immersive) Immersive.Separator else tone(0xFF323232)
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
                item(key = "about:large-title") {
                    ImmersiveLargeTitle(title = title)
                }
            } else {
                item(key = "about:topbar") {
                    ClassicSubScreenTopBar(title = title, onBack = { navController.popBackStack() })
                }
            }

            item(key = "about:hero") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = cardBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .then(if (immersive) Modifier.border(1.dp, Immersive.GlassEdge, RoundedCornerShape(20.dp)) else Modifier),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(R.drawable.ic_launcher_foreground),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (immersive) Color(0xFF141418) else tone(0xFF141414)),
                            )
                            Column(Modifier.padding(start = 16.dp)) {
                                Text(
                                    text = "SoundNest",
                                    color = textColor,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "${stringResource(R.string.settings_item_version_title)} ${BuildConfig.VERSION_NAME}",
                                    color = accentColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.about_app_description),
                            color = mutedColor,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        )
                    }
                }
            }

            item(key = "about:attributions") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = cardBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .then(if (immersive) Modifier.border(1.dp, Immersive.GlassEdge, RoundedCornerShape(20.dp)) else Modifier),
                ) {
                    Column {
                        AboutInfoRow(
                            icon = Icons.Rounded.Code,
                            title = stringResource(R.string.about_source_code_title),
                            detail = "github.com/thake/soundnest",
                            textColor = textColor,
                            mutedColor = mutedColor,
                            accentColor = accentColor,
                        )
                        HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 68.dp, end = 16.dp))
                        AboutInfoRow(
                            icon = Icons.Rounded.FavoriteBorder,
                            title = stringResource(R.string.about_attribution_title),
                            detail = stringResource(R.string.about_upstream_attribution_detail),
                            textColor = textColor,
                            mutedColor = mutedColor,
                            accentColor = accentColor,
                        )
                        HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 68.dp, end = 16.dp))
                        AboutInfoRow(
                            icon = Icons.Rounded.VerifiedUser,
                            title = stringResource(R.string.about_open_source_title),
                            detail = stringResource(R.string.about_free),
                            textColor = textColor,
                            mutedColor = mutedColor,
                            accentColor = accentColor,
                        )
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
private fun AboutInfoRow(
    icon: ImageVector,
    title: String,
    detail: String,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accentColor.copy(alpha = 0.14f)),
        ) {
            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(text = title, color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(text = detail, color = mutedColor, fontSize = 13.sp, lineHeight = 18.sp)
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
