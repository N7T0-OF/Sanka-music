package com.maxrave.spotify.model.response.spotify.playlist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotifyPlaylistsResponse(
    val href: String? = null,
    val limit: Int? = null,
    val next: String? = null,
    val offset: Int? = null,
    val previous: String? = null,
    val total: Int? = null,
    val items: List<SpotifyPlaylist> = emptyList(),
)

@Serializable
data class SpotifyPlaylist(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val href: String? = null,
    val uri: String? = null,
    val public: Boolean? = null,
    val collaborative: Boolean? = null,
    @SerialName("owner")
    val owner: SpotifyOwner? = null,
    @SerialName("images")
    val images: List<SpotifyImage> = emptyList(),
    @SerialName("tracks")
    val tracks: SpotifyPlaylistTracksRef? = null,
)

@Serializable
data class SpotifyOwner(
    val id: String? = null,
    val display_name: String? = null,
    val uri: String? = null,
)

@Serializable
data class SpotifyImage(
    val url: String = "",
    val height: Int? = null,
    val width: Int? = null,
)

@Serializable
data class SpotifyPlaylistTracksRef(
    val href: String? = null,
    val total: Int? = null,
)
