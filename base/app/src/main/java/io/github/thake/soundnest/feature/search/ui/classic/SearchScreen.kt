package io.github.thake.soundnest.feature.search.ui.classic


import io.github.thake.soundnest.core.ui.navigation.ClassicMenuAvatarButton
import io.github.thake.soundnest.core.ui.look.cappedPageWidth
import io.github.thake.soundnest.core.ui.look.secondaryClick
import io.github.thake.soundnest.core.ui.classic.tone
import io.github.thake.soundnest.core.ui.classic.AppAccent
import io.github.thake.soundnest.core.ui.classic.AppText
import io.github.thake.soundnest.core.ui.classic.AppMuted

import kotlinx.coroutines.launch
import io.github.thake.soundnest.core.ui.look.SearchClearButton
import io.github.thake.soundnest.core.ui.look.pressable
import io.github.thake.soundnest.feature.search.ui.launchVoiceSearch
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.thake.soundnest.feature.player.ui.SongOptionsSheet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.catalog.Response
import io.github.thake.soundnest.core.model.SearchResults
import io.github.thake.soundnest.core.model.SongsModel
import io.github.thake.soundnest.core.playback.SongPlayer
import io.github.thake.soundnest.feature.parental.ui.ParentBlockActions
import io.github.thake.soundnest.feature.parental.ui.ParentBlockTarget
import io.github.thake.soundnest.feature.parental.ui.ParentQuickBlockSheet
import io.github.thake.soundnest.feature.parental.ui.ParentRuleKind
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.ui.rememberAdminUnlocked
import io.github.thake.soundnest.feature.parental.settings.rememberParentalPolicy
import io.github.thake.soundnest.core.ui.navigation.Routes
import io.github.thake.soundnest.core.ui.navigation.albumRoute
import io.github.thake.soundnest.core.ui.navigation.artistRoute
import io.github.thake.soundnest.core.ui.navigation.categoryRoute
import io.github.thake.soundnest.core.ui.navigation.playlistRoute
import io.github.thake.soundnest.core.ui.navigation.showRoute
import io.github.thake.soundnest.core.ui.classic.AppBackground
import io.github.thake.soundnest.core.ui.classic.AppPalette
import io.github.thake.soundnest.feature.search.ui.SearchViewModel
import io.github.thake.soundnest.feature.recap.settings.toRecentItem
import io.github.thake.soundnest.feature.recap.settings.toSong
import io.github.thake.soundnest.core.ui.classic.ExplicitBadge
import io.github.thake.soundnest.feature.player.ui.classic.AddButton


@Composable
fun SearchScreen(navController: NavController) {
    val searchViewModel : SearchViewModel = viewModel()
    val results by searchViewModel.results.collectAsState()
    val policy = rememberParentalPolicy()

    // Results are live search hits (or empty); never gate the search UI on them.
    val rawResults = (results as? Response.Success)?.data ?: SearchResults()
    val searchResults = remember(rawResults, policy) {
        ParentBlockActions.filterSearchResults(rawResults, policy)
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(AppBackground.toArgb()))
    ) {
        SumUpSearchScreen(navController = navController, searchResults, searchViewModel,
            loading = results is Response.Loading, failed = results is Response.Error)
    }
}


