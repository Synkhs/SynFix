@file:OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.BuildConfig
import com.example.R
import com.example.domain.model.Song
import com.example.ui.components.ExpandableMiniPlayer
import com.example.ui.components.FloatingMiniBubble
import com.example.ui.components.HorizontalFloatingToolbar
import com.example.ui.components.LocalAnimatedVisibilityScope
import com.example.ui.components.LocalSharedTransitionScope
import com.example.ui.screens.equalizer.EqualizerScreen
import com.example.ui.screens.library.LibraryScreen
import com.example.ui.screens.player.NowPlayingScreen
import com.example.ui.screens.pro.ProLabScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.tags.TagEditorDialog
import com.example.ui.theme.ExpressiveTypography
import com.example.ui.theme.SynFixMotion
import com.example.ui.viewmodel.MusicViewModel
import kotlinx.serialization.Serializable

@Serializable
object LibraryRoute

@Serializable
object NowPlayingRoute

@Serializable
object EqualizerRoute

@Serializable
object SettingsRoute

@Serializable
object ProLabRoute

@Composable
fun MainScreen(viewModel: MusicViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val libraryState by viewModel.libraryState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val appearance by viewModel.appearanceSettings.collectAsStateWithLifecycle()
    val positionMs by viewModel.playbackPositionMs.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var editingSongTags by remember { mutableStateOf<Song?>(null) }

    val context = LocalContext.current
    val requiredPermissions = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_AUDIO)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val audioGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            results[Manifest.permission.READ_MEDIA_AUDIO] == true
        } else {
            results[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }
        if (audioGranted) {
            viewModel.scanMusic()
        }
    }

    LaunchedEffect(Unit) {
        val audioPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(context, audioPerm) == PackageManager.PERMISSION_GRANTED) {
            viewModel.scanMusic()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
        }
    }

    val isNowPlayingDestination = currentRoute?.contains("NowPlayingRoute") == true
    val showNormalMiniPlayer = playerState.currentSong != null && !appearance.floatingBubbleEnabled

    // #26 Shared Element Liquid Morphing via SharedTransitionLayout + LookaheadScope
    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        LookaheadScope {
            CompositionLocalProvider(LocalSharedTransitionScope provides this@SharedTransitionLayout) {
                Box(modifier = Modifier.fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = LibraryRoute,
                        enterTransition = {
                            slideInVertically(SynFixMotion.SpatialIntOffsetSpring) { it / 6 } + fadeIn(SynFixMotion.effectsSpring())
                        },
                        exitTransition = {
                            fadeOut(SynFixMotion.effectsSpring())
                        },
                        popEnterTransition = {
                            fadeIn(SynFixMotion.effectsSpring())
                        },
                        popExitTransition = {
                            slideOutVertically(SynFixMotion.SpatialIntOffsetSpring) { it / 5 } + fadeOut(SynFixMotion.effectsSpring())
                        }
                    ) {
                        composable<LibraryRoute> {
                            CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                                Scaffold(
                                    modifier = Modifier.fillMaxSize(),
                                    containerColor = MaterialTheme.colorScheme.background,
                                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                                    topBar = {
                                        SynFixMainTopAppBar(
                                            songCount = libraryState.songs.size,
                                            isScanning = libraryState.isScanning,
                                            onOpenProLab = { navController.navigate(ProLabRoute) },
                                            onOpenEqualizer = { navController.navigate(EqualizerRoute) },
                                            onScanMusic = { viewModel.scanMusic() },
                                            onOpenSettings = { navController.navigate(SettingsRoute) }
                                        )
                                    },
                                    bottomBar = {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .navigationBarsPadding()
                                                .padding(bottom = 8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (showNormalMiniPlayer) {
                                                playerState.currentSong?.let { song ->
                                                    ExpandableMiniPlayer(
                                                        song = song,
                                                        isPlaying = playerState.isPlaying,
                                                        artworkShapeStyle = appearance.artworkShape,
                                                        useProceduralFallback = appearance.proceduralArtFallback,
                                                        positionProvider = { positionMs },
                                                        durationMs = playerState.durationMs,
                                                        onExpand = { navController.navigate(NowPlayingRoute) },
                                                        onTogglePlayPause = viewModel::togglePlayPause,
                                                        onNext = viewModel::skipNext,
                                                        onPrevious = viewModel::skipPrevious
                                                    )
                                                }
                                            }

                                            HorizontalFloatingToolbar(
                                                selectedTab = libraryState.selectedTab,
                                                onSelectTab = viewModel::setSelectedTab
                                            )
                                        }
                                    }
                                ) { innerPadding ->
                                    LibraryScreen(
                                        viewModel = viewModel,
                                        hasMiniPlayer = showNormalMiniPlayer,
                                        onEditTags = { song -> editingSongTags = song },
                                        onOpenNowPlaying = { navController.navigate(NowPlayingRoute) },
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(top = innerPadding.calculateTopPadding())
                                    )
                                }
                            }
                        }

                        composable<NowPlayingRoute> {
                            CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                                NowPlayingScreen(
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() },
                                    onOpenEqualizer = { navController.navigate(EqualizerRoute) },
                                    onEditTags = { song -> editingSongTags = song }
                                )
                            }
                        }

                        composable<EqualizerRoute> {
                            CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                                EqualizerScreen(
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }

                        composable<SettingsRoute> {
                            CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                                SettingsScreen(
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }

                        composable<ProLabRoute> {
                            CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                                ProLabScreen(
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() },
                                    onOpenNowPlaying = { navController.navigate(NowPlayingRoute) }
                                )
                            }
                        }
                    }

                    // #23 M3 Floating Mini-Bubble with screen-edge collision & magnetic snap
                    if (appearance.floatingBubbleEnabled && !isNowPlayingDestination) {
                        playerState.currentSong?.let { activeSong ->
                            FloatingMiniBubble(
                                song = activeSong,
                                isPlaying = playerState.isPlaying,
                                useProceduralFallback = appearance.proceduralArtFallback,
                                onExpand = { navController.navigate(NowPlayingRoute) },
                                onTogglePlayPause = viewModel::togglePlayPause,
                                onNext = viewModel::skipNext
                            )
                        }
                    }

                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 64.dp)
                    )
                }
            }
        }
    }

    editingSongTags?.let { song ->
        TagEditorDialog(
            song = song,
            onDismiss = { editingSongTags = null },
            onSave = { request ->
                viewModel.saveTagChanges(request)
                editingSongTags = null
            }
        )
    }
}

