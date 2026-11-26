package com.example.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.*
import com.example.ui.components.ArtworkImage
import com.example.ui.components.HoldToPeekOverlayBar
import com.example.ui.components.MosaicPlaylistCover
import com.example.ui.components.PhysicsTagBubbleCloudSheet
import com.example.ui.components.rememberArtworkShape
import com.example.ui.theme.ExpressiveTypography
import com.example.ui.viewmodel.MusicViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    viewModel: MusicViewModel,
    hasMiniPlayer: Boolean,
    onEditTags: (Song) -> Unit,
    onOpenNowPlaying: () -> Unit,
    modifier: Modifier = Modifier
) {
    val libraryState by viewModel.libraryState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val appearance by viewModel.appearanceSettings.collectAsStateWithLifecycle()
    val activeVibeFilter by viewModel.activeVibeFilter.collectAsStateWithLifecycle()
    val versionStackEnabled by viewModel.versionStackEnabled.collectAsStateWithLifecycle()
    val trackVersionGroups by viewModel.trackVersionGroups.collectAsStateWithLifecycle()

    var selectedAlbum by remember { mutableStateOf<Album?>(null) }
    var selectedArtist by remember { mutableStateOf<Artist?>(null) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var selectedSmartPlaylist by remember { mutableStateOf<SmartPlaylistType?>(null) }
    var songForPlaylistPicker by remember { mutableStateOf<Song?>(null) }
    var songForVibePicker by remember { mutableStateOf<Song?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showAddVibeChipDialog by remember { mutableStateOf(false) }
    var showTagBubbleCloud by remember { mutableStateOf(false) }

    // #51 Hold-to-Peek overlay state
    var peekingSong by remember { mutableStateOf<Song?>(null) }
    var peekFraction by remember { mutableFloatStateOf(0f) }

    // #43 Elastic Pull-to-Shuffle overscroll state
    var pullShufflePx by remember { mutableFloatStateOf(0f) }
    var pullThresholdCrossed by remember { mutableStateOf(false) }
    val expandedVersionKeys = remember { mutableStateMapOf<String, Boolean>() }

    val haptics = LocalHapticFeedback.current
    val artworkShape = rememberArtworkShape(appearance.artworkShape)
    val isCompact = appearance.libraryLayout == LibraryViewLayout.LIST_COMPACT
    val isGridLayout = appearance.libraryLayout == LibraryViewLayout.GRID_2 ||
        appearance.libraryLayout == LibraryViewLayout.GRID_3 ||
        appearance.libraryLayout == LibraryViewLayout.GRID_4
    val gridColumns = when (appearance.libraryLayout) {
        LibraryViewLayout.GRID_3 -> 3
        LibraryViewLayout.GRID_4 -> 4
        else -> 2
    }
    val bottomContentPadding = if (hasMiniPlayer) 168.dp else 96.dp
    val vibeChips = remember(appearance.customVibeChipsCsv) {
        appearance.customVibeChipsCsv.split(',').map { it.trim() }.filter { it.isNotBlank() }
    }

    val listState = rememberLazyListState()

    val elasticNestedScroll = remember(libraryState.songs.size) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (pullShufflePx > 0f && available.y < 0f) {
                    val consumedY = available.y.coerceAtLeast(-pullShufflePx)
                    pullShufflePx = (pullShufflePx + consumedY).coerceAtLeast(0f)
                    if (pullShufflePx < 105f) pullThresholdCrossed = false
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.UserInput &&
                    available.y > 0f &&
                    listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0 &&
                    libraryState.songs.isNotEmpty()
                ) {
                    pullShufflePx = (pullShufflePx + available.y * 0.45f).coerceIn(0f, 150f)
                    if (pullShufflePx >= 105f && !pullThresholdCrossed) {
                        pullThresholdCrossed = true
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullShufflePx >= 105f && libraryState.songs.isNotEmpty()) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.elasticShufflePlay()
                    onOpenNowPlaying()
                }
                pullShufflePx = 0f
                pullThresholdCrossed = false
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                pullShufflePx = 0f
                pullThresholdCrossed = false
                return Velocity.Zero
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Expressive Search Field + Tag Bubble Cloud + Quick Shuffle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                    .weight(1f)
                    .testTag("library_search_input")
            )

            // #50 Physics Tag Bubble Cloud trigger
            FilledTonalIconButton(
                onClick = { showTagBubbleCloud = true },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("open_tag_bubble_cloud")
            ) {
                Icon(Icons.Filled.BubbleChart, contentDescription = "Nube de Géneros y Vibes")
            }

            if (libraryState.songs.isNotEmpty()) {
                FilledTonalIconButton(
                    onClick = {
                        viewModel.elasticShufflePlay()
                        onOpenNowPlaying()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("shuffle_all_button")
                ) {
                    Icon(Icons.Filled.Shuffle, contentDescription = "Aleatorio")
                }
            }
        }

        // #71 Custom Vibe Chips + #72 Track Version Stack toggle
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            items(vibeChips, key = { "vibe_$it" }) { vibe ->
                val selected = activeVibeFilter.equals(vibe, ignoreCase = true)
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.setVibeFilter(vibe) },
                    label = { Text("#$vibe") }
                )
            }
            item(key = "add_vibe_chip") {
                AssistChip(
                    onClick = { showAddVibeChipDialog = true },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Vibe") }
                )
            }
            if (libraryState.selectedTab == LibraryTab.SONGS) {
                item(key = "version_stack_toggle") {
                    FilterChip(
                        selected = versionStackEnabled,
                        onClick = { viewModel.setVersionStackEnabled(!versionStackEnabled) },
                        leadingIcon = { Icon(Icons.Filled.Layers, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Versiones") }
                    )
                }
            }
        }

        if (libraryState.songs.isEmpty() && !libraryState.isScanning) {
            EmptyLibraryPlaceholder(
                onScan = { viewModel.scanMusic() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = bottomContentPadding)
            )
        } else {
            LookaheadScope {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(elasticNestedScroll)
                        // #48 Pinch-to-Morph Library Grid (only triggers with 2+ fingers)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                var zoomAccum = 1f
                                var morphed = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressedCount = event.changes.count { it.pressed }
                                    if (pressedCount == 0) break
                                    if (pressedCount >= 2 && !morphed) {
                                        zoomAccum *= event.calculateZoom()
                                        event.changes.forEach { it.consume() }
                                        if (zoomAccum > 1.28f) {
                                            viewModel.morphLibraryLayoutByPinch(zoomIn = true)
                                            morphed = true
                                        } else if (zoomAccum < 0.76f) {
                                            viewModel.morphLibraryLayoutByPinch(zoomIn = false)
                                            morphed = true
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationY = pullShufflePx * 0.35f
                            }
                    ) {
                        when (libraryState.selectedTab) {
                            LibraryTab.SONGS -> {
                                if (isGridLayout) {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(gridColumns),
                                        contentPadding = PaddingValues(
                                            start = 14.dp,
                                            end = 14.dp,
                                            top = 8.dp,
                                            bottom = bottomContentPadding
                                        ),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(
                                            items = libraryState.songs,
                                            key = { it.id },
                                            contentType = { "song_grid" }
                                        ) { song ->
                                            SongGridCard(
                                                song = song,
                                                isActive = playerState.currentSong?.id == song.id,
                                                artworkShape = artworkShape,
                                                onClick = { viewModel.playSongFromList(song, libraryState.songs) }
                                            )
                                        }
                                    }
                                } else if (versionStackEnabled) {
                                    // #72 Track Version Stack grouped cards
                                    LazyColumn(
                                        state = listState,
                                        contentPadding = PaddingValues(top = 6.dp, bottom = bottomContentPadding),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(
                                            items = trackVersionGroups,
                                            key = { "grp_${it.baseTitle}_${it.primarySong.id}" },
                                            contentType = { "version_group" }
                                        ) { group ->
                                            if (group.versions.size <= 1) {
                                                val song = group.primarySong
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
                                                    onToggleHideSong = { viewModel.setSongHidden(song.id, true) },
                                                    onHoldToPeekStart = { frac ->
                                                        peekingSong = song
                                                        peekFraction = frac
                                                        viewModel.startHoldToPeek(song, frac)
                                                    },
                                                    onHoldToPeekScrub = { frac ->
                                                        peekFraction = frac
                                                        viewModel.scrubHoldToPeek(song, frac)
                                                    },
                                                    onHoldToPeekEnd = {
                                                        peekingSong = null
                                                        viewModel.stopHoldToPeek()
                                                    },
                                                    onQuickAssignVibe = { songForVibePicker = song }
                                                )
                                            } else {
                                                val expanded = expandedVersionKeys[group.baseTitle] == true
                                                Column(modifier = Modifier.fillMaxWidth()) {
                                                    Surface(
                                                        shape = RoundedCornerShape(18.dp),
                                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 12.dp, vertical = 4.dp)
                                                            .clickable {
                                                                expandedVersionKeys[group.baseTitle] = !expanded
                                                            }
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                        ) {
                                                            ArtworkImage(
                                                                artworkUri = group.primarySong.artworkUri,
                                                                contentDescription = group.baseTitle,
                                                                targetSizeDp = 46.dp,
                                                                shape = artworkShape,
                                                                modifier = Modifier.size(46.dp)
                                                            )
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(group.baseTitle, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                                Text(
                                                                    "${group.primarySong.artist} • ${group.versions.size} versiones",
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                                )
                                                            }
                                                            Icon(
                                                                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                                                contentDescription = "Expandir versiones"
                                                            )
                                                        }
                                                    }
                                                    if (expanded) {
                                                        group.versions.forEach { ver ->
                                                            SongRowItem(
                                                                song = ver,
                                                                isActive = playerState.currentSong?.id == ver.id,
                                                                isCompact = true,
                                                                artworkShape = artworkShape,
                                                                onClick = { viewModel.playSongFromList(ver, group.versions) },
                                                                onToggleFavorite = { viewModel.toggleFavorite(ver) },
                                                                onPlayNext = { viewModel.playNext(ver) },
                                                                onAddToQueue = { viewModel.addToQueue(ver) },
                                                                onAddToPlaylist = { songForPlaylistPicker = ver },
                                                                onEditTags = { onEditTags(ver) },
                                                                onToggleHideSong = { viewModel.setSongHidden(ver.id, true) },
                                                                onQuickAssignVibe = { songForVibePicker = ver },
                                                                modifier = Modifier.padding(start = 16.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
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
                                                onToggleHideSong = { viewModel.setSongHidden(song.id, true) },
                                                onHoldToPeekStart = { frac ->
                                                    peekingSong = song
                                                    peekFraction = frac
                                                    viewModel.startHoldToPeek(song, frac)
                                                },
                                                onHoldToPeekScrub = { frac ->
                                                    peekFraction = frac
                                                    viewModel.scrubHoldToPeek(song, frac)
                                                },
                                                onHoldToPeekEnd = {
                                                    peekingSong = null
                                                    viewModel.stopHoldToPeek()
                                                },
                                                onQuickAssignVibe = { songForVibePicker = song }
                                            )
                                        }
                                    }
                                }
                            }

                            LibraryTab.ALBUMS -> {
                                if (isGridLayout) {
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
                                if (isGridLayout) {
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
                                            // #75 Mosaic Playlist Cover (2x2 zero-disk grid)
                                            val plSongs by viewModel.getSongsForPlaylist(playlist.id)
                                                .collectAsStateWithLifecycle(initialValue = persistentListOf())
                                            ListItem(
                                                headlineContent = { Text(playlist.name) },
                                                supportingContent = { Text("${playlist.songCount} canciones") },
                                                leadingContent = {
                                                    MosaicPlaylistCover(
                                                        songs = plSongs,
                                                        fallbackUri = playlist.previewArtUri,
                                                        contentDescription = playlist.name,
                                                        modifier = Modifier.size(52.dp)
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

                    // #43 Elastic Pull-to-Shuffle indicator pill (draw-phase graphicsLayer)
                    if (pullShufflePx > 6f) {
                        val ready = pullShufflePx >= 105f
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                            tonalElevation = 6.dp,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .graphicsLayer {
                                    alpha = (pullShufflePx / 50f).coerceIn(0f, 1f)
                                    translationY = (pullShufflePx * 0.45f - 18.dp.toPx()).coerceAtLeast(4.dp.toPx())
                                    val s = 0.88f + (pullShufflePx / 150f) * 0.16f
                                    scaleX = s
                                    scaleY = s
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Shuffle,
                                    contentDescription = null,
                                    tint = if (ready) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (ready) "¡Suelta para Shuffle!" else "Tira para Shuffle elástico",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (ready) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    // #51 Hold-to-Peek Scrubber overlay pill
                    peekingSong?.let { peek ->
                        HoldToPeekOverlayBar(
                            song = peek,
                            fraction = peekFraction,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 12.dp)
                        )
                    }
                }
            }
        }
    }

    // #50 Physics Tag Bubble Cloud Sheet
    if (showTagBubbleCloud) {
        PhysicsTagBubbleCloudSheet(
            songs = libraryState.songs,
            onSelectTag = { tag ->
                val isVibe = vibeChips.any { it.equals(tag, ignoreCase = true) } ||
                    libraryState.songs.any { it.vibeTags.contains(tag, ignoreCase = true) }
                if (isVibe) viewModel.setVibeFilter(tag) else viewModel.setSearchQuery(tag)
            },
            onDismiss = { showTagBubbleCloud = false }
        )
    }

    // #71 Assign Vibe Tags to Song dialog
    songForVibePicker?.let { song ->
        val currentSongTags = song.vibeTags.split(',').map { it.trim() }.filter { it.isNotBlank() }
        AlertDialog(
            onDismissRequest = { songForVibePicker = null },
            title = { Text("Etiquetas Vibe") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(song.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        vibeChips.forEach { vibe ->
                            val hasVibe = currentSongTags.any { it.equals(vibe, ignoreCase = true) }
                            FilterChip(
                                selected = hasVibe,
                                onClick = {
                                    viewModel.toggleSongVibeTag(song, vibe)
                                    songForVibePicker = null
                                },
                                label = { Text("#$vibe") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { songForVibePicker = null }) { Text("Listo") }
            }
        )
    }

    // Add Custom Vibe Chip dialog
    if (showAddVibeChipDialog) {
        var newVibe by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddVibeChipDialog = false },
            title = { Text("Nuevo Chip Vibe") },
            text = {
                OutlinedTextField(
                    value = newVibe,
                    onValueChange = { newVibe = it },
                    label = { Text("Ej. Cardio, Noche, Estudio") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.addCustomVibeChip(newVibe)
                    showAddVibeChipDialog = false
                }) { Text("Añadir") }
            },
            dismissButton = {
                TextButton(onClick = { showAddVibeChipDialog = false }) { Text("Cancelar") }
            }
        )
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
            initialValue = persistentListOf()
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
private fun SongGridCard(
    song: Song,
    isActive: Boolean,
    artworkShape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            ArtworkImage(
                artworkUri = song.artworkUri,
                contentDescription = song.album,
                targetSizeDp = 140.dp,
                shape = artworkShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = song.title,
                style = MaterialTheme.typography.labelLarge,
                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
