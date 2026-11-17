package io.github.thake.soundnest.feature.library.ui.classic

import io.github.thake.soundnest.feature.accounts.ui.ProfileAvatar

import io.github.thake.soundnest.feature.localfiles.ui.classic.localItems

import io.github.thake.soundnest.core.ui.look.titleMarquee
import io.github.thake.soundnest.core.ui.look.wideClickable
import io.github.thake.soundnest.core.ui.classic.AppDestructive
import io.github.thake.soundnest.core.ui.classic.tone
import io.github.thake.soundnest.core.ui.classic.AppText
import io.github.thake.soundnest.core.ui.classic.headerTint

import io.github.thake.soundnest.core.ui.classic.AppMuted

import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.ui.res.stringResource

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import io.github.thake.soundnest.core.ui.classic.SkeletonKind
import io.github.thake.soundnest.core.ui.look.animatedRow
import io.github.thake.soundnest.core.ui.look.pressable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Group
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import io.github.thake.soundnest.core.catalog.Api
import io.github.thake.soundnest.core.catalog.EditablePlaylists
import io.github.thake.soundnest.core.catalog.Response
import io.github.thake.soundnest.core.model.ContextPageModel
import io.github.thake.soundnest.core.model.SongsModel
import io.github.thake.soundnest.core.playback.CurrentSongState
import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.core.util.Palette
import io.github.thake.soundnest.core.playback.SongPlayer
import io.github.thake.soundnest.core.ui.classic.Loader
import io.github.thake.soundnest.core.ui.navigation.artistRoute
import io.github.thake.soundnest.R
import io.github.thake.soundnest.feature.library.ui.ContextViewModel
import io.github.thake.soundnest.feature.library.ui.SmartShuffle
import io.github.thake.soundnest.feature.library.settings.TrackOrder
import io.github.thake.soundnest.feature.library.settings.TrackSort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.AutoAwesome
import io.github.thake.soundnest.host.android.SoundNestRuntime
import io.github.thake.soundnest.core.ui.classic.ExplicitBadge
import io.github.thake.soundnest.feature.player.ui.classic.LikeButton
import io.github.thake.soundnest.feature.library.ui.PlaylistReorder
import io.github.thake.soundnest.feature.library.ui.movableRow
import io.github.thake.soundnest.feature.library.ui.rememberPlaylistDrag

private val SpotifyGreen: Color get() = io.github.thake.soundnest.core.ui.classic.AppAccent
private val PageBackground: Color get() = io.github.thake.soundnest.core.ui.classic.AppBackground


