/* SPDX-License-Identifier: GPL-3.0-only */
package com.music.spotui.parental

import android.content.Context
import com.music.spotui.data.api.Api
import com.music.spotui.data.api.GeniusMeaning
import com.music.spotui.data.api.LyricsApi
import com.music.spotui.data.preferences.AutoplayPref
import com.music.spotui.data.preferences.GeniusPref
import com.music.spotui.data.preferences.LocalFilesPref
import com.music.spotui.data.preferences.setLyricsExtraSources
import com.music.spotui.di.Graph
import com.music.spotui.radio.RadioFeaturePref
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class FilterMode {
    FILTERED_OPEN,
    STRICT_ALLOWLIST,
}

@Serializable
data class ParentalPolicy(
    val filterMode: FilterMode = FilterMode.FILTERED_OPEN,
    val blockExplicit: Boolean = true,
    val allowedTrackIds: Set<String> = emptySet(),
    val allowedAlbumIds: Set<String> = emptySet(),
    val allowedArtistIds: Set<String> = emptySet(),
    val allowedPlaylistIds: Set<String> = emptySet(),
    val allowedShowIds: Set<String> = emptySet(),
    val blockedTrackIds: Set<String> = emptySet(),
    val blockedAlbumIds: Set<String> = emptySet(),
    val blockedArtistIds: Set<String> = emptySet(),
    val blockedPlaylistIds: Set<String> = emptySet(),
    val blockedShowIds: Set<String> = emptySet(),
    val blockedKeywords: Set<String> = emptySet(),
    val blockedRegexPatterns: Set<String> = emptySet(),
    // Feature gates (fail-safe defaults for ages 9-16)
    val allowPodcasts: Boolean = false,
    val allowAudiobooks: Boolean = false,
    val allowLiveRadio: Boolean = false,
    val allowLocalFiles: Boolean = false,
    val allowJam: Boolean = false,
    val allowLyrics: Boolean = true,
    val allowGeniusMeanings: Boolean = false,
    val allowExtraLyricSources: Boolean = false,
    val allowAutoplayAndRadio: Boolean = true,
    val readOnlySpotifyLibrary: Boolean = false,
) {
    /**
     * Autoplay, Smart Shuffle, Track/Artist Radio, Playlist Extender, and Browse Categories
     * are automatically disabled in [FilterMode.STRICT_ALLOWLIST] or when [allowAutoplayAndRadio] is false.
     */
    val effectiveAllowAutoplayAndRadio: Boolean
        get() = filterMode != FilterMode.STRICT_ALLOWLIST && allowAutoplayAndRadio
}

object ParentalPolicyStore {
    private const val PREFS = "parental_policy_store"
    private const val KEY_POLICY_JSON = "policy_json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _policyFlow = MutableStateFlow(ParentalPolicy())
    val policyFlow: StateFlow<ParentalPolicy> = _policyFlow.asStateFlow()

    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    @Volatile
    private var initialized = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun normalizeSpotifyId(raw: String): String =
        raw.trim()
            .substringBefore('?')
            .substringBefore('|')
            .substringAfterLast(':')
            .substringAfterLast('/')
            .trim()

    @Synchronized
    fun current(context: Context = Graph.app): ParentalPolicy {
        if (!initialized) {
            val loaded = loadFromDisk(context)
            _policyFlow.value = loaded
            initialized = true
        }
        return _policyFlow.value
    }

    private fun loadFromDisk(context: Context): ParentalPolicy {
        val raw = prefs(context).getString(KEY_POLICY_JSON, null) ?: return ParentalPolicy()
        return runCatching { json.decodeFromString<ParentalPolicy>(raw) }.getOrDefault(ParentalPolicy())
    }

    @Synchronized
    fun replace(context: Context, newPolicy: ParentalPolicy): ParentalPolicy {
        val app = context.applicationContext
        prefs(app).edit().putString(KEY_POLICY_JSON, json.encodeToString(newPolicy)).apply()
        _policyFlow.value = newPolicy
        initialized = true
        _revision.value++
        syncRuntimeFlags(app, newPolicy)
        return newPolicy
    }

    @Synchronized
    fun update(context: Context, transform: (ParentalPolicy) -> ParentalPolicy): ParentalPolicy {
        val existing = current(context)
        val updated = transform(existing)
        return replace(context, updated)
    }

