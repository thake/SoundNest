/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.settings

import android.content.Context
import io.github.thake.soundnest.core.catalog.Api
import io.github.thake.soundnest.core.model.HomeItem
import io.github.thake.soundnest.core.preferences.featurePrefs
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class RuleLabel(
    val title: String,
    val subtitle: String = "",
)

/**
 * Lightweight persistence of human-readable titles and subtitles for parent-approved or
 * parent-blocked Spotify entities (`playlist`, `album`, `artist`, `track`, `show`, `audible`).
 *
 * Whenever a rule is added from an in-app `⋮` sheet or resolved from [Api.HomeCache], its
 * human-readable title is cached so parents never have to decipher raw Spotify IDs.
 * Backed by `"feature_parental_labels"`.
 */
object RuleLabelStore {
    internal const val PREF_NAME = "parental_labels"
    private const val KEY_LABELS_JSON = "labels_json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun prefs(context: Context) =
        context.applicationContext.featurePrefs(PREF_NAME)

    fun keyOf(kind: String, idOrUri: String): String {
        val normKind = kind.trim().lowercase()
        val normId = ParentalPolicyStore.normalizeSpotifyId(idOrUri)
        return if (normKind.isEmpty() || normId.isEmpty()) "" else "$normKind:$normId"
    }

    fun kindResId(kind: String): Int = when (kind.trim().lowercase()) {
        "playlist" -> io.github.thake.soundnest.R.string.rule_kind_playlist
        "album" -> io.github.thake.soundnest.R.string.rule_kind_album
        "artist" -> io.github.thake.soundnest.R.string.rule_kind_artist
        "track" -> io.github.thake.soundnest.R.string.rule_kind_track
        "show" -> io.github.thake.soundnest.R.string.rule_kind_show
        "audible" -> io.github.thake.soundnest.R.string.rule_kind_audible
        else -> 0
    }

    fun kindDisplayName(context: Context, kind: String): String {
        val res = kindResId(kind)
        return if (res != 0) context.getString(res) else kind.replaceFirstChar { it.uppercase() }
    }

    fun kindDisplayName(kind: String): String = when (kind.trim().lowercase()) {
        "playlist" -> "Playlist"
        "album" -> "Album"
        "artist" -> "Artist"
        "track" -> "Track"
        "show" -> "Show"
        "audible" -> "Audible Book"
        else -> kind.replaceFirstChar { it.uppercase() }
    }

    @Synchronized
    fun all(context: Context): Map<String, RuleLabel> {
        io.github.thake.soundnest.feature.parental.remote.settings.RemotePolicySession.currentLabelsOrNull()?.let { return it }
        val raw = prefs(context).getString(KEY_LABELS_JSON, null) ?: return emptyMap()
        return runCatching { json.decodeFromString<Map<String, RuleLabel>>(raw) }.getOrDefault(emptyMap())
    }

    @Synchronized
    fun record(
        context: Context,
        kind: String,
        idOrUri: String,
        title: String?,
        subtitle: String? = null,
    ) {
        val cleanTitle = title?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val key = keyOf(kind, idOrUri).takeIf { it.isNotEmpty() } ?: return
        val cleanSub = subtitle?.trim().orEmpty()
        if (io.github.thake.soundnest.feature.parental.remote.settings.RemotePolicySession.isActive) {
            io.github.thake.soundnest.feature.parental.remote.settings.RemotePolicySession.recordLabel(
                key,
                RuleLabel(title = cleanTitle, subtitle = cleanSub),
            )
            return
        }
        val current = all(context).toMutableMap()
        current[key] = RuleLabel(title = cleanTitle, subtitle = cleanSub)
        prefs(context).edit().putString(KEY_LABELS_JSON, json.encodeToString(current)).apply()
    }

    @Synchronized
    fun replaceAll(context: Context, entries: Map<String, RuleLabel>) {
        val sanitized = entries
            .mapNotNull { (k, v) ->
                val parts = k.split(':', limit = 2)
                if (parts.size != 2 || v.title.isBlank()) return@mapNotNull null
                val cleanKey = keyOf(parts[0], parts[1]).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                cleanKey to RuleLabel(v.title.trim(), v.subtitle.trim())
            }
            .toMap()
        if (io.github.thake.soundnest.feature.parental.remote.settings.RemotePolicySession.isActive) {
            io.github.thake.soundnest.feature.parental.remote.settings.RemotePolicySession.replaceLabels(sanitized)
            return
        }
        prefs(context).edit().putString(KEY_LABELS_JSON, json.encodeToString(sanitized)).apply()
    }

