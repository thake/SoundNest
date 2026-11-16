package io.github.thake.soundnest.data.api

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.thake.soundnest.api.best
import io.github.thake.soundnest.host.android.SoundNestRuntime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** The signed-in profile (name, avatar, user id), fetched once per session. Compose state. */
object ProfileCache {
    var name by mutableStateOf<String?>(null)
        private set
    var imageUrl by mutableStateOf<String?>(null)
        private set
    /** Spotify user id (owner of the playlists the user can edit). */
    @Volatile var id: String? = null
        private set

    private val fetching = AtomicBoolean(false)
    private val generation = java.util.concurrent.atomic.AtomicLong()

    /**
     * The account is no longer known: at sign-out, and when the session is lost without one
     * (SessionActions.sessionLost), when another account may sign in next. A profile still being
     * read is dropped, and read again for the session that follows.
     */
    fun clearSession() {
        generation.incrementAndGet()
        name = null
        imageUrl = null
        id = null
    }

    fun ensure(context: Context) {
        if (name != null) return
        if (!fetching.compareAndSet(false, true)) return
        SoundNestRuntime.scope.launch {
            try {
                while (true) {
                    val requestGeneration = generation.get()
                    SoundNestRuntime.api.ready.first { it }
                    val me = runCatching { SoundNestRuntime.api.users.me() }.getOrNull()
                    // The session was lost meanwhile: this profile may be the account before, the next one's is read.
                    if (generation.get() != requestGeneration) continue
                    // No account (an anonymous token): nothing is kept, the next session's is read.
                    if (me == null || me.username.isBlank()) return@launch
                    name = me.name
                    imageUrl = me.images.best(160)?.url
                    id = me.username
                    // The accounts kept on the phone: this profile's name and picture, under an opaque key
                    // (no id, no e-mail). The same account in another profile is one account.
                    val key = io.github.thake.soundnest.data.accountKeyOf(me.username)
                    val host = SoundNestRuntime.host
                    val profile = host.profile
                    var duplicates = emptyList<String>()
                    host.accounts.update { list ->
                        list.signedIn(profile, key, me.name, imageUrl, System.currentTimeMillis()).also { duplicates = it.second }.first
                    }
                    duplicates.forEach { host.removeProfile(it) }
                    // Its listen history is the one shown.
                    io.github.thake.soundnest.data.local.ListenHistory.claim(key)
                    return@launch
                }
            } finally {
                fetching.set(false)
            }
        }
    }

    /**
     * The signed-in account's id, read first when it is not known yet ([ensure]): waited for at most
     * [timeoutMs] (offline, Spotify not answering), null then.
     */
    suspend fun awaitId(context: Context, timeoutMs: Long): String? {
        id?.let { return it }
        ensure(context)
        return kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            while (id == null) kotlinx.coroutines.delay(100)
            id
        }
    }
}
