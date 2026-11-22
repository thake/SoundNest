/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.thake.soundnest.R
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.feature.parental.settings.RuleLabelStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ParentBackupEvent {
    data object ExportSuccess : ParentBackupEvent
    data class ExportFailed(val message: String) : ParentBackupEvent
    data object ImportSuccess : ParentBackupEvent
    data class ImportFailed(val message: String) : ParentBackupEvent
}

/**
 * ViewModel for [ParentSettingsScreen], owning off-main-thread backup export/import I/O
 * and network-trust refresh signaling.
 */
class ParentSettingsViewModel(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _networkRefresh = MutableStateFlow(0)
    val networkRefresh: StateFlow<Int> = _networkRefresh.asStateFlow()

    private val _backupEvents = MutableSharedFlow<ParentBackupEvent>(extraBufferCapacity = 4)
    val backupEvents: SharedFlow<ParentBackupEvent> = _backupEvents.asSharedFlow()

    fun bumpNetworkRefresh() {
        _networkRefresh.value += 1
    }

    fun exportBackup(
        produceJson: () -> String,
        writeJson: (String) -> Unit,
    ) {
        viewModelScope.launch(ioDispatcher) {
            val result = runCatching {
                val jsonText = produceJson()
                writeJson(jsonText)
            }
            val event = result.fold(
                onSuccess = { ParentBackupEvent.ExportSuccess },
                onFailure = { e -> ParentBackupEvent.ExportFailed(e.message ?: "") },
            )
            _backupEvents.emit(event)
        }
    }

    fun importBackup(
        readJson: () -> String,
        applyJson: (String) -> Result<Unit>,
    ) {
        viewModelScope.launch(ioDispatcher) {
            val result = runCatching {
                val raw = readJson()
                applyJson(raw).getOrThrow()
            }
            val event = result.fold(
                onSuccess = {
                    _networkRefresh.value += 1
                    ParentBackupEvent.ImportSuccess
                },
                onFailure = { e -> ParentBackupEvent.ImportFailed(e.message ?: "") },
            )
            _backupEvents.emit(event)
        }
    }
}

internal data class ManagedRuleEntry(
    val kind: String,
    val id: String,
    val title: String,
    val subtitle: String,
)

/**
 * Parses a pasted Spotify URI or `open.spotify.com` URL into `(kind, normalizedId)`,
 * or returns `null` if the string is not a supported Spotify track/album/artist/playlist/show link.
 */
internal fun parseQuickSpotifyUri(raw: String): Pair<String, String>? {
    val trimmed = raw.trim()
    val kind = when {
        trimmed.contains(":track:") || trimmed.contains("/track/") -> "track"
        trimmed.contains(":album:") || trimmed.contains("/album/") -> "album"
        trimmed.contains(":artist:") || trimmed.contains("/artist/") -> "artist"
        trimmed.contains(":playlist:") || trimmed.contains("/playlist/") -> "playlist"
        trimmed.contains(":show:") || trimmed.contains("/show/") -> "show"
        else -> return null
    }
    val id = trimmed
        .substringBefore('?')
        .substringBefore('#')
        .trimEnd('/')
        .substringAfterLast('/')
        .substringAfterLast(':')
        .trim()
    if (id.isEmpty()) return null
    return kind to id
}

internal fun applyQuickUriRule(context: Context, raw: String, allow: Boolean): Boolean {
    val (kind, id) = parseQuickSpotifyUri(raw) ?: return false
    when (kind) {
        "track" -> if (allow) ParentalPolicyStore.allowTrack(context, id) else ParentalPolicyStore.blockTrack(context, id)
        "album" -> if (allow) ParentalPolicyStore.allowAlbum(context, id) else ParentalPolicyStore.blockAlbum(context, id)
        "artist" -> if (allow) ParentalPolicyStore.allowArtist(context, id) else ParentalPolicyStore.blockArtist(context, id)
        "playlist" -> if (allow) ParentalPolicyStore.allowPlaylist(context, id) else ParentalPolicyStore.blockPlaylist(context, id)
        "show" -> if (allow) ParentalPolicyStore.allowShow(context, id) else ParentalPolicyStore.blockShow(context, id)
    }
    return true
}

internal fun buildRuleSummaryText(
    context: Context,
    playlists: Int,
    albums: Int,
    artists: Int,
    tracks: Int,
    shows: Int,
    audibleBooks: Int = 0,
): String {
    val total = playlists + albums + artists + tracks + shows + audibleBooks
    if (total == 0) return context.getString(R.string.parent_rules_summary_none)
    val res = context.resources
    val parts = buildList {
        if (playlists > 0) add(res.getQuantityString(R.plurals.parent_rules_summary_playlists, playlists, playlists))
        if (albums > 0) add(res.getQuantityString(R.plurals.parent_rules_summary_albums, albums, albums))
        if (artists > 0) add(res.getQuantityString(R.plurals.parent_rules_summary_artists, artists, artists))
        if (tracks > 0) add(res.getQuantityString(R.plurals.parent_rules_summary_songs, tracks, tracks))
        if (shows > 0) add(res.getQuantityString(R.plurals.parent_rules_summary_shows, shows, shows))
        if (audibleBooks > 0) add(res.getQuantityString(R.plurals.parent_rules_summary_audible, audibleBooks, audibleBooks))
    }
    return parts.joinToString(" · ")
}

internal fun collectManagedRuleEntries(
    context: Context,
    playlists: Set<String>,
    albums: Set<String>,
    artists: Set<String>,
    tracks: Set<String>,
    shows: Set<String>,
    audibleAsins: Set<String> = emptySet(),
): List<ManagedRuleEntry> {
    fun entriesFor(kind: String, ids: Set<String>): List<ManagedRuleEntry> =
        ids.map { id ->
            val label = RuleLabelStore.resolve(context, kind, id)
            ManagedRuleEntry(kind = kind, id = id, title = label.title, subtitle = label.subtitle)
        }.sortedBy { it.title.lowercase() }

    return buildList {
        addAll(entriesFor("playlist", playlists))
        addAll(entriesFor("album", albums))
        addAll(entriesFor("artist", artists))
        addAll(entriesFor("track", tracks))
        addAll(entriesFor("show", shows))
        addAll(entriesFor("audible", audibleAsins))
    }
}

internal fun clearManagedRule(context: Context, kind: String, id: String) {
    when (kind) {
        "playlist" -> ParentalPolicyStore.clearPlaylistRule(context, id)
        "album" -> ParentalPolicyStore.clearAlbumRule(context, id)
        "artist" -> ParentalPolicyStore.clearArtistRule(context, id)
        "track" -> ParentalPolicyStore.clearTrackRule(context, id)
        "show" -> ParentalPolicyStore.clearShowRule(context, id)
        "audible" -> ParentalPolicyStore.setAudibleBookAllowed(context, id, false)
    }
}