    @Synchronized
    fun mergeAll(context: Context, entries: Map<String, RuleLabel>) {
        if (entries.isEmpty()) return
        if (io.github.thake.soundnest.feature.parental.remote.settings.RemotePolicySession.isActive) {
            val sanitized = entries.mapNotNull { (k, v) ->
                val parts = k.split(':', limit = 2)
                if (parts.size != 2 || v.title.isBlank()) null
                else keyOf(parts[0], parts[1]).takeIf { it.isNotEmpty() }?.let { it to RuleLabel(v.title.trim(), v.subtitle.trim()) }
            }.toMap()
            io.github.thake.soundnest.feature.parental.remote.settings.RemotePolicySession.mergeLabels(sanitized)
            return
        }
        val merged = all(context).toMutableMap()
        entries.forEach { (k, v) ->
            val parts = k.split(':', limit = 2)
            if (parts.size == 2 && v.title.isNotBlank()) {
                val cleanKey = keyOf(parts[0], parts[1])
                if (cleanKey.isNotEmpty()) merged[cleanKey] = RuleLabel(v.title.trim(), v.subtitle.trim())
            }
        }
        prefs(context).edit().putString(KEY_LABELS_JSON, json.encodeToString(merged)).apply()
    }

    fun resolve(context: Context, kind: String, idOrUri: String): RuleLabel {
        val normKind = kind.trim().lowercase()
        val normId = ParentalPolicyStore.normalizeSpotifyId(idOrUri)
        val key = "$normKind:$normId"
        all(context)[key]?.takeIf { it.title.isNotBlank() }?.let { return it }

        resolveFromRuntimeCaches(normKind, normId)?.let { resolved ->
            record(context, normKind, normId, resolved.title, resolved.subtitle)
            return resolved
        }

        return RuleLabel(
            title = "${kindDisplayName(context, normKind)} · $normId",
            subtitle = if (normKind == "audible") "audible:book:$normId" else "spotify:$normKind:$normId",
        )
    }

    private fun resolveFromRuntimeCaches(kind: String, id: String): RuleLabel? = runCatching {
        when (kind) {
            "playlist" -> {
                Api.HomeCache.library?.firstOrNull { it.isPlaylist && ParentalPolicyStore.normalizeSpotifyId(it.spotifyId) == id }
                    ?.let { return@runCatching RuleLabel(it.name, it.subtitle) }
                Api.HomeCache.home?.sections?.asSequence()?.flatMap { it.items.asSequence() }
                    ?.filterIsInstance<HomeItem.Playlist>()
                    ?.firstOrNull { ParentalPolicyStore.normalizeSpotifyId(it.id) == id }
                    ?.let { return@runCatching RuleLabel(it.name, it.subtitle) }
                null
            }
            "album" -> {
                Api.HomeCache.albums?.firstOrNull { ParentalPolicyStore.normalizeSpotifyId(it.spotifyId) == id }
                    ?.let { return@runCatching RuleLabel(it.name, it.artists) }
                Api.HomeCache.library?.firstOrNull { !it.isPlaylist && ParentalPolicyStore.normalizeSpotifyId(it.spotifyId) == id }
                    ?.let { return@runCatching RuleLabel(it.name, it.artists.ifBlank { it.subtitle }) }
                Api.HomeCache.home?.sections?.asSequence()?.flatMap { it.items.asSequence() }
                    ?.filterIsInstance<HomeItem.Album>()
                    ?.firstOrNull { ParentalPolicyStore.normalizeSpotifyId(it.id) == id }
                    ?.let { return@runCatching RuleLabel(it.name, it.artists.ifBlank { it.subtitle }) }
                null
            }
            "artist" -> {
                Api.HomeCache.artists?.firstOrNull { ParentalPolicyStore.normalizeSpotifyId(it.id) == id }
                    ?.let { return@runCatching RuleLabel(it.name) }
                Api.HomeCache.home?.sections?.asSequence()?.flatMap { it.items.asSequence() }
                    ?.filterIsInstance<HomeItem.Artist>()
                    ?.firstOrNull { ParentalPolicyStore.normalizeSpotifyId(it.id) == id }
                    ?.let { return@runCatching RuleLabel(it.name) }
                null
            }
            "track" -> {
                Api.LikedCache.tracks?.firstOrNull { ParentalPolicyStore.normalizeSpotifyId(it.spotifyTrackId) == id }
                    ?.let { return@runCatching RuleLabel(it.title, it.singer) }
                null
            }
            "show" -> {
                Api.HomeCache.library?.firstOrNull {
                    ParentalPolicyStore.normalizeSpotifyId(it.spotifyId.removePrefix(Api.SHOW_PREFIX)) == id
                }?.let { return@runCatching RuleLabel(it.name, it.subtitle) }
                Api.HomeCache.home?.sections?.asSequence()?.flatMap { it.items.asSequence() }
                    ?.filterIsInstance<HomeItem.Show>()
                    ?.firstOrNull { ParentalPolicyStore.normalizeSpotifyId(it.id) == id }
                    ?.let { return@runCatching RuleLabel(it.name, it.subtitle) }
                null
            }
            else -> null
        }
    }.getOrNull()

    @Synchronized
    internal fun resetForTests(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
