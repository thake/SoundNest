/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.settings

import io.github.thake.soundnest.R

/**
 * Canonical descriptor for every entity kind that parental rules and labels can target.
 * Centralizes wire keys, display names, string resources, and Spotify URI parsing so
 * adding a new kind does not require editing parallel `when (kind: String)` ladders.
 */
enum class RuleEntityKind(
    val wireKey: String,
    val defaultDisplayName: String,
    val labelResId: Int,
) {
    PLAYLIST("playlist", "Playlist", R.string.rule_kind_playlist),
    ALBUM("album", "Album", R.string.rule_kind_album),
    ARTIST("artist", "Artist", R.string.rule_kind_artist),
    TRACK("track", "Track", R.string.rule_kind_track),
    SHOW("show", "Show", R.string.rule_kind_show),
    AUDIBLE("audible", "Audible Book", R.string.rule_kind_audible);

    companion object {
        fun fromWireKey(raw: String): RuleEntityKind? = when (raw.trim().lowercase()) {
            "playlist" -> PLAYLIST
            "album" -> ALBUM
            "artist" -> ARTIST
            "track", "song" -> TRACK
            "show" -> SHOW
            "audible" -> AUDIBLE
            else -> null
        }

        fun parseSpotifyUri(raw: String): Pair<RuleEntityKind, String>? {
            val trimmed = raw.trim()
            val kind = when {
                trimmed.contains(":track:") || trimmed.contains("/track/") -> TRACK
                trimmed.contains(":album:") || trimmed.contains("/album/") -> ALBUM
                trimmed.contains(":artist:") || trimmed.contains("/artist/") -> ARTIST
                trimmed.contains(":playlist:") || trimmed.contains("/playlist/") -> PLAYLIST
                trimmed.contains(":show:") || trimmed.contains("/show/") -> SHOW
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
    }
}
