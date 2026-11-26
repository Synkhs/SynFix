package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.example.SynFixApplication
import com.example.data.audit.LibraryAuditor
import com.example.data.backup.SynFixBackupManager
import com.example.data.lyrics.LrcParser
import com.example.data.tags.JunkCleanupCandidate
import com.example.data.tags.JunkTagCleaner
import com.example.data.tags.TagUpdateRequest
import com.example.data.worker.CamelotKeyWorker
import com.example.data.worker.ReplayGainWorker
import com.example.domain.model.*
import com.example.player.*
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

@Immutable
data class LibraryUiState(
    val songs: ImmutableList<Song> = persistentListOf(),
    val albums: ImmutableList<Album> = persistentListOf(),
    val artists: ImmutableList<Artist> = persistentListOf(),
    val playlists: ImmutableList<Playlist> = persistentListOf(),
    val favoriteSongs: ImmutableList<Song> = persistentListOf(),
    val mostPlayedSongs: ImmutableList<Song> = persistentListOf(),
    val recentSongs: ImmutableList<Song> = persistentListOf(),
    val neverPlayedSongs: ImmutableList<Song> = persistentListOf(),
    val searchQuery: String = "",
    val selectedTab: LibraryTab = LibraryTab.SONGS,
    val isScanning: Boolean = false
)

