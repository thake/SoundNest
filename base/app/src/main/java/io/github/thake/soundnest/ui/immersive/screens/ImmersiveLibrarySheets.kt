/*
 * Adapted from BitChord (https://github.com/kushagrasinghx/BitChord), GPL-3.0-or-later,
 * app/src/main/java/com/music/bitchord/ui/screens/LibraryPhoneParts.kt at edc1e6f.
 * The sheets' rows and actions are those of Myspoti's Classic Library screen. Modified for Myspoti, 2026-10.
 */
package io.github.thake.soundnest.ui.immersive.screens

import io.github.thake.soundnest.ui.immersive.sheets.ImmersiveGlassSheet
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.thake.soundnest.R
import io.github.thake.soundnest.data.entity.AccountModel
import io.github.thake.soundnest.data.local.LibraryOrder
import io.github.thake.soundnest.di.Graph
import io.github.thake.soundnest.ui.immersive.Immersive
import io.github.thake.soundnest.ui.navigation.Routes
import io.github.thake.soundnest.ui.screens.AccountBrowser

/** The label of a library sort order. */
internal fun libraryOrderLabel(sort: LibraryOrder.Sort): Int = when (sort) {
    LibraryOrder.Sort.CUSTOM -> R.string.your_library_sort_order_custom
    LibraryOrder.Sort.RECENTS -> R.string.library_sort_recents
    LibraryOrder.Sort.RECENTLY_ADDED -> R.string.library_sort_recently_added
    LibraryOrder.Sort.ALPHABETICAL -> R.string.your_library_sort_order_alphabetical
    LibraryOrder.Sort.CREATOR -> R.string.your_library_sort_order_creator
}

/** "Sort by": custom order (moved by hand), recents, alphabetical or by creator, and a reset. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImmersiveLibrarySortSheet(onDismiss: () -> Unit) {
    ImmersiveGlassSheet(onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                text = stringResource(R.string.filter_and_sort_sort_section_title),
                style = Immersive.Title,
                modifier = Modifier.padding(horizontal = Immersive.Gutter, vertical = 8.dp),
            )
            for (sort in LibraryOrder.Sort.entries) {
                val selected = LibraryOrder.sort == sort
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { LibraryOrder.choose(sort); onDismiss() }
                        .padding(horizontal = Immersive.Gutter, vertical = 14.dp),
                ) {
                    Text(
                        text = stringResource(libraryOrderLabel(sort)),
                        style = Immersive.Body.copy(color = if (selected) Immersive.Accent else Immersive.Text),
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = Immersive.Accent, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Text(
                text = stringResource(R.string.library_reorder_hint),
                style = Immersive.Caption,
                modifier = Modifier.padding(horizontal = Immersive.Gutter, vertical = 8.dp),
            )
            Text(
                text = stringResource(R.string.immersive_screens_reset_order),
                style = Immersive.BodyStrong,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { LibraryOrder.reset(); onDismiss() }
                    .padding(horizontal = Immersive.Gutter, vertical = 14.dp),
            )
        }
    }
}

