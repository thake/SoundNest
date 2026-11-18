/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.system.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.thake.soundnest.BuildConfig
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.ui.classic.tone
import io.github.thake.soundnest.core.ui.settings.M3GroupCard
import io.github.thake.soundnest.core.ui.settings.M3SettingsNavRow
import io.github.thake.soundnest.core.ui.settings.M3SettingsScaffold
import io.github.thake.soundnest.core.ui.settings.m3SettingsBlock
import io.github.thake.soundnest.core.ui.settings.m3SettingsSection

private const val SOURCE_REPO_URL = "https://github.com/thake/soundnest"

/**
 * Full-screen Material Design 3 About page for SoundNest across Classic and Immersive looks,
 * preserving GPL-3.0 upstream attributions to Orpheon / Myspoti, BitChord, and Spotui.
 */
@Composable
fun AboutScreen(navController: NavController) {
    val context = LocalContext.current
    val title = stringResource(R.string.menu_about_title)

    M3SettingsScaffold(
        title = title,
        onBack = { navController.popBackStack() },
    ) { palette ->
        m3SettingsBlock(key = "about:hero", palette = palette) {
            M3GroupCard(palette = palette) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (palette.immersive) Color(0xFF141418) else tone(0xFF141414)),
                        )
                        Column(
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .weight(1f),
                        ) {
                            Text(
                                text = "SoundNest",
                                color = palette.text,
                                fontFamily = palette.fontFamily,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(palette.accent.copy(alpha = 0.16f))
                                    .padding(horizontal = 10.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = "${stringResource(R.string.settings_item_version_title)} ${BuildConfig.VERSION_NAME}",
                                    color = palette.accent,
                                    fontFamily = palette.fontFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.about_app_description),
                        color = palette.muted,
                        fontFamily = palette.fontFamily,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }
            }
        }

        m3SettingsSection(
            key = "about:attributions",
            palette = palette,
            header = context.getString(R.string.about_section_credits),
            hasLeadingIconBadges = true,
        ) {
            row {
                M3SettingsNavRow(
                    icon = Icons.Rounded.Code,
                    title = stringResource(R.string.about_source_code_title),
                    detail = "github.com/thake/soundnest",
                    palette = palette,
                    externalLink = true,
                    onClick = {
                        runCatching {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_REPO_URL))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                    },
                )
            }
            row {
                M3SettingsNavRow(
                    icon = Icons.Rounded.FavoriteBorder,
                    title = stringResource(R.string.about_attribution_title),
                    detail = stringResource(R.string.about_upstream_attribution_detail),
                    palette = palette,
                    showChevron = false,
                )
            }
            row {
                M3SettingsNavRow(
                    icon = Icons.Rounded.VerifiedUser,
                    title = stringResource(R.string.about_open_source_title),
                    detail = stringResource(R.string.about_free),
                    palette = palette,
                    showChevron = false,
                )
            }
        }
    }
}
