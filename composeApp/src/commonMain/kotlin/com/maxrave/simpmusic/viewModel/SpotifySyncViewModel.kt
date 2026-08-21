package com.maxrave.simpmusic.viewModel

import com.maxrave.domain.repository.SpotifyPlaylistItem
import com.maxrave.domain.repository.SpotifySyncProgress
import com.maxrave.domain.repository.SpotifySyncRepository
import com.maxrave.simpmusic.viewModel.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SpotifySyncViewModel(
    private val spotifySyncRepository: SpotifySyncRepository,
) : BaseViewModel() {

    private val _syncProgress = MutableStateFlow<SpotifySyncProgress>(SpotifySyncProgress.Idle)
    val syncProgress: StateFlow<SpotifySyncProgress> = _syncProgress.asStateFlow()

    private val _playlists = MutableStateFlow<List<SpotifyPlaylistItem>>(emptyList())
    val playlists: StateFlow<List<SpotifyPlaylistItem>> = _playlists.asStateFlow()

    private val _selectedPlaylists = MutableStateFlow<Set<String>>(emptySet())
    val selectedPlaylists: StateFlow<Set<String>> = _selectedPlaylists.asStateFlow()

    /**
     * Fetches playlists from Spotify. Call this after OAuth login is complete.
     */
    fun fetchPlaylists() {
        viewModelScope.launch {
            spotifySyncRepository.fetchPlaylists().collect { progress ->
                _syncProgress.value = progress
                if (progress is SpotifySyncProgress.PlaylistsReady) {
                    _playlists.value = progress.playlists
                }
            }
        }
    }

    /**
     * Toggles selection of a playlist for import.
     */
    fun togglePlaylistSelection(playlistId: String) {
        _selectedPlaylists.value = _selectedPlaylists.value.let { current ->
            if (playlistId in current) {
                current - playlistId
            } else {
                current + playlistId
            }
        }
    }

    /**
     * Selects or deselects all playlists.
     */
    fun toggleSelectAll() {
        if (_selectedPlaylists.value.size == _playlists.value.size) {
            _selectedPlaylists.value = emptySet()
        } else {
            _selectedPlaylists.value = _playlists.value.map { it.id }.toSet()
        }
    }

    /**
     * Imports a single playlist.
     */
    fun importPlaylist(playlistId: String, playlistName: String) {
        viewModelScope.launch {
            spotifySyncRepository.importPlaylist(playlistId, playlistName).collect { progress ->
                _syncProgress.value = progress
            }
        }
    }

    /**
     * Imports all selected playlists.
     */
    fun importSelectedPlaylists() {
        val selected = _selectedPlaylists.value
        if (selected.isEmpty()) return

        val playlistPairs = _playlists.value
            .filter { it.id in selected }
            .map { it.id to it.name }

        viewModelScope.launch {
            spotifySyncRepository.importAllPlaylists(playlistPairs).collect { progress ->
                _syncProgress.value = progress
            }
        }
    }

    /**
     * Resets the sync progress to idle.
     */
    fun resetProgress() {
        _syncProgress.value = SpotifySyncProgress.Idle
    }
}
