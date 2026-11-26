package com.example.ui.screens.pro

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.Song
import com.example.ui.components.ArtworkImage
import com.example.ui.theme.ExpressiveTypography
import com.example.ui.viewmodel.MusicViewModel
import java.util.Calendar

/**
 * SynFix Lab Pro & Ultra Hub:
 * Centralizes Phase 6 & Phase 7 discovery, physics games, harmonic DJ mixing,
 * Party Sync, Wake-Up/Pomodoro, LRC syncer, Biometric Vault, and .m3u8 relinking.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProLabScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit
) {
    BackHandler(onBack = onBack)

    val libraryState by viewModel.libraryState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val positionMs by viewModel.playbackPositionMs.collectAsStateWithLifecycle()
    val proState by viewModel.proLabState.collectAsStateWithLifecycle()
    val wrappedStats by viewModel.wrappedStats.collectAsStateWithLifecycle()
    val hiddenSongs by viewModel.hiddenSongs.collectAsStateWithLifecycle()
    val partySyncState by viewModel.partySyncState.collectAsStateWithLifecycle()
    val wakeUpState by viewModel.wakeUpAlarmState.collectAsStateWithLifecycle()
    val pocketDjState by viewModel.pocketDjState.collectAsStateWithLifecycle()
    val blindTestState by viewModel.blindTestState.collectAsStateWithLifecycle()
    val artistChainPool by viewModel.artistChainPool.collectAsStateWithLifecycle()
    val trophies by viewModel.trophies.collectAsStateWithLifecycle()
    val junkCandidates by viewModel.junkCleanupCandidates.collectAsStateWithLifecycle()
    val qrServerState by viewModel.qrServerState.collectAsStateWithLifecycle()
    val dspBatteryStats by viewModel.dspBatteryStats.collectAsStateWithLifecycle()

    var showMoodRadar by remember { mutableStateOf(false) }
    var showPreviewSwiper by remember { mutableStateOf(false) }
    var showSnippetCutter by remember { mutableStateOf(false) }
    var showAuditor by remember { mutableStateOf(false) }
    var showSpinWheel by remember { mutableStateOf(false) }
    var showCamelotSheet by remember { mutableStateOf(false) }
    var showWrapped by remember { mutableStateOf(false) }
    var showIntroQuiz by remember { mutableStateOf(false) }
    var showPartySync by remember { mutableStateOf(false) }
    var showWakeUpPomodoro by remember { mutableStateOf(false) }
    var showTapToSyncLrc by remember { mutableStateOf(false) }
    var showAliasCleaner by remember { mutableStateOf(false) }
    var showHiddenVault by remember { mutableStateOf(false) }
    var showM3u8Relinker by remember { mutableStateOf(false) }
    // Phase 8 Dialog States
    var showPocketDj by remember { mutableStateOf(false) }
    var showLyricGap by remember { mutableStateOf(false) }
    var showTapBeatArcade by remember { mutableStateOf(false) }
    var showTrackBracket by remember { mutableStateOf(false) }
    var showMusicBingo by remember { mutableStateOf(false) }
    var showDropReflex by remember { mutableStateOf(false) }
    var showArtistChain by remember { mutableStateOf(false) }
    var showChaosDice by remember { mutableStateOf(false) }
    var showTrophyCabinet by remember { mutableStateOf(false) }
    var showTapTempo by remember { mutableStateOf(false) }
    var showJunkStripper by remember { mutableStateOf(false) }
    var showBlindTest by remember { mutableStateOf(false) }
    var showQrServer by remember { mutableStateOf(false) }
    var showBackupAndStats by remember { mutableStateOf(false) }

    val activeSong: Song? = playerState.currentSong ?: libraryState.songs.firstOrNull()
    val cal = remember { Calendar.getInstance() }
    val month = cal.get(Calendar.MONTH) + 1
    val day = cal.get(Calendar.DAY_OF_MONTH)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SynFix Lab Ultra")
                        Text(
                            text = "Fase 6 Pro + Fase 7 Ultra + Expansión #43–#85",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("pro_lab_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Descubrimiento & Mezcla", style = MaterialTheme.typography.titleMedium)

            ProActionRow(
                leftTitle = "Radar 2D",
                leftSub = "Chill / BPM",
                leftIcon = Icons.Filled.Radar,
                leftTag = "open_mood_radar",
                onLeftClick = { showMoodRadar = true },
                rightTitle = "Preview 0:45",
                rightSub = "Swipe rápido",
                rightIcon = Icons.Filled.FastForward,
                rightTag = "open_preview_swiper",
                onRightClick = { showPreviewSwiper = true }
            )

            ProActionRow(
                leftTitle = "Ruleta Física",
                leftSub = "Cola aleatoria",
                leftIcon = Icons.Filled.Casino,
                leftTag = "open_spin_wheel",
                onLeftClick = { showSpinWheel = true },
                rightTitle = "Camelot & Mix",
                rightSub = "Armónico / Hora",
                rightIcon = Icons.Filled.Album,
                rightTag = "open_camelot_wheel",
                onRightClick = {
                    viewModel.loadHarmonicMatches(activeSong?.camelotKey ?: proState.selectedCamelotKey)
                    showCamelotSheet = true
                }
            )

            Text("Juegos, Estadísticas & Social", style = MaterialTheme.typography.titleMedium)

            ProActionRow(
                leftTitle = "Wrapped Local",
                leftSub = "${wrappedStats.streakDays}d racha",
                leftIcon = Icons.Filled.LocalFireDepartment,
                leftTag = "open_wrapped",
                onLeftClick = { showWrapped = true },
                rightTitle = "Intro Quiz 3s",
                rightSub = "Minijuego",
                rightIcon = Icons.Filled.Quiz,
                rightTag = "open_intro_quiz",
                onRightClick = { showIntroQuiz = true }
            )

            ProActionRow(
                leftTitle = "Party Sync UDP",
                leftSub = partySyncState.mode.label,
                leftIcon = Icons.Filled.WifiTethering,
                leftTag = "open_party_sync",
                onLeftClick = { showPartySync = true },
                rightTitle = "Wake-Up & Focus",
                rightSub = if (wakeUpState.enabled) String.format("%02d:%02d", wakeUpState.hour, wakeUpState.minute) else "Pomodoro 25/5",
                rightIcon = Icons.Filled.Alarm,
                rightTag = "open_wakeup_pomodoro",
                onRightClick = { showWakeUpPomodoro = true }
            )

            Text("Ingeniería & Biblioteca", style = MaterialTheme.typography.titleMedium)

            ProActionRow(
                leftTitle = "Recortar Clip",
                leftSub = "Transformer",
                leftIcon = Icons.Filled.ContentCut,
                leftTag = "open_snippet_cutter",
                onLeftClick = {
                    if (activeSong != null) showSnippetCutter = true
                    else viewModel.emitSnackbar("Escanea o selecciona una canción primero")
                },
                rightTitle = "Tap-to-Sync LRC",
                rightSub = "Crear letras",
                rightIcon = Icons.Filled.Lyrics,
                rightTag = "open_tap_lrc",
                onRightClick = {
                    if (activeSong != null) showTapToSyncLrc = true
                    else viewModel.emitSnackbar("Selecciona una canción primero")
                }
            )

            ProActionRow(
                leftTitle = "Auditor FLAC",
                leftSub = "Bitrate & Dupes",
                leftIcon = Icons.Filled.Verified,
                leftTag = "open_library_auditor",
                onLeftClick = { showAuditor = true },
                rightTitle = "Alias & Limpieza",
                rightSub = "Unificar tags",
                rightIcon = Icons.Filled.CleaningServices,
                rightTag = "open_alias_cleaner",
                onRightClick = { showAliasCleaner = true }
            )

            ProActionRow(
                leftTitle = "Bóveda Oculta",
                leftSub = "Biometría",
                leftIcon = Icons.Filled.Fingerprint,
                leftTag = "open_hidden_vault",
                onLeftClick = { showHiddenVault = true },
                rightTitle = "Relinker .m3u8",
                rightSub = "Import / Export",
                rightIcon = Icons.Filled.Link,
                rightTag = "open_m3u8_relinker",
                onRightClick = { showM3u8Relinker = true }
            )

            Text("Pocket DJ & Suite de Minijuegos (#44–#68)", style = MaterialTheme.typography.titleMedium)

            ProActionRow(
                leftTitle = "Pocket DJ 2-Deck",
                leftSub = "Crossfade & Mashup",
                leftIcon = Icons.Filled.Tune,
                leftTag = "open_pocket_dj",
                onLeftClick = { showPocketDj = true },
                rightTitle = "Lyric Gap",
                rightSub = "Completa la letra",
                rightIcon = Icons.Filled.Spellcheck,
                rightTag = "open_lyric_gap",
                onRightClick = { showLyricGap = true }
            )

            ProActionRow(
                leftTitle = "Tap-the-Beat",
                leftSub = "Arcade 120 FPS",
                leftIcon = Icons.Filled.TouchApp,
                leftTag = "open_tap_beat",
                onLeftClick = { showTapBeatArcade = true },
                rightTitle = "Track Bracket 1v1",
                rightSub = "Torneo local",
                rightIcon = Icons.Filled.EmojiEvents,
                rightTag = "open_track_bracket",
                onRightClick = { showTrackBracket = true }
            )

            ProActionRow(
                leftTitle = "Music Bingo",
                leftSub = "Cartón 3×3 offline",
                leftIcon = Icons.Filled.GridOn,
                leftTag = "open_music_bingo",
                onLeftClick = { showMusicBingo = true },
                rightTitle = "Drop Reflex",
                rightSub = "Reflejos al drop",
                rightIcon = Icons.Filled.Bolt,
                rightTag = "open_drop_reflex",
                onRightClick = { showDropReflex = true }
            )

            ProActionRow(
                leftTitle = "Artist Link Chain",
                leftSub = "Cadena feats",
                leftIcon = Icons.Filled.Hub,
                leftTag = "open_artist_chain",
                onLeftClick = { showArtistChain = true },
                rightTitle = "Chaos Dice 3D",
                rightSub = "Dado de mezclas",
                rightIcon = Icons.Filled.Casino,
                rightTag = "open_chaos_dice",
                onRightClick = { showChaosDice = true }
            )

            ProActionRow(
                leftTitle = "Vitrina Trofeos",
                leftSub = "${trophies.count { it.unlocked }}/${trophies.size} logros",
                leftIcon = Icons.Filled.MilitaryTech,
                leftTag = "open_trophies",
                onLeftClick = { showTrophyCabinet = true },
                rightTitle = "Tap-Tempo BPM",
                rightSub = "Detector al pulso",
                rightIcon = Icons.Filled.Speed,
                rightTag = "open_tap_tempo",
                onRightClick = { showTapTempo = true }
            )

            Text("Superpoderes de Sistema (#76–#85)", style = MaterialTheme.typography.titleMedium)

            ProActionRow(
                leftTitle = "Junk Tag Stripper",
                leftSub = "Limpiar URLs/Tags",
                leftIcon = Icons.Filled.AutoFixHigh,
                leftTag = "open_junk_stripper",
                onLeftClick = { showJunkStripper = true },
                rightTitle = "Blind Test A/B",
                rightSub = "FLAC vs MP3",
                rightIcon = Icons.Filled.Compare,
                rightTag = "open_blind_test",
                onRightClick = { showBlindTest = true }
            )

            ProActionRow(
                leftTitle = "Servidor QR Wi-Fi",
                leftSub = if (qrServerState.isRunning) "Puerto ${qrServerState.port}" else "Subir sin cables",
                leftIcon = Icons.Filled.QrCode2,
                leftTag = "open_qr_server",
                onLeftClick = { showQrServer = true },
                rightTitle = "Backup & Eficiencia",
                rightSub = "${dspBatteryStats.offloadPercentage}% Offload",
                rightIcon = Icons.Filled.BatteryChargingFull,
                rightTag = "open_backup_stats",
                onRightClick = { showBackupAndStats = true }
            )

            // #13 Zero-Play Streak (Daily 3 never-played gems)
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Explore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Zero-Play Streak", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "3 joyas nunca escuchadas",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = viewModel::refreshZeroPlayStreak) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Renovar 3 pistas")
                        }
                    }

                    if (proState.zeroPlayStreak.isEmpty()) {
                        Text(
                            text = "Sin pistas pendientes sin reproducir o biblioteca aún sin escanear.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        proState.zeroPlayStreak.forEach { song ->
                            ProSongMiniRow(
                                song = song,
                                badge = "${song.bpm} BPM",
                                onClick = {
                                    viewModel.playSongFromList(song, proState.zeroPlayStreak)
                                    onOpenNowPlaying()
                                }
                            )
                        }
                    }
                }
            }

            // #12 Throwback Time-Machine
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Column {
                            Text("Cápsula Time-Machine", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Historial del $day/$month por franja horaria",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val slots = listOf("Mañana", "Tarde", "Noche", "Madrugada")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        slots.forEachIndexed { idx, label ->
                            FilterChip(
                                selected = proState.throwbackSlotIndex == idx,
                                onClick = { viewModel.loadThrowback(month, day, idx) },
                                label = { Text(label) }
                            )
                        }
                    }

                    if (proState.throwbackEntries.isEmpty()) {
                        Text(
                            text = "Reproduce música en distintas horas del día para construir tu cápsula del tiempo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        proState.throwbackEntries.take(5).forEach { entry ->
                            ProSongMiniRow(
                                song = entry.song,
                                badge = "${entry.timeSlotLabel} ${entry.year}",
                                onClick = {
                                    viewModel.playSongFromList(entry.song, proState.throwbackEntries.map { it.song })
                                    onOpenNowPlaying()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showMoodRadar) {
        MoodRadarSheet(
            currentBpm = proState.moodBpm,
            currentEnergy = proState.moodEnergy,
            matches = proState.moodMatches,
            onQueryMood = viewModel::queryMoodRadar,
            onPlaySong = { song, list ->
                viewModel.playSongFromList(song, list)
                showMoodRadar = false
                onOpenNowPlaying()
            },
            onDismiss = { showMoodRadar = false }
        )
    }

    if (showPreviewSwiper) {
        QuickPreviewSwiperDialog(
            songs = libraryState.songs,
            onPreviewAt45s = viewModel::previewSongAt45s,
            onFavoriteAndQueue = { song ->
                if (!song.isFavorite) viewModel.toggleFavorite(song)
                viewModel.addToQueue(song)
            },
            onDismiss = { showPreviewSwiper = false }
        )
    }

    if (showSnippetCutter && activeSong != null) {
        SnippetCutterDialog(
            song = activeSong,
            isExporting = proState.isExportingClip,
            lastExportedPath = proState.lastExportedClipPath,
            onExportClip = { start, end -> viewModel.exportSnippetClip(activeSong, start, end) },
            onDismiss = { showSnippetCutter = false }
        )
    }

    if (showAuditor) {
        LibraryAuditorDialog(
            findings = proState.auditFindings,
            isAuditing = proState.isAuditing,
            hasRunAudit = proState.hasRunAudit,
            onRunAudit = viewModel::runLibraryAudit,
            onDismiss = { showAuditor = false }
        )
    }

    if (showSpinWheel) {
        PhysicsSpinWheelDialog(
            songs = libraryState.songs,
            onPlaySelected = { song, list ->
                viewModel.playSongFromList(song, list)
                showSpinWheel = false
                onOpenNowPlaying()
            },
            onDismiss = { showSpinWheel = false }
        )
    }

    if (showCamelotSheet) {
        CamelotAndTimeMixSheet(
            selectedCamelotKey = proState.selectedCamelotKey,
            harmonicMatches = proState.harmonicMatches,
            contextTimeSlot = proState.contextTimeSlot,
            contextMixSongs = proState.contextMixSongs,
            onSelectCamelotKey = viewModel::loadHarmonicMatches,
            onScheduleCamelotScan = viewModel::scheduleCamelotScan,
            onSelectTimeSlot = viewModel::loadContextTimeMix,
            onPlaySong = { song, list ->
                viewModel.playSongFromList(song, list)
                showCamelotSheet = false
                onOpenNowPlaying()
            },
            onDismiss = { showCamelotSheet = false }
        )
    }

    if (showWrapped) {
        WrappedDialog(stats = wrappedStats, onDismiss = { showWrapped = false })
    }

    if (showIntroQuiz) {
        IntroQuizDialog(songs = libraryState.songs, onDismiss = { showIntroQuiz = false })
    }

    if (showPartySync) {
        PartySyncSheet(
            state = partySyncState,
            onStartHost = viewModel::startPartyHost,
            onStartGuest = viewModel::startPartyGuest,
            onStop = viewModel::stopPartySync,
            onDismiss = { showPartySync = false }
        )
    }

    if (showWakeUpPomodoro) {
        WakeUpPomodoroDialog(
            state = wakeUpState,
            onConfigureWakeUp = viewModel::configureWakeUpAlarm,
            onTogglePomodoro = viewModel::togglePomodoro,
            onDismiss = { showWakeUpPomodoro = false }
        )
    }

    if (showTapToSyncLrc && activeSong != null) {
        TapToSyncLrcDialog(
            song = activeSong,
            positionMsProvider = { positionMs },
            isPlaying = playerState.isPlaying,
            onTogglePlayPause = viewModel::togglePlayPause,
            onSaveLyrics = viewModel::saveLyricsForSong,
            onDismiss = { showTapToSyncLrc = false }
        )
    }

    if (showAliasCleaner) {
        ArtistBatchCleanerDialog(
            aliasGroups = proState.aliasGroups,
            onScanAliases = viewModel::scanArtistAliases,
            onMergeAlias = viewModel::mergeArtistAlias,
            onDismiss = { showAliasCleaner = false }
        )
    }

    if (showHiddenVault) {
        HiddenVaultDialog(
            hiddenSongs = hiddenSongs,
            onPlaySong = { song, list ->
                viewModel.playSongFromList(song, list)
                showHiddenVault = false
                onOpenNowPlaying()
            },
            onUnhideSong = { id -> viewModel.setSongHidden(id, false) },
            onDismiss = { showHiddenVault = false }
        )
    }

    if (showM3u8Relinker) {
        M3u8RelinkerDialog(
            librarySongs = libraryState.songs,
            lastResult = proState.lastM3u8Result,
            onImportM3u8 = viewModel::importM3u8Playlist,
            onExportM3u8 = viewModel::exportSongsToM3u8,
            onDismiss = { showM3u8Relinker = false }
        )
    }

    // Phase 8 Dialogs (#44, #55, #61-#68, #70, #76, #77, #80, #81, #85)
    if (showPocketDj) {
        PocketDjAndMashupDialog(
            songs = libraryState.songs,
            djState = pocketDjState,
            onLoadDeckA = viewModel::pocketDjLoadA,
            onLoadDeckB = viewModel::pocketDjLoadB,
            onToggleDeckA = viewModel::pocketDjToggleA,
            onToggleDeckB = viewModel::pocketDjToggleB,
            onSeekDeckA = viewModel::pocketDjSeekA,
            onSeekDeckB = viewModel::pocketDjSeekB,
            onCrossfadeChange = viewModel::pocketDjCrossfade,
            onToggleMashup = viewModel::pocketDjMashup,
            onToggleSyncBpm = viewModel::pocketDjSyncBpm,
            onDismiss = {
                viewModel.releasePocketDj()
                showPocketDj = false
            }
        )
    }

    if (showLyricGap) {
        LyricGapGameDialog(
            songs = libraryState.songs,
            onPlaySong = { song ->
                viewModel.playSongFromList(song, libraryState.songs)
                showLyricGap = false
                onOpenNowPlaying()
            },
            onDismiss = { showLyricGap = false }
        )
    }

    if (showTapBeatArcade) {
        TapBeatArcadeDialog(
            currentSong = activeSong,
            positionMsProvider = { positionMs },
            onEnsureAnalyzed = viewModel::ensureSongEnergyAnalyzed,
            onPlaySong = { song -> viewModel.playSongFromList(song, libraryState.songs) },
            onDismiss = { showTapBeatArcade = false }
        )
    }

    if (showTrackBracket) {
        TrackBracketDialog(
            songs = libraryState.songs,
            onPlaySong = { song -> viewModel.playSongFromList(song, libraryState.songs) },
            onCreateChampionPlaylist = { champ ->
                viewModel.createPlaylist("Campeona: ${champ.title.take(18)}")
                viewModel.toggleFavorite(champ)
            },
            onDismiss = { showTrackBracket = false }
        )
    }

    if (showMusicBingo) {
        MusicBingoDialog(
            songs = libraryState.songs,
            currentSong = playerState.currentSong,
            onPlayRandom = { viewModel.elasticShufflePlay() },
            onDismiss = { showMusicBingo = false }
        )
    }

    if (showDropReflex) {
        DropReflexDialog(
            songs = libraryState.songs,
            onEnsureAnalyzed = viewModel::ensureSongEnergyAnalyzed,
            onSeekAndPlay = { song, posMs ->
                viewModel.playSongFromList(song, libraryState.songs)
                viewModel.seekTo(posMs)
            },
            onDismiss = { showDropReflex = false }
        )
    }

    if (showArtistChain) {
        ArtistLinkChainDialog(
            songs = libraryState.songs,
            candidates = artistChainPool,
            onFindNext = viewModel::loadArtistChainCandidates,
            onPlaySong = { song -> viewModel.playSongFromList(song, libraryState.songs) },
            onDismiss = { showArtistChain = false }
        )
    }

    if (showChaosDice) {
        ChaosDice3dDialog(
            songs = libraryState.songs,
            onPlayMix = { first, list ->
                viewModel.playSongFromList(first, list)
                showChaosDice = false
                onOpenNowPlaying()
            },
            onDismiss = { showChaosDice = false }
        )
    }

    if (showTrophyCabinet) {
        TrophyCabinetDialog(
            trophies = trophies,
            onRefresh = viewModel::refreshTrophies,
            onDismiss = { showTrophyCabinet = false }
        )
    }

    if (showTapTempo) {
        TapTempoFinderDialog(
            songs = libraryState.songs,
            onPlaySong = { song, list ->
                viewModel.playSongFromList(song, list)
                showTapTempo = false
                onOpenNowPlaying()
            },
            onDismiss = { showTapTempo = false }
        )
    }

    if (showJunkStripper) {
        JunkTagStripperDialog(
            candidates = junkCandidates,
            onScan = viewModel::scanJunkTagCandidates,
            onApplyBulkCleanup = viewModel::applyBulkJunkTagCleanup,
            onDismiss = { showJunkStripper = false }
        )
    }

    if (showBlindTest) {
        BlindTestAbDialog(
            songs = libraryState.songs,
            state = blindTestState,
            onStartTest = viewModel::startBlindTest,
            onSwitchSlot = viewModel::switchBlindTestSlot,
            onTogglePlay = viewModel::toggleBlindTestPlayPause,
            onSubmitGuess = viewModel::submitBlindTestGuess,
            onDismiss = {
                viewModel.releaseBlindTest()
                showBlindTest = false
            }
        )
    }

    if (showQrServer) {
        LocalQrWebServerDialog(
            state = qrServerState,
            onStartServer = viewModel::startQrWebServer,
            onStopServer = viewModel::stopQrWebServer,
            onDismiss = { showQrServer = false }
        )
    }

    if (showBackupAndStats) {
        BackupAndEfficiencyDialog(
            stats = dspBatteryStats,
            onExportBackup = viewModel::exportSynFixBackup,
            onImportBackup = viewModel::importSynFixBackup,
            onDismiss = { showBackupAndStats = false }
        )
    }
}

@Composable
private fun ProActionRow(
    leftTitle: String,
    leftSub: String,
    leftIcon: androidx.compose.ui.graphics.vector.ImageVector,
    leftTag: String,
    onLeftClick: () -> Unit,
    rightTitle: String,
    rightSub: String,
    rightIcon: androidx.compose.ui.graphics.vector.ImageVector,
    rightTag: String,
    onRightClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ProToolActionCard(
            title = leftTitle,
            subtitle = leftSub,
            icon = leftIcon,
            onClick = onLeftClick,
            modifier = Modifier.weight(1f).testTag(leftTag)
        )
        ProToolActionCard(
            title = rightTitle,
            subtitle = rightSub,
            icon = rightIcon,
            onClick = onRightClick,
            modifier = Modifier.weight(1f).testTag(rightTag)
        )
    }
}

@Composable
private fun ProToolActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(26.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProSongMiniRow(
    song: Song,
    badge: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ArtworkImage(
                artworkUri = song.artworkUri,
                contentDescription = song.album,
                targetSizeDp = 44.dp,
                proceduralSeedKey = "${song.album}|${song.artist}",
                modifier = Modifier.size(44.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Text(
                text = badge,
                style = ExpressiveTypography.MonoTimecode,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
