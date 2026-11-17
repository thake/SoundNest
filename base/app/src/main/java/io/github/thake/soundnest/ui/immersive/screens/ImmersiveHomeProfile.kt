/*
 * Adapted from BitChord (https://github.com/kushagrasinghx/BitChord), GPL-3.0-or-later,
 * sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/screens/HomeScreen.kt at edc1e6f.
 * Modified for Myspoti, 2026-10.
 */
package io.github.thake.soundnest.ui.immersive.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.thake.soundnest.R
import io.github.thake.soundnest.data.api.ProfileCache
import io.github.thake.soundnest.ui.immersive.Immersive
import io.github.thake.soundnest.ui.immersive.components.ImmersiveCover
import io.github.thake.soundnest.ui.immersive.components.immersiveClickable

/**
 * The profile picture as a disc (the first letter of the name until it loads), opening [onClick];
 * null when the caller takes the tap on a wider area around it.
 */
@Composable
internal fun ProfileDisc(onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val avatar = ProfileCache.imageUrl
    val initial = ProfileCache.name?.trim()?.firstOrNull()?.uppercase() ?: "•"
    AvatarDisc(
        url = avatar,
        initial = initial,
        contentDescription = stringResource(R.string.recently_played_user_profile),
        onClick = onClick,
        modifier = modifier,
    )
}

/** A round avatar: the picture at [url], or [initial] on the accent while there is none. */
@Composable
internal fun AvatarDisc(
    url: String?,
    initial: String,
    contentDescription: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .let { if (onClick != null) it.immersiveClickable(onClick = onClick) else it }
            .clip(CircleShape)
            .background(Immersive.Accent),
    ) {
        if (!url.isNullOrBlank()) {
            ImmersiveCover(
                url = url,
                modifier = Modifier.size(size),
                shape = CircleShape,
                contentDescription = contentDescription,
                border = false,
            )
        } else {
            Text(text = initial, style = Immersive.BodyStrong.copy(color = Immersive.Page, fontWeight = FontWeight.Bold))
        }
    }
}
