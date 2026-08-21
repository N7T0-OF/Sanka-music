package com.maxrave.simpmusic.ui.screen.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.maxrave.domain.repository.SpotifyPlaylistItem
import com.maxrave.domain.repository.SpotifySyncProgress
import com.maxrave.simpmusic.ui.component.RippleIconButton
import com.maxrave.simpmusic.ui.icon.ArrowBackIosNew
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.SpotifySyncViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifySyncScreen(
    innerPadding: PaddingValues,
    navController: NavController,
    viewModel: SpotifySyncViewModel = koinViewModel(),
) {
    val syncProgress by viewModel.syncProgress.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val selectedPlaylists by viewModel.selectedPlaylists.collectAsStateWithLifecycle()

    // Auto-fetch playlists when screen loads (user should already be OAuth logged in)
    LaunchedEffect(Unit) {
        viewModel.fetchPlaylists()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top App Bar
        TopAppBar(
            title = {
                Text(
                    text = "Spotify Sync",
                    style = typo().titleMedium,
                )
            },
            navigationIcon = {
                Box(Modifier.padding(horizontal = 5.dp)) {
                    RippleIconButton(
                        SimpIcons.ArrowBackIosNew,
                        Modifier.size(32.dp),
                        true,
                    ) {
                        navController.navigateUp()
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
            ),
        )

        // Content
        when (val progress = syncProgress) {
            is SpotifySyncProgress.Idle, is SpotifySyncProgress.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is SpotifySyncProgress.FetchingPlaylists -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Récupération des playlists... (${progress.current}/${progress.total})",
                        style = typo().bodyMedium,
                    )
                }
            }

            is SpotifySyncProgress.PlaylistsReady -> {
                PlaylistSelectionContent(
                    playlists = progress.playlists,
                    selectedIds = selectedPlaylists,
                    onToggleSelection = viewModel::togglePlaylistSelection,
                    onSelectAll = viewModel::toggleSelectAll,
                    onImport = viewModel::importSelectedPlaylists,
                    isImporting = false,
                )
            }

            is SpotifySyncProgress.Importing -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Import de \"${progress.playlistName}\"",
                        style = typo().titleSmall,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${progress.currentTrack} / ${progress.totalTracks} morceaux",
                        style = typo().bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (progress.totalTracks > 0) {
                        LinearProgressIndicator(
                            progress = { progress.currentTrack.toFloat() / progress.totalTracks },
                            modifier = Modifier.fillMaxWidth(0.6f),
                        )
                    }
                }
            }

            is SpotifySyncProgress.PlaylistImported -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "✅ Import terminé !",
                        style = typo().titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "\"${progress.playlistName}\"",
                        style = typo().bodyLarge,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${progress.tracksImported} morceaux importés, ${progress.tracksSkipped} ignorés",
                        style = typo().bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.resetProgress() }) {
                        Text("Retour")
                    }
                }
            }

            is SpotifySyncProgress.AllImported -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "🎉 Tout est importé !",
                        style = typo().titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${progress.totalPlaylists} playlists • ${progress.totalTracksImported} morceaux importés • ${progress.totalTracksSkipped} ignorés",
                        style = typo().bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.resetProgress() }) {
                        Text("Retour")
                    }
                }
            }

            is SpotifySyncProgress.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "❌ Erreur",
                        style = typo().titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = progress.message,
                        style = typo().bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.fetchPlaylists() }) {
                        Text("Réessayer")
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistSelectionContent(
    playlists: List<SpotifyPlaylistItem>,
    selectedIds: Set<String>,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onImport: () -> Unit,
    isImporting: Boolean,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header with select all and import button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onSelectAll) {
                Text(
                    text = if (selectedIds.size == playlists.size) "Tout désélectionner" else "Tout sélectionner",
                )
            }
            Button(
                onClick = onImport,
                enabled = selectedIds.isNotEmpty() && !isImporting,
            ) {
                Text("Importer (${selectedIds.size})")
            }
        }

        // Playlist list
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(
                items = playlists,
                key = { it.id },
            ) { playlist ->
                PlaylistItem(
                    playlist = playlist,
                    isSelected = playlist.id in selectedIds,
                    onToggle = { onToggleSelection(playlist.id) },
                )
            }
        }
    }
}

@Composable
private fun PlaylistItem(
    playlist: SpotifyPlaylistItem,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                ),
            )

            // Playlist thumbnail
            AsyncImage(
                model = playlist.imageUrl,
                contentDescription = playlist.name,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Playlist info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = typo().bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${playlist.trackCount} morceaux${playlist.ownerName?.let { " • $it" } ?: ""}",
                    style = typo().bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
