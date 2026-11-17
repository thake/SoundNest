package io.github.thake.soundnest.feature.library.settings

import io.github.thake.soundnest.core.model.ArtistsModel
import io.github.thake.soundnest.core.model.LibraryEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryChipsTest {
    private fun playlist(id: String) = LibraryEntry(spotifyId = id, name = id, subtitle = "", coverUri = "", isPlaylist = true)
    private fun album(id: String) = LibraryEntry(spotifyId = id, name = id, subtitle = "", coverUri = "", isPlaylist = false)
    private fun folder(id: String) = LibraryEntry(spotifyId = id, name = id, subtitle = "", coverUri = "", isPlaylist = false, folderUri = id)

    private val liked = playlist("liked")
    private val local = playlist("local")
    private val songIds = setOf("liked", "local")
    private val entries = listOf(liked, local, playlist("p1"), album("a1"), folder("f1"), playlist("p2"), album("a2"))
    private val artists = listOf(ArtistsModel("Adele", "", "1"), ArtistsModel("Zaz", "", "2"))

    private fun ids(chip: LibraryChip) = LibraryChips.apply(chip, entries, artists, songIds).first.map { it.spotifyId }

    @Test fun allKeepsEverything() {
        val (e, a) = LibraryChips.apply(LibraryChip.ALL, entries, artists, songIds)
        assertEquals(entries, e)
        assertEquals(artists, a)
    }

    @Test fun playlistsKeepPlaylistsAndFoldersButNotTheSongRows() =
        assertEquals(listOf("p1", "f1", "p2"), ids(LibraryChip.PLAYLISTS))

    @Test fun albumsKeepOnlyAlbums() = assertEquals(listOf("a1", "a2"), ids(LibraryChip.ALBUMS))

    @Test fun songsKeepLikedAndPhoneRows() = assertEquals(listOf("liked", "local"), ids(LibraryChip.SONGS))

    @Test fun artistsKeepOnlyTheArtists() {
        val (e, a) = LibraryChips.apply(LibraryChip.ARTISTS, entries, artists, songIds)
        assertEquals(emptyList<LibraryEntry>(), e)
        assertEquals(artists, a)
        assertEquals(emptyList<ArtistsModel>(), LibraryChips.apply(LibraryChip.ALBUMS, entries, artists, songIds).second)
    }

    @Test fun tappingTheChosenChipAgainShowsEverything() {
        assertEquals(LibraryChip.ALBUMS, LibraryChips.toggle(LibraryChip.ALL, LibraryChip.ALBUMS))
        assertEquals(LibraryChip.ARTISTS, LibraryChips.toggle(LibraryChip.ALBUMS, LibraryChip.ARTISTS))
        assertEquals(LibraryChip.ALL, LibraryChips.toggle(LibraryChip.ARTISTS, LibraryChip.ARTISTS))
        assertEquals(LibraryChip.AUDIOBOOKS, LibraryChips.toggle(LibraryChip.ALL, LibraryChip.AUDIOBOOKS))
        assertEquals(LibraryChip.ALL, LibraryChips.toggle(LibraryChip.AUDIOBOOKS, LibraryChip.AUDIOBOOKS))
    }

    @Test fun audiobooksChipKeepsAudibleAndSpotifyAudiobooksAndExcludesThemFromAlbums() {
        val audibleBook = LibraryEntry(
            spotifyId = "audible:B08G9PRS1K",
            name = "Project Hail Mary",
            subtitle = "Andy Weir",
            coverUri = "https://example.com/phm.jpg",
            isPlaylist = false,
            uri = "audible:book:B08G9PRS1K",
        )
        val spotifyBook = LibraryEntry(
            spotifyId = "book1",
            name = "The Hobbit",
            subtitle = "J.R.R. Tolkien",
            coverUri = "https://example.com/hobbit.jpg",
            isPlaylist = false,
            uri = "spotify:audiobook:book1",
        )
        val mixed = entries + listOf(audibleBook, spotifyBook)

        val audiobookIds = LibraryChips.apply(LibraryChip.AUDIOBOOKS, mixed, artists, songIds).first.map { it.spotifyId }
        assertEquals(listOf("audible:B08G9PRS1K", "book1"), audiobookIds)

        val albumIds = LibraryChips.apply(LibraryChip.ALBUMS, mixed, artists, songIds).first.map { it.spotifyId }
        assertEquals(listOf("a1", "a2"), albumIds)

        val allIds = LibraryChips.apply(LibraryChip.ALL, mixed, artists, songIds).first.map { it.spotifyId }
        assertEquals(mixed.map { it.spotifyId }, allIds)
    }
}