@Composable
private fun SynFixMainTopAppBar(
    songCount: Int,
    isScanning: Boolean,
    onOpenProLab: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onScanMusic: () -> Unit,
    onOpenSettings: () -> Unit
) {
    TopAppBar(
        modifier = Modifier.statusBarsPadding(),
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface
        ),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.inverseSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.synfix_logo_foreground),
                        contentDescription = "Logotipo de SynFix",
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.inverseOnSurface),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "SynFix",
                            style = ExpressiveTypography.SectionHeaderEmphasized,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.testTag("app_version_badge")
                        ) {
                            Text(
                                text = "v${BuildConfig.VERSION_NAME}",
                                style = ExpressiveTypography.MonoTimecode,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "$songCount pistas locales",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        actions = {
            IconButton(
                onClick = onOpenProLab,
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("open_pro_lab_button")
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = "SynFix Lab Pro")
            }
            IconButton(
                onClick = onOpenEqualizer,
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("toolbar_equalizer_button")
            ) {
                Icon(Icons.Rounded.GraphicEq, contentDescription = "Ecualizador")
            }
            IconButton(
                onClick = onScanMusic,
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("scan_music_button")
            ) {
                if (isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Escanear biblioteca")
                }
            }
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("settings_button")
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = "Ajustes")
            }
        }
    )
}
