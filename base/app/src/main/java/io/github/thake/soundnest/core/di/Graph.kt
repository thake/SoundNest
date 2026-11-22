package io.github.thake.soundnest.core.di

import android.app.Application
import io.github.thake.soundnest.core.catalog.Api
import io.github.thake.soundnest.core.catalog.AppRepository
import io.github.thake.soundnest.core.playback.CurrentSongState
import io.github.thake.soundnest.core.playback.SongPlayer
import io.github.thake.soundnest.feature.appearance.settings.AccentPref
import io.github.thake.soundnest.feature.equalizer.settings.EqualizerSettings
import io.github.thake.soundnest.feature.home.settings.HideDjPref
import io.github.thake.soundnest.feature.library.settings.LibraryOrder
import io.github.thake.soundnest.feature.localfiles.automix.settings.AutoMixSettings
import io.github.thake.soundnest.feature.localfiles.data.LocalPlaylists
import io.github.thake.soundnest.feature.localfiles.settings.LocalFilesSettings
import io.github.thake.soundnest.feature.lyrics.genius.settings.GeniusSettings
import io.github.thake.soundnest.feature.lyrics.settings.LyricsSettings
import io.github.thake.soundnest.feature.lyrics.translation.settings.LyricsTranslationSettings
import io.github.thake.soundnest.feature.player.settings.PlaybackSettings
import io.github.thake.soundnest.feature.player.settings.applyCrossfade
import io.github.thake.soundnest.feature.player.settings.applyStreamingQuality
import io.github.thake.soundnest.feature.player.settings.getCrossfadeSeconds
import io.github.thake.soundnest.feature.radio.data.RadioFavoritesStore
import io.github.thake.soundnest.feature.radio.settings.RadioSettings
import io.github.thake.soundnest.feature.recap.data.ListenHistory
import io.github.thake.soundnest.feature.system.data.DataSaverGlide
import io.github.thake.soundnest.feature.system.settings.DataSaver
import io.github.thake.soundnest.host.android.SoundNestRuntime

/** Manual replacement for Spotui's Hilt graph: app-wide singletons. */
object Graph {
    lateinit var app: Application
        private set

    fun init(application: Application) {
        app = application
        // The data saver first: on a metered network with it on, nothing may go out, not even the page.
        DataSaver.init(application)
        io.github.thake.soundnest.feature.parental.remote.settings.DeviceRoleStore.init(application)
        io.github.thake.soundnest.feature.parental.remote.settings.PairedDevicesStore.init(application)
        val saver = DataSaver.active
        val dedicatedParent = io.github.thake.soundnest.feature.parental.remote.settings.DeviceRoleStore.isParentController &&
            !io.github.thake.soundnest.feature.accounts.settings.OnboardingSettings.isLoggedIn(application)
        SoundNestRuntime.start(application, loadPage = !saver && !dedicatedParent, held = saver || dedicatedParent)
        // Feature settings needed before crossfade / playback / UI start.
        PlaybackSettings.init(application)
        HideDjPref.init(application)
        // The streaming quality the user chose, set on the player at every page load.
        applyStreamingQuality(application)
        LocalFilesSettings.init(application)
        AutoMixSettings.init(application)
        LyricsSettings.init(application)
        GeniusSettings.init(application)
        LyricsTranslationSettings.init(application)
        // The crossfade length (0 = off), in the player from the first song on.
        applyCrossfade(getCrossfadeSeconds(application))
        ListenHistory.init(application)
        // Per-look accent colour before any screen draws.
        AccentPref.init(application)
        SongPlayer.startLibraryOutbox(application)
        LocalPlaylists.init(application)
        LibraryOrder.init(application)
        runCatching { DataSaverGlide.install(application) }
        // Live Radio & Equalizer: their own settings and favorites file.
        RadioSettings.init(application)
        EqualizerSettings.init(application)
        RadioFavoritesStore.init(application)
        registerParentalPolicyHooks()
    }

    internal fun registerParentalPolicyHooks() {
        val store = io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
        listOf(
            io.github.thake.soundnest.feature.speakers.settings.SpeakersSettings,
            RadioSettings,
            LocalFilesSettings,
            AutoMixSettings,
            LyricsSettings,
            GeniusSettings,
            LyricsTranslationSettings,
            io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer,
            PlaybackSettings,
            io.github.thake.soundnest.feature.home.whatsnew.settings.NewReleaseNotifier,
            HideDjPref,
        ).forEach(store::registerPolicySynced)
        store.registerCacheInvalidator {
            Api.HomeCache.home = null
            Api.HomeCache.facets.clear()
            Api.HomeCache.library = null
            Api.HomeCache.libraryRoot = null
            Api.HomeCache.folders.clear()
        }
    }

    val api: Api by lazy { Api(app) }
    val repository: AppRepository by lazy { AppRepository(api) }
    val currentSongState: CurrentSongState by lazy { CurrentSongState() }
}
