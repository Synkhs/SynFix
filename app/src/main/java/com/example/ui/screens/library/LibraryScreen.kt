package com.example.ui.screens.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.*
import com.example.ui.components.ArtworkImage
import com.example.ui.components.rememberArtworkShape
import com.example.ui.theme.ExpressiveTypography
import com.example.ui.viewmodel.MusicViewModel
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: MusicViewModel,
    hasMiniPlayer: Boolean,
    onToolbarVisibilityChange: (Boolean) -> Unit,
    onEditTags: (Song) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenProLab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val libraryState by viewModel.libraryState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val appearance by viewModel.appearanceSettings.collectAsStateWithLifecycle()

    var selectedAlbum by remember { mutableStateOf<Album?>(null) }
    var selectedArtist by remember { mutableStateOf<Artist?>(null) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var selectedSmartPlaylist by remember { mutableStateOf<SmartPlaylistType?>(null) }
    var songForPlaylistPicker by remember { mutableStateOf<Song?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    val artworkShape = rememberArtworkShape(appearance.artworkShape)
    val isCompact = appearance.libraryLayout == LibraryViewLayout.LIST_COMPACT
    val gridColumns = when (appearance.libraryLayout) {
        LibraryViewLayout.GRID_3 -> 3
        else -> 2
    }
    val bottomContentPadding = if (hasMiniPlayer) 180.dp else 104.dp

    val listState = rememberLazyListState()
    var lastScrollOffset by remember { mutableIntStateOf(0) }
    var lastFirstVisible by remember { mutableIntStateOf(0) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (firstIndex, offset) ->
                val scrollingDown = firstIndex > lastFirstVisible ||
                    (firstIndex == lastFirstVisible && offset > lastScrollOffset + 18)
                val scrollingUp = firstIndex < lastFirstVisible ||
                    (firstIndex == lastFirstVisible && offset < lastScrollOffset - 18)
                if (scrollingDown) onToolbarVisibilityChange(false)
                else if (scrollingUp || firstIndex == 0) onToolbarVisibilityChange(true)
                lastFirstVisible = firstIndex
                lastScrollOffset = offset
            }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Brand Header + Search + Rescan
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.inverseSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.synfix_logo_foreground),
                        contentDescription = "Logotipo de SynFix",
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.inverseOnSurface),
                        modifier = Modifier.size(28.dp)
                    )
                }
                Column {
                    Text(
                        text = "SynFix",
                        style = ExpressiveTypography.SectionHeaderEmphasized,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${libraryState.songs.size} pistas locales",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(
                    onClick = onOpenProLab,
                    modifier = Modifier.testTag("open_pro_lab_button")
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = "SynFix Lab Pro")
                }
                if (libraryState.songs.isNotEmpty()) {
                    FilledTonalIconButton(
                        onClick = {
                            val random = libraryState.songs.random()
                            viewModel.playSongFromList(random, libraryState.songs.shuffled())
                            onOpenNowPlaying()
                        },
                        modifier = Modifier.testTag("shuffle_all_button")
                    ) {
                        Icon(Icons.Filled.Shuffle, contentDescription = "Aleatorio")
                    }
                }
                FilledTonalIconButton(
                    onClick = { viewModel.scanMusic() },
                    modifier = Modifier.testTag("scan_music_button")
                ) {
                    if (libraryState.isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = "Escanear biblioteca")
                    }
                }
            }
        }

        // Expressive Search Field
        OutlinedTextField(
            value = libraryState.searchQuery,
            onValueChange = viewModel::setSearchQuery,
            placeholder = { Text("Buscar canciones, álbumes o artistas...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (libraryState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Limpiar búsqueda")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag("library_search_input")
        )

        Spacer(modifier = Modifier.height(6.dp))

        if (libraryState.songs.isEmpty() && !libraryState.isScanning) {
            EmptyLibraryPlaceholder(
                onScan = { viewModel.scanMusic() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = bottomContentPadding)
            )
        } else {
            when (libraryState.selectedTab) {
                LibraryTab.SONGS -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(top = 6.dp, bottom = bottomContentPadding),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = libraryState.songs,
                            key = { it.id },
                            contentType = { "song" }
                        ) { song ->
                            SongRowItem(
                                song = song,
                                isActive = playerState.currentSong?.id == song.id,
                                isCompact = isCompact,
                                artworkShape = artworkShape,
                                onClick = { viewModel.playSongFromList(song, libraryState.songs) },
                                onToggleFavorite = { viewModel.toggleFavorite(song) },
                                onPlayNext = { viewModel.playNext(song) },
                                onAddToQueue = { viewModel.addToQueue(song) },
                                onAddToPlaylist = { songForPlaylistPicker = song },
                                onEditTags = { onEditTags(song) },
                                onToggleHideSong = { viewModel.setSongHidden(song.id, true) }
                            )
                        }
                    }
                }

                LibraryTab.ALBUMS -> {
                    if (appearance.libraryLayout == LibraryViewLayout.GRID_2 ||
                        appearance.libraryLayout == LibraryViewLayout.GRID_3
                    ) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(gridColumns),
                            contentPadding = PaddingValues(
                                start = 14.dp,
                                end = 14.dp,
                                top = 8.dp,
                                bottom = bottomContentPadding
                            ),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = libraryState.albums,
                                key = { "${it.id}_${it.name}" },
                                contentType = { "album" }
                            ) { album ->
                                AlbumGridCard(
                                    album = album,
                                    artworkShape = artworkShape,
                                    onClick = { selectedAlbum = album }
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(top = 8.dp, bottom = bottomContentPadding),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = libraryState.albums,
                                key = { "${it.id}_${it.name}" },
                                contentType = { "album" }
                            ) { album ->
                                ListItem(
                                    headlineContent = { Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    supportingContent = { Text("${album.artist} • ${album.songCount} pistas", maxLines = 1) },
                                    leadingContent = {
                                        ArtworkImage(
                                            artworkUri = album.artworkUri,
                                            contentDescription = album.name,
                                            targetSizeDp = 54.dp,
                                            shape = artworkShape,
                                            modifier = Modifier.size(54.dp)
                                        )
                                    },
                                    modifier = Modifier.clickable { selectedAlbum = album }
                                )
                            }
                        }
                    }
                }

                LibraryTab.ARTISTS -> {
                    if (appearance.libraryLayout == LibraryViewLayout.GRID_2 ||
                        appearance.libraryLayout == LibraryViewLayout.GRID_3
                    ) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(gridColumns),
                            contentPadding = PaddingValues(
                                start = 14.dp,
                                end = 14.dp,
                                top = 8.dp,
                                bottom = bottomContentPadding
                            ),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = libraryState.artists,
                                key = { it.name },
                                contentType = { "artist" }
                            ) { artist ->
                                ArtistGridCard(
                                    artist = artist,
                                    onClick = { selectedArtist = artist }
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(top = 8.dp, bottom = bottomContentPadding),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = libraryState.artists,
                                key = { it.name },
                                contentType = { "artist" }
                            ) { artist ->
                                ListItem(
                                    headlineContent = { Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    supportingContent = { Text("${artist.albumCount} álbumes • ${artist.songCount} canciones") },
                                    leadingContent = {
                                        ArtworkImage(
                                            artworkUri = artist.artworkUri,
                                            contentDescription = artist.name,
                                            targetSizeDp = 54.dp,
                                            circular = true,
                                            modifier = Modifier.size(54.dp)
                                        )
                                    },
                                    modifier = Modifier.clickable { selectedArtist = artist }
                                )
                            }
                        }
                    }
                }

                LibraryTab.PLAYLISTS -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(top = 8.dp, bottom = bottomContentPadding),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(key = "smart_header", contentType = "header") {
                            Text(
                                text = "Playlists Inteligentes",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        item(key = "smart_carousel", contentType = "smart_carousel") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(SmartPlaylistType.entries, key = { it.name }) { type ->
                                    val count = when (type) {
                                        SmartPlaylistType.MOST_PLAYED -> libraryState.mostPlayedSongs.size
                                        SmartPlaylistType.RECENTLY_ADDED -> libraryState.recentSongs.size
                                        SmartPlaylistType.FAVORITES -> libraryState.favoriteSongs.size
                                        SmartPlaylistType.NEVER_PLAYED -> libraryState.neverPlayedSongs.size
                                    }
                                    val icon = when (type) {
                                        SmartPlaylistType.MOST_PLAYED -> Icons.Filled.Whatshot
                                        SmartPlaylistType.RECENTLY_ADDED -> Icons.Filled.NewReleases
                                        SmartPlaylistType.FAVORITES -> Icons.Filled.Favorite
                                        SmartPlaylistType.NEVER_PLAYED -> Icons.Filled.Explore
                                    }
                                    ElevatedCard(
                                        onClick = { selectedSmartPlaylist = type },
                                        shape = RoundedCornerShape(22.dp),
                                        colors = CardDefaults.elevatedCardColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                                        ),
                                        modifier = Modifier.width(164.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            Spacer(modifier = Modifier.height(14.dp))
                                            Text(
                                                text = type.displayName,
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            Text(
                                                text = "$count canciones",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item(key = "user_playlists_header", contentType = "header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Tus Playlists", style = MaterialTheme.typography.titleMedium)
                                FilledTonalButton(
                                    onClick = { showCreatePlaylistDialog = true },
                                    modifier = Modifier.testTag("create_playlist_button")
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Nueva")
                                }
                            }
                        }

                        if (libraryState.playlists.isEmpty()) {
                            item(key = "empty_playlists", contentType = "empty") {
                                Text(
                                    text = "Aún no creaste playlists personalizadas.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        } else {
                            items(
                                items = libraryState.playlists,
                                key = { "pl_${it.id}" },
                                contentType = { "playlist" }
                            ) { playlist ->
                                ListItem(
                                    headlineContent = { Text(playlist.name) },
                                    supportingContent = { Text("${playlist.songCount} canciones") },
                                    leadingContent = {
                                        ArtworkImage(
                                            artworkUri = playlist.previewArtUri,
                                            contentDescription = playlist.name,
                                            targetSizeDp = 50.dp,
                                            shape = artworkShape,
                                            modifier = Modifier.size(50.dp)
                                        )
                                    },
                                    trailingContent = {
                                        IconButton(onClick = { viewModel.deletePlaylist(playlist) }) {
                                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Eliminar playlist")
                                        }
                                    },
                                    modifier = Modifier.clickable { selectedPlaylist = playlist }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Album Detail Modal Sheet
    selectedAlbum?.let { album ->
        SongCollectionSheet(
            title = album.name,
            subtitle = "${album.artist} • ${album.songCount} pistas",
            songs = album.songs,
            currentSongId = playerState.currentSong?.id,
            artworkShape = artworkShape,
            onDismiss = { selectedAlbum = null },
            onPlaySong = { song -> viewModel.playSongFromList(song, album.songs) },
            onToggleFavorite = viewModel::toggleFavorite,
            onPlayNext = viewModel::playNext,
            onAddToQueue = viewModel::addToQueue,
            onAddToPlaylist = { songForPlaylistPicker = it },
            onEditTags = onEditTags
        )
    }

    // Artist Detail Modal Sheet
    selectedArtist?.let { artist ->
        SongCollectionSheet(
            title = artist.name,
            subtitle = "${artist.albumCount} álbumes • ${artist.songCount} canciones",
            songs = artist.songs,
            currentSongId = playerState.currentSong?.id,
            artworkShape = artworkShape,
            onDismiss = { selectedArtist = null },
            onPlaySong = { song -> viewModel.playSongFromList(song, artist.songs) },
            onToggleFavorite = viewModel::toggleFavorite,
            onPlayNext = viewModel::playNext,
            onAddToQueue = viewModel::addToQueue,
            onAddToPlaylist = { songForPlaylistPicker = it },
            onEditTags = onEditTags
        )
    }

    // Smart Playlist Sheet
    selectedSmartPlaylist?.let { type ->
        val smartSongs = when (type) {
            SmartPlaylistType.MOST_PLAYED -> libraryState.mostPlayedSongs
            SmartPlaylistType.RECENTLY_ADDED -> libraryState.recentSongs
            SmartPlaylistType.FAVORITES -> libraryState.favoriteSongs
            SmartPlaylistType.NEVER_PLAYED -> libraryState.neverPlayedSongs
        }
        SongCollectionSheet(
            title = type.displayName,
            subtitle = "Playlist inteligente • ${smartSongs.size} pistas",
            songs = smartSongs,
            currentSongId = playerState.currentSong?.id,
            artworkShape = artworkShape,
            onDismiss = { selectedSmartPlaylist = null },
            onPlaySong = { song -> viewModel.playSongFromList(song, smartSongs) },
            onToggleFavorite = viewModel::toggleFavorite,
            onPlayNext = viewModel::playNext,
            onAddToQueue = viewModel::addToQueue,
            onAddToPlaylist = { songForPlaylistPicker = it },
            onEditTags = onEditTags
        )
    }

    // Custom Playlist Detail Sheet
    selectedPlaylist?.let { playlist ->
        val plSongs by viewModel.getSongsForPlaylist(playlist.id).collectAsStateWithLifecycle(
            initialValue = kotlinx.collections.immutable.persistentListOf()
        )
        SongCollectionSheet(
            title = playlist.name,
            subtitle = "${plSongs.size} canciones",
            songs = plSongs,
            currentSongId = playerState.currentSong?.id,
            artworkShape = artworkShape,
            onDismiss = { selectedPlaylist = null },
            onPlaySong = { song -> viewModel.playSongFromList(song, plSongs) },
            onToggleFavorite = viewModel::toggleFavorite,
            onPlayNext = viewModel::playNext,
            onAddToQueue = viewModel::addToQueue,
            onAddToPlaylist = { songForPlaylistPicker = it },
            onEditTags = onEditTags
        )
    }

    // Add to Playlist Picker
    songForPlaylistPicker?.let { song ->
        AlertDialog(
            onDismissRequest = { songForPlaylistPicker = null },
            title = { Text("Añadir a playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (libraryState.playlists.isEmpty()) {
                        Text("No hay playlists creadas todavía.")
                    } else {
                        libraryState.playlists.forEach { pl ->
                            ListItem(
                                headlineContent = { Text(pl.name) },
                                supportingContent = { Text("${pl.songCount} pistas") },
                                leadingContent = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
                                modifier = Modifier.clickable {
                                    viewModel.addSongToPlaylist(pl.id, song.id)
                                    songForPlaylistPicker = null
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    songForPlaylistPicker = null
                    showCreatePlaylistDialog = true
                }) { Text("Crear playlist") }
            },
            dismissButton = {
                TextButton(onClick = { songForPlaylistPicker = null }) { Text("Cancelar") }
            }
        )
    }

    if (showCreatePlaylistDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("Nueva playlist") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nombre de la playlist") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.createPlaylist(newName)
                    showCreatePlaylistDialog = false
                }) { Text("Crear") }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun AlbumGridCard(
    album: Album,
    artworkShape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            ArtworkImage(
                artworkUri = album.artworkUri,
                contentDescription = album.name,
                targetSizeDp = 160.dp,
                shape = artworkShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = album.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${album.artist} • ${album.songCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ArtistGridCard(
    artist: Artist,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ArtworkImage(
                artworkUri = artist.artworkUri,
                contentDescription = artist.name,
                targetSizeDp = 140.dp,
                circular = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${artist.songCount} pistas",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyLibraryPlaceholder(
    onScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(84.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.LibraryMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Tu biblioteca está vacía",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Escanea el almacenamiento local para detectar tu música MP3, FLAC, M4A u OGG.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onScan,
                modifier = Modifier.testTag("empty_scan_button")
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Escanear música")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongCollectionSheet(
    title: String,
    subtitle: String,
    songs: ImmutableList<Song>,
    currentSongId: Long?,
    artworkShape: androidx.compose.ui.graphics.Shape,
    onDismiss: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onEditTags: (Song) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (songs.isNotEmpty()) {
                    FilledTonalButton(onClick = { onPlaySong(songs.first()) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reproducir")
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 460.dp)) {
                items(items = songs, key = { it.id }, contentType = { "sheet_song" }) { song ->
                    SongRowItem(
                        song = song,
                        isActive = song.id == currentSongId,
                        isCompact = true,
                        artworkShape = artworkShape,
                        onClick = { onPlaySong(song) },
                        onToggleFavorite = { onToggleFavorite(song) },
                        onPlayNext = { onPlayNext(song) },
                        onAddToQueue = { onAddToQueue(song) },
                        onAddToPlaylist = { onAddToPlaylist(song) },
                        onEditTags = { onEditTags(song) }
                    )
                }
            }
        }
    }
}
