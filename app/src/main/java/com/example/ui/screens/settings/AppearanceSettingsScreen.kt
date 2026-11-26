package com.example.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.domain.model.*
import com.example.ui.components.ArtworkImage
import com.example.ui.components.LinearWavyProgressIndicator
import com.example.ui.components.rememberArtworkShape
import com.example.ui.theme.ExpressiveTypography
import com.example.ui.theme.SeedColorPresets
import com.example.ui.viewmodel.MusicViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    AppearanceSettingsScreen(viewModel = viewModel, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppearanceSettingsScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val appearance by viewModel.appearanceSettings.collectAsStateWithLifecycle()
    val scanner by viewModel.scannerSettings.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()

    val previewArtShape = rememberArtworkShape(appearance.artworkShape)
    val context = LocalContext.current
    var hasRecordAudioPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val recordAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasRecordAudioPerm = granted
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes y Apariencia") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
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
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Real-Time Live Preview Card
            ElevatedCard(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Vista Previa en Tiempo Real", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        AssistChip(onClick = {}, label = { Text(appearance.nowPlayingStyle.label) })
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ArtworkImage(
                            artworkUri = playerState.currentSong?.artworkUri,
                            contentDescription = "Preview",
                            targetSizeDp = 64.dp,
                            shape = previewArtShape,
                            circular = appearance.nowPlayingStyle == NowPlayingStyle.VINYL_MINIMAL,
                            proceduralSeedKey = playerState.currentSong?.let { "${it.album}|${it.artist}" } ?: "SynFixPreview",
                            useProceduralFallback = appearance.proceduralArtFallback,
                            modifier = Modifier.size(64.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playerState.currentSong?.title ?: "SynFix Hi-Res Audio",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = playerState.currentSong?.artist ?: "${appearance.colorMode.label} • ${appearance.brightnessMode.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    LinearWavyProgressIndicator(
                        progressProvider = { 0.62f },
                        isPlaying = true,
                        onSeekProgress = {}
                    )
                }
            }

            // 1. Color Engine
            SettingsSectionCard(title = "Motor de Color") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ColorThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = appearance.colorMode == mode,
                            onClick = { viewModel.updateAppearance { it.copy(colorMode = mode) } },
                            label = { Text(mode.label) }
                        )
                    }
                }

                if (appearance.colorMode == ColorThemeMode.CUSTOM_SEED) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Color Semilla", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SeedColorPresets.forEach { option ->
                            val argb = option.color.toArgb().toLong() and 0xFFFFFFFFL
                            val selected = (appearance.seedColorArgb and 0xFFFFFFFFL) == argb
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(option.color)
                                    .then(
                                        if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        else Modifier
                                    )
                                    .clickable {
                                        viewModel.updateAppearance { it.copy(seedColorArgb = argb) }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(Icons.Filled.Check, contentDescription = option.name, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            // 2. Brightness Mode (including Pure AMOLED)
            SettingsSectionCard(title = "Brillo & Negro Puro AMOLED") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BrightnessMode.entries.forEach { mode ->
                        FilterChip(
                            selected = appearance.brightnessMode == mode,
                            onClick = { viewModel.updateAppearance { it.copy(brightnessMode = mode) } },
                            label = { Text(mode.label) },
                            modifier = Modifier.testTag("brightness_${mode.name.lowercase()}")
                        )
                    }
                }
            }

            // 3. Now Playing Layouts
            SettingsSectionCard(title = "Estilo de Pantalla \"Ahora Suena\"") {
                NowPlayingStyle.entries.forEach { style ->
                    val selected = appearance.nowPlayingStyle == style
                    ListItem(
                        headlineContent = { Text(style.label) },
                        supportingContent = { Text(style.description) },
                        leadingContent = {
                            RadioButton(
                                selected = selected,
                                onClick = { viewModel.updateAppearance { it.copy(nowPlayingStyle = style) } }
                            )
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { viewModel.updateAppearance { it.copy(nowPlayingStyle = style) } }
                    )
                }
            }

            // 4. Library Layout & Artwork Shape
            SettingsSectionCard(title = "Personalización de Biblioteca") {
                Text("Densidad y columnas", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LibraryViewLayout.entries.forEach { layout ->
                        FilterChip(
                            selected = appearance.libraryLayout == layout,
                            onClick = { viewModel.updateAppearance { it.copy(libraryLayout = layout) } },
                            label = { Text(layout.label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Forma de carátulas", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ArtworkShapeStyle.entries.forEach { shapeStyle ->
                        FilterChip(
                            selected = appearance.artworkShape == shapeStyle,
                            onClick = { viewModel.updateAppearance { it.copy(artworkShape = shapeStyle) } },
                            label = { Text(shapeStyle.label) }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Beat-Synced Shape Morphing", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = if (hasRecordAudioPerm) {
                                "Muta la carátula con subgraves (20-100 Hz) a 30 Hz"
                            } else {
                                "Toca para activar análisis FFT en vivo (requiere permiso de audio)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = appearance.beatSyncedMorphEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.updateAppearance { it.copy(beatSyncedMorphEnabled = enabled) }
                            if (enabled && !hasRecordAudioPerm) {
                                recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Carátulas Procedurales", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Genera arte geométrico determinista (0 MB) en pistas sin portada",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = appearance.proceduralArtFallback,
                        onCheckedChange = { enabled ->
                            viewModel.updateAppearance { it.copy(proceduralArtFallback = enabled) }
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Mini-Burbuja Flotante M3", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Reproductor flotante con colisión contra bordes y anclaje magnético",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = appearance.floatingBubbleEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.updateAppearance { it.copy(floatingBubbleEnabled = enabled) }
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Motor Háptico Sub-Bass (20-60 Hz)", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Pulsos hápticos sincronizados con subgraves (auto-apagado <15% batería)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = appearance.subBassHapticsEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.updateAppearance { it.copy(subBassHapticsEnabled = enabled) }
                            if (enabled && !hasRecordAudioPerm) {
                                recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // #45 Shake-to-Skip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Shake-to-Skip", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Agita el teléfono con pantalla encendida para saltar de pista",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = appearance.shakeToSkipEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.updateAppearance { it.copy(shakeToSkipEnabled = enabled) }
                        }
                    )
                }

                // #47 Gyro Parallax Depth Art
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Gyro Parallax Depth Art (≤30 Hz)", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Efecto 3D en carátula controlado por giroscopio solo en primer plano",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = appearance.gyroParallaxEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.updateAppearance { it.copy(gyroParallaxEnabled = enabled) }
                        }
                    )
                }

                // #49 Beat-Sync Party Strobe + Hardware Torch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Estrobo Party Beat-Sync", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Borde luminoso al ritmo de subgraves en Ahora Suena",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = appearance.partyStrobeEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.updateAppearance { it.copy(partyStrobeEnabled = enabled) }
                            if (enabled && !hasRecordAudioPerm) {
                                recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    )
                }

                if (appearance.partyStrobeEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Flash LED de Cámara (≤12 Hz)", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Sincroniza la linterna trasera con el ritmo (se apaga al minimizar)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = appearance.partyStrobeTorchEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.updateAppearance { it.copy(partyStrobeTorchEnabled = enabled) }
                            }
                        )
                    }
                }
            }

            // 5. Smart Library Scanner & Folder Whitelist / Blacklist Manager
            SettingsSectionCard(title = "Escáner Inteligente & Carpetas") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Duración mínima de pista", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "${scanner.minDurationSeconds}s",
                        style = ExpressiveTypography.MonoTimecode,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                var sliderSec by remember(scanner.minDurationSeconds) {
                    mutableFloatStateOf(scanner.minDurationSeconds.toFloat())
                }
                Slider(
                    value = sliderSec,
                    onValueChange = { sliderSec = it },
                    onValueChangeFinished = { viewModel.setMinDurationSeconds(sliderSec.roundToInt()) },
                    valueRange = 0f..60f
                )

                Button(
                    onClick = { viewModel.scanMusic() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sincronizar biblioteca ahora")
                }

                if (scanner.knownFolders.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Gestor de Carpetas (activa para incluir, desactiva para excluir):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    scanner.knownFolders.forEach { folder ->
                        ListItem(
                            headlineContent = { Text(folder.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text("${folder.trackCount} archivos • ${folder.path}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
                            trailingContent = {
                                Switch(
                                    checked = !folder.isExcluded,
                                    onCheckedChange = { viewModel.toggleExcludedFolder(folder.path) }
                                )
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("settings_version_footer")
                ) {
                    Text(
                        text = "SynFix v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                        style = ExpressiveTypography.MonoTimecode,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
