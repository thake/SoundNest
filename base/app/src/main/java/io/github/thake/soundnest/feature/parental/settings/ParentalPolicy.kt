/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import io.github.thake.soundnest.core.catalog.Api
import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.core.preferences.PolicySyncedSetting
import io.github.thake.soundnest.core.preferences.featurePrefs
import io.github.thake.soundnest.feature.home.settings.HideDjPref
import io.github.thake.soundnest.feature.home.settings.HomeParentalPolicy
import io.github.thake.soundnest.feature.home.whatsnew.settings.NewReleaseNotifier
import io.github.thake.soundnest.feature.library.settings.LibraryParentalPolicy
import io.github.thake.soundnest.feature.localfiles.automix.settings.AutoMixSettings
import io.github.thake.soundnest.feature.localfiles.settings.LocalFilesParentalPolicy
import io.github.thake.soundnest.feature.localfiles.settings.LocalFilesSettings
import io.github.thake.soundnest.feature.lyrics.genius.settings.GeniusSettings
import io.github.thake.soundnest.feature.lyrics.settings.LyricsParentalPolicy
import io.github.thake.soundnest.feature.lyrics.settings.LyricsSettings
import io.github.thake.soundnest.feature.lyrics.translation.settings.LyricsTranslationSettings
import io.github.thake.soundnest.feature.player.settings.PlaybackParentalPolicy
import io.github.thake.soundnest.feature.player.settings.PlaybackSettings
import io.github.thake.soundnest.feature.radio.settings.RadioParentalPolicy
import io.github.thake.soundnest.feature.radio.settings.RadioSettings
import io.github.thake.soundnest.feature.speakers.settings.SpeakersParentalPolicy
import io.github.thake.soundnest.feature.speakers.settings.SpeakersSettings
import io.github.thake.soundnest.feature.spokenword.audible.data.AudibleUris
import io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer
import io.github.thake.soundnest.feature.spokenword.settings.SpokenWordParentalPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Composable
fun rememberParentalPolicy(): ParentalPolicy {
    val context = LocalContext.current
    ParentalPolicyStore.current(context)
    val policy by ParentalPolicyStore.policyFlow.collectAsState()
    return policy
}

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
    val blockedTrackIds: Set<String> = emptySet(),
    val blockedAlbumIds: Set<String> = emptySet(),
    val blockedArtistIds: Set<String> = emptySet(),
    val blockedPlaylistIds: Set<String> = emptySet(),
    val blockedKeywords: Set<String> = emptySet(),
    val blockedRegexPatterns: Set<String> = emptySet(),
    // Nested feature parental policies (fail-safe defaults for ages 9-16)
    val spokenWord: SpokenWordParentalPolicy = SpokenWordParentalPolicy(),
    val localFiles: LocalFilesParentalPolicy = LocalFilesParentalPolicy(),
    val radio: RadioParentalPolicy = RadioParentalPolicy(),
    val lyrics: LyricsParentalPolicy = LyricsParentalPolicy(),
    val speakers: SpeakersParentalPolicy = SpeakersParentalPolicy(),
    val home: HomeParentalPolicy = HomeParentalPolicy(),
    val library: LibraryParentalPolicy = LibraryParentalPolicy(),
    val playback: PlaybackParentalPolicy = PlaybackParentalPolicy(),
) {
    /**
     * Autoplay, Smart Shuffle, Track/Artist Radio, Playlist Extender, and Browse Categories
     * are automatically disabled in [FilterMode.STRICT_ALLOWLIST] or when [PlaybackParentalPolicy.allowAutoplayAndRadio] is false.
     */
    val effectiveAllowAutoplayAndRadio: Boolean
        get() = filterMode != FilterMode.STRICT_ALLOWLIST && playback.allowAutoplayAndRadio

    val allowAudiobooks: Boolean get() = spokenWord.allowAudiobooks
    val allowAudible: Boolean get() = spokenWord.allowAudible
    val allowAnyAudiobooks: Boolean get() = spokenWord.allowAnyAudiobooks
    val allowPodcasts: Boolean get() = spokenWord.allowPodcasts
    val allowedAudibleAsins: Set<String> get() = spokenWord.audible.allowedAsins
    val pinnedAudibleAsins: Set<String> get() = spokenWord.audible.pinnedAsins
    val homeSections: HomeParentalPolicy get() = home
}

