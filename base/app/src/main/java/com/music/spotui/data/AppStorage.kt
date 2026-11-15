package com.music.spotui.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Settings > Storage: what the app keeps in its cache folder, shown and emptied there
 * (the AutoMix analyses and Glide's covers).
 */
object AppStorage {
    /** What each part takes, in bytes. */
    class Usage(val automix: Long, val images: Long) {
        val caches get() = automix + images
    }

    suspend fun usage(context: Context): Usage = withContext(Dispatchers.IO) {
        Usage(
            automix = runCatching { com.music.spotui.automix.AutoMix.cacheBytes() }.getOrDefault(0L),
            images = size(File(context.cacheDir, com.bumptech.glide.load.engine.cache.DiskCache.Factory.DEFAULT_DISK_CACHE_DIR)),
        )
    }

    /** Empties the caches. The covers in memory are dropped too, on the main thread. */
    suspend fun clearCaches(context: Context) {
        withContext(Dispatchers.IO) {
            runCatching { com.music.spotui.automix.AutoMix.clearCache() }
            runCatching { com.bumptech.glide.Glide.get(context).clearDiskCache() }
        }
        withContext(Dispatchers.Main) { runCatching { com.bumptech.glide.Glide.get(context).clearMemory() } }
    }

    private fun size(dir: File): Long = runCatching { dir.walk().filter { it.isFile }.sumOf { it.length() } }.getOrDefault(0L)
}