@OptIn(ExperimentalFoundationApi::class, ExperimentalGlideComposeApi::class)
@Composable
fun SumUpSearchScreen(
    navController: NavController,
    results: SearchResults,
    searchViewModel: SearchViewModel,
    loading: Boolean = false,
    failed: Boolean = false,
) {
    val context = LocalContext.current
    val policy = rememberParentalPolicy()
    val adminUnlocked = rememberAdminUnlocked()

    val text = searchViewModel.query
    // Recents are the *items opened from results* (songs/artists/albums), not the
    // typed queries, and only appear once the user taps into the search bar.
    var searchFocused by remember { mutableStateOf(false) }
    var recents by remember(policy) {
        mutableStateOf(loadClassicSearchRecents(context, policy))
    }
    var menuSong by remember { mutableStateOf<SongsModel?>(null) }
    var quickBlockTarget by remember { mutableStateOf<ParentBlockTarget?>(null) }
    // Re-tapping the Search tab while already on it focuses the field (official behaviour)
    // and shows the recents again; no query is re-run. The field lives in the sticky bar,
    // so a focus requester is handed down to it.
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val searchFieldFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) {
        io.github.thake.soundnest.core.ui.classic.TabReselect.search.collect {
            // Focus first: the keyboard should be up as soon as the tab is tapped.
            // Not attached yet (screen being torn down): the tap is simply dropped.
            runCatching { searchFieldFocus.requestFocus() }
            if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
                listState.animateScrollToItem(0)
            }
        }
    }
    // Spotify's filter chips: Top (mixed), or one kind of result.
    val filter = searchViewModel.filter
    menuSong?.let { sel ->
        SongOptionsSheet(
            song = sel,
            navController = navController,
            context = context,
            onDismiss = { menuSong = null },
        )
    }
    quickBlockTarget?.let { target ->
        ParentQuickBlockSheet(
            target = target,
            onDismiss = { quickBlockTarget = null },
        )
    }

    // A tap on a result puts the keyboard away; the query and the list stay as they are.
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val dismissKeyboard = { keyboard?.hide(); focusManager.clearFocus() }
    val recordRecent: (io.github.thake.soundnest.feature.recap.settings.RecentItem) -> Unit = { item ->
        dismissKeyboard()
        io.github.thake.soundnest.feature.recap.settings.addRecentItem(context, item)
        recents = loadClassicSearchRecents(context, policy)
    }
    // One relevance-mixed list (songs, artists, albums interleaved) instead of
    // separate type sections — matches how most music apps present search.
    val mixed = remember(results, filter) { filterSearchResults(results, filter) }
    val suggestions by androidx.compose.runtime.produceState(emptyList<String>(), text) {
        value = emptyList()
        if (text.isBlank()) return@produceState
        kotlinx.coroutines.delay(300)
        value = runCatching { io.github.thake.soundnest.host.android.SoundNestRuntime.api.search.suggestions(text.trim(), 6) }.getOrDefault(emptyList())
            .filter { !it.equals(text.trim(), ignoreCase = true) }
    }

    LazyColumn(
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = io.github.thake.soundnest.core.ui.look.bottomBarsSpace()),
        modifier = Modifier
            .fillMaxSize()
            // The field and the results capped at a readable width, centred, on wide windows
            // (they ran 1800 dp long); the browse grid spreads over that width.
            .cappedPageWidth()
            .background(Color(AppBackground.toArgb()))
            .statusBarsPadding()

    ){
        item{
            SearchTopBar()
        }
        stickyHeader {
            SearchStickyBar(
                text,
                onFocusChange = { searchFocused = it },
                focusRequester = searchFieldFocus,
            ) {
                searchViewModel.search(it)
            }
        }

        if (text.isBlank()) {
            if (searchFocused && recents.isNotEmpty()) {
                // ── Recent searches: the items the user opened (Spotify-style),
                // shown only once the search bar is focused ──
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp, 16.dp, 16.dp, 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            stringResource(R.string.assisted_curation_search_search_history_title),
                            color = AppText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(R.string.chat_content_picker_clear_recent_searches),
                            color = AppMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.pressable(0.96f) {
                                io.github.thake.soundnest.feature.recap.settings.clearRecentItems(context)
                                recents = emptyList()
                                // Spotify's own list too, so other devices forget them as well.
                                clearSpotifyRecents()
                            },
                        )
                    }
                }
                items(recents.size) { i ->
                    val item = recents[i]
                    val row: @Composable () -> Unit = { RecentItemRow(
                        item = item,
                        onClick = {
                            when (item.type) {
                                "song" -> item.toSong()?.let { song ->
                                    searchViewModel.startRadioFromSong(song)
                                    SongPlayer.playSong(song.url, context, songs = listOf(song), alone = true)
                                    searchViewModel.updateSongState(
                                        song.coverUri, song.title, song.singer, true, song.id, 0, song.album)
                                }
                                "artist" -> navController.navigate(artistRoute(item.name, item.key.takeIf { it != item.name }.orEmpty()))
                                "album" -> navController.navigate(albumRoute(item.name, item.singer, item.key.takeIf { it != item.name }.orEmpty()))
                                "show" -> navController.navigate(showRoute(item.key, item.name))
                                "playlist" -> navController.navigate(playlistRoute(item.key, item.name))
                            }
                        },
                        onRemove = {
                            io.github.thake.soundnest.feature.recap.settings.removeRecentItem(context, item)
                            recents = loadClassicSearchRecents(context, policy)
                        },
                    ) }
                    // A recent song slides into the queue like a result does.
                    val song = item.toSong()
                    if (song != null) io.github.thake.soundnest.feature.player.ui.SwipeToQueue(song, content = row) else row()
                }
            } else {
                // ── Spotify-style "Browse all" category grid ──
                item {
                    // Real Spotify opens a genre *catalogue* (a page of curated
                    // playlists) rather than running a keyword song search.
                    BrowseAllSection { genre, title ->
                        if (genre == NEW_RELEASES_GENRE) navController.navigate(io.github.thake.soundnest.core.ui.navigation.Routes.NewReleases.route)
                        else navController.navigate(categoryRoute(genre, title))
                    }
                }
            }
        } else {
            // Spotify's completions for what is typed: one tap searches it.
            if (suggestions.isNotEmpty()) item {
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    items(suggestions.size) { i ->
                        val q = suggestions[i]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(tone(0xFF2A2A2A))
                                .pressable(0.96f) { searchViewModel.search(q) }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            Icon(androidx.compose.material.icons.Icons.Default.Search, null, tint = AppMuted, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(q, color = AppText, fontSize = 13.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            item { SearchFilterChips(filter) { searchViewModel.filter = it } }
            // Loading, nothing found and a failure each say what they are (never an empty page).
            if (mixed.isEmpty() && results.shows.isEmpty() && results.episodes.isEmpty()) item {
                SearchStatus(text, loading, failed) { searchViewModel.retry() }
            }
            items(mixed.size) { i ->
                when (val row = mixed[i]) {
                    is SearchRow.Song -> io.github.thake.soundnest.feature.player.ui.SwipeToQueue(row.song) {
                        SearchSongRow(
                            song = row.song,
                            searchViewModel = searchViewModel,
                            onPlayed = { recordRecent(row.song.toRecentItem()) },
                            onLongClick = { menuSong = row.song },
                            showMoreButton = adminUnlocked,
                        )
                    }
                    is SearchRow.Artist -> SearchArtistRow(
                        artist = row.artist,
                        onMore = if (adminUnlocked && row.artist.id.isNotBlank()) ({
                            quickBlockTarget = ParentBlockTarget(
                                kind = ParentRuleKind.ARTIST,
                                id = row.artist.id,
                                title = row.artist.name,
                                subtitle = "Artist",
                                coverUri = row.artist.coverUri,
                            )
                        }) else null,
                    ) {
                        recordRecent(io.github.thake.soundnest.feature.recap.settings.RecentItem(
                            type = "artist",
                            key = row.artist.id.ifBlank { row.artist.name },
                            name = row.artist.name,
                            image = row.artist.coverUri,
                        ))
                        navController.navigate(artistRoute(row.artist.name, row.artist.id))
                    }
                    is SearchRow.Album -> SearchAlbumRow(
                        album = row.album,
                        onMore = if (adminUnlocked && row.album.spotifyId.isNotBlank()) ({
                            quickBlockTarget = ParentBlockTarget(
                                kind = ParentRuleKind.ALBUM,
                                id = row.album.spotifyId,
                                title = row.album.name,
                                subtitle = row.album.artists,
                                coverUri = row.album.coverUri,
                            )
                        }) else null,
                    ) {
                        recordRecent(io.github.thake.soundnest.feature.recap.settings.RecentItem(
                            type = "album",
                            key = row.album.spotifyId.ifBlank { row.album.name },
                            name = row.album.name,
                            singer = row.album.artists,
                            image = row.album.coverUri,
                        ))
                        navController.navigate(albumRoute(row.album.name, row.album.artists, row.album.spotifyId))
                    }
                    is SearchRow.Playlist -> SearchPlaylistRow(
                        playlist = row.playlist,
                        onMore = if (adminUnlocked && row.playlist.spotifyId.isNotBlank()) ({
                            quickBlockTarget = ParentBlockTarget(
                                kind = ParentRuleKind.PLAYLIST,
                                id = row.playlist.spotifyId,
                                title = row.playlist.name,
                                subtitle = row.playlist.subtitle,
                                coverUri = row.playlist.coverUri,
                            )
                        }) else null,
                    ) {
                        recordRecent(io.github.thake.soundnest.feature.recap.settings.RecentItem(
                            type = "playlist",
                            key = row.playlist.spotifyId,
                            name = row.playlist.name,
                            singer = row.playlist.subtitle,
                            image = row.playlist.coverUri,
                        ))
                        navController.navigate(playlistRoute(row.playlist.spotifyId, row.playlist.name))
                    }
                }
            }
            // ── Podcasts & Audiobooks: shows / books (→ detail) then individual episodes (→ play) ──
            if ((io.github.thake.soundnest.core.catalog.Features.PODCASTS || io.github.thake.soundnest.core.catalog.Features.AUDIOBOOKS || io.github.thake.soundnest.core.catalog.Features.AUDIBLE) && (filter == SearchFilter.Top || filter == SearchFilter.Podcasts)) {
            if (results.shows.isNotEmpty()) {
                val hasPodcasts = results.shows.any { !io.github.thake.soundnest.feature.spokenword.audible.data.AudibleUris.isAudibleUri(it.id) }
                val headerRes = if (hasPodcasts) R.string.assisted_curation_search_filter_podcasts else R.string.audiobooks_title
                item { SearchSectionHeader(stringResource(headerRes)) }
                items(results.shows.size) { i ->
                    val show = results.shows[i]
                    val isAudible = io.github.thake.soundnest.feature.spokenword.audible.data.AudibleUris.isAudibleUri(show.id)
                    SearchShowRow(
                        show = show,
                        onMore = if (adminUnlocked && !isAudible && show.id.isNotBlank()) ({
                            quickBlockTarget = ParentBlockTarget(
                                kind = ParentRuleKind.SHOW,
                                id = show.id,
                                title = show.name,
                                subtitle = show.publisher,
                                coverUri = show.coverUri,
                            )
                        }) else null,
                    ) {
                        recordRecent(io.github.thake.soundnest.feature.recap.settings.RecentItem(
                            type = "show",
                            key = show.id,
                            name = show.name,
                            singer = show.publisher,
                            image = show.coverUri,
                        ))
                        navController.navigate(showRoute(show.id, show.name))
                    }
                }
            }
            if (results.episodes.isNotEmpty()) {
                item { SearchSectionHeader(stringResource(R.string.assisted_curation_search_filter_episodes)) }
                items(results.episodes.size) { i ->
                    val ep = results.episodes[i]
                    SearchSongRow(
                        song = ep,
                        searchViewModel = searchViewModel,
                        onPlayed = { recordRecent(ep.toRecentItem()) },
                        onLongClick = { menuSong = ep },
                        showMoreButton = adminUnlocked,
                    )
                }
            }
            }
        }

    }
}

/** A single row in the search results: a track, an artist, or an album. */
sealed class SearchRow {
    data class Song(val song: SongsModel) : SearchRow()
    data class Artist(val artist: io.github.thake.soundnest.core.model.ArtistsModel) : SearchRow()
    data class Album(val album: io.github.thake.soundnest.core.model.AlbumsModel) : SearchRow()
    data class Playlist(val playlist: io.github.thake.soundnest.core.model.LibraryEntry) : SearchRow()
}

/** The search filter chips (official labels). Podcasts shows shows + episodes. */
@Composable
private fun SearchStatus(query: String, loading: Boolean, failed: Boolean, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp, 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            loading -> io.github.thake.soundnest.core.ui.classic.SkeletonList(count = 7, modifier = Modifier.padding(top = 0.dp))
            failed -> io.github.thake.soundnest.core.ui.look.ErrorRetry(stringResource(R.string.search_failed), Modifier.padding(0.dp), onRetry)
            else -> Text(stringResource(R.string.assisted_curation_search_empty_view_no_result_title, query.trim()),
                color = AppText, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

enum class SearchFilter(@androidx.annotation.StringRes val label: Int) {
    Top(R.string.assisted_curation_search_filter_top),
    Songs(R.string.assisted_curation_search_filter_songs),
    Artists(R.string.assisted_curation_search_filter_artists),
    Albums(R.string.assisted_curation_search_filter_albums),
    Playlists(R.string.assisted_curation_search_filter_playlists),
    Podcasts(R.string.assisted_curation_search_filter_podcasts),
}

private fun filterSearchResults(results: SearchResults, filter: SearchFilter): List<SearchRow> = when (filter) {
    SearchFilter.Top -> mixSearchResults(results)
    SearchFilter.Songs -> results.songs.map { SearchRow.Song(it) }
    SearchFilter.Artists -> results.artists.map { SearchRow.Artist(it) }
    SearchFilter.Albums -> results.albums.map { SearchRow.Album(it) }
    SearchFilter.Playlists -> results.playlists.map { SearchRow.Playlist(it) }
    SearchFilter.Podcasts -> emptyList()
}

@Composable
private fun SearchFilterChips(selected: SearchFilter, onSelect: (SearchFilter) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        val filters = SearchFilter.entries.filter {
            it != SearchFilter.Podcasts || io.github.thake.soundnest.core.catalog.Features.PODCASTS || io.github.thake.soundnest.core.catalog.Features.AUDIOBOOKS || io.github.thake.soundnest.core.catalog.Features.AUDIBLE
        }
        items(filters.size) { i ->
            val f = filters[i]
            val on = f == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.height(32.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (on) io.github.thake.soundnest.core.ui.classic.AppAccent else tone(0xFF2A2A2A))
                    .pressable(0.96f) { onSelect(if (on && f != SearchFilter.Top) SearchFilter.Top else f) }
                    .padding(horizontal = 14.dp),
            ) {
                Text(stringResource(f.label), color = if (on) Color.Black else AppText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/**
 * Interleaves the three result types into one list, weighted toward songs
 * (2 songs per artist+album cycle) so the list reads as mixed rather than
 * grouped, while songs — the most common search intent — stay prominent.
 */
private fun mixSearchResults(results: SearchResults): List<SearchRow> {
    val songs = results.songs.iterator()
    val artists = results.artists.iterator()
    val albums = results.albums.iterator()
    // A few playlists in the mix; the Playlists chip lists them all.
    val playlists = results.playlists.take(5).iterator()
    val out = ArrayList<SearchRow>()
    while (songs.hasNext() || artists.hasNext() || albums.hasNext() || playlists.hasNext()) {
        repeat(2) { if (songs.hasNext()) out += SearchRow.Song(songs.next()) }
        if (artists.hasNext()) out += SearchRow.Artist(artists.next())
        if (albums.hasNext()) out += SearchRow.Album(albums.next())
        if (playlists.hasNext()) out += SearchRow.Playlist(playlists.next())
    }
    return out
}

/** A recent item row (song/artist/album/show the user opened), with remove (x). */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun RecentItemRow(
    item: io.github.thake.soundnest.feature.recap.settings.RecentItem,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.pressable(0.96f) { onClick() }
            .fillMaxWidth()
            
            .padding(16.dp, 8.dp),
    ) {
        GlideImage(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(if (item.type == "artist") 100.dp else 6.dp)),
            model = item.image,
            contentScale = ContentScale.Crop,
            failure = placeholder(R.drawable.placeholder),
            loading = placeholder(R.drawable.placeholder),
            contentDescription = "",
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp, end = 8.dp),
        ) {
            Text(text = item.name, color = AppText, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            val subtitle = when (item.type) {
                "song" -> stringResource(R.string.assisted_curation_search_song) + " • ${item.singer}"
                "artist" -> stringResource(R.string.artist_subtitle)
                "album" -> stringResource(R.string.album) + " • ${item.singer}"
                "show" -> stringResource(if (io.github.thake.soundnest.feature.spokenword.audible.data.AudibleUris.isAudibleUri(item.key)) R.string.audiobooks_title else R.string.album_type_podcast) + (if (item.singer.isNotBlank()) " • ${item.singer}" else "")
                "playlist" -> stringResource(R.string.assisted_curation_search_playlist) + (if (item.singer.isNotBlank()) " • ${item.singer}" else "")
                else -> ""
            }
            Text(text = subtitle, color = AppMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Icon(
            imageVector = Icons.Default.Close,
            tint = AppMuted,
            modifier = Modifier.pressable(0.96f) { onRemove() }
                .size(20.dp)
                ,
            contentDescription = stringResource(R.string.aligned_curation_first_save_default_row_remove_action_description),
        )
    }
}

@OptIn(ExperimentalGlideComposeApi::class, ExperimentalFoundationApi::class)
@Composable
fun SearchSongRow(
    song: SongsModel,
    searchViewModel: SearchViewModel,
    onPlayed: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    showMoreButton: Boolean = false,
) {
    val context = LocalContext.current
    val isLiked = io.github.thake.soundnest.core.playback.rememberTrackSaved(song.spotifyTrackId)
    val currentPlayingIndicatorColor =
        if (song.id == searchViewModel.currentSongId.value) Color(AppPalette.toArgb()) else AppText

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp, 8.dp)
            // A mouse's right click opens the same menu as the long press (DeX, tablets).
            .secondaryClick(onLongClick)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onLongClick = onLongClick,
                onClick = {
                    onPlayed()
                    // Start a radio from the tapped track (queue = this song + Spotify
                    // recommendations) rather than queuing the whole search list.
                    searchViewModel.startRadioFromSong(song)
                    SongPlayer.playSong(song.url, context, songs = listOf(song), alone = true)
                    searchViewModel.updateSongState(
                        song.coverUri,
                        song.title,
                        song.singer,
                        true,
                        song.id,
                        0,
                        song.album,
                    )
                },
            ),
    ) {
        Row(
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        ) {
            GlideImage(
                modifier = Modifier
                    .padding(0.dp, 0.dp, 10.dp, 0.dp)
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp)),
                model = song.coverUri,
                contentScale = ContentScale.Crop,
                failure = placeholder(R.drawable.placeholder),
                loading = placeholder(R.drawable.placeholder),
                contentDescription = "",
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = song.title, color = currentPlayingIndicatorColor, fontSize = 16.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    if (song.explicit) { io.github.thake.soundnest.core.ui.classic.ExplicitBadge(); androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp)) }
                    Text(text = stringResource(R.string.assisted_curation_search_song) + " • ${song.singer}", color = AppMuted, fontSize = 13.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            io.github.thake.soundnest.feature.player.ui.classic.AddButton(song = { song }, liked = isLiked, size = 22.dp) {
                // Spotify library, per track (not the now-playing like state).
                io.github.thake.soundnest.core.playback.SongPlayer.setTrackSaved(song.spotifyTrackId, true)
            }
            if (showMoreButton && onLongClick != null) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.content_description_context_menu),
                    tint = AppMuted,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { onLongClick() }
                        .padding(8.dp),
                )
            }
        }
    }
}