object ParentalPolicyStore {
    internal const val PREF_NAME = "parental_policy"
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
        context.applicationContext.featurePrefs(PREF_NAME)

    fun normalizeSpotifyId(raw: String): String =
        raw.trim()
            .substringBefore('?')
            .substringBefore('|')
            .substringAfterLast(':')
            .substringAfterLast('/')
            .trim()

    fun normalizeAudibleAsin(raw: String): String =
        AudibleUris.asinFromAny(raw) ?: normalizeSpotifyId(raw)

    @Synchronized
    fun current(context: Context = Graph.app): ParentalPolicy {
        if (!initialized) {
            val loaded = loadFromDisk(context)
            _policyFlow.value = loaded
            initialized = true
            syncRuntimeFlags(context.applicationContext, loaded)
        }
        return _policyFlow.value
    }

    internal fun migrateLegacyAudiblePolicy(rawJson: String, policy: ParentalPolicy): ParentalPolicy {
        if (!rawJson.contains("\"allowAudible\"") && policy.spokenWord.allowAudiobooks && policy.spokenWord.audible.allowedAsins.isNotEmpty()) {
            return policy.copy(spokenWord = policy.spokenWord.copy(allowAudible = true))
        }
        return policy
    }

    private fun loadFromDisk(context: Context): ParentalPolicy {
        val raw = prefs(context).getString(KEY_POLICY_JSON, null) ?: return ParentalPolicy()
        return runCatching {
            val decoded = json.decodeFromString<ParentalPolicy>(raw)
            migrateLegacyAudiblePolicy(raw, decoded)
        }.getOrDefault(ParentalPolicy())
    }

    private fun sanitizePolicy(policy: ParentalPolicy): ParentalPolicy {
        val audible = policy.spokenWord.audible
        return if (audible.pinnedAsins.all { it in audible.allowedAsins }) {
            policy
        } else {
            policy.copy(
                spokenWord = policy.spokenWord.copy(
                    audible = audible.copy(pinnedAsins = audible.pinnedAsins.intersect(audible.allowedAsins)),
                ),
            )
        }
    }

    @Synchronized
    fun replace(context: Context, newPolicy: ParentalPolicy): ParentalPolicy {
        val app = context.applicationContext
        val sanitized = sanitizePolicy(newPolicy)
        prefs(app).edit().putString(KEY_POLICY_JSON, json.encodeToString(sanitized)).apply()
        _policyFlow.value = sanitized
        initialized = true
        _revision.value++
        syncRuntimeFlags(app, sanitized)
        return sanitized
    }

    @Synchronized
    fun update(context: Context, transform: (ParentalPolicy) -> ParentalPolicy): ParentalPolicy {
        val existing = current(context)
        val updated = transform(existing)
        return replace(context, updated)
    }

    @Synchronized
    internal fun syncSpeakersFromGate(context: Context, speakers: SpeakersParentalPolicy) {
        val app = context.applicationContext
        val base = if (initialized) _policyFlow.value else loadFromDisk(app).also { initialized = true }
        val updated = base.copy(speakers = speakers)
        prefs(app).edit().putString(KEY_POLICY_JSON, json.encodeToString(updated)).apply()
        _policyFlow.value = updated
        _revision.value++
    }

    @Synchronized
    internal fun syncCustomSpeakerNames(context: Context, names: Map<String, String>) {
        val app = context.applicationContext
        val base = if (initialized) _policyFlow.value else loadFromDisk(app).also { initialized = true }
        val updated = base.copy(speakers = base.speakers.copy(customSpeakerNames = names))
        prefs(app).edit().putString(KEY_POLICY_JSON, json.encodeToString(updated)).apply()
        _policyFlow.value = updated
        _revision.value++
    }

