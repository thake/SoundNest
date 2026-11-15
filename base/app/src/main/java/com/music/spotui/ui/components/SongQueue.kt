package com.music.spotui.ui.components

import android.content.Context
import android.widget.Toast
import app.myspoti.engine.R
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.di.SongPlayer

/**
 * "Add to queue" and "Play next", for every place that offers them (the song menus, the
 * swipe on a row).
 */
object SongQueue {
    /** Whether [song] can be queued at all: a Spotify song with a track id. */
    fun canQueue(song: SongsModel): Boolean = song.spotifyTrackId.isNotBlank()

    /** Queues [song] (after the song playing when [next]) and says so. */
    fun add(context: Context, song: SongsModel, next: Boolean = false) {
        val id = song.spotifyTrackId
        if (id.isBlank()) return
        queue(context.applicationContext, id, next)
    }

    private fun queue(context: Context, spotifyTrackId: String, next: Boolean) {
        if (next) SongPlayer.playNext(spotifyTrackId) else SongPlayer.addToQueue(spotifyTrackId)
        val said = if (next) R.string.context_menu_play_next else R.string.snackbar_added_to_queue
        Toast.makeText(context, said, Toast.LENGTH_SHORT).show()
    }
}