@Immutable
data class PlayerUiState(
    val currentSong: Song? = null,
    val queue: ImmutableList<Song> = persistentListOf(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatModeState = RepeatModeState.OFF,
    val lyrics: ImmutableList<LyricLine> = persistentListOf()
)

@Immutable
data class EqualizerUiState(
    val enabled: Boolean = true,
    val activePresetId: String = "flat",
    val bandsDb: ImmutableList<Float> = persistentListOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
    val preampDb: Float = 0f,
    val bassBoost: Int = 0,
    val virtualizer: Int = 0,
    val builtInPresets: ImmutableList<EqualizerPreset> = BuiltInEqPresets.PRESETS.toImmutableList(),
    val customPresets: ImmutableList<EqualizerPreset> = persistentListOf(),
    val currentOutput: AudioOutputType = AudioOutputType.SPEAKER,
    val autoOutputProfile: Boolean = false,
    val devicePresetMap: ImmutableMap<AudioOutputType, String> = persistentMapOf(),
    val usingDynamicsProcessing: Boolean = false
)

@Immutable
data class ProLabUiState(
    val moodBpm: Int = 115,
    val moodEnergy: Float = 0.55f,
    val moodMatches: ImmutableList<Song> = persistentListOf(),
    val zeroPlayStreak: ImmutableList<Song> = persistentListOf(),
    val throwbackEntries: ImmutableList<ThrowbackEntry> = persistentListOf(),
    val throwbackSlotIndex: Int = 1,
    val auditFindings: ImmutableList<AuditFinding> = persistentListOf(),
    val isAuditing: Boolean = false,
    val hasRunAudit: Boolean = false,
    val isExportingClip: Boolean = false,
    val lastExportedClipPath: String? = null,
    // Phase 7 Ultra additions
    val selectedCamelotKey: String = "8A",
    val harmonicMatches: ImmutableList<Song> = persistentListOf(),
    val contextTimeSlot: ContextTimeSlot = ContextTimeSlot.AFTERNOON_FLOW,
    val contextMixSongs: ImmutableList<Song> = persistentListOf(),
    val aliasGroups: ImmutableList<ArtistAliasGroup> = persistentListOf(),
    val lastM3u8Result: M3u8RelinkResult? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SynFixApplication
    private val repository = app.repository
    private val preferences = app.preferences
    private val tagEditor = app.tagEditor
    private val audioEngine = app.audioEffectsEngine
    private val playerConnection = PlayerConnection(application)

    private val _searchQuery = MutableStateFlow("")
    private val _selectedTab = MutableStateFlow(LibraryTab.SONGS)
    private val _isScanning = MutableStateFlow(false)

    private val _playerState = MutableStateFlow(PlayerUiState())
    val playerState: StateFlow<PlayerUiState> = _playerState.asStateFlow()

    private val _playbackPositionMs = MutableStateFlow(0L)
    val playbackPositionMs: StateFlow<Long> = _playbackPositionMs.asStateFlow()

    private val _albumSeedColor = MutableStateFlow<Color?>(null)
    val albumSeedColor: StateFlow<Color?> = _albumSeedColor.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    val appearanceSettings: StateFlow<AppearanceSettings> = preferences.appearanceSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceSettings())

    val playbackSettings: StateFlow<PlaybackSettings> = preferences.playbackSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlaybackSettings())

    val sleepTimerState: StateFlow<SleepTimerState> = PlaybackRuntimeCoordinator.sleepTimerState
    val abLoopState: StateFlow<AbLoopState> = PlaybackRuntimeCoordinator.abLoopState
    val pipelineTelemetry: StateFlow<AudioPipelineTelemetry> = PlaybackRuntimeCoordinator.pipelineTelemetry
    val drTelemetry: StateFlow<DrClippingTelemetry> = PlaybackRuntimeCoordinator.drTelemetry
    val adaptiveGainDb: StateFlow<Float> = audioEngine.adaptiveGainDb
    val partySyncState: StateFlow<PartySyncState> = app.partySyncEngine.state

    val subBassEnergy: StateFlow<Float> = app.visualizerController.subBassEnergy
    val rmsAmplitude: StateFlow<Float> = app.visualizerController.rmsAmplitude

    // Phase 8 StateFlows
    val ambienceMixState: StateFlow<AmbienceMixState> = preferences.ambienceMixState
        .stateIn(viewModelScope, SharingStarted.Eagerly, AmbienceMixState())
    val pinnedQueueIds: StateFlow<Set<Long>> = preferences.pinnedQueueIds
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val gyroTiltX: StateFlow<Float> = app.sensorController.tiltX
    val gyroTiltY: StateFlow<Float> = app.sensorController.tiltY
    val partyStrobePulse: StateFlow<Float> = app.partyStrobeController.strobePulse
    val ambienceStandaloneRunning: StateFlow<Boolean> = app.ambienceStandaloneEngine.isStandaloneRunning
    val qrServerState: StateFlow<QrServerState> = app.localQrWebServer.state
    val connectedBtDevice: StateFlow<Pair<String, String>?> = app.btProfileManager.connectedDevice

    val trophies: StateFlow<ImmutableList<TrophyItem>> = repository.trophies
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), persistentListOf())
    val bluetoothProfiles: StateFlow<ImmutableList<BluetoothMacProfile>> = repository.bluetoothProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), persistentListOf())

    private val _autoEqProfiles = MutableStateFlow<ImmutableList<AutoEqProfile>>(persistentListOf())
    val autoEqProfiles: StateFlow<ImmutableList<AutoEqProfile>> = _autoEqProfiles.asStateFlow()

    private val _activeVibeFilter = MutableStateFlow<String?>(null)
    val activeVibeFilter: StateFlow<String?> = _activeVibeFilter.asStateFlow()

    private val _versionStackEnabled = MutableStateFlow(false)
    val versionStackEnabled: StateFlow<Boolean> = _versionStackEnabled.asStateFlow()

    private val _junkCleanupCandidates = MutableStateFlow<ImmutableList<JunkCleanupCandidate>>(persistentListOf())
    val junkCleanupCandidates: StateFlow<ImmutableList<JunkCleanupCandidate>> = _junkCleanupCandidates.asStateFlow()

    private val _artistChainPool = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    val artistChainPool: StateFlow<ImmutableList<Song>> = _artistChainPool.asStateFlow()

    private var pocketDjEngine: PocketDjEngine? = null
    private val _pocketDjState = MutableStateFlow(PocketDjState())
    val pocketDjState: StateFlow<PocketDjState> = _pocketDjState.asStateFlow()

    private var blindTestEngine: BlindTestAbEngine? = null
    private val _blindTestState = MutableStateFlow(BlindTestAbState())
    val blindTestState: StateFlow<BlindTestAbState> = _blindTestState.asStateFlow()

    private var isLifecycleResumed = false

    val wrappedStats: StateFlow<WrappedStats> = repository.wrappedStats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WrappedStats())

    val hiddenSongs: StateFlow<ImmutableList<Song>> = repository.hiddenSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), persistentListOf())

    private val _wakeUpAlarmState = MutableStateFlow(WakeUpAlarmState())
    val wakeUpAlarmState: StateFlow<WakeUpAlarmState> = _wakeUpAlarmState.asStateFlow()
    private var pomodoroJob: Job? = null

    val currentSongNotes: StateFlow<ImmutableList<TrackNote>> = _playerState
        .map { it.currentSong?.id }
        .distinctUntilChanged()
        .flatMapLatest { songId ->
            if (songId == null) flowOf(persistentListOf())
            else repository.getTrackNotesForSong(songId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), persistentListOf())

    private val _proLabState = MutableStateFlow(ProLabUiState())
    val proLabState: StateFlow<ProLabUiState> = _proLabState.asStateFlow()

    val scannerSettings: StateFlow<ScannerSettings> = combine(
        preferences.minDurationSeconds,
        preferences.excludedFolders,
        repository.discoveredFolders
    ) { minDur, excluded, discovered ->
        val folderList = discovered.map { (path, count) ->
            FolderInfo(
                path = path,
                displayName = path.substringAfterLast('/').ifBlank { path },
                trackCount = count,
                isExcluded = excluded.any { path.startsWith(it, ignoreCase = true) }
            )
        }.toImmutableList()
        ScannerSettings(
            minDurationSeconds = minDur,
            excludedFolders = excluded.toImmutableSet(),
            knownFolders = folderList
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScannerSettings())

    private val filteredSongsFlow = combine(
        repository.allSongs,
        _searchQuery,
        _activeVibeFilter
    ) { songs, query, vibe ->
        var base = songs
        if (!vibe.isNullOrBlank()) {
            base = base.filter { song ->
                song.vibeTags.split(',').any { it.trim().equals(vibe, ignoreCase = true) }
            }.toImmutableList()
        }
        if (query.isBlank()) base
        else {
            val q = query.trim()
            base.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.artist.contains(q, ignoreCase = true) ||
                    it.album.contains(q, ignoreCase = true)
            }.toImmutableList()
        }
    }.flowOn(Dispatchers.Default)

    private val albumsAndArtistsFlow = repository.allSongs.map { songs ->
        val albums = songs.groupBy { it.albumId to it.album }
            .map { (key, albumSongs) ->
                val sorted = albumSongs.sortedBy { it.trackNumber }.toImmutableList()
                val first = sorted.first()
                Album(
                    id = key.first,
                    name = key.second,
                    artist = first.artist,
                    year = first.year,
                    songCount = sorted.size,
                    artworkUri = first.artworkUri,
                    songs = sorted
                )
            }
            .sortedBy { it.name.lowercase() }
            .toImmutableList()

        val artists = songs.groupBy { it.artist }
            .map { (name, artistSongs) ->
                val sorted = artistSongs.sortedBy { it.title.lowercase() }.toImmutableList()
                Artist(
                    name = name,
                    albumCount = artistSongs.map { it.albumId }.distinct().size,
                    songCount = sorted.size,
                    artworkUri = sorted.firstOrNull()?.artworkUri,
                    songs = sorted
                )
            }
            .sortedBy { it.name.lowercase() }
            .toImmutableList()

        albums to artists
    }.flowOn(Dispatchers.Default)

    val libraryState: StateFlow<LibraryUiState> = combine(
        filteredSongsFlow,
        albumsAndArtistsFlow,
        repository.userPlaylists,
        repository.favoriteSongs,
        repository.mostPlayedSongs,
        repository.recentSongs,
        repository.neverPlayedSongs,
        _searchQuery,
        _selectedTab,
        _isScanning
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val songs = values[0] as ImmutableList<Song>
        @Suppress("UNCHECKED_CAST")
        val albumsAndArtists = values[1] as Pair<ImmutableList<Album>, ImmutableList<Artist>>
        @Suppress("UNCHECKED_CAST")
        val playlists = values[2] as ImmutableList<Playlist>
        @Suppress("UNCHECKED_CAST")
        val favs = values[3] as ImmutableList<Song>
        @Suppress("UNCHECKED_CAST")
        val mostPlayed = values[4] as ImmutableList<Song>
        @Suppress("UNCHECKED_CAST")
        val recent = values[5] as ImmutableList<Song>
        @Suppress("UNCHECKED_CAST")
        val neverPlayed = values[6] as ImmutableList<Song>
        val query = values[7] as String
        val tab = values[8] as LibraryTab
        val scanning = values[9] as Boolean

        val filteredAlbums = if (query.isBlank()) albumsAndArtists.first else albumsAndArtists.first.filter {
            it.name.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
        }.toImmutableList()

        val filteredArtists = if (query.isBlank()) albumsAndArtists.second else albumsAndArtists.second.filter {
            it.name.contains(query, ignoreCase = true)
        }.toImmutableList()

        LibraryUiState(
            songs = songs,
            albums = filteredAlbums,
            artists = filteredArtists,
            playlists = playlists,
            favoriteSongs = favs,
            mostPlayedSongs = mostPlayed,
            recentSongs = recent,
            neverPlayedSongs = neverPlayed,
            searchQuery = query,
            selectedTab = tab,
            isScanning = scanning
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LibraryUiState())

    val equalizerState: StateFlow<EqualizerUiState> = combine(
        preferences.eqState,
        repository.customEqPresets,
        audioEngine.currentOutput,
        audioEngine.usingDynamicsProcessing
    ) { stored, custom, output, usingDp ->
        EqualizerUiState(
            enabled = stored.enabled,
            activePresetId = stored.activePresetId,
            bandsDb = stored.bandsDb.toImmutableList(),
            preampDb = stored.preampDb,
            bassBoost = stored.bassBoost,
            virtualizer = stored.virtualizer,
            builtInPresets = BuiltInEqPresets.PRESETS.toImmutableList(),
            customPresets = custom,
            currentOutput = output,
            autoOutputProfile = stored.autoOutputProfile,
            devicePresetMap = stored.devicePresetMap,
            usingDynamicsProcessing = usingDp
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, EqualizerUiState())

    val trackVersionGroups: StateFlow<ImmutableList<TrackVersionGroup>> = filteredSongsFlow
        .map { JunkTagCleaner.groupByVersionStack(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), persistentListOf())

    val dspBatteryStats: StateFlow<DspBatteryStats> = combine(
        repository.allSongs,
        preferences.offloadDspSeconds
    ) { songs, (offloadSec, dspSec) ->
        repository.buildDspBatteryStats(songs, offloadSec, dspSec)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DspBatteryStats())

    private var songLookupMap: Map<Long, Song> = emptyMap()

    init {
        observeSongsForMetadataUpdates()
        observeMediaController()
        observeEqualizerAndAudioBalance()
        observeOutputDeviceAutoSwitch()
        observeVisualizerAndHaptics()
        observeSensorsAndStrobe()
        observeWakeUpTrigger()
        startPositionTicker()
        refreshZeroPlayStreak()
        loadDefaultThrowback()
        loadDefaultContextTimeMix()
        loadAutoEqProfiles()
    }

    private fun observeSensorsAndStrobe() {
        viewModelScope.launch {
            combine(
                appearanceSettings,
                _playerState.map { it.isPlaying }.distinctUntilChanged()
            ) { appSettings, playing ->
                appSettings to playing
            }.collect { (appSettings, playing) ->
                app.sensorController.configure(
                    gyroParallaxEnabled = appSettings.gyroParallaxEnabled,
                    shakeToSkipEnabled = appSettings.shakeToSkipEnabled,
                    onShake = {
                        skipNext()
                        _snackbarMessage.tryEmit("Shake-to-Skip: Siguiente pista")
                    }
                )
                app.partyStrobeController.updateConfig(
                    enabled = appSettings.partyStrobeEnabled,
                    useTorch = appSettings.partyStrobeTorchEnabled,
                    playing = playing,
                    resumed = isLifecycleResumed
                )
            }
        }
    }

    private fun observeVisualizerAndHaptics() {
        viewModelScope.launch {
            combine(
                _playerState.map { it.isPlaying }.distinctUntilChanged(),
                appearanceSettings.map { it.beatSyncedMorphEnabled }.distinctUntilChanged(),
                appearanceSettings.map { it.subBassHapticsEnabled }.distinctUntilChanged(),
                appearanceSettings.map { it.partyStrobeEnabled }.distinctUntilChanged()
            ) { playing, morphEnabled, hapticsEnabled, strobeEnabled ->
                listOf(playing, morphEnabled, hapticsEnabled, strobeEnabled)
            }.collect { flags ->
                val playing = flags[0]
                val needsVisualizer = flags[1] || flags[2] || flags[3]
                app.visualizerController.updatePlaybackState(playing, needsVisualizer)
                app.subBassHapticEngine.updateState(flags[2], playing)
            }
        }
    }

    private fun observeWakeUpTrigger() {
        viewModelScope.launch {
            SmartWakeUpScheduler.wakeUpTriggered.collect { triggered ->
                if (triggered) {
                    SmartWakeUpScheduler.consumeWakeUpTrigger()
                    val current = _playerState.value.currentSong ?: libraryState.value.songs.firstOrNull()
                    if (current != null) {
                        if (_playerState.value.currentSong == null) {
                            playSongFromList(current, libraryState.value.songs)
                        } else if (!_playerState.value.isPlaying) {
                            togglePlayPause()
                        }
                        _snackbarMessage.tryEmit("Rutina Smart Wake-Up iniciada")
                    }
                }
            }
        }
    }

    private fun observeSongsForMetadataUpdates() {
        viewModelScope.launch {
            repository.allSongs.collect { songs ->
                songLookupMap = songs.associateBy { it.id }
                songs.forEach { song ->
                    PlaybackRuntimeCoordinator.songBpmMap[song.id] = song.bpm
                    PlaybackRuntimeCoordinator.songReplayGainMap[song.id] = song.replayGainDb
                }
                val activeId = _playerState.value.currentSong?.id
                if (activeId != null) {
                    val refreshed = songLookupMap[activeId]
                    if (refreshed != null && refreshed != _playerState.value.currentSong) {
                        _playerState.update { it.copy(currentSong = refreshed) }
                        loadLyricsAndPalette(refreshed)
                    }
                }
            }
        }
    }

    private fun observeMediaController() {
        viewModelScope.launch {
            playerConnection.mediaController.filterNotNull().collect { controller ->
                syncStateFromController(controller)
                controller.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _playerState.update { it.copy(isPlaying = isPlaying) }
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        syncStateFromController(controller)
                        val songId = mediaItem?.mediaId?.toLongOrNull()
                        if (songId != null) {
                            viewModelScope.launch { repository.recordSongPlayed(songId) }
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        syncStateFromController(controller)
                    }

                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                        _playerState.update { it.copy(shuffleEnabled = shuffleModeEnabled) }
                    }

                    override fun onRepeatModeChanged(repeatMode: Int) {
                        _playerState.update { it.copy(repeatMode = repeatMode.toRepeatState()) }
                    }
                })
            }
        }
    }

    private fun observeEqualizerAndAudioBalance() {
        viewModelScope.launch {
            combine(equalizerState, playbackSettings) { eq, playback ->
                eq to playback
            }.collect { (eq, playback) ->
                audioEngine.updateSettings(
                    enabled = eq.enabled,
                    bandsDb = eq.bandsDb,
                    preampDb = eq.preampDb,
                    bassBoostStrength = eq.bassBoost,
                    virtualizerStrength = eq.virtualizer,
                    channelBalance = playback.channelBalance,
                    nightModeDrc = playback.nightModeDrcEnabled,
                    venueReverb = playback.venueReverbPreset,
                    focusFilter = playback.focusFilterBand,
                    slowedReverb = playback.slowedReverbEnabled,
                    adaptiveAmbient = playback.adaptiveAmbientEnabled,
                    earSafeLimiter = playback.earSafeLimiterEnabled
                )
            }
        }
    }

    private fun observeOutputDeviceAutoSwitch() {
        viewModelScope.launch {
            audioEngine.currentOutput.collect { output ->
                val eq = equalizerState.value
                if (eq.autoOutputProfile) {
                    val targetPresetId = eq.devicePresetMap[output] ?: return@collect
                    val allPresets = eq.builtInPresets + eq.customPresets
                    allPresets.find { it.id == targetPresetId }?.let { selectEqPreset(it) }
                }
            }
        }
    }

    private fun startPositionTicker() {
        viewModelScope.launch {
            while (isActive) {
                val controller = playerConnection.mediaController.value
                if (controller != null && controller.isPlaying) {
                    _playbackPositionMs.value = controller.currentPosition.coerceAtLeast(0L)
                    delay(80L)
                } else {
                    delay(250L)
                }
            }
        }
    }

    private fun syncStateFromController(controller: MediaController) {
        val currentId = controller.currentMediaItem?.mediaId?.toLongOrNull()
        val song = currentId?.let { songLookupMap[it] } ?: _playerState.value.currentSong
        val queueIds = (0 until controller.mediaItemCount).mapNotNull { idx ->
            controller.getMediaItemAt(idx).mediaId.toLongOrNull()?.let { songLookupMap[it] }
        }.toImmutableList()

        val previousId = _playerState.value.currentSong?.id
        _playerState.update {
            it.copy(
                currentSong = song,
                queue = queueIds,
                currentIndex = controller.currentMediaItemIndex,
                isPlaying = controller.isPlaying,
                durationMs = controller.duration.takeIf { d -> d > 0 } ?: song?.durationMs ?: 0L,
                shuffleEnabled = controller.shuffleModeEnabled,
                repeatMode = controller.repeatMode.toRepeatState()
            )
        }
        _playbackPositionMs.value = controller.currentPosition.coerceAtLeast(0L)

        if (song != null && song.id != previousId) {
            loadLyricsAndPalette(song)
        }
    }

    private fun loadLyricsAndPalette(song: Song) {
        viewModelScope.launch {
            val lyrics = LrcParser.loadLyricsForSong(song)
            _playerState.update { it.copy(lyrics = lyrics) }
        }
        viewModelScope.launch {
            val seed = PaletteExtractor.extractSeedColor(app, song.artworkUri)
            _albumSeedColor.value = seed
        }
        // #69 Energy Heatmap Wavy Progress: compute & cache 64-float heatmap if missing
        if (song.energyHeatmap.size < 64) {
            viewModelScope.launch {
                repository.ensureSongEnergyAnalyzed(song)
            }
        }
    }

    fun scanMusic() {
        if (_isScanning.value) return
        viewModelScope.launch {
            _isScanning.value = true
            val minDur = preferences.minDurationSeconds.first()
            val excluded = preferences.excludedFolders.first()
            val count = repository.scanMediaStoreIncremental(minDur, excluded)
            _isScanning.value = false
            _snackbarMessage.tryEmit("Biblioteca sincronizada: $count canciones")
        }
    }

    fun playSongFromList(song: Song, list: List<Song>) {
        val controller = playerConnection.mediaController.value ?: return
        val effectiveList = if (list.isEmpty()) listOf(song) else list
        val startIndex = effectiveList.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        val mediaItems = effectiveList.map { it.toMediaItem() }

        controller.setMediaItems(mediaItems, startIndex, 0L)
        controller.prepare()
        controller.play()

        _playerState.update {
            it.copy(
                currentSong = song,
                queue = effectiveList.toImmutableList(),
                currentIndex = startIndex,
                isPlaying = true,
                durationMs = song.durationMs
            )
        }
        loadLyricsAndPalette(song)
        viewModelScope.launch { repository.recordSongPlayed(song.id) }
    }

    fun playNext(song: Song) {
        val controller = playerConnection.mediaController.value ?: return
        if (controller.mediaItemCount == 0) {
            playSongFromList(song, listOf(song))
            return
        }
        val insertIndex = (controller.currentMediaItemIndex + 1).coerceAtMost(controller.mediaItemCount)
        controller.addMediaItem(insertIndex, song.toMediaItem())
        syncStateFromController(controller)
        _snackbarMessage.tryEmit("Siguiente en la cola: ${song.title}")
    }

    fun addToQueue(song: Song) {
        val controller = playerConnection.mediaController.value ?: return
        if (controller.mediaItemCount == 0) {
            playSongFromList(song, listOf(song))
            return
        }
        controller.addMediaItem(song.toMediaItem())
        syncStateFromController(controller)
        _snackbarMessage.tryEmit("Añadida a la cola: ${song.title}")
    }

    fun removeFromQueue(index: Int) {
        val controller = playerConnection.mediaController.value ?: return
        if (index in 0 until controller.mediaItemCount && index != controller.currentMediaItemIndex) {
            controller.removeMediaItem(index)
            syncStateFromController(controller)
        }
    }

    fun jumpToQueueIndex(index: Int) {
        val controller = playerConnection.mediaController.value ?: return
        if (index in 0 until controller.mediaItemCount) {
            controller.seekToDefaultPosition(index)
            controller.play()
        }
    }

    fun togglePlayPause() {
        if (playbackSettings.value.turntableBrakeEnabled) {
            val handler = PlaybackRuntimeCoordinator.onRequestTurntableTogglePlayPause
            if (handler != null) {
                handler.invoke()
                return
            }
        }
        val controller = playerConnection.mediaController.value ?: return
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun skipNext() {
        val controller = playerConnection.mediaController.value ?: return
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
            controller.play()
        }
    }

    fun skipPrevious() {
        val controller = playerConnection.mediaController.value ?: return
        if (controller.currentPosition > 3500L) {
            controller.seekTo(0L)
        } else if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
            controller.play()
        } else {
            controller.seekTo(0L)
        }
    }

    fun seekTo(positionMs: Long) {
        val controller = playerConnection.mediaController.value ?: return
        val clamped = positionMs.coerceIn(0L, _playerState.value.durationMs.coerceAtLeast(1L))
        controller.seekTo(clamped)
        _playbackPositionMs.value = clamped
    }

    fun toggleShuffle() {
        val controller = playerConnection.mediaController.value ?: return
        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
    }

    fun cycleRepeatMode() {
        val controller = playerConnection.mediaController.value ?: return
        controller.repeatMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            repository.updateFavorite(song.id, !song.isFavorite)
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedTab(tab: LibraryTab) {
        _selectedTab.value = tab
    }

    fun getSongsForPlaylist(playlistId: Long): Flow<ImmutableList<Song>> =
        repository.getSongsForPlaylist(playlistId)

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createPlaylist(name.trim())
            _snackbarMessage.tryEmit("Playlist \"$name\" creada")
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist)
        }
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, songId)
            _snackbarMessage.tryEmit("Canción añadida a la playlist")
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, songId)
        }
    }

    fun saveTagChanges(request: TagUpdateRequest) {
        viewModelScope.launch {
            val updated = tagEditor.applyTagUpdates(request)
            repository.updateSong(updated)
            val lyrics = LrcParser.loadLyricsForSong(updated.toDomain())
            if (_playerState.value.currentSong?.id == updated.id) {
                _playerState.update { it.copy(currentSong = updated.toDomain(), lyrics = lyrics) }
                _albumSeedColor.value = PaletteExtractor.extractSeedColor(app, updated.toDomain().artworkUri)
            }
            _snackbarMessage.tryEmit("Etiquetas guardadas correctamente")
        }
    }

    fun saveLyricsForSong(songId: Long, rawLrc: String) {
        viewModelScope.launch {
            repository.updateLyrics(songId, rawLrc.takeIf { it.isNotBlank() })
            if (_playerState.value.currentSong?.id == songId) {
                _playerState.update { it.copy(lyrics = LrcParser.parse(rawLrc)) }
            }
            _snackbarMessage.tryEmit("Letras guardadas")
        }
    }

    fun startSleepTimer(minutes: Int, stopAfterCurrentTrack: Boolean) {
        PlaybackRuntimeCoordinator.startSleepTimer(minutes, stopAfterCurrentTrack)
        val msg = if (stopAfterCurrentTrack) "Se detendrá al terminar esta canción" else "Temporizador: $minutes min"
        _snackbarMessage.tryEmit(msg)
    }

    fun cancelSleepTimer() {
        PlaybackRuntimeCoordinator.cancelSleepTimer()
        _snackbarMessage.tryEmit("Temporizador cancelado")
    }

    fun updateAppearance(transform: (AppearanceSettings) -> AppearanceSettings) {
        viewModelScope.launch { preferences.updateAppearance(transform) }
    }

    fun updatePlaybackSettings(transform: (PlaybackSettings) -> PlaybackSettings) {
        viewModelScope.launch { preferences.updatePlaybackSettings(transform) }
    }

    fun setMinDurationSeconds(seconds: Int) {
        viewModelScope.launch {
            preferences.setMinDurationSeconds(seconds)
            scanMusic()
        }
    }

    fun toggleExcludedFolder(folderPath: String) {
        viewModelScope.launch {
            preferences.toggleExcludedFolder(folderPath)
            scanMusic()
        }
    }

    fun setEqEnabled(enabled: Boolean) {
        val current = equalizerState.value
        viewModelScope.launch {
            preferences.saveEqState(
                enabled = enabled,
                presetId = current.activePresetId,
                bandsDb = current.bandsDb,
                preampDb = current.preampDb,
                bassBoost = current.bassBoost,
                virtualizer = current.virtualizer
            )
        }
    }

    fun updateEqBand(index: Int, gainDb: Float) {
        val current = equalizerState.value
        val updated = current.bandsDb.toMutableList().apply {
            if (index in indices) this[index] = gainDb.coerceIn(-12f, 12f)
        }
        viewModelScope.launch {
            preferences.saveEqState(
                enabled = current.enabled,
                presetId = "custom",
                bandsDb = updated,
                preampDb = current.preampDb,
                bassBoost = current.bassBoost,
                virtualizer = current.virtualizer
            )
        }
    }

    fun updatePreamp(preampDb: Float) {
        val current = equalizerState.value
        viewModelScope.launch {
            preferences.saveEqState(
                enabled = current.enabled,
                presetId = "custom",
                bandsDb = current.bandsDb,
                preampDb = preampDb.coerceIn(-12f, 12f),
                bassBoost = current.bassBoost,
                virtualizer = current.virtualizer
            )
        }
    }

    fun updateBassBoost(strength: Int) {
        val current = equalizerState.value
        viewModelScope.launch {
            preferences.saveEqState(
                enabled = current.enabled,
                presetId = "custom",
                bandsDb = current.bandsDb,
                preampDb = current.preampDb,
                bassBoost = strength.coerceIn(0, 1000),
                virtualizer = current.virtualizer
            )
        }
    }

    fun updateVirtualizer(strength: Int) {
        val current = equalizerState.value
        viewModelScope.launch {
            preferences.saveEqState(
                enabled = current.enabled,
                presetId = "custom",
                bandsDb = current.bandsDb,
                preampDb = current.preampDb,
                bassBoost = current.bassBoost,
                virtualizer = strength.coerceIn(0, 1000)
            )
        }
    }

    fun selectEqPreset(preset: EqualizerPreset) {
        val current = equalizerState.value
        viewModelScope.launch {
            preferences.saveEqState(
                enabled = current.enabled,
                presetId = preset.id,
                bandsDb = preset.bandsDb,
                preampDb = preset.preampDb,
                bassBoost = preset.bassBoost,
                virtualizer = preset.virtualizer
            )
        }
    }

    fun saveCustomEqPreset(name: String) {
        if (name.isBlank()) return
        val current = equalizerState.value
        val preset = EqualizerPreset(
            id = "custom_${System.currentTimeMillis()}",
            name = name.trim(),
            bandsDb = current.bandsDb,
            preampDb = current.preampDb,
            bassBoost = current.bassBoost,
            virtualizer = current.virtualizer,
            isCustom = true
        )
        viewModelScope.launch {
            repository.saveCustomEqPreset(preset)
            selectEqPreset(preset)
            _snackbarMessage.tryEmit("Perfil \"$name\" guardado")
        }
    }

    fun deleteCustomEqPreset(id: String) {
        viewModelScope.launch { repository.deleteCustomEqPreset(id) }
    }

    fun setAutoOutputProfile(enabled: Boolean) {
        viewModelScope.launch { preferences.setAutoOutputProfile(enabled) }
    }

    fun assignPresetToOutput(output: AudioOutputType, presetId: String) {
        viewModelScope.launch { preferences.assignPresetToOutput(output, presetId) }
    }

    // --- Phase 6 Pro Features ---

    fun addTrackNote(timestampMs: Long, text: String) {
        val song = _playerState.value.currentSong ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.addTrackNote(song.id, timestampMs, text)
            _snackbarMessage.tryEmit("Nota guardada en ${com.example.ui.components.formatDuration(timestampMs)}")
        }
    }

    fun deleteTrackNote(noteId: Long) {
        viewModelScope.launch { repository.deleteTrackNote(noteId) }
    }

    fun queryMoodRadar(bpm: Int, energy: Float) {
        _proLabState.update { it.copy(moodBpm = bpm, moodEnergy = energy) }
        viewModelScope.launch {
            val matches = repository.queryMoodAndTempo(bpm, energy)
            _proLabState.update { it.copy(moodMatches = matches) }
        }
    }

    fun refreshZeroPlayStreak() {
        viewModelScope.launch {
            val streak = repository.getZeroPlayStreak()
            _proLabState.update { it.copy(zeroPlayStreak = streak) }
        }
    }

    private fun loadDefaultThrowback() {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val slot = when (hour) {
            in 6..11 -> 0
            in 12..17 -> 1
            in 18..22 -> 2
            else -> 3
        }
        loadThrowback(cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), slot)
    }

    fun loadThrowback(month: Int, dayOfMonth: Int, slotIndex: Int) {
        _proLabState.update { it.copy(throwbackSlotIndex = slotIndex) }
        viewModelScope.launch {
            val entries = repository.getThrowbackForSlot(month, dayOfMonth, slotIndex)
            _proLabState.update { it.copy(throwbackEntries = entries) }
        }
    }

    fun previewSongAt45s(song: Song, pool: List<Song>) {
        val controller = playerConnection.mediaController.value ?: return
        val effectiveList = if (pool.isEmpty()) listOf(song) else pool
        val startIndex = effectiveList.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        val targetPosMs = if (song.durationMs > 52_000L) 45_000L else (song.durationMs / 2L).coerceAtLeast(0L)
        controller.setMediaItems(effectiveList.map { it.toMediaItem() }, startIndex, targetPosMs)
        controller.prepare()
        controller.play()
        _playerState.update {
            it.copy(
                currentSong = song,
                queue = effectiveList.toImmutableList(),
                currentIndex = startIndex,
                isPlaying = true,
                durationMs = song.durationMs
            )
        }
        _playbackPositionMs.value = targetPosMs
        loadLyricsAndPalette(song)
    }

    fun exportSnippetClip(song: Song, startMs: Long, endMs: Long) {
        if (_proLabState.value.isExportingClip) return
        viewModelScope.launch {
            _proLabState.update { it.copy(isExportingClip = true, lastExportedClipPath = null) }
            val result = SnippetCutter.exportClip(app, song, startMs, endMs)
            result.onSuccess { file ->
                _proLabState.update {
                    it.copy(isExportingClip = false, lastExportedClipPath = file.absolutePath)
                }
                _snackbarMessage.tryEmit("Clip exportado: ${file.name}")
            }.onFailure { err ->
                _proLabState.update { it.copy(isExportingClip = false) }
                _snackbarMessage.tryEmit("Error al recortar: ${err.message ?: "formato no soportado"}")
            }
        }
    }

    fun runLibraryAudit() {
        if (_proLabState.value.isAuditing) return
        viewModelScope.launch {
            _proLabState.update { it.copy(isAuditing = true) }
            val songs = libraryState.value.songs
            val findings = LibraryAuditor.auditLibrary(songs, repository)
            _proLabState.update {
                it.copy(isAuditing = false, hasRunAudit = true, auditFindings = findings)
            }
            _snackbarMessage.tryEmit("Auditoría completada: ${findings.size} hallazgos")
        }
    }

    fun scheduleReplayGainScan() {
        ReplayGainWorker.scheduleChargingScan(app)
        _snackbarMessage.tryEmit("Escaneo LUFS ReplayGain programado al cargar")
    }

    // --- Phase 7 Ultra Features (22..42) ---

    // #22 Haptic Vinyl Scratch
    fun onVinylScratchTick() {
        app.subBassHapticEngine.triggerScratchTick()
    }

    fun onVinylScratchScrub(speedFactor: Float?, seekTargetMs: Long?) {
        PlaybackRuntimeCoordinator.setVinylScratchState(speedFactor, seekTargetMs)
        if (seekTargetMs != null) {
            _playbackPositionMs.value = seekTargetMs.coerceIn(0L, _playerState.value.durationMs.coerceAtLeast(1L))
        }
    }

    // #27 A-B Beat Looper (native ExoPlayer PlayerMessage)
    fun setAbLoopPointA() {
        PlaybackRuntimeCoordinator.setPointA(_playbackPositionMs.value)
        _snackbarMessage.tryEmit("Punto A fijado en ${com.example.ui.components.formatDuration(_playbackPositionMs.value)}")
    }

    fun setAbLoopPointB() {
        val pos = _playbackPositionMs.value
        PlaybackRuntimeCoordinator.setPointB(pos)
        _snackbarMessage.tryEmit("Bucle A-B activado")
    }

    fun clearAbLoop() {
        PlaybackRuntimeCoordinator.clearAbLoop()
        _snackbarMessage.tryEmit("Bucle A-B desactivado")
    }

    // #30 Harmonic Key & Camelot Wheel
    fun loadHarmonicMatches(camelotKey: String) {
        _proLabState.update { it.copy(selectedCamelotKey = camelotKey) }
        viewModelScope.launch {
            val matches = repository.getHarmonicMatches(camelotKey)
            _proLabState.update { it.copy(harmonicMatches = matches) }
        }
    }

    fun scheduleCamelotScan() {
        CamelotKeyWorker.scheduleChargingScan(app)
        _snackbarMessage.tryEmit("Análisis armónico Camelot programado al cargar")
    }

    // #37 Context-Aware Time Mixes
    private fun loadDefaultContextTimeMix() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val slot = when (hour) {
            in 6..11 -> ContextTimeSlot.MORNING_FOCUS
            in 12..17 -> ContextTimeSlot.AFTERNOON_FLOW
            in 18..22 -> ContextTimeSlot.EVENING_CHILL
            else -> ContextTimeSlot.LATE_NIGHT
        }
        loadContextTimeMix(slot)
    }

    fun loadContextTimeMix(slot: ContextTimeSlot) {
        _proLabState.update { it.copy(contextTimeSlot = slot) }
        viewModelScope.launch {
            val songs = repository.getContextTimeMix(slot)
            _proLabState.update { it.copy(contextMixSongs = songs) }
        }
    }

    // #35 Local Party Sync
    fun startPartyHost() {
        app.partySyncEngine.startHost(
            titleProvider = { _playerState.value.currentSong?.title ?: "SynFix" },
            positionProvider = { _playbackPositionMs.value },
            isPlayingProvider = { _playerState.value.isPlaying }
        )
        _snackbarMessage.tryEmit("Modo Anfitrión Party Sync iniciado")
    }

    fun startPartyGuest() {
        app.partySyncEngine.startGuest(
            positionProvider = { _playbackPositionMs.value },
            onSeekToMs = { target -> seekTo(target) },
            onSyncPlayState = { hostPlaying ->
                val controller = playerConnection.mediaController.value ?: return@startGuest
                if (hostPlaying && !controller.isPlaying) controller.play()
                else if (!hostPlaying && controller.isPlaying) controller.pause()
            }
        )
        _snackbarMessage.tryEmit("Escuchando señal Party Sync...")
    }

    fun stopPartySync() {
        app.partySyncEngine.stop()
        _snackbarMessage.tryEmit("Party Sync detenido")
    }

    // #36 Smart Wake-Up & Pomodoro Routines
    fun configureWakeUpAlarm(enabled: Boolean, hour: Int, minute: Int) {
        if (enabled) {
            SmartWakeUpScheduler.scheduleWakeUp(app, hour, minute)
            _snackbarMessage.tryEmit(String.format("Alarma Wake-Up fijada a las %02d:%02d", hour, minute))
        } else {
            SmartWakeUpScheduler.cancelWakeUp(app)
            _snackbarMessage.tryEmit("Alarma Wake-Up desactivada")
        }
        _wakeUpAlarmState.update { it.copy(enabled = enabled, hour = hour, minute = minute) }
    }

    fun togglePomodoro() {
        if (_wakeUpAlarmState.value.pomodoroActive) {
            pomodoroJob?.cancel()
            _wakeUpAlarmState.update {
                it.copy(pomodoroActive = false, pomodoroRemainingMs = 25 * 60_000L, pomodoroIsBreak = false)
            }
            _snackbarMessage.tryEmit("Rutina Pomodoro detenida")
            return
        }

        // Start 25 min focus + ambient playback
        if (!_playerState.value.isPlaying) {
            val song = _playerState.value.currentSong ?: libraryState.value.songs.firstOrNull()
            if (song != null && _playerState.value.currentSong == null) {
                playSongFromList(song, libraryState.value.songs)
            } else {
                togglePlayPause()
            }
        }

        _wakeUpAlarmState.update {
            it.copy(pomodoroActive = true, pomodoroIsBreak = false, pomodoroRemainingMs = 25 * 60_000L)
        }
        pomodoroJob?.cancel()
        pomodoroJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                val cur = _wakeUpAlarmState.value
                if (!cur.pomodoroActive) break
                val next = cur.pomodoroRemainingMs - 1000L
                if (next <= 0L) {
                    val nextBreak = !cur.pomodoroIsBreak
                    val duration = if (nextBreak) 5 * 60_000L else 25 * 60_000L
                    _wakeUpAlarmState.update {
                        it.copy(pomodoroIsBreak = nextBreak, pomodoroRemainingMs = duration)
                    }
                    if (nextBreak && _playerState.value.isPlaying) togglePlayPause()
                    else if (!nextBreak && !_playerState.value.isPlaying) togglePlayPause()
                    _snackbarMessage.tryEmit(if (nextBreak) "Pomodoro: Descanso 5 min" else "Pomodoro: Sesión Focus 25 min")
                } else {
                    _wakeUpAlarmState.update { it.copy(pomodoroRemainingMs = next) }
                }
            }
        }
    }

    // #39 Virtual Artist Alias & Batch Cleaner
    fun scanArtistAliases() {
        viewModelScope.launch {
            val groups = repository.findArtistAliasSuggestions()
            _proLabState.update { it.copy(aliasGroups = groups) }
        }
    }

    fun mergeArtistAlias(oldNames: List<String>, canonicalName: String) {
        if (canonicalName.isBlank()) return
        viewModelScope.launch {
            repository.mergeArtistAliasAndCleanTags(oldNames, canonicalName)
            val groups = repository.findArtistAliasSuggestions()
            _proLabState.update { it.copy(aliasGroups = groups) }
            _snackbarMessage.tryEmit("Artistas unificados en \"$canonicalName\"")
        }
    }

    // #40 Biometric Hidden Vault
    fun setSongHidden(songId: Long, hidden: Boolean) {
        viewModelScope.launch {
            repository.setSongHidden(songId, hidden)
            _snackbarMessage.tryEmit(if (hidden) "Pista movida a la bóveda oculta" else "Pista restaurada a la biblioteca")
        }
    }

    // #41 Smart .m3u8 Playlist Relinker
    fun importM3u8Playlist(uri: Uri, playlistName: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val text = M3u8PlaylistManager.readTextFromUri(app, uri) ?: return@withContext null
                val entries = M3u8PlaylistManager.parseM3u8Content(text)
                val allSongs = libraryState.value.songs
                val relinked = M3u8PlaylistManager.relinkEntries(playlistName, entries, allSongs)
                if (relinked.resolvedSongs.isNotEmpty()) {
                    val plId = repository.createPlaylist(playlistName.ifBlank { "Importada .m3u8" })
                    relinked.resolvedSongs.forEach { song ->
                        repository.addSongToPlaylist(plId, song.id)
                    }
                }
                relinked
            }
            if (result == null) {
                _snackbarMessage.tryEmit("No se pudo leer el archivo .m3u8")
            } else {
                _proLabState.update { it.copy(lastM3u8Result = result) }
                _snackbarMessage.tryEmit(
                    "Playlist importada: ${result.resolvedSongs.size}/${result.totalEntries} pistas (${result.fingerprintRelinked} re-vinculadas)"
                )
            }
        }
    }

    fun exportSongsToM3u8(uri: Uri, songs: List<Song>) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val text = M3u8PlaylistManager.buildM3u8Text(songs)
                M3u8PlaylistManager.writeTextToUri(app, uri, text)
            }
            _snackbarMessage.tryEmit(if (ok) "Playlist exportada en .m3u8" else "Error al exportar .m3u8")
        }
    }

    fun emitSnackbar(message: String) {
        _snackbarMessage.tryEmit(message)
    }

    // --- Phase 8 Actions (#43-#85) ---

    fun setLifecycleResumed(resumed: Boolean) {
        isLifecycleResumed = resumed
        app.sensorController.setLifecycleResumed(resumed)
        if (!resumed) {
            app.sharedAuxPlayer.stopAndRelease()
        }
        val appSet = appearanceSettings.value
        app.partyStrobeController.updateConfig(
            enabled = appSet.partyStrobeEnabled,
            useTorch = appSet.partyStrobeTorchEnabled,
            playing = _playerState.value.isPlaying,
            resumed = resumed
        )
    }

    // #43 Elastic Pull-to-Shuffle
    fun elasticShufflePlay() {
        val pool = libraryState.value.songs
        if (pool.isEmpty()) return
        val shuffled = pool.shuffled()
        playSongFromList(shuffled.first(), shuffled)
        _snackbarMessage.tryEmit("Cola aleatoria rápida (${shuffled.size} pistas)")
    }

    // #46 Edge Liquid Volume Pill
    fun setMusicVolumeFraction(fraction: Float) {
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (fraction.coerceIn(0f, 1f) * max).toInt().coerceIn(0, max)
        runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0) }
    }

    fun getMusicVolumeFraction(): Float {
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        return (cur.toFloat() / max.toFloat()).coerceIn(0f, 1f)
    }

    // #48 Pinch-to-Morph Library Grid
    fun morphLibraryLayoutByPinch(zoomIn: Boolean) {
        val cycle = listOf(
            LibraryViewLayout.LIST_COMPACT,
            LibraryViewLayout.GRID_2,
            LibraryViewLayout.GRID_3,
            LibraryViewLayout.GRID_4
        )
        val current = appearanceSettings.value.libraryLayout
        val idx = cycle.indexOf(current).let { if (it < 0) 0 else it }
        // Pinch out (zoomIn=true) -> fewer bigger items or pinch in -> denser grid (4 col)
        val nextIdx = if (zoomIn) (idx - 1).coerceAtLeast(0) else (idx + 1).coerceAtMost(cycle.lastIndex)
        val target = cycle[nextIdx]
        if (target != current) {
            updateAppearance { it.copy(libraryLayout = target) }
        }
    }

    // #51 Hold-to-Peek Scrubber
    fun startHoldToPeek(song: Song, fraction: Float) {
        app.sharedAuxPlayer.startPeek(song, fraction)
    }

    fun scrubHoldToPeek(song: Song, fraction: Float) {
        app.sharedAuxPlayer.scrubPeek(song, fraction)
    }

    fun stopHoldToPeek() {
        app.sharedAuxPlayer.stopAndRelease()
    }

    // #52 Focus & Sleep Ambience Mixer
    fun updateAmbienceMix(state: AmbienceMixState) {
        viewModelScope.launch { preferences.updateAmbienceMix(state) }
    }

    // #54 AutoEQ Headphone Calibration
    fun loadAutoEqProfiles() {
        viewModelScope.launch {
            _autoEqProfiles.value = app.autoEqRepository.getProfiles()
        }
    }

    fun applyAutoEqProfile(profile: AutoEqProfile) {
        val current = equalizerState.value
        viewModelScope.launch {
            preferences.saveEqState(
                enabled = true,
                presetId = profile.id,
                bandsDb = profile.bandsDb,
                preampDb = profile.preampDb,
                bassBoost = current.bassBoost,
                virtualizer = current.virtualizer
            )
            _snackbarMessage.tryEmit("AutoEQ aplicado: ${profile.model}")
        }
    }

    // #71 Custom Vibe Chips
    fun setVibeFilter(vibe: String?) {
        _activeVibeFilter.value = if (_activeVibeFilter.value == vibe) null else vibe
    }

    fun toggleSongVibeTag(song: Song, vibe: String) {
        val current = song.vibeTags.split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableList()
        val existingIdx = current.indexOfFirst { it.equals(vibe, ignoreCase = true) }
        if (existingIdx >= 0) current.removeAt(existingIdx) else current.add(vibe.trim())
        viewModelScope.launch {
            repository.updateSongVibeTags(song.id, current.joinToString(","))
            repository.evaluateAndUnlockTrophies()
        }
    }

    fun addCustomVibeChip(chip: String) {
        val clean = chip.trim().replace(",", "")
        if (clean.isBlank()) return
        updateAppearance { current ->
            val list = current.customVibeChipsCsv.split(',').map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
            if (list.none { it.equals(clean, ignoreCase = true) }) list.add(clean)
            current.copy(customVibeChipsCsv = list.joinToString(","))
        }
    }

    // #72 Track Version Stack
    fun setVersionStackEnabled(enabled: Boolean) {
        _versionStackEnabled.value = enabled
    }

    // #73 Pinned Queue Anchors
    fun togglePinQueueSong(songId: Long) {
        val current = pinnedQueueIds.value.toMutableSet()
        val pinned = if (current.contains(songId)) {
            current.remove(songId)
            false
        } else {
            current.add(songId)
            true
        }
        viewModelScope.launch { preferences.setPinnedQueueIds(current) }
        _snackbarMessage.tryEmit(if (pinned) "Pista fijada en su posición" else "Anclaje quitado")
    }

    fun smartShuffleQueuePreservingPins() {
        val controller = playerConnection.mediaController.value ?: return
        val queue = _playerState.value.queue
        if (queue.size <= 2) return
        val pinned = pinnedQueueIds.value
        val currentSongId = _playerState.value.currentSong?.id

        val fixedIndices = HashMap<Int, Song>()
        val movable = ArrayList<Song>()
        queue.forEachIndexed { idx, s ->
            if (s.id in pinned || s.id == currentSongId) {
                fixedIndices[idx] = s
            } else {
                movable.add(s)
            }
        }
        movable.shuffle()
        var mCursor = 0
        val rebuilt = ArrayList<Song>(queue.size)
        for (i in queue.indices) {
            val fixed = fixedIndices[i]
            if (fixed != null) rebuilt.add(fixed) else rebuilt.add(movable[mCursor++])
        }
        val pos = controller.currentPosition
        val newCurrentIdx = rebuilt.indexOfFirst { it.id == currentSongId }.coerceAtLeast(0)
        controller.setMediaItems(rebuilt.map { it.toMediaItem() }, newCurrentIdx, pos)
        controller.prepare()
        if (_playerState.value.isPlaying) controller.play()
        syncStateFromController(controller)
        _snackbarMessage.tryEmit("Cola mezclada respetando ${pinned.size} ancla(s)")
    }

    // #83 Track-Count Sleep Timer
    fun startTrackSleepTimer(trackCount: Int) {
        PlaybackRuntimeCoordinator.startTrackSleepTimer(trackCount)
        _snackbarMessage.tryEmit("Se detendrá tras $trackCount pista(s)")
    }

    // #62 & #65 Energy / Beat Peaks helper
    suspend fun ensureSongEnergyAnalyzed(song: Song): Song = repository.ensureSongEnergyAnalyzed(song)

    // #66 Artist Link Chain
    fun loadArtistChainCandidates(currentSong: Song, excludeIds: Set<Long>) {
        viewModelScope.launch {
            _artistChainPool.value = repository.findArtistChainCandidates(currentSong, excludeIds)
        }
    }

    // #68 Trophy Cabinet
    fun refreshTrophies() {
        viewModelScope.launch { repository.evaluateAndUnlockTrophies() }
    }

    // #76 Junk Tag & URL Stripper
    fun scanJunkTagCandidates() {
        viewModelScope.launch(Dispatchers.Default) {
            _junkCleanupCandidates.value = JunkTagCleaner.findCleanupCandidates(libraryState.value.songs)
        }
    }

    fun applyBulkJunkTagCleanup() {
        val candidates = _junkCleanupCandidates.value
        if (candidates.isEmpty()) return
        viewModelScope.launch {
            val cleaned = repository.bulkCleanJunkTags(candidates)
            _junkCleanupCandidates.value = persistentListOf()
            _snackbarMessage.tryEmit("Limpieza atómica completada: $cleaned pistas")
        }
    }

    // #82 Per-MAC Bluetooth Auto-Profile
    fun saveBluetoothProfile(profile: BluetoothMacProfile) {
        viewModelScope.launch {
            repository.saveBluetoothProfile(profile)
            app.btProfileManager.applyProfileNow(profile)
            _snackbarMessage.tryEmit("Perfil BT guardado para ${profile.deviceName}")
        }
    }

    fun deleteBluetoothProfile(mac: String) {
        viewModelScope.launch { repository.deleteBluetoothProfile(mac) }
    }

    // #80 Local QR Web Server
    fun startQrWebServer() {
        app.localQrWebServer.start(
            songsProvider = { libraryState.value.songs },
            onFileUploaded = { scanMusic() }
        )
    }

    fun stopQrWebServer() {
        app.localQrWebServer.stop()
    }

    // #81 Backup .synfix Export / Import
    fun exportSynFixBackup(uri: Uri) {
        viewModelScope.launch {
            val snapshot = repository.buildBackupSnapshot(appearanceSettings.value.customVibeChipsCsv)
            val result = SynFixBackupManager.exportToUri(app, uri, snapshot)
            result.onSuccess { bytes ->
                _snackbarMessage.tryEmit("Backup .synfix exportado (${bytes / 1024} KB)")
            }.onFailure {
                _snackbarMessage.tryEmit("Error al exportar backup .synfix")
            }
        }
    }

    fun importSynFixBackup(uri: Uri) {
        viewModelScope.launch {
            val result = SynFixBackupManager.importFromUri(app, uri)
            result.onSuccess { snapshot ->
                val count = repository.restoreBackupSnapshot(snapshot)
                if (snapshot.customVibeChipsCsv.isNotBlank()) {
                    updateAppearance { it.copy(customVibeChipsCsv = snapshot.customVibeChipsCsv) }
                }
                _snackbarMessage.tryEmit("Backup restaurado ($count elementos)")
            }.onFailure {
                _snackbarMessage.tryEmit("Archivo .synfix inválido o corrupto")
            }
        }
    }

    // #44 & #55 Pocket DJ & Acapella Mashup Lab (on-demand lifecycle)
    private fun ensurePocketDj(): PocketDjEngine {
        return pocketDjEngine ?: PocketDjEngine(app).also { engine ->
            pocketDjEngine = engine
            viewModelScope.launch {
                engine.state.collect { _pocketDjState.value = it }
            }
        }
    }

    fun pocketDjLoadA(song: Song) = ensurePocketDj().loadDeckA(song)
    fun pocketDjLoadB(song: Song) = ensurePocketDj().loadDeckB(song)
    fun pocketDjToggleA() = ensurePocketDj().toggleDeckA()
    fun pocketDjToggleB() = ensurePocketDj().toggleDeckB()
    fun pocketDjSeekA(fraction: Float) = ensurePocketDj().seekDeckA(fraction)
    fun pocketDjSeekB(fraction: Float) = ensurePocketDj().seekDeckB(fraction)
    fun pocketDjCrossfade(value: Float) = ensurePocketDj().setCrossfader(value)
    fun pocketDjMashup(enabled: Boolean) = ensurePocketDj().setAcapellaMashup(enabled)
    fun pocketDjSyncBpm(enabled: Boolean) = ensurePocketDj().setSyncBpm(enabled)
    fun releasePocketDj() {
        pocketDjEngine?.releaseAll()
        pocketDjEngine = null
        _pocketDjState.value = PocketDjState()
    }

    // #77 Blind Test A/B (on-demand lifecycle)
    private fun ensureBlindTest(): BlindTestAbEngine {
        return blindTestEngine ?: BlindTestAbEngine(app).also { engine ->
            blindTestEngine = engine
            viewModelScope.launch {
                engine.state.collect { _blindTestState.value = it }
            }
        }
    }

    fun startBlindTest(first: Song, second: Song) = ensureBlindTest().startTest(first, second)
    fun switchBlindTestSlot(slot: String) = ensureBlindTest().switchTo(slot)
    fun toggleBlindTestPlayPause() = ensureBlindTest().togglePlayPause()
    fun submitBlindTestGuess(slot: String) = ensureBlindTest().submitGuess(slot)
    fun releaseBlindTest() {
        blindTestEngine?.releaseAll()
        blindTestEngine = null
        _blindTestState.value = BlindTestAbState()
    }

    override fun onCleared() {
        pomodoroJob?.cancel()
        releasePocketDj()
        releaseBlindTest()
        app.sharedAuxPlayer.stopAndRelease()
        app.localQrWebServer.stop()
        playerConnection.release()
        super.onCleared()
    }

    private fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
        .setUri(contentUri)
        .setMediaId(id.toString())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUri)
                .build()
        )
        .build()

    private fun Int.toRepeatState(): RepeatModeState = when (this) {
        Player.REPEAT_MODE_ALL -> RepeatModeState.ALL
        Player.REPEAT_MODE_ONE -> RepeatModeState.ONE
        else -> RepeatModeState.OFF
    }
}
