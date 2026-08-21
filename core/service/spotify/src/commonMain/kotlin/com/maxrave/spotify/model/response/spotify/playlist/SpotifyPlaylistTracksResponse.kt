package com.maxrave.spotify.model.response.spotify.playlist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotifyPlaylistTracksResponse(
    val href: String? = null,
    val limit: Int? = null,
    val next: String? = null,
    val offset: Int? = null,
    val previous: String? = null,
    val total: Int? = null,
    val items: List<SpotifyPlaylistTrackItem> = emptyList(),
)

@Serializable
data class SpotifyPlaylistTrackItem(
    @SerialName("added_at")
    val addedAt: String? = null,
    @SerialName("added_by")
    val addedBy: SpotifyOwner? = null,
    @SerialName("is_local")
    val isLocal: Boolean? = null,
    @SerialName("track")
    val track: SpotifyTrack? = null,
)

@Serializable
data class SpotifyTrack(
    val id: String = "",
    val name: String = "",
    val uri: String? = null,
    val duration_ms: Long? = null,
    val explicit: Boolean? = null,
    val href: String? = null,
    @SerialName("artists")
    val artists: List<SpotifyArtist> = emptyList(),
    @SerialName("album")
    val album: SpotifyAlbum? = null,
)

@Serializable
data class SpotifyArtist(
    val id: String? = null,
    val name: String = "",
    val uri: String? = null,
)

@Serializable
data class SpotifyAlbum(
    val id: String? = null,
    val name: String? = null,
    val uri: String? = null,
    @SerialName("images")
    val images: List<SpotifyImage> = emptyList(),
    @SerialName("total_tracks")
    val totalTracks: Int? = null,
)
