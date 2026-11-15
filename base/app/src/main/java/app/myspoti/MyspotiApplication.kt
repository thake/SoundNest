// Myspoti / Orpheon (https://github.com/kota-zo/Orpheon), GPL-3.0.
// Reuse requires credit to Myspoti / Orpheon, these notices kept, and your full source published under GPL-3.0.
package app.myspoti

import android.app.Application
import android.content.Context
import android.support.v4.media.MediaBrowserCompat
import app.myspoti.host.android.MediaLibrary
import app.myspoti.host.android.PlaybackService
import com.music.spotui.di.AutoBrowser
import com.music.spotui.di.Graph
import com.music.spotui.di.SongPlayer
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Starts the Spotify session, the UI singletons and the playback service bindings. */
class MyspotiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // A crash or a freeze of the last run, back in this run's diagnostic report.
        app.myspoti.host.android.LastExit.install(this)
        app.myspoti.power.EnergySettings.init(this)
        // Spotify session (hidden Web Player + API), caches, data saver.
        Graph.init(this)
        SongPlayer.attachEngine(Graph.currentSongState)
        // Headphones or Bluetooth gone: pause rather than play on the speaker.
        com.music.spotui.di.NoisyAudio.register(this)
        runCatching { java.io.File(filesDir, "wrapped_queue.json").delete() }
        PlaybackService.library = object : MediaLibrary {
            override suspend fun children(context: Context, parentId: String): List<MediaBrowserCompat.MediaItem> =
                AutoBrowser.children(context, parentId)
            override suspend fun search(query: String): List<MediaBrowserCompat.MediaItem> = AutoBrowser.search(query)
        }
        prefetch()
        // New releases of the artists followed, said in a notification (a setting).
        com.music.spotui.data.NewReleaseNotifier.start(this)
        followAccount()
    }

    private fun followAccount() {
        if (!app.myspoti.host.android.MyspotiRuntime.isStarted) return
        val ctx = this
        app.myspoti.host.android.MyspotiRuntime.scope.launch {
            app.myspoti.host.android.MyspotiRuntime.host.accounts.list
                .map { it.active }.distinctUntilChanged().drop(1)
                .collect {
                    app.myspoti.host.android.Logbook.log("account: settings of the account now in use (quality)")
                    com.music.spotui.data.preferences.applyStreamingQuality(ctx)
                }
        }
    }

    private fun prefetch() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            app.myspoti.host.android.MyspotiRuntime.api.ready.first { it }
            if (!getSharedPreferences("myspoti_prefs", MODE_PRIVATE).getBoolean("LoggedIn", false)) return@launch
            val api = com.music.spotui.data.api.Api()
            launch { runCatching { api.getLibraryRoot().collect {} } }
            launch { runCatching { api.getLikedPage().collect {} } }
        }
    }
}
