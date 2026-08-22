package com.maxrave.data.repository

import com.maxrave.data.db.datasource.LocalDataSource
import com.maxrave.domain.data.entities.LocalPlaylistEntity
import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.repository.SpotifyPlaylistItem
import com.maxrave.domain.repository.SpotifySyncProgress
import com.maxrave.domain.repository.SpotifySyncRepository
import com.maxrave.kotlinytmusicscraper.YouTube
import com.maxrave.kotlinytmusicscraper.models.SongItem
import com.maxrave.logger.Logger
import com.maxrave.spotify.Spotify
import com.maxrave.spotify.model.response.spotify.playlist.SpotifyPlaylist
import com.maxrave.spotify.model.response.spotify.playlist.SpotifyTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

private const val TAG = "SpotifySyncRepo"

internal class SpotifySyncRepositoryImpl(
    private val spotify: Spotify,
    private val youtube: YouTube,
    private val localDataSource: LocalDataSource,
    private val dataStoreManager: DataStoreManager,
) : SpotifySyncRepository {

    private suspend fun getValidToken(): String? {
        // 1. Try OAuth PKCE token first (for dedicated playlist access)
        val oauthToken = dataStoreManager.spotifyOAuthAccessToken.first()
        val oauthExpiresAt = dataStoreManager.spotifyOAuthExpiresAt.first()
        if (oauthToken.isNotEmpty() && oauthExpiresAt > Clock.System.now().toEpochMilliseconds()) {
            return oauthToken
        }
        // OAuth expired? Try refresh
        val oauthRefreshToken = dataStoreManager.spotifyOAuthRefreshToken.first()
        if (oauthRefreshToken.isNotEmpty()) {
            try {
                val result = spotify.refreshOAuthToken(oauthRefreshToken).getOrNull()
                if (result != null) {
                    dataStoreManager.setSpotifyOAuthAccessToken(result.accessToken)
                    dataStoreManager.setSpotifyOAuthRefreshToken(result.refreshToken)
                    dataStoreManager.setSpotifyOAuthExpiresAt(Clock.System.now().toEpochMilliseconds() + (result.expiresIn * 1000L))
                    return result.accessToken
                }
            } catch (e: Exception) { Logger.e(TAG, "OAuth refresh failed: ${e.message}") }
        }

        // 2. Fall back to web player personal token (from existing SimpMusic Spotify login via sp_dc)
        val personalToken = dataStoreManager.spotifyPersonalToken.first()
        val personalExpires = dataStoreManager.spotifyPersonalTokenExpires.first()
        if (personalToken.isNotEmpty() && personalExpires > Clock.System.now().toEpochMilliseconds()) {
            return personalToken
        }
        // Personal token expired? Try to refresh via sp_dc cookie
        val spdc = dataStoreManager.spdc.first()
        if (spdc.isNotEmpty()) {
            try {
                val result = spotify.getPersonalToken(spdc).getOrNull()
                if (result != null) {
                    dataStoreManager.setSpotifyPersonalToken(result.accessToken)
                    dataStoreManager.setSpotifyPersonalTokenExpires(result.accessTokenExpirationTimestampMs)
                    return result.accessToken
                }
            } catch (e: Exception) { Logger.e(TAG, "Personal token refresh failed: ${e.message}") }
        }

        return null
    }

    override fun fetchPlaylists(): Flow<SpotifySyncProgress> = flow {
        emit(SpotifySyncProgress.Loading)
        val token = getValidToken()
        if (token == null) { emit(SpotifySyncProgress.Error("Spotify non connecté.")); return@flow }
        val all = mutableListOf<SpotifyPlaylist>()
        var offset = 0; var total = Int.MAX_VALUE
        try {
            while (offset < total) {
                emit(SpotifySyncProgress.FetchingPlaylists(offset, total))
                spotify.getUserPlaylists(token, 50, offset).onSuccess {
                    total = it.total ?: 0; all.addAll(it.items); offset += 50
                }.onFailure { emit(SpotifySyncProgress.Error("Erreur: ${it.message}")); return@flow }
            }
            emit(SpotifySyncProgress.PlaylistsReady(all.map { SpotifyPlaylistItem(it.id, it.name, it.description, it.tracks?.total ?: 0, it.images.firstOrNull()?.url, it.owner?.display_name) }))
        } catch (e: Exception) { emit(SpotifySyncProgress.Error("Erreur: ${e.message}")) }
    }.flowOn(Dispatchers.IO)

    override fun importPlaylist(playlistId: String, playlistName: String): Flow<SpotifySyncProgress> = flow {
        emit(SpotifySyncProgress.Loading)
        val token = getValidToken()
        if (token == null) { emit(SpotifySyncProgress.Error("Spotify non connecté.")); return@flow }
        try {
            val tracks = mutableListOf<SpotifyTrack>()
            var offset = 0; var total = Int.MAX_VALUE
            while (offset < total) {
                emit(SpotifySyncProgress.Importing(playlistName, tracks.size, total))
                spotify.getPlaylistTracks(token, playlistId, 100, offset).onSuccess {
                    total = it.total ?: 0; it.items.forEach { i -> i.track?.let { t -> if (t.id.isNotEmpty() && !(i.isLocal ?: false)) tracks.add(t) } }; offset += 100
                }.onFailure { emit(SpotifySyncProgress.Error("Erreur: ${it.message}")); return@flow }
            }
            val songs = mutableListOf<SongEntity>(); val vids = mutableListOf<String>(); var skip = 0
            tracks.forEachIndexed { idx, t ->
                emit(SpotifySyncProgress.Importing(playlistName, idx + 1, tracks.size))
                try {
                    val r = youtube.search("${t.artists.joinToString(" ") { it.name }} ${t.name}", YouTube.SearchFilter.FILTER_SONG)
                    val first = r.getOrNull()?.items?.firstOrNull()
                    if (first is SongItem) { songs.add(SongEntity(videoId = first.id, albumId = null, albumName = null, artistId = first.artists.mapNotNull { it.id }.takeIf { it.isNotEmpty() }, artistName = first.artists.map { it.name }.takeIf { it.isNotEmpty() }, duration = first.duration?.toString() ?: "", durationSeconds = first.duration ?: 0, isAvailable = true, isExplicit = first.explicit, likeStatus = "", thumbnails = first.thumbnail, title = first.title, videoType = "MUSIC_VIDEO_TYPE_ATV", category = null, resultType = null)); vids.add(first.id) } else skip++
                } catch (_: Exception) { skip++ }
            }
            if (songs.isNotEmpty()) localDataSource.insertSongs(songs)
            localDataSource.insertLocalPlaylistWithTracks(LocalPlaylistEntity(title = playlistName, thumbnail = null, tracks = vids), vids)
            emit(SpotifySyncProgress.PlaylistImported(playlistName, songs.size, skip))
        } catch (e: Exception) { emit(SpotifySyncProgress.Error("Erreur: ${e.message}")) }
    }.flowOn(Dispatchers.IO)

    override fun importAllPlaylists(playlists: List<Pair<String, String>>): Flow<SpotifySyncProgress> = flow {
        var imp = 0; var sk = 0
        playlists.forEach { (id, name) ->
            emit(SpotifySyncProgress.Importing(name, playlists.indexOf(Pair(id, name)), playlists.size))
            importPlaylist(id, name).collect { p ->
                if (p is SpotifySyncProgress.PlaylistImported) { imp += p.tracksImported; sk += p.tracksSkipped }
                else if (p is SpotifySyncProgress.Error) emit(p)
            }
        }
        emit(SpotifySyncProgress.AllImported(playlists.size, imp, sk))
    }.flowOn(Dispatchers.IO)
}
