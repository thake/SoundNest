package io.github.thake.soundnest.di

import android.app.Application
import io.github.thake.soundnest.host.android.SoundNestRuntime
import io.github.thake.soundnest.data.api.Api
import io.github.thake.soundnest.ui.repository.AppRepository

/** Manual replacement for Spotui's Hilt graph: app-wide singletons. */
object Graph {
    lateinit var app: Application
        private set

    fun init(application: Application) {
        app = application
        // The data saver first: on a metered network with it on, nothing may go out, not even the page.
        io.github.thake.soundnest.data.DataSaver.init(application)
        val saver = io.github.thake.soundnest.data.DataSaver.active
        SoundNestRuntime.start(application, loadPage = !saver, held = saver)
        // The streaming quality the user chose, set on the player at every page load.
        io.github.thake.soundnest.data.preferences.applyStreamingQuality(application)
        // The crossfade length (0 = off), in the player from the first song on.
        io.github.thake.soundnest.data.preferences.applyCrossfade(io.github.thake.soundnest.data.preferences.getCrossfadeSeconds(application))
        io.github.thake.soundnest.data.local.ListenHistory.init(application)
        // Per-look accent colour before any screen draws.
        io.github.thake.soundnest.data.preferences.AccentPref.init(application)
        SongPlayer.startLibraryOutbox(application)
        io.github.thake.soundnest.data.local.LocalPlaylists.init(application)
        io.github.thake.soundnest.data.local.LibraryOrder.init(application)
        runCatching { io.github.thake.soundnest.data.DataSaverGlide.install(application) }
        // Live Radio: its own setting and favorites file (see io.github.thake.soundnest.radio).
        io.github.thake.soundnest.radio.RadioFeaturePref.init(application)
        io.github.thake.soundnest.data.preferences.LocalFilesPref.init(application)
        io.github.thake.soundnest.radio.RadioFavoritesStore.init(application)
    }

    val api: Api by lazy { Api(app) }
    val repository: AppRepository by lazy { AppRepository(api) }
    val currentSongState: CurrentSongState by lazy { CurrentSongState() }
}