@Composable
fun SearchSectionHeader(title: String) {
    Text(
        text = title,
        color = AppText,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(16.dp, 18.dp, 16.dp, 4.dp),
    )
}

@OptIn(ExperimentalGlideComposeApi::class, ExperimentalFoundationApi::class)
@Composable
fun SearchShowRow(
    show: io.github.thake.soundnest.core.model.PodcastModel,
    onMore: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = (if (onMore != null) {
            Modifier
                .secondaryClick(onMore)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onLongClick = onMore,
                    onClick = onClick,
                )
        } else {
            Modifier.pressable(0.96f) { onClick() }
        })
            .fillMaxWidth()
            .padding(16.dp, 8.dp),
    ) {
        GlideImage(
            modifier = Modifier
                .padding(0.dp, 0.dp, 10.dp, 0.dp)
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp)),
            model = show.coverUri,
            contentScale = ContentScale.Crop,
            failure = placeholder(R.drawable.placeholder),
            loading = placeholder(R.drawable.placeholder),
            contentDescription = "",
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = show.name, color = AppText, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            val kindRes = if (io.github.thake.soundnest.feature.spokenword.audible.data.AudibleUris.isAudibleUri(show.id)) R.string.audiobooks_title else R.string.album_type_podcast
            Text(
                text = stringResource(kindRes) + (if (show.publisher.isNotBlank()) " • ${show.publisher}" else ""),
                color = AppMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        if (onMore != null) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.content_description_context_menu),
                tint = AppMuted,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { onMore() }
                    .padding(8.dp),
            )
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class, ExperimentalFoundationApi::class)
@Composable
fun SearchArtistRow(
    artist: io.github.thake.soundnest.core.model.ArtistsModel,
    onMore: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = (if (onMore != null) {
            Modifier
                .secondaryClick(onMore)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onLongClick = onMore,
                    onClick = onClick,
                )
        } else {
            Modifier.pressable(0.96f) { onClick() }
        })
            .fillMaxWidth()
            .padding(16.dp, 8.dp),
    ) {
        GlideImage(
            modifier = Modifier
                .padding(0.dp, 0.dp, 10.dp, 0.dp)
                .size(48.dp)
                .clip(RoundedCornerShape(100.dp)),
            model = artist.coverUri,
            contentScale = ContentScale.Crop,
            failure = placeholder(R.drawable.placeholder),
            loading = placeholder(R.drawable.placeholder),
            contentDescription = "",
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = artist.name, color = AppText, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(text = stringResource(R.string.artist_subtitle), color = AppMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        if (onMore != null) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.content_description_context_menu),
                tint = AppMuted,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { onMore() }
                    .padding(8.dp),
            )
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class, ExperimentalFoundationApi::class)
@Composable
fun SearchAlbumRow(
    album: io.github.thake.soundnest.core.model.AlbumsModel,
    onMore: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = (if (onMore != null) {
            Modifier
                .secondaryClick(onMore)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onLongClick = onMore,
                    onClick = onClick,
                )
        } else {
            Modifier.pressable(0.96f) { onClick() }
        })
            .fillMaxWidth()
            .padding(16.dp, 8.dp),
    ) {
        GlideImage(
            modifier = Modifier
                .padding(0.dp, 0.dp, 10.dp, 0.dp)
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp)),
            model = album.coverUri,
            contentScale = ContentScale.Crop,
            failure = placeholder(R.drawable.placeholder),
            loading = placeholder(R.drawable.placeholder),
            contentDescription = "",
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = album.name, color = AppText, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(text = stringResource(R.string.album) + " • ${album.artists}", color = AppMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        if (onMore != null) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.content_description_context_menu),
                tint = AppMuted,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { onMore() }
                    .padding(8.dp),
            )
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class, ExperimentalFoundationApi::class)
@Composable
fun SearchPlaylistRow(
    playlist: io.github.thake.soundnest.core.model.LibraryEntry,
    onMore: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = (if (onMore != null) {
            Modifier
                .secondaryClick(onMore)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onLongClick = onMore,
                    onClick = onClick,
                )
        } else {
            Modifier.pressable(0.96f) { onClick() }
        }).fillMaxWidth().padding(16.dp, 8.dp),
    ) {
        GlideImage(
            modifier = Modifier.padding(0.dp, 0.dp, 10.dp, 0.dp).size(48.dp).clip(RoundedCornerShape(6.dp)),
            model = playlist.coverUri,
            contentScale = ContentScale.Crop,
            failure = placeholder(R.drawable.placeholder),
            loading = placeholder(R.drawable.placeholder),
            contentDescription = "",
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = playlist.name, color = AppText, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(text = stringResource(R.string.assisted_curation_search_playlist) +
                (if (playlist.subtitle.isNotBlank()) " • ${playlist.subtitle}" else ""),
                color = AppMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        if (onMore != null) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.content_description_context_menu),
                tint = AppMuted,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { onMore() }
                    .padding(8.dp),
            )
        }
    }
}

