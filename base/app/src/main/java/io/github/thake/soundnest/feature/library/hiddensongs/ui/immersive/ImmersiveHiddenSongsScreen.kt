package io.github.thake.soundnest.feature.library.hiddensongs.ui.immersive


import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveGroupRow
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersivePage
import io.github.thake.soundnest.core.ui.immersive.pages.immersiveBlock
import io.github.thake.soundnest.core.ui.immersive.pages.immersiveGroup
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation.NavController
import io.github.thake.soundnest.R
import io.github.thake.soundnest.feature.library.hiddensongs.settings.HiddenSongs
import io.github.thake.soundnest.core.ui.immersive.Immersive
import io.github.thake.soundnest.core.ui.immersive.components.immersiveClickable
import androidx.compose.ui.unit.dp

/**
 * Settings > Hidden songs (Immersive): the songs marked "Not interested" (see [HiddenSongs]),
 * newest first, each with a "Show again" that takes it off the list.
 */
@Composable
fun ImmersiveHiddenSongsScreen(navController: NavController) {
    val context = LocalContext.current
    val songs = HiddenSongs.songs
    ImmersivePage(title = stringResource(R.string.hidden_songs_title), onBack = { navController.popBackStack() }) {
        if (songs.isEmpty()) immersiveBlock("empty") {
            Text(stringResource(R.string.hidden_songs_empty), style = Immersive.Subhead, textAlign = TextAlign.Center)
        } else immersiveGroup("songs", footerRes = R.string.hidden_songs_detail) {
            songs.forEach { s ->
                row {
                    ImmersiveGroupRow(
                        s.title, detail = s.singer,
                        trailing = {
                            Text(
                                stringResource(R.string.show_song_again),
                                style = Immersive.Body.copy(color = Immersive.Accent),
                                modifier = Modifier.immersiveClickable(onClick = { HiddenSongs.show(context, s.id) }).padding(8.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}
