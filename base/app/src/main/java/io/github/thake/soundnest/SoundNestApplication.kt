// Myspoti / Orpheon (https://github.com/kota-zo/Orpheon), GPL-3.0.
// Reuse requires credit to Myspoti / Orpheon, these notices kept, and your full source published under GPL-3.0.
package io.github.thake.soundnest

import android.app.Application
import android.content.Context
import android.support.v4.media.MediaBrowserCompat
import io.github.thake.soundnest.host.android.MediaLibrary
import io.github.thake.soundnest.host.android.PlaybackService
import io.github.thake.soundnest.feature.androidauto.AutoBrowser
import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.core.playback.SongPlayer
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Starts the Spotify session, the UI singletons and the playback service bindings. */
class SoundNestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // A crash or a freeze of the last run, back in this run's diagnostic report.
        io.github.thake.soundnest.host.android.LastExit.install(this)
        io.github.thake.soundnest.feature.system.settings.EnergySettings.init(this)
        // Spotify session (hidden Web Player + API), caches, data saver.
        Graph.init(this)
        SongPlayer.attachEngine(Graph.currentSongState)
        // Headphones or Bluetooth gone: pause rather than play on the speaker.
        io.github.thake.soundnest.core.playback.NoisyAudio.register(this)
        runCatching { java.io.File(filesDir, "wrapped_queue.json").delete() }
        PlaybackService.library = object : MediaLibrary {
            override suspend fun children(context: Context, parentId: String): List<MediaBrowserCompat.MediaItem> =
                AutoBrowser.children(context, parentId)
            override suspend fun search(query: String): List<MediaBrowserCompat.MediaItem> = AutoBrowser.search(query)
        }
        prefetch()
        // New releases of the artists followed, said in a notification (a setting).
        io.github.thake.soundnest.feature.home.whatsnew.settings.NewReleaseNotifier.start(this)
        followAccount()
    }

    private fun followAccount() {
        if (!io.github.thake.soundnest.host.android.SoundNestRuntime.isStarted) return
        val ctx = this
        io.github.thake.soundnest.host.android.SoundNestRuntime.scope.launch {
            io.github.thake.soundnest.host.android.SoundNestRuntime.host.accounts.list
                .map { it.active }.distinctUntilChanged().drop(1)
                .collect {
                    io.github.thake.soundnest.host.android.Logbook.log("account: settings of the account now in use (quality)")
                    io.github.thake.soundnest.feature.player.settings.applyStreamingQuality(ctx)
                }
        }
    }

    private fun prefetch() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            io.github.thake.soundnest.host.android.SoundNestRuntime.api.ready.first { it }
            if (!io.github.thake.soundnest.feature.accounts.settings.OnboardingSettings.isLoggedIn(this@SoundNestApplication)) return@launch
            val api = io.github.thake.soundnest.core.catalog.Api()
            launch { runCatching { api.getLibraryRoot().collect {} } }
            launch { runCatching { api.getLikedPage().collect {} } }
        }
    }
}
