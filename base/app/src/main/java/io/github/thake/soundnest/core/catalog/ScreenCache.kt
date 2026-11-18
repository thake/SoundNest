package io.github.thake.soundnest.core.catalog

import android.util.AtomicFile
import android.util.Log
import io.github.thake.soundnest.feature.appearance.settings.AppLanguage
import io.github.thake.soundnest.core.model.ContextPageModel
import io.github.thake.soundnest.core.model.HomeFeedModel
import io.github.thake.soundnest.core.model.LibraryEntry
import io.github.thake.soundnest.core.model.SongsModel
import io.github.thake.soundnest.core.di.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The last home feed, library and Liked Songs (and a few playlist and album pages), kept on disk: after a restart the screens
 * show them at once, while the hidden Web Player is still starting, then refresh.
 * Private app storage; cleared on logout ([clear]).
 */
object ScreenCache {
    private const val TAG = "ScreenCache"
    // Bumped when a cached model changes shape.
    private const val DIR = "screen_cache1"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    // One thread: writes and clear() run in call order, so a write already in
    // flight at logout cannot land after the clear and restore the old account.
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val dir: File by lazy { File(Graph.app.filesDir, DIR).also { it.mkdirs() } }
    private val localeFile get() = File(dir, "locale.stamp")

    fun checkLocale() {
        val currentTag = runCatching {
            AppLanguage.effectiveLocale(Graph.app).toLanguageTag()
        }.getOrDefault("en")
        val file = localeFile
        if (file.exists()) {
            val savedTag = runCatching { file.readText().trim() }.getOrNull()
            if (savedTag != null && savedTag != currentTag) {
                Log.i(TAG, "Locale changed from '$savedTag' to '$currentTag'; invalidating disk screen cache")
                dir.listFiles { f -> f.name.endsWith(".json") || f.name.endsWith(".stamp") }?.forEach { it.delete() }
                runCatching { file.writeText(currentTag) }
            }
        } else {
            runCatching { file.writeText(currentTag) }
        }
    }

    val home = Entry("home", HomeFeedModel.serializer())
    val libraryRoot = Entry("library_root", ListSerializer(LibraryEntry.serializer()))
    val liked = Entry("liked", ListSerializer(SongsModel.serializer()))

    /**
     * When Liked Songs were last read in full from Spotify (not just their newest page): the list
     * on storage is trusted for a day after that, see Api.getLikedPage. Null when never.
     */
    fun likedFullAt(): Long? = runCatching { File(dir, "liked_full.stamp").takeIf { it.exists() }?.readText()?.trim()?.toLong() }.getOrNull()

    fun markLikedFull() { io.launch { runCatching { File(dir, "liked_full.stamp").writeText(System.currentTimeMillis().toString()) } } }

    /** An album page opened before, one file per album: it opens whole at once, the network refreshing it behind. */
    fun albumPage(id: String) = Entry("album_page_$id", ContextPageModel.serializer())

    /** Keeps the [keep] most recent album pages on storage and deletes the others. */
    fun pruneAlbumPages(keep: Int) {
        io.launch {
            dir.listFiles { f -> f.name.startsWith("album_page_") && f.name.endsWith(".json") }
                ?.sortedByDescending { it.lastModified() }?.drop(keep)?.forEach { it.delete() }
        }
    }

    /** A playlist page fetched ahead (see PagePrefetch), one file per playlist. */
    fun page(id: String) = Entry("page_$id", ContextPageModel.serializer())

    /** When the page of [id] was last saved; null when there is none. */
    fun pageSavedAt(id: String): Long? = File(dir, "page_$id.json").takeIf { it.exists() }?.lastModified()

    /** Keeps the [keep] most recent pages on storage and deletes the others. */
    fun prunePages(keep: Int) {
        io.launch {
            dir.listFiles { f -> f.name.startsWith("page_") && f.name.endsWith(".json") }
                ?.sortedByDescending { it.lastModified() }?.drop(keep)?.forEach { it.delete() }
        }
    }

    class Entry<T>(private val name: String, private val serializer: KSerializer<T>) {
        private val file get() = AtomicFile(File(dir, "$name.json"))

        /** Blocking read: call from a background thread. */
        fun read(): T? = runCatching {
            checkLocale()
            if (!file.baseFile.exists()) return null
            json.decodeFromString(serializer, file.readFully().decodeToString())
        }.onFailure { Log.w(TAG, "drop unreadable $name: ${it.javaClass.simpleName}"); runCatching { file.delete() } }.getOrNull()

        fun write(value: T) {
            checkLocale()
            io.launch {
                runCatching {
                    val f = file
                    val out = f.startWrite()
                    try {
                        out.write(json.encodeToString(serializer, value).encodeToByteArray())
                        f.finishWrite(out)
                    } catch (t: Throwable) { f.failWrite(out); throw t }
                }.onFailure { Log.w(TAG, "could not save $name: ${it.javaClass.simpleName}") }
            }
        }
    }

    /** Logout / account switch. */
    fun clear() { io.launch { dir.listFiles()?.forEach { it.delete() } } }
}
