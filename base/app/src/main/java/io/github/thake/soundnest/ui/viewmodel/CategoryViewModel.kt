package io.github.thake.soundnest.ui.viewmodel

import io.github.thake.soundnest.di.Graph
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.thake.soundnest.data.api.Response
import io.github.thake.soundnest.data.entity.LibraryEntry
import io.github.thake.soundnest.ui.repository.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CategoryViewModel constructor(private val repository: AppRepository = Graph.repository) : ViewModel() {

    private val _playlists: MutableStateFlow<Response<List<LibraryEntry>>> = MutableStateFlow(Response.Loading())
    val playlists: StateFlow<Response<List<LibraryEntry>>> = _playlists

    private var genreKey: String? = null

    fun load(genre: String) {
        if (genreKey == genre) return
        genreKey = genre
        viewModelScope.launch(Dispatchers.IO) {
            repository.provideCategoryPlaylists(genre).collect { _playlists.value = it }
        }
    }
}