/** Genre key of the "New releases" tile, opened in its own screen. */
const val NEW_RELEASES_GENRE = "new-releases"

/** One "Browse all" tile: Spotify's own category (title, colour, artwork). */
private data class BrowseTile(val key: String, val title: String, val color: Color, val image: String)

@Composable
fun BrowseAllSection(onCategoryClick: (genre: String, title: String) -> Unit) {
    val policy = io.github.thake.soundnest.feature.parental.settings.rememberParentalPolicy()
    val newReleases = stringResource(R.string.browse_new_releases)
    // Spotify's real categories (the Web Player's "Browse all"), New releases first when allowed.
    val tiles by androidx.compose.runtime.produceState(initialValue = emptyList<BrowseTile>(), key1 = policy) {
        val headerTiles = if (io.github.thake.soundnest.feature.parental.filter.HomeSectionFilter.isWhatsNewAllowed(policy)) {
            listOf(BrowseTile(NEW_RELEASES_GENRE, newReleases, Color(0xFFE8115B), ""))
        } else {
            emptyList()
        }
        val categoryTiles = if (policy.effectiveAllowAutoplayAndRadio) {
            val categories = runCatching { io.github.thake.soundnest.host.android.SoundNestRuntime.api.home.categories() }.getOrDefault(emptyList())
            categories
                .filter { (policy.spokenWord.allowPodcasts || !it.title.contains("odcast", ignoreCase = true)) && !io.github.thake.soundnest.feature.parental.filter.ParentalFilterEngine.matchesBlockedText(policy, it.title) }
                .map { c ->
                    val color = runCatching { Color(android.graphics.Color.parseColor(c.color ?: "#333333")) }.getOrDefault(tone(0xFF333333))
                    BrowseTile(c.uri, c.title, color, c.images.firstOrNull()?.url.orEmpty())
                }
        } else {
            emptyList()
        }
        value = headerTiles + categoryTiles
    }
    if (tiles.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.browse_all),
            color = AppText,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 8.dp),
        )
        // Wide windows: more tiles per row (two on phones), counted in the list's own width
        // (capped on wide windows), not the window's.
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = io.github.thake.soundnest.core.ui.look.WindowSize.columnsIn(maxWidth, 2, 220.dp)
        Column(Modifier.fillMaxWidth()) {
        tiles.chunked(cols).forEach { rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp, 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowItems.forEach { t ->
                    BrowseCategoryTile(t.title, t.color, t.image, Modifier.weight(1f)) { onCategoryClick(t.key, t.title) }
                }
                // Keep the tiles' width on a last row that is not full.
                repeat(cols - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun BrowseCategoryTile(
    name: String,
    color: Color,
    cover: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(96.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
    ) {
        if (cover.isNotBlank()) {
            GlideImage(
                model = cover,
                contentScale = ContentScale.Crop,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(64.dp)
                    .offset(x = 16.dp, y = 10.dp)
                    .rotate(25f)
                    .clip(RoundedCornerShape(4.dp)),
            )
        }
        Text(
            text = name,
            color = AppText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
fun SearchTopBar() {
    Row(
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
    ) {
        ClassicMenuAvatarButton(modifier = Modifier.padding(end = 12.dp))
        Text(
            text = stringResource(R.string.navigationbar_musicappitems_search_title),
            color = AppText,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchStickyBar(
    text: String,
    onFocusChange: (Boolean) -> Unit = {},
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    onTextChange: (String) -> Unit,
) {

    // The field is always attached to a requester, so the clear button can put the cursor back in it.
    val ownFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val fieldFocus = focusRequester ?: ownFocus
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .height(55.dp)
                .background(tone(0xFF2A2A2A))
                .padding(10.dp, 0.dp)
        ){
            Icon(
                painterResource(id = R.drawable.ic_search_big),
                tint = AppText,
                contentDescription = "")

            TextField(
                enabled = true,
                // The rest of the bar, so the clear button sits at its end and not after a 280 dp field.
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(fieldFocus)
                    .onFocusChanged { onFocusChange(it.isFocused) },
                value = text,
                textStyle = TextStyle.Default.copy(fontSize = 16.sp, color = AppText, fontWeight = FontWeight(500)),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = AppText,
                    unfocusedTextColor = AppText,
                    cursorColor = AppPalette

                ),
                singleLine = true,
                onValueChange = onTextChange,
                placeholder = {
                    // At the field's start, after the magnifier, where the typed text goes, and on
                    // one line: a hint longer than the field is cut short, never wrapped onto two
                    // lines centred in the bar.
                    Text(
                        textAlign = TextAlign.Start,
                        color = AppMuted,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        text = stringResource(R.string.search_header_field_hint),
                    )
                },
                trailingIcon = if (text.isEmpty()) null else {
                    { SearchClearButton(fieldFocus, AppText) { onTextChange("") } }
                },
            )
        }
        Spacer(Modifier.width(10.dp))
        // Voice search: the Google app's own "Search with your voice" for music, one tap away.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(55.dp)
                .clip(CircleShape)
                .background(tone(0xFF2A2A2A))
                .pressable(0.9f) { launchVoiceSearch(context) },
        ) {
            Icon(
                imageVector = Icons.Outlined.Mic,
                contentDescription = stringResource(R.string.voice_search),
                tint = AppText,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** Spotify's own recent searches, cleared with the app's list. */
private fun clearSpotifyRecents() {
    val runtime = io.github.thake.soundnest.host.android.SoundNestRuntime
    runtime.scope.launch { runCatching { runtime.api.search.clearRecent() } }
}

private fun loadClassicSearchRecents(
    context: android.content.Context,
    policy: ParentalPolicy,
): List<io.github.thake.soundnest.feature.recap.settings.RecentItem> =
    io.github.thake.soundnest.feature.recap.settings.getRecentItems(context).filter { item ->
        (item.type != "show" ||
            (if (io.github.thake.soundnest.feature.spokenword.audible.data.AudibleUris.isAudibleUri(item.key)) io.github.thake.soundnest.core.catalog.Features.AUDIBLE else io.github.thake.soundnest.core.catalog.Features.PODCASTS)) &&
            ParentBlockActions.isRecentItemAllowed(item, policy)
    }

