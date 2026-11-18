/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.library.hiddensongs.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.ui.settings.M3GroupCard
import io.github.thake.soundnest.core.ui.settings.M3SettingsPalette
import io.github.thake.soundnest.core.ui.settings.M3SettingsScaffold
import io.github.thake.soundnest.core.ui.settings.m3SettingsBlock
import io.github.thake.soundnest.core.ui.settings.m3SettingsSection
import io.github.thake.soundnest.feature.library.hiddensongs.settings.HiddenSongs
import io.github.thake.soundnest.feature.parental.filter.ParentalFilterEngine
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.feature.parental.settings.rememberParentalPolicy

/**
 * Shared Material Design 3 Hidden Songs screen for both Classic and Immersive looks.
 * Displays 48dp artwork thumbnails, artist metadata, and a tonal "Show again" pill action,
 * filtering out tracks blocked by the active Parental Controls policy.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun MaterialHiddenSongsScreen(navController: NavController) {
    val context = LocalContext.current
    val policy = rememberParentalPolicy()
    val songs = remember(HiddenSongs.songs, policy) {
        HiddenSongs.songs.filter { s ->
            val normId = ParentalPolicyStore.normalizeSpotifyId(s.id)
            if (normId.isNotEmpty() && normId in policy.blockedTrackIds) return@filter false
            if (normId.isNotEmpty() && normId in policy.allowedTrackIds) return@filter true
            !ParentalFilterEngine.matchesBlockedText(policy, s.title, s.singer)
        }
    }

    M3SettingsScaffold(
        title = stringResource(R.string.hidden_songs_title),
        onBack = { navController.popBackStack() },
    ) { palette ->
        if (songs.isEmpty()) {
            m3SettingsBlock(key = "hidden:empty", palette = palette) {
                EmptyHiddenSongsCard(palette = palette)
            }
        } else {
            m3SettingsSection(
                key = "hidden:songs",
                palette = palette,
                footer = context.getString(R.string.hidden_songs_detail),
            ) {
                songs.forEach { song ->
                    row {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            GlideImage(
                                model = song.coverUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                failure = placeholder(R.drawable.placeholder),
                                loading = placeholder(R.drawable.placeholder),
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    color = palette.text,
                                    fontFamily = palette.fontFamily,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = song.singer,
                                    color = palette.muted,
                                    fontFamily = palette.fontFamily,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(palette.accent.copy(alpha = 0.16f))
                                    .clickable(role = Role.Button) { HiddenSongs.show(context, song.id) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.show_song_again),
                                    color = palette.accent,
                                    fontFamily = palette.fontFamily,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHiddenSongsCard(palette: M3SettingsPalette) {
    M3GroupCard(palette = palette) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.accent.copy(alpha = 0.14f)),
            ) {
                Icon(
                    imageVector = Icons.Rounded.VisibilityOff,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.hidden_songs_empty),
                color = palette.muted,
                fontFamily = palette.fontFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