@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun ContextScreen(navController: NavController, type: String, id: String) {
    val vm: ContextViewModel = viewModel(key = "$type:$id")
    // During composition, so a cached page is there for the very first frame.
    remember(type, id) { vm.load(type, id) }
    val resp by vm.page.collectAsState()
    // The page fades in over its placeholder instead of replacing it in one frame.
    io.github.thake.soundnest.core.ui.look.LoadedFade(resp is Response.Loading, Modifier.fillMaxSize().background(PageBackground)) { loading ->
        Box(modifier = Modifier.fillMaxSize()) {
            // The placeholder fading out stays the placeholder, whatever the response is now.
            if (loading) Loader(SkeletonKind.Header)
            else when (val r = resp) {
                is Response.Error -> io.github.thake.soundnest.core.ui.look.ErrorRetry(
                    stringResource(R.string.page_load_failed),
                    Modifier.align(Alignment.Center),
                ) { vm.reload() }
                is Response.Success -> ContextContent(navController, vm, r.data)
                is Response.Loading -> Loader(SkeletonKind.Header)
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ContextContent(navController: NavController, vm: ContextViewModel, p: ContextPageModel) {
    val context = LocalContext.current
    val playerVm: io.github.thake.soundnest.feature.player.ui.PlayerViewModel = viewModel()
    val currentSongId = playerVm.currentSongId.value
    val playing = playerVm.currentSongPlayingState.value
    val shuffle = playerVm.shuffleState.value
    val np by SongPlayer.nowPlaying
    val isThisContext = np.contextUri == p.uri || ((p.type == "liked" || p.type == "liked-artist") && np.contextUri.contains(":collection"))
    val saved by vm.savedUris
    val contextSaved by vm.contextSaved
    val policy = io.github.thake.soundnest.feature.parental.settings.rememberParentalPolicy()
    LaunchedEffect(policy.effectiveAllowAutoplayAndRadio) {
        if (!policy.effectiveAllowAutoplayAndRadio && SmartShuffle.on.value == p.uri) {
            SmartShuffle.on.value = null
        }
    }

    var tintTarget by remember { mutableStateOf(Palette.cachedSecond(p.coverUri) ?: tone(0xFF3A3A3A)) }
    LaunchedEffect(p.coverUri) {
        if (p.coverUri.isNotBlank()) Palette().extractSecondColorFromCoverUrl(context, p.coverUri) { tintTarget = it }
    }
    val tint by androidx.compose.animation.animateColorAsState(tintTarget, androidx.compose.animation.core.tween(350), label = "tint")
    var menuSong by remember { mutableStateOf<SongsModel?>(null) }
    var savedInSong by remember { mutableStateOf<SongsModel?>(null) }
    // "Find in Liked Songs / in playlist".
    var find by remember { mutableStateOf("") }
    // A song tapped in the found rows puts the keyboard away; the search and the list stay as they are.
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val dismissKeyboard = { keyboard?.hide(); focusManager.clearFocus() }
    // "Sort by" for the list, kept per list (also after leaving it or restarting the app).
    val ctx = LocalContext.current
    var sort by remember(p.uri) { mutableStateOf(TrackOrder.load(ctx, p.uri)) }
    var playlistMenu by remember { mutableStateOf(false) }
    var albumMenu by remember { mutableStateOf(false) }
    var addingSongs by remember { mutableStateOf(false) }
    var leaveAsk by remember { mutableStateOf(false) }
    // The profile id comes once per session (and is no Compose state): `name` is set just before it,
    // so reading it here redraws the page when the id is there.
    LaunchedEffect(Unit) { io.github.thake.soundnest.feature.accounts.session.ProfileCache.ensure(context) }
    val ownPlaylist = io.github.thake.soundnest.feature.accounts.session.ProfileCache.let { cache ->
        cache.name
        cache.id?.let { id -> p.ownerUri == "spotify:user:$id" } == true
    }
    // A11: confirmations (and their Undo) float above the list, not inside a menu that closes.
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // A11: the row goes at once, then the removal is written to Spotify. The snackbar keeps
    // the mistake reversible: Undo adds the track back and the page is read again.
    fun removeFromThisPlaylist(song: SongsModel) {
        val playlistId = p.uri.substringAfterLast(':')
        vm.dropTrack(song)
        io.github.thake.soundnest.core.catalog.SpotifySync.removeRowFromPlaylist(context, playlistId, song.spotifyTrackId, song.rowUid) { ok ->
            if (!ok) {
                vm.reload()
                android.widget.Toast.makeText(context, context.getString(R.string.playlist_change_refused), android.widget.Toast.LENGTH_SHORT).show()
            } else scope.launch {
                val chosen = snackbar.showSnackbar(
                    message = context.getString(R.string.removed_from, p.name),
                    actionLabel = context.getString(R.string.undo),
                    withDismissAction = false,
                    // Long: the write has just gone through, the mistake deserves time to be undone.
                    duration = androidx.compose.material3.SnackbarDuration.Long,
                )
                if (chosen == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                    io.github.thake.soundnest.core.catalog.SpotifySync.addTrackToPlaylist(context, playlistId, song.spotifyTrackId) { back ->
                        if (back) vm.reload()
                        else android.widget.Toast.makeText(context, context.getString(R.string.playlist_change_refused), android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    if (leaveAsk) androidx.compose.material3.AlertDialog(
        onDismissRequest = { leaveAsk = false },
        containerColor = tone(0xFF282828),
        title = { Text(stringResource(R.string.playlist_leave), color = AppText) },
        text = { Text(stringResource(R.string.playlist_leave_confirm, p.name), color = AppMuted) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                leaveAsk = false
                // As from the playlist menu: the page closes now; a refusal says so and the library is read again.
                io.github.thake.soundnest.core.catalog.LibraryEdits.run(context, null,
                    write = { SoundNestRuntime.api.playlists.leave(p.uri) },
                    onDone = { ok -> if (ok) io.github.thake.soundnest.core.catalog.LibraryEdits.reloadSoon() })
                navController.navigateUp()
            }) { Text(stringResource(R.string.playlist_leave_button), color = AppDestructive) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { leaveAsk = false }) { Text(stringResource(R.string.cancel), color = AppText) } },
    )
    if (addingSongs) io.github.thake.soundnest.feature.library.ui.AddSongsScreen(p) { added -> addingSongs = false; if (added) vm.reload() }
    if (albumMenu) io.github.thake.soundnest.feature.library.ui.AlbumOptionsSheet(p.uri, p.name, known = p) { albumMenu = false }
    if (playlistMenu) io.github.thake.soundnest.feature.library.ui.PlaylistOptionsSheet(
        page = p,
        onAddSongs = if (p.type == "playlist" && vm.editableContext.value) ({ addingSongs = true }) else null,
        onDismiss = { playlistMenu = false },
        onChanged = { vm.reload() },
        onEdited = { name, description -> vm.showDetails(name, description) },
        onDeleted = { navController.navigateUp() },
    )
    savedInSong?.let { sel ->
        io.github.thake.soundnest.feature.library.ui.SavedInSheet(song = sel, context = context, onDismiss = { savedInSong = null })
    }
    menuSong?.let { sel ->
        val inPlaylist = p.type == "playlist"
        val canRemove = inPlaylist && vm.editableContext.value
        io.github.thake.soundnest.feature.player.ui.SongOptionsSheet(
            song = sel,
            navController = navController,
            context = context,
            onDismiss = { menuSong = null },
            // The removal entry only exists on a playlist page the user may change: nowhere else
            // is there a list to take the track out of.
            onRemoveFromPlaylist = if (canRemove) ({ removeFromThisPlaylist(sel) }) else null,
            currentPlaylistId = if (inPlaylist) p.uri.substringAfterLast(':') else null,
        )
    }

    // Liked Songs: a track unliked anywhere leaves the list right away. Derived state:
    // recomputed when the page, the search, the sort or a like changes, while a recomposition
    // of the screen with nothing new in them re-sorts nothing (a 900-track list is real work).
    val findable = p.type == "liked" || p.type == "playlist"
    val all by remember(p, policy) {
        derivedStateOf {
            val base = if (p.type == "liked" || p.type == "liked-artist") {
                p.tracks.filter { SongPlayer.library["spotify:track:${it.spotifyTrackId}"] != false }
            } else {
                p.tracks
            }
            base.filter { io.github.thake.soundnest.feature.parental.filter.ParentalFilterEngine.isTrackAllowed(it, policy, parentContextUri = p.uri) }
        }
    }
    // An own playlist in its own order: its songs can be moved by hand, a handle on each while "Move" is on.
    var moving by remember(p.uri) { mutableStateOf(false) }
    val canMove = remember(p, vm.editableContext.value, sort, find) {
        PlaylistReorder.allowed(p.type, vm.editableContext.value, sort.isCustom, p.complete, find, p.tracks.map { it.rowUid })
    }
    val reordering = moving && canMove
    // Unique and stable for a given list: a playlist can hold the same song twice (the id alone
    // is not unique) and the index changes at every sort (which would lose every item). Made with
    // the rows in one derived state: the list re-reads its rows outside composition (a like, a
    // search letter), and keys remembered apart from them could be one list behind (a crash).
    // Rows that move are keyed by their own id, which goes with them wherever they are put.
    val shown by remember(p, policy, find, sort, reordering) {
        derivedStateOf {
            val found = if (findable) all.filter { io.github.thake.soundnest.core.ui.look.matchesFind(find, it.title, it.singer, it.album) } else all
            io.github.thake.soundnest.core.ui.look.withRowKeys(if (findable) sort.apply(found) else found) { if (reordering) PlaylistReorder.key(it) else it.id.toString() }
        }
    }
    val rows = shown.first

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val drag = rememberPlaylistDrag(listState)
    drag.rows = rows
    drag.onDrop = { move ->
        vm.moveRow(move) { ok ->
            if (!ok) android.widget.Toast.makeText(context, context.getString(R.string.playlist_change_refused), android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    // The page has the new order (or had it put back): its own rows are shown again.
    LaunchedEffect(p, reordering) { drag.settle() }
    val pullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    val refreshing by vm.refreshing
    // The search field is folded away: the page opens on its songs. A pull down at the top unfolds
    // it in place (that pull does not refresh; the next one does), and it folds again once the
    // songs are scrolled past it, unless a search is typed.
    val hasExtras = findable && all.isNotEmpty()
    var extrasShown by remember(p.uri) { mutableStateOf(false) }
    val revealPull = remember(p.uri) { ExtrasReveal() }
    revealPull.enabled = hasExtras && !extrasShown
    revealPull.onReveal = { extrasShown = true; io.github.thake.soundnest.core.util.Haptics.toggle(true) }
    // The search typed now, not the one when the page opened (always empty): read stale, the field
    // folded away under the user's fingers while typing, keyboard open (reported freezes and crashes).
    val findNow by androidx.compose.runtime.rememberUpdatedState(find)
    LaunchedEffect(p.uri, listState) {
        androidx.compose.runtime.snapshotFlow { listState.firstVisibleItemIndex }.collect { first ->
            if (first > 3 && findNow.isBlank()) extrasShown = false
        }
    }
    Box(Modifier.fillMaxSize()) {
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { vm.refresh() },
        state = pullState,
        modifier = Modifier.fillMaxSize(),
        // Below the status bar: the page's header runs under it.
        indicator = {
            androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator(
                state = pullState, isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
                containerColor = tone(0xFF282828), color = SpotifyGreen,
            )
        },
    ) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().nestedScroll(revealPull)) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(headerTint(tint, 0.25f), PageBackground)))
                    .statusBarsPadding()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(12.dp)
                        .wideClickable { navController.navigateUp() }
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.accessibility_navigate_back), tint = AppText)
                }
                // The cover: two thirds of the width as on phones, but never more than 40 % of the
                // window's height nor 360 dp (it grew to 686 dp on a landscape tablet, the whole
                // first screen). Large windows put it beside the title, details and actions.
                val window = io.github.thake.soundnest.core.ui.look.windowSize()
                val coverShape = RoundedCornerShape(4.dp)
                val cover: @Composable (Dp) -> Unit = { side ->
                    if (p.type == "liked" || p.type == "liked-artist") LikedCover(Modifier.size(side).shadow(16.dp, coverShape)) else
                    GlideImage(
                        model = p.coverUri,
                        contentDescription = "",
                        contentScale = ContentScale.Crop,
                        // No cover at all (a new playlist): the app's own image, not an empty square.
                        failure = com.bumptech.glide.integration.compose.placeholder(R.drawable.placeholder),
                        modifier = Modifier
                            .size(side)
                            .shadow(16.dp, coverShape)
                            .clip(coverShape),
                    )
                }
                val details: @Composable () -> Unit = {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        io.github.thake.soundnest.core.ui.look.FitText(p.name, fontSize = 26.sp, minSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                        if (p.description.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(io.github.thake.soundnest.core.ui.look.rememberLinkedText(p.description, io.github.thake.soundnest.core.ui.classic.AppAccent), color = AppText.copy(alpha = 0.7f), fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        val crew = vm.members.value
                        if (crew.size > 1) {
                            // Collaborative: everyone who adds to it, as the official app shows them.
                            Spacer(Modifier.height(10.dp))
                            var showMembers by remember { mutableStateOf(false) }
                            if (showMembers) MembersSheet(
                                members = crew, navController = navController,
                                // The owner manages everyone; a guest only has "Leave playlist" on their own row.
                                ownPlaylist = ownPlaylist, me = io.github.thake.soundnest.feature.accounts.session.ProfileCache.id, canLeave = p.canLeave,
                                onChange = { m, collaborator ->
                                    vm.changeMember(p, m, collaborator) { ok ->
                                        if (!ok) android.widget.Toast.makeText(context, R.string.playlist_change_refused, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onLeave = { showMembers = false; leaveAsk = true },
                            ) { showMembers = false }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.pressable(0.96f) { showMembers = true }) {
                                Box(Modifier.width((24 + 16 * (crew.size.coerceAtMost(4) - 1)).dp).height(24.dp)) {
                                    crew.take(4).forEachIndexed { n, m ->
                                        Box(Modifier.padding(start = (16 * n).dp).size(24.dp).clip(CircleShape).background(tone(0xFF121212)).padding(1.dp)) {
                                            io.github.thake.soundnest.feature.accounts.ui.ProfileAvatar(m.imageUrl, m.name, 22.dp)
                                        }
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (crew.size <= 2) crew.joinToString(", ") { it.name }
                                    else stringResource(R.string.playlist_members_more, crew.first().name, crew.size - 1),
                                    color = AppText, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        } else if (p.ownerName.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = if (p.ownerUri.startsWith("spotify:artist:")) {
                                    Modifier.pressable(0.96f) {
                                        navController.navigate(artistRoute(p.ownerName.substringBefore(",").trim(), p.ownerUri.substringAfterLast(":")))
                                    }
                                } else Modifier,
                            ) {
                                if (p.ownerAvatar.isNotBlank()) {
                                    GlideImage(
                                        model = p.ownerAvatar,
                                        contentDescription = "",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(24.dp).clip(CircleShape),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(p.ownerName, color = AppText, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (p.metaLine.isNotBlank() || p.explicit) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (p.explicit && p.type == "album") {
                                    ExplicitBadge()
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(p.metaLine, color = AppText.copy(alpha = 0.7f), fontSize = 13.sp)
                            }
                        }
                        val parentTarget = remember(p.type, p.uri, p.name, p.ownerName, p.coverUri) {
                            when (p.type) {
                                "playlist" -> io.github.thake.soundnest.feature.parental.ui.ParentBlockTarget(
                                    io.github.thake.soundnest.feature.parental.ui.ParentRuleKind.PLAYLIST,
                                    p.uri,
                                    p.name,
                                    p.ownerName,
                                    p.coverUri,
                                )
                                "album" -> io.github.thake.soundnest.feature.parental.ui.ParentBlockTarget(
                                    io.github.thake.soundnest.feature.parental.ui.ParentRuleKind.ALBUM,
                                    p.uri,
                                    p.name,
                                    p.ownerName,
                                    p.coverUri,
                                )
                                else -> null
                            }
                        }
                        if (parentTarget != null) {
                            io.github.thake.soundnest.feature.parental.ui.ParentInlineBlockPill(
                                target = parentTarget,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                    }
                }
                val actions: @Composable () -> Unit = {
                    // Action row: save, shuffle, big green play. Each small glyph answers in a 48 dp
                    // square (26-28 dp targets were hard to hit); the start padding and the gaps give
                    // back the difference, so the glyphs barely move (a few dp apart where their
                    // centres were under 48 dp apart).
                    val addFirst = p.type == "playlist" && vm.editableContext.value
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = if (addFirst) 10.dp else 6.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
                    ) {
                        // Own playlist: "Add to this playlist" (search, suggestions), where the small cover was.
                        if (addFirst) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(io.github.thake.soundnest.core.ui.look.MinTouch).pressable(0.9f) { addingSongs = true }
                                    .wrapContentSize().size(36.dp).clip(CircleShape).background(AppText.copy(alpha = 0.12f)),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_songs_title), tint = AppText, modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                        // Saving is for other people's playlists: the user's own are in the library already.
                        if (p.type != "liked" && !(p.type == "playlist" && ownPlaylist)) {
                            LikeButton(liked = SongPlayer.library[p.uri] ?: contextSaved, size = 28.dp, touch = io.github.thake.soundnest.core.ui.look.MinTouch) { vm.toggleContextSaved(p) }
                        }
                        if (p.type == "playlist" || p.type == "album" || p.type == "liked" || p.type == "liked-artist") {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.content_description_context_menu), tint = AppText.copy(alpha = 0.8f),
                                modifier = Modifier.size(io.github.thake.soundnest.core.ui.look.MinTouch).pressable(0.9f) { if (p.type == "playlist") playlistMenu = true else albumMenu = true }
                                    .wrapContentSize().size(26.dp))
                        }
                        // Sorting stays in reach in the header (the search field above the songs is folded).
                        if (findable && all.isNotEmpty()) {
                            TrackSortButton(sort) { sort = it; TrackOrder.save(ctx, p.uri, it) }
                        }
                        // "Move": a handle on each song, to put them in another order (again: done).
                        if (canMove) {
                            Icon(Icons.Default.DragHandle,
                                contentDescription = stringResource(if (moving) R.string.done else R.string.edit_playlist_items_move_chip),
                                tint = if (moving) SpotifyGreen else AppText.copy(alpha = 0.8f),
                                modifier = Modifier.size(io.github.thake.soundnest.core.ui.look.MinTouch).pressable(0.9f) { moving = !moving }.wrapContentSize().size(26.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        val smart = policy.effectiveAllowAutoplayAndRadio && SmartShuffle.on.value == p.uri
                        // The glyph and its dot light up in a short fade instead of in one frame.
                        val shuffleLit = shuffle || smart
                        val shuffleTint by androidx.compose.animation.animateColorAsState(
                            if (shuffleLit) SpotifyGreen else AppText.copy(alpha = 0.8f),
                            if (io.github.thake.soundnest.core.ui.look.rememberReduceMotion()) androidx.compose.animation.core.snap()
                            else androidx.compose.animation.core.tween(io.github.thake.soundnest.core.ui.look.Motion.FADE_MS),
                            label = "shuffleTint",
                        )
                        val shuffleDot by androidx.compose.animation.core.animateFloatAsState(
                            if (shuffleLit) 1f else 0f,
                            if (io.github.thake.soundnest.core.ui.look.rememberReduceMotion()) androidx.compose.animation.core.snap()
                            else androidx.compose.animation.core.tween(io.github.thake.soundnest.core.ui.look.Motion.FADE_MS),
                            label = "shuffleDot",
                        )
                        Box {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_player_shuffle),
                            contentDescription = stringResource(if (smart) R.string.smart_shuffle_title else R.string.play_mode_item_shuffle_title),
                            tint = shuffleTint,
                            modifier = Modifier
                                .size(io.github.thake.soundnest.core.ui.look.MinTouch)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    when {
                                        // The list does not play yet: it starts shuffled, from a random song.
                                        !isThisContext && !smart && p.tracks.isNotEmpty() -> {
                                            playerVm.updateShuffleState(true)
                                            vm.play(p, p.tracks.random(), sort)
                                        }
                                        smart -> { SmartShuffle.on.value = null; playerVm.updateShuffleState(false) }
                                        shuffle && policy.effectiveAllowAutoplayAndRadio && SmartShuffle.canPlay(p.uri) -> {
                                            SmartShuffle.on.value = p.uri
                                            android.widget.Toast.makeText(context, context.getString(R.string.smart_shuffle_on), android.widget.Toast.LENGTH_SHORT).show()
                                            if (isThisContext) vm.playSmart(p, p.tracks.firstOrNull { it.id == currentSongId })
                                        }
                                        else -> playerVm.updateShuffleState(!shuffle)
                                    }
                                }
                                .wrapContentSize()
                                .size(26.dp)
                                .drawWithContent {
                                    drawContent()
                                    if (shuffleDot > 0f) drawCircle(SpotifyGreen.copy(alpha = shuffleDot), radius = 2.dp.toPx(),
                                        center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height + 7.dp.toPx()))
                                },
                        )
                        // At the glyph's corner, 11 dp in from the 48 dp touch square's.
                        if (smart) Icon(
                            androidx.compose.material.icons.Icons.Default.AutoAwesome, contentDescription = null, tint = SpotifyGreen,
                            modifier = Modifier.align(Alignment.TopEnd).offset(x = (-5).dp, y = 5.dp).size(11.dp),
                        )
                        }
                        Spacer(Modifier.width(9.dp))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(56.dp)
                                .pressable(pressedScale = 0.92f) {
                                    when {
                                        isThisContext && playing -> SongPlayer.pause()
                                        isThisContext -> SongPlayer.play()
                                        p.tracks.isNotEmpty() && SmartShuffle.on.value == p.uri -> vm.playSmart(p, null)
                                        p.tracks.isNotEmpty() -> vm.play(p, if (shuffle) p.tracks.random() else sort.apply(p.tracks).first(), sort)
                                    }
                                }
                                .clip(CircleShape)
                                .background(SpotifyGreen),
                        ) {
                            io.github.thake.soundnest.core.ui.look.PlayPauseIcon(
                                playing = isThisContext && playing,
                                contentDescription = if (isThisContext && playing) stringResource(R.string.content_description_pause_button) else stringResource(R.string.content_description_play_button),
                                tint = Color.Black,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val side = minOf(maxWidth * 0.66f, (window.heightDp * 0.4f).dp, 360.dp)
                    // The action row needs about 380 dp: beside the cover only where that is left,
                    // under both otherwise (a foldable, a portrait tablet).
                    val actionsBeside = maxWidth - side - 24.dp >= 380.dp
                    if (window.large) {
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp),
                            ) {
                                cover(side)
                                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                    details()
                                    if (actionsBeside) actions()
                                }
                            }
                            if (!actionsBeside) actions()
                        }
                    } else Column(Modifier.fillMaxWidth()) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) { cover(side) }
                        Spacer(Modifier.height(20.dp))
                        details()
                        actions()
                    }
                }
            }
        }
        // Liked Songs: a track unliked anywhere leaves the list right away.
        if (vm.collabLocked.value && !vm.editableContext.value) item(key = "collab-locked") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp)).background(AppText.copy(alpha = 0.06f)).padding(12.dp),
            ) {
                Icon(Icons.Outlined.Group, contentDescription = null, tint = AppMuted, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.playlist_collab_locked, p.ownerName.ifBlank { stringResource(R.string.playlist_collab_owner) }),
                    color = AppMuted, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
            }
        }
        if (findable && all.isNotEmpty()) item(key = "find") {
            // Kept while it is being typed in: cleared with its X, it folded away under the user's finger.
            var findFocused by remember { mutableStateOf(false) }
            androidx.compose.animation.AnimatedVisibility(extrasShown || find.isNotBlank() || findFocused, enter = UnfoldIn, exit = FoldOut) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)) {
                io.github.thake.soundnest.core.ui.look.FindInListField(
                    find, { find = it },
                    stringResource(if (p.type == "liked") R.string.find_in_context_edit_text_liked_songs_hint else R.string.find_in_context_edit_text_playlist_hint),
                    Modifier.weight(1f),
                    onFocused = { findFocused = it },
                )
            }
            }
        }
        if (shown.first.isEmpty() && find.isNotBlank()) item(key = "find-empty") {
            io.github.thake.soundnest.core.ui.look.FindInListEmpty(stringResource(R.string.assisted_curation_search_empty_view_no_result_title, find.trim()))
        }
        if (reordering) item(key = "reorder-hint") {
            Text(stringResource(R.string.playlist_reorder_hint), color = AppMuted, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        }
        // While a song is moved: the rows in the order under the finger.
        val (listed, keys) = drag.order?.takeIf { reordering }?.let { o -> o to o.map(PlaylistReorder::key) } ?: shown
        itemsIndexed(listed, key = { i, _ -> keys[i] }) { _, song ->
            io.github.thake.soundnest.feature.player.ui.SwipeToQueue(song, if (reordering) movableRow(drag, song, tone(0xFF2A2A2A)) else animatedRow()) { TrackRow(
                song = song,
                showCover = p.type != "album",
                isCurrent = song.id == currentSongId,
                saved = io.github.thake.soundnest.core.playback.trackSaved(song.spotifyTrackId, saved),
                addedBy = vm.adders.value.let { a -> a["row:" + song.rowUid] ?: a["song:" + song.spotifyTrackId] },
                onClick = { dismissKeyboard(); if (SmartShuffle.on.value == p.uri) vm.playSmart(p, song) else vm.play(p, song, sort) },
                onMore = { menuSong = song },
                onSaved = { savedInSong = song },
                handle = if (reordering) drag.handle(song.rowUid) else null,
            ) }
        }
        // "Recommended songs", as under an own playlist in the official apps (an empty one too).
        if (p.type == "playlist" && vm.editableContext.value && find.isBlank() && policy.effectiveAllowAutoplayAndRadio) recommendedItems(vm, p, ctx, currentSongId)
        // Phone files added to this Spotify playlist on this phone (Spotify can't hold them).
        if (p.type == "playlist") {
            val playlistId = p.uri.substringAfterLast(":")
            val additions = io.github.thake.soundnest.feature.localfiles.data.LocalPlaylists.additions[playlistId].orEmpty()
            if (additions.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.local_added_section), color = AppText, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp, 20.dp, 16.dp, 4.dp))
                }
                localItems(additions, p.name) { io.github.thake.soundnest.feature.localfiles.data.LocalPlaylists.removeFromSpotify(playlistId, it.key) }
            }
        }
        // An album: Spotify's "More like this", albums close to its first song.
        // Under an album's songs: its release date, label and copyright lines.
        if (p.type == "album" && p.footer.isNotEmpty() && find.isBlank()) item(key = "album-footer") {
            androidx.compose.foundation.layout.Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                p.footer.forEachIndexed { i, line ->
                    Text(line, color = if (i == 0) AppText else AppMuted, fontSize = if (i == 0) 14.sp else 12.sp, modifier = Modifier.padding(vertical = 1.dp))
                }
            }
        }
        if (p.type == "album" && policy.effectiveAllowAutoplayAndRadio) item(key = "similar") { SimilarAlbums(navController, p.tracks.firstOrNull()?.spotifyTrackId.orEmpty(), p.uri) }
        item { Spacer(Modifier.height(io.github.thake.soundnest.core.ui.look.bottomBarsSpace())) }
    }
    }
    io.github.thake.soundnest.core.ui.look.FastScrollbar(listState, Modifier.align(Alignment.CenterEnd))
    // As in the official app: once the header has mostly scrolled away, a bar in the page's colour
    // fades in at the top with the back arrow and the name; the list no longer runs bare under the
    // status bar. Its opacity follows the scroll in the draw phase (no recomposition per frame).
    val barAlpha = remember(listState) {
        androidx.compose.runtime.derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f else {
                val h = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.toFloat() ?: return@derivedStateOf 0f
                ((listState.firstVisibleItemScrollOffset - h * 0.6f) / (h * 0.2f)).coerceIn(0f, 1f)
            }
        }
    }
    val barShown by remember(listState) { androidx.compose.runtime.derivedStateOf { barAlpha.value > 0f } }
    if (barShown) Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = barAlpha.value }
            .background(headerTint(tint, 0.45f))
            .statusBarsPadding()
            .height(56.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 8.dp).wideClickable(horizontal = 8.dp, vertical = 8.dp) { navController.navigateUp() }.size(40.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.accessibility_navigate_back), tint = AppText)
        }
        // Centred, as on the artist page.
        Text(p.name, color = AppText, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f).padding(end = 56.dp).titleMarquee(iterations = 3))
    }
    // Opened from a collaborative-playlist invitation (…?pt=): the playlist is joined from here.
    val invite by io.github.thake.soundnest.core.catalog.InviteLinks.pending.collectAsState()
    var joining by remember { mutableStateOf(false) }
    val offered = invite?.takeIf { p.type == "playlist" && it.uri == p.uri && !vm.editableContext.value }
    if (offered != null) Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(start = 16.dp, end = 16.dp, bottom = io.github.thake.soundnest.core.ui.look.bottomBarsSpace())
            .clip(RoundedCornerShape(12.dp))
            .background(tone(0xFF282828))
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Text(stringResource(R.string.playlist_invite_banner), color = AppText, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(end = 12.dp))
        androidx.compose.material3.Button(
            enabled = !joining,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = Color.Black),
            onClick = {
                joining = true
                io.github.thake.soundnest.core.catalog.InviteLinks.join(context, offered, p) { ok ->
                    joining = false
                    if (ok) {
                        vm.editableContext.value = true
                        vm.contextSaved.value = true
                        SongPlayer.library[p.uri] = true
                        vm.reload()
                        android.widget.Toast.makeText(context, context.getString(R.string.playlist_invite_joined), android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            },
        ) { Text(stringResource(R.string.playlist_invite_join), fontWeight = FontWeight.Bold) }
    }
    androidx.compose.material3.SnackbarHost(
        hostState = snackbar,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = io.github.thake.soundnest.core.ui.look.bottomBarsSpace()),
    ) { data ->
        androidx.compose.material3.Snackbar(
            snackbarData = data,
            containerColor = tone(0xFF282828),
            contentColor = AppText,
            actionContentColor = SpotifyGreen,
        )
    }
    }
}

