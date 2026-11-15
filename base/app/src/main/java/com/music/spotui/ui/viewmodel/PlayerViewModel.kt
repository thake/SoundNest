package com.music.spotui.ui.viewmodel

import com.music.spotui.di.Graph
import android.content.Context
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.di.CurrentSongState
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.repository.AppRepository
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class PlayerViewModel constructor(private val currentSongState: CurrentSongState = Graph.currentSongState, private val repository: AppRepository = Graph.repository) : ViewModel(){

    val currentSongTitle: State<String> get() = currentSongState.title
    val currentSongSinger: State<String> get() = currentSongState.singer
    val currentSongCoverUri: State<String> get() = currentSongState.coverUri
    val currentSongPlayingState: State<Boolean> get() = currentSongState.playingState
    val currentSongIndex : State<Int> get() = currentSongState.songIndex
    val currentSongAlbum : State<String> get() = currentSongState.album

    val currentSongId : State<Int> get() = currentSongState.songId

    val queue : State<List<SongsModel>> get() = currentSongState.queue

    fun updateQueue(songs: List<SongsModel>) = currentSongState.updateQueue(songs)



    val shuffleState = currentSongState.shuffle
    val repeatState = currentSongState.repeat
    val likeState = currentSongState.likeState





    // Spotify owns the queue (context + autoplay): skip in the engine.
    fun playNextSongs(queueSongs : List<SongsModel>, context: Context) {
        SongPlayer.swipeNext()
    }

    /** "Go to song radio": opens the track's radio, Spotify's station playlist. */
    fun goToSongRadio(trackId: String, title: String, navigate: (route: String) -> Unit, onFailed: () -> Unit) {
        viewModelScope.launch {
            val id = com.music.spotui.di.Graph.api.radioPlaylistId("spotify:track:$trackId")
            if (id.isNullOrBlank()) onFailed()
            else navigate(com.music.spotui.ui.navigation.playlistRoute(id, title))
        }
    }

    /** The song's artist page; a song by several artists asks which one first (see ArtistChooser). */
    fun goToArtist(trackId: String, fallbackName: String, navigate: (route: String) -> Unit) =
        com.music.spotui.ui.components.ArtistChooser.goForTrack(trackId, fallbackName, navigate)

    fun playPreviousSong(queueSongs : List<SongsModel>, context: Context) {
        SongPlayer.previous()
    }
    fun formatDuration(durationMillis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis) - TimeUnit.MINUTES.toSeconds(minutes)
        return String.format("%01d:%02d", minutes, seconds)
    }
    fun updateSongState(coverUri: String, title: String, singer: String, playingState: Boolean, songId : Int, songIndex : Int = 0, album : String = "") {
        currentSongState.updateSongState(coverUri, title, singer, playingState, songId, songIndex, album)
    }

    // Shuffle / repeat / like are Spotify's: toggle them in the engine and show
    // the new value right away (the engine status confirms it shortly after).
    fun updateShuffleState(shuffleState : Boolean){
        SongPlayer.setShuffle(shuffleState)
        currentSongState.shuffle.value = shuffleState
    }
    fun updateRepeatState(repeatState : Boolean){
        SongPlayer.setRepeat(repeatState)
        currentSongState.updateRepeatState(repeatState)
    }
    fun updateLikeState(likeState : Boolean){
        SongPlayer.setLiked(likeState)
    }
}


internal fun confirmedTrackIdFromUri(uri: String): String =
    if (uri.startsWith("spotify:track:")) uri.removePrefix("spotify:track:") else ""

/**
 * The Spotify id of the song shown, once the engine confirms it: CurrentSongState uses this
 * canonical id.
 */
internal fun confirmedTrackIdForDisplay(
    uri: String,
    displayedSongId: Int,
): String {
    val trackId = confirmedTrackIdFromUri(uri)
    val confirmedSongId = ("track:$trackId").hashCode() and 0x7fffffff
    return trackId.takeIf { it.isNotBlank() && confirmedSongId == displayedSongId }.orEmpty()
}