    private fun syncRuntimeFlags(context: Context, policy: ParentalPolicy) {
        TrustedNetworkGate.setJamAllowed(context, policy.allowJam)
        runCatching { RadioFeaturePref.set(context, policy.allowLiveRadio) }
        runCatching { LocalFilesPref.set(context, policy.allowLocalFiles) }
        runCatching { AutoplayPref.set(context, policy.effectiveAllowAutoplayAndRadio) }
        val geniusAllowed = policy.allowLyrics && policy.allowGeniusMeanings
        runCatching { GeniusPref.set(context, geniusAllowed) }
        if (!geniusAllowed) runCatching { GeniusMeaning.forget() }
        val extraAllowed = policy.allowLyrics && policy.allowExtraLyricSources
        runCatching {
            setLyricsExtraSources(context, extraAllowed)
            LyricsApi.extraSources = extraAllowed
            LyricsApi.extraSourcesOff = !extraAllowed
            if (!policy.allowLyrics || !extraAllowed) LyricsApi.forgetAll()
        }
        runCatching {
            Api.HomeCache.home = null
            Api.HomeCache.facets.clear()
            Api.HomeCache.library = null
            Api.HomeCache.libraryRoot = null
            Api.HomeCache.folders.clear()
        }
    }

    // ── Convenience Allow / Block Helpers (maintain disjoint sets) ──

    fun allowTrack(context: Context, idOrUri: String) {
        val id = normalizeSpotifyId(idOrUri).takeIf { it.isNotEmpty() } ?: return
        update(context) {
            it.copy(
                allowedTrackIds = it.allowedTrackIds + id,
                blockedTrackIds = it.blockedTrackIds - id,
            )
        }
    }

    fun blockTrack(context: Context, idOrUri: String) {
        val id = normalizeSpotifyId(idOrUri).takeIf { it.isNotEmpty() } ?: return
        update(context) {
            it.copy(
                blockedTrackIds = it.blockedTrackIds + id,
                allowedTrackIds = it.allowedTrackIds - id,
            )
        }
    }

    fun clearTrackRule(context: Context, idOrUri: String) {
        val id = normalizeSpotifyId(idOrUri).takeIf { it.isNotEmpty() } ?: return
        update(context) {
            it.copy(
                allowedTrackIds = it.allowedTrackIds - id,
                blockedTrackIds = it.blockedTrackIds - id,
            )
        }
    }

    fun allowAlbum(context: Context, idOrUri: String) {
        val id = normalizeSpotifyId(idOrUri).takeIf { it.isNotEmpty() } ?: return
        update(context) {
            it.copy(
                allowedAlbumIds = it.allowedAlbumIds + id,
                blockedAlbumIds = it.blockedAlbumIds - id,
            )
        }
    }

    fun blockAlbum(context: Context, idOrUri: String) {
        val id = normalizeSpotifyId(idOrUri).takeIf { it.isNotEmpty() } ?: return
        update(context) {
            it.copy(
                blockedAlbumIds = it.blockedAlbumIds + id,
                allowedAlbumIds = it.allowedAlbumIds - id,
            )
        }
    }

    fun clearAlbumRule(context: Context, idOrUri: String) {
        val id = normalizeSpotifyId(idOrUri).takeIf { it.isNotEmpty() } ?: return
        update(context) {
            it.copy(
                allowedAlbumIds = it.allowedAlbumIds - id,
                blockedAlbumIds = it.blockedAlbumIds - id,
            )
        }
    }

    fun allowArtist(context: Context, idOrUri: String) {
        val id = normalizeSpotifyId(idOrUri).takeIf { it.isNotEmpty() } ?: return
        update(context) {
            it.copy(
                allowedArtistIds = it.allowedArtistIds + id,
                blockedArtistIds = it.blockedArtistIds - id,
            )
        }
    }

    fun blockArtist(context: Context, idOrUri: String) {
        val id = normalizeSpotifyId(idOrUri).takeIf { it.isNotEmpty() } ?: return
        update(context) {
            it.copy(
                blockedArtistIds = it.blockedArtistIds + id,
                allowedArtistIds = it.allowedArtistIds - id,
            )
        }
    }

    fun clearArtistRule(context: Context, idOrUri: String) {
        val id = normalizeSpotifyId(idOrUri).takeIf { it.isNotEmpty() } ?: return
        update(context) {
            it.copy(
                allowedArtistIds = it.allowedArtistIds - id,
                blockedArtistIds = it.blockedArtistIds - id,
            )
        }
    }

    fun allowPlaylist(context: Context, idOrUri: String) {
        val id = normalizeSpotifyId(idOrUri).takeIf { it.isNotEmpty() } ?: return
        update(context) {
            it.copy(
                allowedPlaylistIds = it.allowedPlaylistIds + id,
                blockedPlaylistIds = it.blockedPlaylistIds - id,
            )
        }
    }

    fun blockPlaylist(context: Context, idOrUri: String) {
        val id = normalizeSpotifyId(idOrUri).takeIf { it.isNotEmpty() } ?: return
        update(context) {
            it.copy(
                blockedPlaylistIds = it.blockedPlaylistIds + id,
                allowedPlaylistIds = it.allowedPlaylistIds - id,
            )
        }
    }

