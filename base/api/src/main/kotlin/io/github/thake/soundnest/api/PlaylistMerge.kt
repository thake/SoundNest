package io.github.thake.soundnest.api

/**
 * Adding a whole playlist or album to another playlist, with the Web Player's duplicate
 * handling: its `CHECK_DUPLICATE_TRACKS` step reads the target (`getContents`), splits what is
 * to be added into songs already there and new ones, and only calls `PlaylistAPI.add` straight
 * away when nothing is already there. Otherwise its dialog offers "Add anyway" / "Don't add"
 * (everything is a duplicate) or "Add all" / "Add new ones" (some are).
 */
data class MergePlan(
    /** Everything the source holds, in its order. */
    val all: List<Uri>,
    /** The part of [all] that is not in the target yet, in order. */
    val fresh: List<Uri>,
) {
    val duplicateCount: Int get() = all.size - fresh.size
    val hasDuplicates: Boolean get() = duplicateCount > 0
    /** Every song is already there: the dialog offers "Add anyway" and "Don't add". */
    val allDuplicate: Boolean get() = all.isNotEmpty() && fresh.isEmpty()
    /** Some are there, some not: the dialog offers "Add all" and "Add new ones". */
    val someDuplicate: Boolean get() = hasDuplicates && fresh.isNotEmpty()
}

object PlaylistMerge {
    /** The most songs sent in one write. */
    const val CHUNK = 100

    fun plan(existing: Collection<Uri>, incoming: List<Uri>): MergePlan {
        val there = existing.toHashSet()
        return MergePlan(incoming, incoming.filter { it !in there })
    }

    /** Local files cannot be added by uri: they are left out of an album or playlist add. */
    fun addable(uris: List<Uri>): List<Uri> = uris.filterNot(LocalFile::isLocal)

    /** Reads every page ([page] gets the offset to read) and returns the song uris in order. */
    suspend fun readAll(page: suspend (offset: Int) -> Page<Track>): List<Uri> {
        val out = ArrayList<Uri>()
        var offset: Int? = 0
        var guard = 0
        while (offset != null && guard++ < 400) {
            val p = page(offset)
            if (p.items.isEmpty()) break
            out += p.items.map { it.uri }
            offset = p.nextOffset
        }
        return out
    }

    /** The songs of [source] (an album or a playlist) using narrow [Catalog] & [Playlists] ports (ISP). */
    suspend fun sourceTracks(catalog: Catalog, playlists: Playlists, source: Uri): List<Uri> = addable(
        if (source.startsWith("spotify:album:")) readAll { catalog.albumTracks(source, it, 50) }
        else readAll { playlists.tracks(source, it, 100) }
    )

    /** The songs of [source] (an album or a playlist). */
    suspend fun sourceTracks(api: SoundNestApi, source: Uri): List<Uri> =
        sourceTracks(api.catalog, api.playlists, source)

    /**
     * What liking every song of [source] would do: [MergePlan.fresh] holds the ones not liked yet
     * ([liked] answers, in order, whether each is), each song once.
     */
    suspend fun likePlan(source: List<Uri>, liked: suspend (List<Uri>) -> List<Boolean>): MergePlan {
        val incoming = source.distinct()
        val flags = liked(incoming)
        return MergePlan(incoming, incoming.filterIndexed { i, _ -> flags.getOrNull(i) != true })
    }

    /** [likePlan] for an album or playlist using narrow ports (ISP). */
    suspend fun likePlan(catalog: Catalog, playlists: Playlists, library: Library, source: Uri): MergePlan =
        likePlan(sourceTracks(catalog, playlists, source)) { library.contains(it) }

    /** [likePlan] for an album or playlist, read from [api]. */
    suspend fun likePlan(api: SoundNestApi, source: Uri): MergePlan =
        likePlan(api.catalog, api.playlists, api.library, source)

    /** What adding [source] to [target] would do using narrow ports (ISP). */
    suspend fun plan(catalog: Catalog, playlists: Playlists, target: Uri, source: Uri): MergePlan =
        plan(readAll { playlists.tracks(target, it, 100) }, sourceTracks(catalog, playlists, source))

    /** What adding [source] to [target] would do. */
    suspend fun plan(api: SoundNestApi, target: Uri, source: Uri): MergePlan =
        plan(api.catalog, api.playlists, target, source)
}
