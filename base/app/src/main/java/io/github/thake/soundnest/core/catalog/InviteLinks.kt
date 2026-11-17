package io.github.thake.soundnest.core.catalog

import android.content.Context
import android.util.Log
import io.github.thake.soundnest.R
import io.github.thake.soundnest.host.android.SoundNestRuntime
import io.github.thake.soundnest.core.model.ContextPageModel
import io.github.thake.soundnest.core.model.LibraryEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Collaborative-playlist invitations: `open.spotify.com/playlist/{id}?si=…&pt={token}`, as the
 * official apps share them (or a spotify.link short link that redirects to it). `pt` is the
 * permission-grant token; claiming it makes the account a collaborator. The link is kept here
 * until the user taps "Join" on the playlist page (see [join]).
 */
object InviteLinks {
    private const val TAG = "InviteLinks"

    /** A new object for every link opened, so the same link opened twice navigates twice. */
    class Invite(val playlistId: String, val token: String) {
        val uri get() = "spotify:playlist:$playlistId"
        /** The playlist page has been opened on it (not again when the screen is rebuilt). */
        @Volatile var opened = false
    }

    /** The invitation waiting for the user's answer; cleared once joined. */
    val pending = MutableStateFlow<Invite?>(null)

    /** A shared playlist / album / artist / podcast link: its page, opened by App.kt (not played). */
    val page = MutableStateFlow<String?>(null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http by lazy { okhttp3.OkHttpClient.Builder().followRedirects(true).build() }

    private val PLAYLIST = Regex("""(?:open|www|play)\.spotify\.com/(?:intl-[a-z-]+/)?playlist/([A-Za-z0-9]{22})([^\s"'<>]*)""")
    private val TOKEN = Regex("""[?&]pt=([A-Za-z0-9_-]+)""")
    private val SHORT = Regex("""https?://(?:spotify\.link|spoti\.fi)/[^\s"'<>]+""")

    /** The invitation inside [link] (a playlist link carrying `pt`), or null. */
    fun parse(link: String): Invite? {
        val m = PLAYLIST.find(link.replace("&amp;", "&").replace("\\u0026", "&")) ?: return null
        val token = TOKEN.find(m.groupValues[2])?.groupValues?.get(1) ?: return null
        return Invite(m.groupValues[1], token)
    }

    /**
     * An incoming link. True when it is taken here: a playlist invitation (kept as [pending], the
     * playlist page opens on it) or a short link (resolved on a background thread, then handled as
     * the link it leads to; one that is not an invitation plays like any Spotify link).
     */
    fun handle(link: String): Boolean {
        val policy = io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore.current()
        return when (val decision = io.github.thake.soundnest.feature.parental.filter.SpotifyLinkGuard.evaluate(link, policy)) {
            is io.github.thake.soundnest.feature.parental.filter.LinkDecision.ExternalBrowser -> false
            is io.github.thake.soundnest.feature.parental.filter.LinkDecision.JoinCollaborativeInvite -> {
                pending.value = decision.invite
                true
            }
            is io.github.thake.soundnest.feature.parental.filter.LinkDecision.OpenInAppPage -> {
                page.value = decision.spotifyUri
                true
            }
            is io.github.thake.soundnest.feature.parental.filter.LinkDecision.PlaySpotifyTrack -> {
                playFilteredSpotifyUri(decision.spotifyUri)
                true
            }
            is io.github.thake.soundnest.feature.parental.filter.LinkDecision.ResolveShortLink -> {
                scope.launch {
                    val target = resolve(decision.url) ?: return@launch
                    val resolvedPolicy = io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore.current()
                    when (val resolved = io.github.thake.soundnest.feature.parental.filter.SpotifyLinkGuard.evaluate(target, resolvedPolicy)) {
                        is io.github.thake.soundnest.feature.parental.filter.LinkDecision.JoinCollaborativeInvite -> pending.value = resolved.invite
                        is io.github.thake.soundnest.feature.parental.filter.LinkDecision.OpenInAppPage -> page.value = resolved.spotifyUri
                        is io.github.thake.soundnest.feature.parental.filter.LinkDecision.PlaySpotifyTrack -> playFilteredSpotifyUri(resolved.spotifyUri)
                        is io.github.thake.soundnest.feature.parental.filter.LinkDecision.ExternalBrowser,
                        is io.github.thake.soundnest.feature.parental.filter.LinkDecision.ResolveShortLink,
                        is io.github.thake.soundnest.feature.parental.filter.LinkDecision.Blocked -> Unit
                    }
                }
                true
            }
            is io.github.thake.soundnest.feature.parental.filter.LinkDecision.Blocked -> io.github.thake.soundnest.feature.parental.filter.SpotifyLinkGuard.isSpotifyLink(link)
        }
    }

    private fun playFilteredSpotifyUri(spotifyUri: String) {
        val parts = spotifyUri.split(':')
        if (parts.size < 3) return
        val kind = parts[1]
        val id = parts[2]
        val playArg = when (kind) {
            "episode" -> "episode:$id"
            "track" -> "spotify:track:$id|"
            else -> return
        }
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            io.github.thake.soundnest.core.playback.SongPlayer.playSong(playArg, alone = true)
        }
    }

    /** Where a short link leads: its final URL, or the playlist link inside the page it answers with. */
    private suspend fun resolve(link: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(okhttp3.Request.Builder().url(link).header("user-agent", "Mozilla/5.0").build()).execute().use { r ->
                val final = r.request.url.toString()
                if (PLAYLIST.containsMatchIn(final) && TOKEN.containsMatchIn(final)) final
                else {
                    val body = r.body.string().replace("\\/", "/")
                    PLAYLIST.find(body.replace("&amp;", "&").replace("\\u0026", "&"))?.value?.let { "https://$it" } ?: final
                }
            }
        }.onFailure { Log.w(TAG, "short link not resolved: ${it.message}") }.getOrNull()
    }

    /**
     * Joins the playlist of [invite] ([page] is that playlist's page, for the library row shown at
     * once): claims the token, follows the playlist when needed, and has the library read again.
     * A refused token (wrong or expired link) undoes the row and says so.
     */
    fun join(context: Context, invite: Invite, page: ContextPageModel, onDone: (Boolean) -> Unit = {}) {
        if (!io.github.thake.soundnest.feature.parental.settings.FeatureGates.canModifySpotifyLibrary(context)) {
            onDone(false)
            return
        }
        val app = context.applicationContext
        val edit = LibraryEdits.Add(null, LibraryEntry(
            spotifyId = invite.playlistId, name = page.name,
            subtitle = app.getString(R.string.assisted_curation_search_playlist) + page.ownerName.takeIf { it.isNotBlank() }?.let { " • $it" }.orEmpty(),
            coverUri = page.coverUri, isPlaylist = true, uri = invite.uri,
            addedAt = java.time.Instant.now().toString(),
        ))
        LibraryEdits.run(app, edit, write = {
            SoundNestRuntime.api.playlists.join(invite.uri, invite.token)
            Api.HomeCache.libraryRoot = null
            Api.HomeCache.folders.clear()
        }, onDone = { ok ->
            if (ok && pending.value === invite) pending.value = null
            onDone(ok)
        })
    }
}