    fun clearPlaylistRule(context: Context, idOrUri: String) {
        val id = normalizeSpotifyId(idOrUri).takeIf { it.isNotEmpty() } ?: return
        update(context) {
            it.copy(
                allowedPlaylistIds = it.allowedPlaylistIds - id,
                blockedPlaylistIds = it.blockedPlaylistIds - id,
            )
        }
    }

    fun allowShow(context: Context, idOrUri: String) {
        val id = normalizeSpotifyId(idOrUri).takeIf { it.isNotEmpty() } ?: return
        update(context) {
            it.copy(
                allowedShowIds = it.allowedShowIds + id,
                blockedShowIds = it.blockedShowIds - id,
            )
        }
    }

    fun blockShow(context: Context, idOrUri: String) {
        val id = normalizeSpotifyId(idOrUri).takeIf { it.isNotEmpty() } ?: return
        update(context) {
            it.copy(
                blockedShowIds = it.blockedShowIds + id,
                allowedShowIds = it.allowedShowIds - id,
            )
        }
    }

    fun addBlockedKeyword(context: Context, keyword: String) {
        val cleaned = keyword.trim().lowercase().takeIf { it.isNotEmpty() } ?: return
        update(context) { it.copy(blockedKeywords = it.blockedKeywords + cleaned) }
    }

    fun removeBlockedKeyword(context: Context, keyword: String) {
        val cleaned = keyword.trim().lowercase()
        update(context) { it.copy(blockedKeywords = it.blockedKeywords - cleaned) }
    }

    fun addBlockedRegex(context: Context, pattern: String) {
        val cleaned = pattern.trim().takeIf { it.isNotEmpty() } ?: return
        if (runCatching { Regex(cleaned, RegexOption.IGNORE_CASE) }.isFailure) return
        update(context) { it.copy(blockedRegexPatterns = it.blockedRegexPatterns + cleaned) }
    }

    fun removeBlockedRegex(context: Context, pattern: String) {
        update(context) { it.copy(blockedRegexPatterns = it.blockedRegexPatterns - pattern.trim()) }
    }

    @Synchronized
    internal fun resetForTests(context: Context) {
        prefs(context).edit().clear().apply()
        val defaults = ParentalPolicy()
        _policyFlow.value = defaults
        initialized = true
        _revision.value++
        syncRuntimeFlags(context.applicationContext, defaults)
    }
}

/**
 * Runtime feature gate checks used by UI, `Api`, `LyricsApi`, `LocalMusic`, `SpotifySync`, and `AutoBrowser`.
 */
object FeatureGates {
    private fun safeContext(): Context? = runCatching { Graph.app }.getOrNull()

    fun canUsePodcasts(context: Context? = safeContext()): Boolean =
        context?.let { ParentalPolicyStore.current(it).allowPodcasts } ?: false

    fun canUseAudiobooks(context: Context? = safeContext()): Boolean =
        context?.let { ParentalPolicyStore.current(it).allowAudiobooks } ?: false

    fun canUseLiveRadio(context: Context? = safeContext()): Boolean =
        context?.let { ParentalPolicyStore.current(it).allowLiveRadio } ?: false

    fun canUseLocalFiles(context: Context? = safeContext()): Boolean =
        context?.let { ParentalPolicyStore.current(it).allowLocalFiles } ?: false

    /** Only request `READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE` when Local Files is enabled by a parent. */
    fun shouldRequestAudioStoragePermission(context: Context? = safeContext()): Boolean =
        canUseLocalFiles(context)

    fun canUseJam(context: Context? = safeContext()): Boolean {
        val ctx = context ?: return false
        return ParentalPolicyStore.current(ctx).allowJam && TrustedNetworkGate.isJamAllowed(ctx)
    }

    fun canShowLyrics(context: Context? = safeContext()): Boolean =
        context?.let { ParentalPolicyStore.current(it).allowLyrics } ?: true

    fun canUseGeniusMeanings(context: Context? = safeContext()): Boolean =
        context?.let {
            val p = ParentalPolicyStore.current(it)
            p.allowLyrics && p.allowGeniusMeanings
        } ?: false

    fun canUseExtraLyricSources(context: Context? = safeContext()): Boolean =
        context?.let {
            val p = ParentalPolicyStore.current(it)
            p.allowLyrics && p.allowExtraLyricSources
        } ?: false

    fun canUseAutoplayAndRadio(context: Context? = safeContext()): Boolean =
        context?.let { ParentalPolicyStore.current(it).effectiveAllowAutoplayAndRadio } ?: true

    fun canModifySpotifyLibrary(context: Context? = safeContext()): Boolean {
        val ctx = context ?: return true
        val policy = ParentalPolicyStore.current(ctx)
        if (!policy.readOnlySpotifyLibrary) return true
        return ParentSecurityManager.isAdminUnlocked(ctx)
    }
}
