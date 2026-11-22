/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.filter

import io.github.thake.soundnest.core.catalog.InviteLinks
import io.github.thake.soundnest.feature.parental.settings.FilterMode
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.spokenword.audible.data.AudibleUris

sealed interface LinkDecision {
    /** Non-Spotify HTTP(S) URL delegated unchanged to Android OS / Google Family Link. */
    data class ExternalBrowser(val url: String) : LinkDecision
    data class OpenInAppPage(val spotifyUri: String) : LinkDecision
    data class PlaySpotifyTrack(val spotifyUri: String) : LinkDecision
    data class JoinCollaborativeInvite(val invite: InviteLinks.Invite) : LinkDecision
    data class ResolveShortLink(val url: String) : LinkDecision
    data class Blocked(val reason: String) : LinkDecision
}

/**
 * Validates Spotify URIs and links (`spotify:*`, `open.spotify.com`, `spotify.link`, `spoti.fi`)
 * against [ParentalPolicy] while delegating non-Spotify external browser URLs (`http(s)://`)
 * directly to the Android OS (`Intent.ACTION_VIEW`) for Google Family Link enforcement.
 */
object SpotifyLinkGuard {

    private val SHORT_LINK = Regex("""^https?://(?:spotify\.link|spoti\.fi)/[^\s"'<>]+$""", RegexOption.IGNORE_CASE)
    private val SPOTIFY_HOST = Regex("""(?:^|://)(?:[a-z0-9-]+\.)*spotify\.com(?:/|$)""", RegexOption.IGNORE_CASE)
    private val SPOTIFY_URI = Regex(
        """^spotify:(track|album|playlist|artist|show|episode|audiobook|chapter|user|blend|jam|socialsession):([A-Za-z0-9_-]+)""",
        RegexOption.IGNORE_CASE,
    )
    private val SPOTIFY_WEB_ENTITY = Regex(
        """(?:open|www|play)\.spotify\.com/(?:intl-[a-z-]+/)?(track|album|playlist|artist|show|episode|audiobook|chapter|user|blend|jam|socialsession)/([A-Za-z0-9_-]+)""",
        RegexOption.IGNORE_CASE,
    )

    fun isSpotifyLink(raw: String): Boolean {
        val trimmed = raw.trim()
        return trimmed.startsWith("spotify:", ignoreCase = true) ||
            SHORT_LINK.containsMatchIn(trimmed) ||
            SPOTIFY_HOST.containsMatchIn(trimmed) ||
            trimmed.contains("spotify.link/", ignoreCase = true) ||
            trimmed.contains("spoti.fi/", ignoreCase = true)
    }

    fun evaluate(rawLink: String, policy: ParentalPolicy): LinkDecision {
        val trimmed = rawLink.trim()
        if (trimmed.isEmpty()) return LinkDecision.Blocked("empty")

        if (AudibleUris.isAudibleUri(trimmed)) {
            val asin = AudibleUris.asinFromAny(trimmed)
                ?: return LinkDecision.Blocked("unrecognized_audible_uri")
            return if (ParentalFilterEngine.isAudibleBookAllowed(asin, policy)) {
                LinkDecision.OpenInAppPage(AudibleUris.bookUri(asin))
            } else {
                LinkDecision.Blocked("audible_book_not_approved")
            }
        }

        if (!isSpotifyLink(trimmed)) {
            val normalizedWeb = if (trimmed.startsWith("www.", ignoreCase = true)) "https://$trimmed" else trimmed
            val lower = normalizedWeb.lowercase()
            return if (lower.startsWith("https://") || lower.startsWith("http://")) {
                LinkDecision.ExternalBrowser(normalizedWeb)
            } else {
                LinkDecision.Blocked("unsupported_scheme")
            }
        }

        // Short links require redirect resolution first, then re-evaluation through evaluate()
        if (SHORT_LINK.matches(trimmed)) {
            return LinkDecision.ResolveShortLink(trimmed)
        }

        // Collaborative playlist invite (?pt=...)
        InviteLinks.parse(trimmed)?.let { invite ->
            if (policy.library.readOnlySpotifyLibrary) {
                return LinkDecision.Blocked("collaborative_invite_blocked_by_readonly_library")
            }
            if (!ParentalFilterEngine.isPlaylistAllowed(invite.playlistId, name = "", policy = policy)) {
                return LinkDecision.Blocked("playlist_blocked")
            }
            return LinkDecision.JoinCollaborativeInvite(invite)
        }

        val (kind, id) = SPOTIFY_URI.find(trimmed)?.let {
            it.groupValues[1].lowercase() to it.groupValues[2]
        } ?: SPOTIFY_WEB_ENTITY.find(trimmed)?.let {
            it.groupValues[1].lowercase() to it.groupValues[2]
        } ?: return LinkDecision.Blocked("unrecognized_spotify_link")

        return when (kind) {
            "user", "blend" -> LinkDecision.Blocked("social_or_blend_removed")
            "jam", "socialsession" ->
                if (policy.speakers.jam.enabled) LinkDecision.Blocked("jam_handled_by_session_sheet")
                else LinkDecision.Blocked("jam_disabled")
            "show" ->
                if (ParentalFilterEngine.isShowAllowed(id, name = "", policy = policy)) {
                    LinkDecision.OpenInAppPage("spotify:show:$id")
                } else {
                    LinkDecision.Blocked("show_blocked")
                }
            "audiobook" ->
                if (ParentalFilterEngine.isShowAllowed(id, name = "", isAudiobook = true, policy = policy)) {
                    LinkDecision.OpenInAppPage("spotify:show:$id")
                } else {
                    LinkDecision.Blocked("audiobook_blocked")
                }
            "episode", "chapter" -> {
                val allowedKind = if (kind == "chapter") policy.spokenWord.allowAudiobooks else policy.spokenWord.allowPodcasts
                if (!allowedKind || id in policy.blockedTrackIds) {
                    LinkDecision.Blocked("${kind}_blocked")
                } else if (policy.filterMode == FilterMode.STRICT_ALLOWLIST && id !in policy.allowedTrackIds) {
                    LinkDecision.Blocked("${kind}_not_allowlisted")
                } else {
                    LinkDecision.PlaySpotifyTrack("spotify:episode:$id")
                }
            }
            "playlist" ->
                if (ParentalFilterEngine.isPlaylistAllowed(id, name = "", policy = policy)) {
                    LinkDecision.OpenInAppPage("spotify:playlist:$id")
                } else {
                    LinkDecision.Blocked("playlist_blocked")
                }
            "album" ->
                if (ParentalFilterEngine.isAlbumAllowed(id, name = "", policy = policy)) {
                    LinkDecision.OpenInAppPage("spotify:album:$id")
                } else {
                    LinkDecision.Blocked("album_blocked")
                }
            "artist" ->
                if (ParentalFilterEngine.isArtistAllowed(id, name = "", policy = policy)) {
                    LinkDecision.OpenInAppPage("spotify:artist:$id")
                } else {
                    LinkDecision.Blocked("artist_blocked")
                }
            "track" -> {
                if (id in policy.blockedTrackIds) {
                    LinkDecision.Blocked("track_blocked")
                } else if (policy.filterMode == FilterMode.STRICT_ALLOWLIST && id !in policy.allowedTrackIds) {
                    LinkDecision.Blocked("track_not_allowlisted")
                } else {
                    LinkDecision.PlaySpotifyTrack("spotify:track:$id")
                }
            }
            else -> LinkDecision.Blocked("unsupported_kind:$kind")
        }
    }
}