private val UnfoldIn = androidx.compose.animation.expandVertically(androidx.compose.animation.core.tween(280, easing = androidx.compose.animation.core.FastOutSlowInEasing)) +
    androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220, delayMillis = 60))
private val FoldOut = androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(220)) +
    androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120))

/**
 * The pull that unfolds a page's folded parts (search, Blend line). It sits between the list and
 * the pull-to-refresh: a drag past the top goes here first, and the whole gesture is kept from
 * the refresh, so unfolding never also reloads the page.
 */
private class ExtrasReveal : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
    var enabled = false
    var onReveal: () -> Unit = {}
    /** This drag unfolded them: the rest of it is not a refresh pull. */
    private var swallowing = false

    override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset,
                              source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
        if (source != androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput || available.y <= 0f) return androidx.compose.ui.geometry.Offset.Zero
        if (enabled && !swallowing) { swallowing = true; onReveal() }
        return if (swallowing) available else androidx.compose.ui.geometry.Offset.Zero
    }

    override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
        if (!swallowing) return androidx.compose.ui.unit.Velocity.Zero
        swallowing = false
        return available
    }
}

/** The suggestions under an own playlist: tap to listen, "+" to add, "Refresh" for others. */
private fun androidx.compose.foundation.lazy.LazyListScope.recommendedItems(vm: ContextViewModel, p: ContextPageModel, context: android.content.Context, currentSongId: Int) {
    item(key = "recs-title") {
        LaunchedEffect(p.uri) { vm.loadRecs(p) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(16.dp, 28.dp, 8.dp, 4.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.recommended_songs), color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(if (p.tracks.isEmpty()) R.string.recommended_songs_from_name else R.string.recommended_songs_from_songs),
                    color = AppMuted, fontSize = 13.sp)
            }
            androidx.compose.material3.TextButton(onClick = { vm.loadRecs(p, refresh = true) }) {
                Text(stringResource(R.string.recommended_songs_refresh), color = AppText, fontWeight = FontWeight.Bold)
            }
        }
    }
    val recs = vm.recs.value
    if (recs == null) item(key = "recs-loading") { io.github.thake.soundnest.core.ui.classic.SkeletonList(count = 3) }
    else items(recs, key = { "rec-" + it.spotifyTrackId }) { song ->
        RecommendedRow(
            song,
            isCurrent = song.id == currentSongId,
            onPlay = { SongPlayer.playSong(song.url, context, songs = listOf(song)) },
            onAdd = {
                vm.takeRec(p, song)
                io.github.thake.soundnest.core.catalog.SpotifySync.addTrackToPlaylist(context, p.uri.substringAfterLast(':'), song.spotifyTrackId) { ok ->
                    if (ok) vm.reload()
                    else android.widget.Toast.makeText(context, context.getString(R.string.playlist_change_refused), android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.animateItem(),
        )
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun RecommendedRow(song: SongsModel, isCurrent: Boolean, onPlay: () -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().clickable(onClick = onPlay).padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        GlideImage(model = song.coverUri, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)))
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
            // The song playing is green, as in the playlist above.
            Text(song.title, color = if (isCurrent) SpotifyGreen else AppText, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.singer, color = AppMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        androidx.compose.material3.IconButton(onClick = onAdd) {
            Icon(androidx.compose.material.icons.Icons.Outlined.AddCircleOutline, contentDescription = stringResource(R.string.recommended_songs_add), tint = AppText)
        }
    }
}

/** The people of a collaborative playlist: owner first, then collaborators. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun MembersSheet(
    members: List<io.github.thake.soundnest.api.PlaylistMember>,
    navController: NavController? = null,
    ownPlaylist: Boolean,
    me: String?,
    canLeave: Boolean,
    /** Owner: [collaborator] true/false changes the member's level, null removes the member. */
    onChange: (io.github.thake.soundnest.api.PlaylistMember, Boolean?) -> Unit,
    onLeave: () -> Unit,
    onDismiss: () -> Unit,
) {
    io.github.thake.soundnest.core.ui.look.LookSheet(onDismissRequest = onDismiss, containerColor = tone(0xFF282828)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(stringResource(R.string.playlist_members_title), color = AppText, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(20.dp, 0.dp, 20.dp, 12.dp))
            members.forEach { m ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    io.github.thake.soundnest.feature.accounts.ui.ProfileAvatar(m.imageUrl, m.name, 44.dp)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(m.name, color = AppText, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(when {
                            m.isOwner -> R.string.playlist_member_owner
                            m.isCollaborator -> R.string.playlist_member_collab
                            else -> R.string.playlist_member_listener
                        }), color = AppMuted, fontSize = 13.sp)
                    }
                    // As the Web Player's "Shared with": the owner's menu per member, "Leave playlist" on a guest's own row.
                    val ownRow = canLeave && me != null && m.username == me
                    if (!m.isOwner && (ownPlaylist || ownRow)) {
                        var open by remember { mutableStateOf(false) }
                        Box {
                            Icon(Icons.Default.MoreVert, contentDescription = null, tint = AppText,
                                modifier = Modifier.size(40.dp).clip(CircleShape).clickable { open = true }.padding(8.dp))
                            androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = tone(0xFF282828)) {
                                @Composable fun item(label: Int, action: () -> Unit) = androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(stringResource(label), color = AppText) },
                                    onClick = { open = false; action() })
                                if (ownPlaylist) {
                                    if (m.isCollaborator) item(R.string.playlist_member_remove_collab) { onChange(m, false) }
                                    else item(R.string.playlist_member_make_collab) { onChange(m, true) }
                                    item(R.string.playlist_member_remove) { onChange(m, null) }
                                } else item(R.string.playlist_leave) { onLeave() }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** How long a greyed track is given to start before the app says why it did not. */
private const val UNAVAILABLE_CHECK_MS = 4_000L

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun TrackRow(
    song: SongsModel,
    showCover: Boolean,
    isCurrent: Boolean,
    saved: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
    /** The green check tapped: where the song is saved, to take it out (as in the official app). */
    onSaved: () -> Unit = {},
    addedBy: io.github.thake.soundnest.api.PlaylistMember? = null,
    /** The song can be moved: a drag on this handle carries it. */
    handle: Modifier? = null,
) {
    // A greyed row is still tried (see the click): the tap is followed by a check, not a refusal.
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val appContext = androidx.compose.ui.platform.LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            // A song marked "Not interested" is dimmed too (lists skip it; a tap still plays it).
            .then(if (song.playable && !io.github.thake.soundnest.feature.library.hiddensongs.settings.HiddenSongs.isHidden(song.spotifyTrackId)) Modifier else Modifier.alpha(0.4f))
            .clickable {
                onClick()
                // Spotify's playability flag is per market and can be wrong (testers heard greyed
                // "region" tracks play in the official app), so a greyed row is still tried. If the
                // player has not moved to it a few seconds later, the reason is said — a tap that
                // appears to do nothing is what makes the app look broken.
                if (!song.playable) scope.launch {
                    kotlinx.coroutines.delay(UNAVAILABLE_CHECK_MS)
                    val playing = SongPlayer.nowPlaying.value.uri
                    if (song.spotifyTrackId.isNotBlank() && !playing.endsWith(song.spotifyTrackId)) {
                        android.widget.Toast.makeText(
                            appContext,
                            io.github.thake.soundnest.R.string.track_unavailable_region,
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
            // The 8 dp above and below are the cover's and the words' own, not the row's: the ⋮
            // answers in a 48 dp square (its 24 dp glyph was the whole target) and would make the
            // row taller. The end padding gives back the square's 12 dp beside the glyph.
            .padding(start = 16.dp, end = if (handle == null) 4.dp else 16.dp)
    ) {
        if (showCover) {
            GlideImage(
                model = song.coverUri,
                contentDescription = "",
                contentScale = ContentScale.Crop,
                modifier = Modifier.padding(vertical = 8.dp).size(48.dp),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                song.title,
                color = if (isCurrent) SpotifyGreen else AppText,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                if (song.explicit) {
                    ExplicitBadge()
                    Spacer(Modifier.width(6.dp))
                }
                Text(song.singer, color = AppText.copy(alpha = 0.7f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // Collaborative playlist: who added this song.
        addedBy?.let { m ->
            io.github.thake.soundnest.feature.accounts.ui.ProfileAvatar(m.imageUrl, m.name, 20.dp)
            // 14 dp to the ⋮ glyph: its touch square takes 12 of them.
            Spacer(Modifier.width(2.dp))
        }
        if (saved) {
            // A 40 dp target around the 20 dp disc: the disc alone was too small to hit (reported).
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onSaved),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(20.dp).clip(CircleShape).background(SpotifyGreen),
                ) {
                    Icon(Icons.Default.Check, contentDescription = stringResource(R.string.liked_song), tint = Color.Black, modifier = Modifier.size(14.dp))
                }
            }
            // No gap: the ⋮'s touch square starts 12 dp before its glyph.
        }
        Icon(
            Icons.Default.MoreVert,
            contentDescription = stringResource(R.string.content_description_context_menu),
            tint = AppText.copy(alpha = 0.7f),
            modifier = Modifier.size(io.github.thake.soundnest.core.ui.look.MinTouch).pressable(0.96f) { onMore() }
                .wrapContentSize()
                .size(24.dp)
                ,
        )
        handle?.let { h ->
            Icon(
                Icons.Default.DragHandle,
                contentDescription = stringResource(R.string.edit_playlist_items_move_chip),
                tint = AppText.copy(alpha = 0.7f),
                modifier = h.size(28.dp),
            )
        }
    }
}

/** The Liked Songs artwork: purple-to-blue gradient with a white heart. */
@Composable
private fun LikedCover(modifier: Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF450AF5), Color(0xFF8E8EE5), Color(0xFFC4EFD9)))),
    ) {
        Icon(Icons.Default.Favorite, contentDescription = null, tint = AppText, modifier = Modifier.fillMaxWidth(0.4f).aspectRatio(1f))
    }
}

/** Spotify's sort menu: picking the sort already chosen flips its direction (the arrow shows it). */
@Composable
private fun TrackSortButton(sort: TrackOrder, onSort: (TrackOrder) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Icon(androidx.compose.material.icons.Icons.Default.SwapVert,
            contentDescription = stringResource(R.string.filter_and_sort_sort_section_title),
            tint = if (sort.isCustom) AppText.copy(alpha = 0.8f) else SpotifyGreen,
            modifier = Modifier.size(io.github.thake.soundnest.core.ui.look.MinTouch).pressable(0.9f) { open = true }.wrapContentSize().size(26.dp))
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = tone(0xFF282828)) {
            Text(stringResource(R.string.filter_and_sort_sort_section_title), color = AppMuted, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            TrackSort.entries.forEach { option ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(stringResource(option.label), color = if (option == sort.sort) SpotifyGreen else AppText) },
                    trailingIcon = if (option == sort.sort) { {
                        Icon(if (sort.reversed) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = null, tint = SpotifyGreen, modifier = Modifier.size(18.dp))
                    } } else null,
                    onClick = { open = false; onSort(if (option == sort.sort) sort.copy(reversed = !sort.reversed) else TrackOrder(option)) },
                )
            }
        }
    }
}


