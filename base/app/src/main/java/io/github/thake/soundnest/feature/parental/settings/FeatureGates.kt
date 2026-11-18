/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.settings

import android.content.Context
import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.feature.equalizer.settings.EqualizerSettings
import io.github.thake.soundnest.feature.parental.security.ParentSecurityManager
import io.github.thake.soundnest.feature.speakers.settings.TrustedNetworkGate

/**
 * Runtime feature gate checks used by UI, `Api`, `LyricsApi`, `LocalMusic`, `SpotifySync`, and `AutoBrowser`.
 */
object FeatureGates {
    private fun safeContext(): Context? = runCatching { Graph.app }.getOrNull()

    fun canUsePodcasts(context: Context? = safeContext()): Boolean =
        context?.let { ParentalPolicyStore.current(it).spokenWord.allowPodcasts } ?: false

    fun canUseAudiobooks(context: Context? = safeContext()): Boolean =
        context?.let { ParentalPolicyStore.current(it).spokenWord.allowAudiobooks } ?: false

    fun canUseAudible(context: Context? = safeContext()): Boolean =
        context?.let { ParentalPolicyStore.current(it).spokenWord.allowAudible } ?: false

    fun canUseLiveRadio(context: Context? = safeContext()): Boolean =
        context?.let { ParentalPolicyStore.current(it).radio.enabled } ?: false

    fun canUseLocalFiles(context: Context? = safeContext()): Boolean =
        context?.let { ParentalPolicyStore.current(it).localFiles.enabled } ?: false

    fun canUseEqualizer(context: Context? = safeContext()): Boolean =
        context?.let { EqualizerSettings.isAvailable(ParentalPolicyStore.current(it)) } ?: false

    /** Only request `READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE` when Local Files is enabled by a parent. */
    fun shouldRequestAudioStoragePermission(context: Context? = safeContext()): Boolean =
        canUseLocalFiles(context)

    fun canUseJam(context: Context? = safeContext()): Boolean {
        val ctx = context ?: return false
        return ParentalPolicyStore.current(ctx).speakers.jam.enabled && TrustedNetworkGate.isJamAllowed(ctx)
    }

    fun canShowLyrics(context: Context? = safeContext()): Boolean =
        context?.let { ParentalPolicyStore.current(it).lyrics.enabled } ?: true

    fun canUseGeniusMeanings(context: Context? = safeContext()): Boolean =
        context?.let {
            val p = ParentalPolicyStore.current(it)
            p.lyrics.enabled && p.lyrics.allowGeniusMeanings
        } ?: false

    fun canUseExtraLyricSources(context: Context? = safeContext()): Boolean =
        context?.let {
            val p = ParentalPolicyStore.current(it)
            p.lyrics.enabled && p.lyrics.allowExtraSources
        } ?: false

    fun canUseAutoplayAndRadio(context: Context? = safeContext()): Boolean =
        context?.let { ParentalPolicyStore.current(it).effectiveAllowAutoplayAndRadio } ?: true

    fun canModifySpotifyLibrary(context: Context? = safeContext()): Boolean {
        val ctx = context ?: return true
        val policy = ParentalPolicyStore.current(ctx)
        if (!policy.library.readOnlySpotifyLibrary) return true
        return ParentSecurityManager.isAdminUnlocked(ctx)
    }
}
