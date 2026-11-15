package com.music.spotui.di

import android.app.Application
import app.myspoti.host.android.MyspotiRuntime
import com.music.spotui.data.api.Api
import com.music.spotui.ui.repository.AppRepository

/** Manual replacement for Spotui's Hilt graph: app-wide singletons. */
object Graph {
    lateinit var app: Application
        private set

    fun init(application: Application) {
        app = application
        // The data saver first: on a metered network with it on, nothing may go out, not even the page.
        com.music.spotui.data.DataSaver.init(application)
        val saver = com.music.spotui.data.DataSaver.active
        MyspotiRuntime.start(application, loadPage = !saver, held = saver)
        // The streaming quality the user chose, set on the player at every page load.
        com.music.spotui.data.preferences.applyStreamingQuality(application)
        // The crossfade length (0 = off), in the player from the first song on.
        com.music.spotui.data.preferences.applyCrossfade(com.music.spotui.data.preferences.getCrossfadeSeconds(application))
        com.music.spotui.data.local.ListenHistory.init(application)
        // Per-look accent colour before any screen draws.
        com.music.spotui.data.preferences.AccentPref.init(application)
        SongPlayer.startLibraryOutbox(application)
        com.music.spotui.data.local.LocalPlaylists.init(application)
        com.music.spotui.data.local.LibraryOrder.init(application)
        runCatching { com.music.spotui.data.DataSaverGlide.install(application) }
        // Live Radio: its own setting and favorites file (see com.music.spotui.radio).
        com.music.spotui.radio.RadioFeaturePref.init(application)
        com.music.spotui.data.preferences.LocalFilesPref.init(application)
        com.music.spotui.radio.RadioFavoritesStore.init(application)
    }

    val api: Api by lazy { Api(app) }
    val repository: AppRepository by lazy { AppRepository(api) }
    val currentSongState: CurrentSongState by lazy { CurrentSongState() }
}