    private val policySyncedSettings: List<PolicySyncedSetting> = listOf(
        SpeakersSettings,
        RadioSettings,
        LocalFilesSettings,
        AutoMixSettings,
        LyricsSettings,
        GeniusSettings,
        LyricsTranslationSettings,
        AudiblePlayer,
        PlaybackSettings,
        NewReleaseNotifier,
        HideDjPref,
    )

    private fun syncRuntimeFlags(context: Context, policy: ParentalPolicy) {
        policySyncedSettings.forEach { setting ->
            runCatching { setting.enforcePolicy(context, policy) }
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

    fun allowTrack(context: Context, idOrUri: String, title: String? = null, subtitle: String? = null) {
        val id = normalizeSpotifyId(idOrUri).takeIf { it.isNotEmpty() } ?: return
        RuleLabelStore.record(context, "track", id, title, subtitle)
        update(context) {
            it.copy(
                allowedTrackIds = it.allowedTrackIds + id,
                blockedTrackIds = it.blockedTrackIds - id,
            )
        }
    }

    fun blockTrack(context: Context, idOrUri: String, title: String? = null, subtitle: String? = null) {
        val id = normalizeSpotifyId(idOrUri).takeIf { it.isNotEmpty() } ?: return
        RuleLabelStore.record(context, "track", id, title, subtitle)
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

    fun allowAlbum(context: Context, idOrUri: String, title: String? = null, subtitle: String? = null) {
        val id = normalizeSpotifyId(idOrUri).takeIf { it.isNotEmpty() } ?: return
        RuleLabelStore.record(context, "album", id, title, subtitle)
        update(context) {
            it.copy(
                allowedAlbumIds = it.allowedAlbumIds + id,
                blockedAlbumIds = it.blockedAlbumIds - id,
            )
        }
    }

    fun blockAlbum(context: Context, idOrUri: String, title: String? = null, subtitle: String? = null) {
        val id = normalizeSpotifyId(idOrUri).takeIf { it.isNotEmpty() } ?: return
        RuleLabelStore.record(context, "album", id, title, subtitle)
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

    fun allowArtist(context: Context, idOrUri: String, title: String? = null, subtitle: String? = null) {
        val id = normalizeSpotifyId(idOrUri).takeIf { it.isNotEmpty() } ?: return
        RuleLabelStore.record(context, "artist", id, title, subtitle)
        update(context) {
            it.copy(
                allowedArtistIds = it.allowedArtistIds + id,
                blockedArtistIds = it.blockedArtistIds - id,
            )
        }
    }

    fun blockArtist(context: Context, idOrUri: String, title: String? = null, subtitle: String? = null) {
        val id = normalizeSpotifyId(idOrUri).takeIf { it.isNotEmpty() } ?: return
        RuleLabelStore.record(context, "artist", id, title, subtitle)
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

    fun allowPlaylist(context: Context, idOrUri: String, title: String? = null, subtitle: String? = null) {
        val id = normalizeSpotifyId(idOrUri).takeIf { it.isNotEmpty() } ?: return
        RuleLabelStore.record(context, "playlist", id, title, subtitle)
        update(context) {
            it.copy(
                allowedPlaylistIds = it.allowedPlaylistIds + id,
                blockedPlaylistIds = it.blockedPlaylistIds - id,
            )
        }
    }

    fun blockPlaylist(context: Context, idOrUri: String, title: String? = null, subtitle: String? = null) {
        val id = normalizeSpotifyId(idOrUri).takeIf { it.isNotEmpty() } ?: return
        RuleLabelStore.record(context, "playlist", id, title, subtitle)
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

    fun allowShow(context: Context, idOrUri: String, title: String? = null, subtitle: String? = null) {
        val id = normalizeSpotifyId(idOrUri).takeIf { it.isNotEmpty() } ?: return
        RuleLabelStore.record(context, "show", id, title, subtitle)
        update(context) {
            val pods = it.spokenWord.podcasts
            it.copy(
                spokenWord = it.spokenWord.copy(
                    podcasts = pods.copy(
                        allowedShowIds = pods.allowedShowIds + id,
                        blockedShowIds = pods.blockedShowIds - id,
                    ),
                ),
            )
        }
    }

    fun blockShow(context: Context, idOrUri: String, title: String? = null, subtitle: String? = null) {
        val id = normalizeSpotifyId(idOrUri).takeIf { it.isNotEmpty() } ?: return
        RuleLabelStore.record(context, "show", id, title, subtitle)
        update(context) {
            val pods = it.spokenWord.podcasts
            it.copy(
                spokenWord = it.spokenWord.copy(
                    podcasts = pods.copy(
                        blockedShowIds = pods.blockedShowIds + id,
                        allowedShowIds = pods.allowedShowIds - id,
                    ),
                ),
            )
        }
    }

    fun clearShowRule(context: Context, idOrUri: String) {
        val id = normalizeSpotifyId(idOrUri).takeIf { it.isNotEmpty() } ?: return
        update(context) {
            val pods = it.spokenWord.podcasts
            it.copy(
                spokenWord = it.spokenWord.copy(
                    podcasts = pods.copy(
                        allowedShowIds = pods.allowedShowIds - id,
                        blockedShowIds = pods.blockedShowIds - id,
                    ),
                ),
            )
        }
    }

    fun setAudibleBookAllowed(
        context: Context,
        asin: String,
        allowed: Boolean,
        title: String? = null,
        subtitle: String? = null,
    ) {
        val id = normalizeAudibleAsin(asin).takeIf { it.isNotEmpty() } ?: return
        if (allowed) {
            RuleLabelStore.record(context, "audible", id, title, subtitle)
            update(context) {
                val aud = it.spokenWord.audible
                it.copy(
                    spokenWord = it.spokenWord.copy(
                        audible = aud.copy(allowedAsins = aud.allowedAsins + id),
                    ),
                )
            }
        } else {
            update(context) {
                val aud = it.spokenWord.audible
                it.copy(
                    spokenWord = it.spokenWord.copy(
                        audible = aud.copy(
                            allowedAsins = aud.allowedAsins - id,
                            pinnedAsins = aud.pinnedAsins - id,
                        ),
                    ),
                )
            }
        }
    }

    fun setAudibleBookPinned(context: Context, asin: String, pinned: Boolean) {
        val id = normalizeAudibleAsin(asin).takeIf { it.isNotEmpty() } ?: return
        update(context) {
            val aud = it.spokenWord.audible
            when {
                pinned && id in aud.allowedAsins -> it.copy(
                    spokenWord = it.spokenWord.copy(
                        audible = aud.copy(pinnedAsins = aud.pinnedAsins + id),
                    ),
                )
                !pinned -> it.copy(
                    spokenWord = it.spokenWord.copy(
                        audible = aud.copy(pinnedAsins = aud.pinnedAsins - id),
                    ),
                )
                else -> it
            }
        }
    }

    fun clearAllAudibleRules(context: Context) {
        update(context) {
            val aud = it.spokenWord.audible
            it.copy(
                spokenWord = it.spokenWord.copy(
                    audible = aud.copy(
                        allowedAsins = emptySet(),
                        pinnedAsins = emptySet(),
                    ),
                ),
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

    fun hideHomeShelf(context: Context, shelfKey: String) {
        val cleaned = shelfKey.trim().takeIf { it.isNotEmpty() } ?: return
        update(context) {
            it.copy(home = it.home.copy(hiddenShelfKeys = it.home.hiddenShelfKeys + cleaned))
        }
    }

    fun unhideHomeShelf(context: Context, shelfKey: String) {
        val cleaned = shelfKey.trim().takeIf { it.isNotEmpty() } ?: return
        update(context) {
            it.copy(home = it.home.copy(hiddenShelfKeys = it.home.hiddenShelfKeys - cleaned))
        }
    }

    @Synchronized
    internal fun resetForTests(context: Context) {
        prefs(context).edit().clear().apply()
        RuleLabelStore.resetForTests(context)
        val defaults = ParentalPolicy()
        _policyFlow.value = defaults
        initialized = true
        _revision.value++
        syncRuntimeFlags(context.applicationContext, defaults)
    }
}
