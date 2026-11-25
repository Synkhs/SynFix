@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.domain.model.Song
import com.example.ui.components.ExpandableMiniPlayer
import com.example.ui.components.FloatingMiniBubble
import com.example.ui.components.LocalAnimatedVisibilityScope
import com.example.ui.components.LocalSharedTransitionScope
import com.example.ui.components.SynFixFloatingToolbar
import com.example.ui.screens.equalizer.EqualizerScreen
import com.example.ui.screens.library.LibraryScreen
import com.example.ui.screens.player.NowPlayingScreen
import com.example.ui.screens.pro.ProLabScreen
import com.example.ui.screens.settings.AppearanceSettingsScreen
import com.example.ui.screens.tags.TagEditorDialog
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
object AppearanceRoute

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
    var toolbarVisible by remember { mutableStateOf(true) }
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
                                Box(modifier = Modifier.fillMaxSize()) {
                                    LibraryScreen(
                                        viewModel = viewModel,
                                        hasMiniPlayer = playerState.currentSong != null && !appearance.floatingBubbleEnabled,
                                        onToolbarVisibilityChange = { visible -> toolbarVisible = visible },
                                        onEditTags = { song -> editingSongTags = song },
                                        onOpenNowPlaying = { navController.navigate(NowPlayingRoute) },
                                        onOpenProLab = { navController.navigate(ProLabRoute) }
                                    )

                                    Column(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .padding(bottom = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        if (!appearance.floatingBubbleEnabled) {
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
                                                    onPrevious = viewModel::skipPrevious,
                                                    modifier = if (!toolbarVisible) Modifier.navigationBarsPadding() else Modifier
                                                )
                                            }
                                        }

                                        SynFixFloatingToolbar(
                                            visible = toolbarVisible,
                                            selectedTab = libraryState.selectedTab,
                                            onSelectTab = viewModel::setSelectedTab,
                                            onOpenEqualizer = { navController.navigate(EqualizerRoute) },
                                            onOpenSettings = { navController.navigate(AppearanceRoute) }
                                        )
                                    }
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

                        composable<AppearanceRoute> {
                            CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                                AppearanceSettingsScreen(
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
