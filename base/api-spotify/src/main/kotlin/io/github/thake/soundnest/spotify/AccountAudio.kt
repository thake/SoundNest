package io.github.thake.soundnest.spotify

/** Shared read-only ownership state for native audio ports. */
interface AccountAudioState {
    /** The user turned it on and it can play on this device. */
    val on: Boolean
    /**
     * It holds the account's playback now (a song loaded on it, playing or paused): the page then
     * sees "another device" play, and that device is this one. False once playback moved away.
     */
    val holdsPlayback: Boolean get() = false
}

/**
 * Playback-only port for audio played outside the Web Player page (the native engine),
 * consumed by [SpotifyPlayer]. Segregated from [AccountAudioConnect] per ISP.
 */
interface AccountAudioPlayer : AccountAudioState {
    /**
     * What it plays and where, from its own player, live; null with nothing loaded. [SpotifyPlayer]
     * shows this rather than the page's state of it, which Spotify's servers relay seconds late.
     * Null flow: a player that does not tell (the page's state is shown).
     */
    val now: kotlinx.coroutines.flow.StateFlow<io.github.thake.soundnest.engine.NativeEngine.Now?>? get() = null
    /**
     * It plays a listen mirror now: not the account's songs the user plays, so its playback is not
     * shown as theirs, and the page's side never repairs, pauses or follows it.
     */
    val mirrors: Boolean get() = false
    /** [order]: the context's songs in an order of the app's (a sorted list, a smart shuffle), null for the list's own. */
    /** [shuffle]: the shuffle the play starts with (the user's last choice); null leaves the account's. */
    suspend fun play(contextUri: String, trackUri: String?, index: Int?, positionMs: Long?, order: List<String>?, shuffle: Boolean? = null)
    suspend fun pause()
    suspend fun resume()
    suspend fun seek(positionMs: Long)
    suspend fun next()
    suspend fun previous()
}

/**
 * Spotify Connect transfer/ownership port for audio played outside the Web Player page,
 * consumed by [SpotifyDevices]. Segregated from [AccountAudioPlayer] per ISP.
 */
interface AccountAudioConnect : AccountAudioState {
    /** The Spotify Connect device it registered as, null before it did: this phone, not "another device". */
    val deviceId: String?
    suspend fun transferTo(deviceId: String, play: Boolean, aliasId: String? = null): Boolean = false
    suspend fun yieldPlayback() {}
    suspend fun pullHere(play: Boolean): Boolean = false
}

/**
 * Combined native audio engine contract implemented by `NativeAudio` in `:app`.
 */
interface AccountAudio : AccountAudioPlayer, AccountAudioConnect
