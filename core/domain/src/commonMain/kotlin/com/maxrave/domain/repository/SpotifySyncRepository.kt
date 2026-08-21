package com.maxrave.domain.repository

import kotlinx.coroutines.flow.Flow

interface SpotifySyncRepository {
    fun fetchPlaylists(): Flow<SpotifySyncProgress>
    fun importPlaylist(playlistId: String, playlistName: String): Flow<SpotifySyncProgress>
    fun importAllPlaylists(playlists: List<Pair<String, String>>): Flow<SpotifySyncProgress>
}

sealed interface SpotifySyncProgress {
    data object Idle : SpotifySyncProgress
    data object Loading : SpotifySyncProgress
    data class FetchingPlaylists(val current: Int = 0, val total: Int = 0) : SpotifySyncProgress
    data class Importing(val playlistName: String = "", val currentTrack: Int = 0, val totalTracks: Int = 0) : SpotifySyncProgress
    data class PlaylistsReady(val playlists: List<SpotifyPlaylistItem>) : SpotifySyncProgress
    data class PlaylistImported(val playlistName: String, val tracksImported: Int, val tracksSkipped: Int) : SpotifySyncProgress
    data class AllImported(val totalPlaylists: Int, val totalTracksImported: Int, val totalTracksSkipped: Int) : SpotifySyncProgress
    data class Error(val message: String) : SpotifySyncProgress
}

data class SpotifyPlaylistItem(
    val id: String,
    val name: String,
    val description: String?,
    val trackCount: Int,
    val imageUrl: String?,
    val ownerName: String?,
)