/** "More like this" under an album: Spotify's similar albums, from its first song. */
@OptIn(com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi::class)
@Composable
private fun SimilarAlbums(navController: NavController, firstTrackId: String, albumUri: String) {
    val albums by androidx.compose.runtime.produceState(emptyList<io.github.thake.soundnest.api.Album>(), firstTrackId) {
        if (firstTrackId.isBlank()) return@produceState
        value = runCatching { io.github.thake.soundnest.host.android.SoundNestRuntime.api.catalog.similarAlbums("spotify:track:$firstTrackId", 12) }
            .getOrDefault(emptyList()).filter { it.uri != albumUri }
    }
    if (albums.isEmpty()) return
    Column(Modifier.padding(top = 24.dp)) {
        Text(stringResource(R.string.more_like_this), color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(albums.size) { i ->
                val a = albums[i]
                val artists = a.artists.joinToString(", ") { it.name }
                Column(Modifier.width(140.dp).pressable(0.96f) {
                    navController.navigate(io.github.thake.soundnest.core.ui.navigation.albumRoute(a.name, artists, a.uri.substringAfterLast(':')))
                }) {
                    GlideImage(model = a.images.maxByOrNull { it.width ?: 0 }?.url, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(140.dp).clip(RoundedCornerShape(4.dp)))
                    Text(a.name, color = AppText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    Text(artists, color = AppMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
