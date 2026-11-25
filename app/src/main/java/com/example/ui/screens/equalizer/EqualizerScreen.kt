package com.example.ui.screens.equalizer

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.AudioOutputType
import com.example.domain.model.FocusFilterBand
import com.example.domain.model.VenueReverbPreset
import com.example.player.BuiltInEqPresets
import com.example.ui.theme.ExpressiveTypography
import com.example.ui.viewmodel.MusicViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EqualizerScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val eqState by viewModel.equalizerState.collectAsStateWithLifecycle()
    val playbackSettings by viewModel.playbackSettings.collectAsStateWithLifecycle()
    val telemetry by viewModel.pipelineTelemetry.collectAsStateWithLifecycle()
    val adaptiveGainDb by viewModel.adaptiveGainDb.collectAsStateWithLifecycle()
    var showSavePresetDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var hasMicPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPerm = granted
    }

    val allPresets = remember(eqState.builtInPresets, eqState.customPresets) {
        eqState.builtInPresets + eqState.customPresets
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Motor de Audio & EQ")
                        Text(
                            text = "${eqState.currentOutput.label} • " +
                                if (eqState.usingDynamicsProcessing) "DynamicsProcessing 10-Band" else "AudioFX",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("eq_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    Switch(
                        checked = eqState.enabled,
                        onCheckedChange = viewModel::setEqEnabled,
                        modifier = Modifier.padding(end = 12.dp).testTag("eq_master_switch")
                    )
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
            // Smooth Bezier Curve Visualizer
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Curva de Frecuencia (Hz)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = if (eqState.enabled) "ACTIVO" else "BYPASS",
                            style = ExpressiveTypography.MonoTimecode,
                            color = if (eqState.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    EqBezierCurveCanvas(
                        bandsDb = eqState.bandsDb,
                        enabled = eqState.enabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                    )
                }
            }

            // Presets Row + Save Custom Profile
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Perfiles de Sonido", style = MaterialTheme.typography.titleMedium)
                    FilledTonalButton(
                        onClick = { showSavePresetDialog = true },
                        modifier = Modifier.testTag("save_eq_preset_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Guardar")
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(allPresets, key = { it.id }) { preset ->
                        val selected = preset.id == eqState.activePresetId
                        InputChip(
                            selected = selected,
                            onClick = { viewModel.selectEqPreset(preset) },
                            label = { Text(preset.name) },
                            trailingIcon = if (preset.isCustom) {
                                {
                                    IconButton(
                                        onClick = { viewModel.deleteCustomEqPreset(preset.id) },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = "Eliminar", modifier = Modifier.size(14.dp))
                                    }
                                }
                            } else null
                        )
                    }
                }
            }

            // 10-Band Equalizer Sliders
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Ecualizador Gráfico 10 Bandas (-12 a +12 dB)", style = MaterialTheme.typography.titleSmall)
                    BuiltInEqPresets.BAND_LABELS.forEachIndexed { index, label ->
                        val value = eqState.bandsDb.getOrElse(index) { 0f }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = label,
                                style = ExpressiveTypography.MonoTimecode,
                                modifier = Modifier.width(36.dp),
                                textAlign = TextAlign.End
                            )
                            Slider(
                                value = value,
                                onValueChange = { viewModel.updateEqBand(index, it) },
                                valueRange = -12f..12f,
                                enabled = eqState.enabled,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = String.format("%+.1f", value),
                                style = ExpressiveTypography.MonoTimecode,
                                modifier = Modifier.width(46.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }

            // Dedicated FX Controls: Preamp, Bass Boost, Virtualizer, Balance
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Procesamiento & Dinámica", style = MaterialTheme.typography.titleSmall)

                    LabeledSliderRow(
                        label = "Preamplificador (Preamp)",
                        valueText = String.format("%+.1f dB", eqState.preampDb),
                        value = eqState.preampDb,
                        valueRange = -12f..12f,
                        enabled = eqState.enabled,
                        onValueChange = viewModel::updatePreamp
                    )

                    LabeledSliderRow(
                        label = "Bass Boost",
                        valueText = "${(eqState.bassBoost / 10)}%",
                        value = eqState.bassBoost.toFloat(),
                        valueRange = 0f..1000f,
                        enabled = eqState.enabled,
                        onValueChange = { viewModel.updateBassBoost(it.roundToInt()) }
                    )

                    LabeledSliderRow(
                        label = "Virtualizer (Envolvente)",
                        valueText = "${(eqState.virtualizer / 10)}%",
                        value = eqState.virtualizer.toFloat(),
                        valueRange = 0f..1000f,
                        enabled = eqState.enabled,
                        onValueChange = { viewModel.updateVirtualizer(it.roundToInt()) }
                    )

                    LabeledSliderRow(
                        label = "Balance L / R",
                        valueText = when {
                            playbackSettings.channelBalance < -0.05f -> "L ${(-playbackSettings.channelBalance * 100).roundToInt()}%"
                            playbackSettings.channelBalance > 0.05f -> "R ${(playbackSettings.channelBalance * 100).roundToInt()}%"
                            else -> "Centro"
                        },
                        value = playbackSettings.channelBalance,
                        valueRange = -1f..1f,
                        enabled = true,
                        onValueChange = { bal ->
                            val snapped = if (kotlin.math.abs(bal) < 0.06f) 0f else bal
                            viewModel.updatePlaybackSettings { it.copy(channelBalance = snapped) }
                        }
                    )
                }
            }

            // Automatic Output Device Profile Binding
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Perfiles por Dispositivo de Salida", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Cambia el ecualizador automáticamente al conectar auriculares o Bluetooth.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = eqState.autoOutputProfile,
                            onCheckedChange = viewModel::setAutoOutputProfile
                        )
                    }

                    AudioOutputType.entries.forEach { outputType ->
                        val mappedId = eqState.devicePresetMap[outputType] ?: "flat"
                        val mappedName = allPresets.find { it.id == mappedId }?.name ?: "Plano"
                        var expanded by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = when (outputType) {
                                        AudioOutputType.SPEAKER -> Icons.Filled.Speaker
                                        AudioOutputType.WIRED_HEADSET -> Icons.Filled.Headphones
                                        AudioOutputType.BLUETOOTH -> Icons.Filled.BluetoothAudio
                                    },
                                    contentDescription = null,
                                    tint = if (outputType == eqState.currentOutput) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(outputType.label, style = MaterialTheme.typography.bodyMedium)
                            }

                            Box {
                                OutlinedButton(onClick = { expanded = true }) {
                                    Text(mappedName)
                                }
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    allPresets.forEach { preset ->
                                        DropdownMenuItem(
                                            text = { Text(preset.name) },
                                            onClick = {
                                                viewModel.assignPresetToOutput(outputType, preset.id)
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Audiophile Playback Features: Gapless, Crossfade, Speed, Pitch
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Motor Audiófilo (ExoPlayer)", style = MaterialTheme.typography.titleSmall)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Gapless Playback", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Reproducción sin silencios entre pistas del mismo álbum",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = playbackSettings.gaplessEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.updatePlaybackSettings { it.copy(gaplessEnabled = enabled) }
                            }
                        )
                    }

                    LabeledSliderRow(
                        label = "Crossfade entre pistas",
                        valueText = if (playbackSettings.crossfadeSeconds == 0) "Desactivado" else "${playbackSettings.crossfadeSeconds}s",
                        value = playbackSettings.crossfadeSeconds.toFloat(),
                        valueRange = 0f..12f,
                        enabled = true,
                        onValueChange = { sec ->
                            viewModel.updatePlaybackSettings { it.copy(crossfadeSeconds = sec.roundToInt()) }
                        }
                    )

                    LabeledSliderRow(
                        label = "Velocidad de reproducción",
                        valueText = String.format("%.2fx", playbackSettings.playbackSpeed),
                        value = playbackSettings.playbackSpeed,
                        valueRange = 0.5f..2.0f,
                        enabled = true,
                        onValueChange = { spd ->
                            val snapped = if (kotlin.math.abs(spd - 1f) < 0.04f) 1f else spd
                            viewModel.updatePlaybackSettings { it.copy(playbackSpeed = snapped) }
                        }
                    )

                    LabeledSliderRow(
                        label = "Tono (Pitch)",
                        valueText = String.format("%.2fx", playbackSettings.playbackPitch),
                        value = playbackSettings.playbackPitch,
                        valueRange = 0.5f..2.0f,
                        enabled = true,
                        onValueChange = { pitch ->
                            val snapped = if (kotlin.math.abs(pitch - 1f) < 0.04f) 1f else pitch
                            viewModel.updatePlaybackSettings { it.copy(playbackPitch = snapped) }
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Beat-Matched Crossfade (±4%)", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Sincroniza BPM en los últimos 8s preservando Audio Offload al finalizar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = playbackSettings.beatMatchedCrossfade,
                            onCheckedChange = { enabled ->
                                viewModel.updatePlaybackSettings {
                                    it.copy(
                                        beatMatchedCrossfade = enabled,
                                        crossfadeSeconds = if (enabled && it.crossfadeSeconds == 0) 6 else it.crossfadeSeconds
                                    )
                                }
                            }
                        )
                    }
                }
            }

            // Phase 6 Pro DSP: #6 Dead-Air, #7 Night DRC, #8 LUFS ReplayGain, #9 Venue Reverb, #10 Mid/Side Stems
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("DSP Avanzado (Fase 6 Pro)", style = MaterialTheme.typography.titleSmall)

                    // #10 Mid/Side Stem Control
                    LabeledSliderRow(
                        label = "Control Mid/Side (Karaoke ↔ Vocal)",
                        valueText = when {
                            playbackSettings.midSideStemBalance < -0.05f -> "Karaoke ${(-playbackSettings.midSideStemBalance * 100).roundToInt()}%"
                            playbackSettings.midSideStemBalance > 0.05f -> "Vocal +${(playbackSettings.midSideStemBalance * 100).roundToInt()}%"
                            else -> "Estéreo Normal"
                        },
                        value = playbackSettings.midSideStemBalance,
                        valueRange = -1f..1f,
                        enabled = true,
                        onValueChange = { stem ->
                            val snapped = if (kotlin.math.abs(stem) < 0.06f) 0f else stem
                            viewModel.updatePlaybackSettings { it.copy(midSideStemBalance = snapped) }
                        }
                    )

                    // #6 Smart Dead-Air Trimmer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Smart Dead-Air Trimmer", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Omite silencios muertos iniciales y finales automáticamente",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = playbackSettings.deadAirTrimmerEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.updatePlaybackSettings { it.copy(deadAirTrimmerEnabled = enabled) }
                            }
                        )
                    }

                    // #7 Night Mode DRC
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Modo Nocturno DRC (Hardware MBC)", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Compresión dinámica multibanda + limitador para escucha nocturna",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = playbackSettings.nightModeDrcEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.updatePlaybackSettings { it.copy(nightModeDrcEnabled = enabled) }
                            }
                        )
                    }

                    // #8 True LUFS ReplayGain
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("True LUFS ReplayGain (-14 LUFS)", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Nivela el volumen percibido entre pistas (escaneo solo cargando)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = playbackSettings.replayGainEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.updatePlaybackSettings { it.copy(replayGainEnabled = enabled) }
                                if (enabled) viewModel.scheduleReplayGainScan()
                            }
                        )
                    }

                    HorizontalDivider()

                    // #9 Venue & Acoustic Virtualizer
                    Text("Virtualizador de Sala (EnvironmentalReverb)", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VenueReverbPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = playbackSettings.venueReverbPreset == preset,
                                onClick = {
                                    viewModel.updatePlaybackSettings { it.copy(venueReverbPreset = preset) }
                                },
                                label = { Text(preset.label) }
                            )
                        }
                    }
                }
            }

            // Phase 7 Ultra Studio DSP: #28 4-Band Focus, #29 Lo-Fi Tape, #31 Slowed+Reverb, #32 Adaptive Volume, #42 Telemetry HUD
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Estudio DSP Ultra (Fase 7)", style = MaterialTheme.typography.titleSmall)

                    // #42 Real-Time Audio Pipeline HUD
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${telemetry.codec} • ${telemetry.sampleRateHz / 1000f} kHz • ${telemetry.bitDepthLabel}",
                                    style = ExpressiveTypography.MonoTimecode,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = if (telemetry.bitrateKbps > 0) "${telemetry.bitrateKbps} kbps • ${telemetry.channelCount} ch" else "${telemetry.channelCount} canales",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                )
                            }
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(if (telemetry.isOffloadActive) "HW Offload" else "DSP PCM")
                                }
                            )
                        }
                    }

                    // #28 4-Band Focus Filter
                    Text("Filtro de Enfoque 4 Bandas (DynamicsProcessing)", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FocusFilterBand.entries.forEach { band ->
                            FilterChip(
                                selected = playbackSettings.focusFilterBand == band,
                                onClick = {
                                    viewModel.updatePlaybackSettings { it.copy(focusFilterBand = band) }
                                },
                                label = { Text(band.label) }
                            )
                        }
                    }

                    HorizontalDivider()

                    // #29 Lo-Fi Tape & Vinyl Simulator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Simulador Lo-Fi Tape & Vinilo", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Saturación cálida y textura con buffer circular de 4 KB en RAM",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = playbackSettings.lofiTapeEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.updatePlaybackSettings { it.copy(lofiTapeEnabled = enabled) }
                            }
                        )
                    }

                    // #31 1-Tap Slowed + Reverb
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("1-Tap Slowed + Reverb (0.85x)", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Combina velocidad/tono 0.85x y PresetReverb por DSP de hardware",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = playbackSettings.slowedReverbEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.updatePlaybackSettings { it.copy(slowedReverbEnabled = enabled) }
                            }
                        )
                    }

                    // #32 Adaptive Ambient Volume
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Volumen Ambiental Adaptativo", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = if (playbackSettings.adaptiveAmbientEnabled) {
                                    "LoudnessEnhancer activo (+${String.format("%.1f", adaptiveGainDb)} dB, muestreo 1s/30s)"
                                } else {
                                    "Mide ruido ambiente 1s cada 30s usando LoudnessEnhancer nativo"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = playbackSettings.adaptiveAmbientEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.updatePlaybackSettings { it.copy(adaptiveAmbientEnabled = enabled) }
                                if (enabled && !hasMicPerm) {
                                    micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showSavePresetDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = { Text("Guardar perfil de EQ") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del perfil") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveCustomEqPreset(name)
                    showSavePresetDialog = false
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun EqBezierCurveCanvas(
    bandsDb: List<Float>,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val lineColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val fillTop = lineColor.copy(alpha = 0.30f)
    val fillBottom = lineColor.copy(alpha = 0.0f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val midY = h / 2f

        // 0 dB center grid line + +/-6 dB lines
        drawLine(gridColor, Offset(0f, midY), Offset(w, midY), strokeWidth = 1.dp.toPx())
        drawLine(gridColor.copy(alpha = 0.25f), Offset(0f, h * 0.25f), Offset(w, h * 0.25f), strokeWidth = 1.dp.toPx())
        drawLine(gridColor.copy(alpha = 0.25f), Offset(0f, h * 0.75f), Offset(w, h * 0.75f), strokeWidth = 1.dp.toPx())

        if (bandsDb.size < 2) return@Canvas

        val points = bandsDb.mapIndexed { idx, db ->
            val x = (idx.toFloat() / (bandsDb.size - 1).toFloat()) * w
            val norm = (1f - (db.coerceIn(-12f, 12f) + 12f) / 24f)
            val y = (norm * (h - 24.dp.toPx())) + 12.dp.toPx()
            Offset(x, y)
        }

        val curvePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 0 until points.lastIndex) {
                val p0 = points[i]
                val p1 = points[i + 1]
                val controlX = (p0.x + p1.x) / 2f
                cubicTo(controlX, p0.y, controlX, p1.y, p1.x, p1.y)
            }
        }

        val fillPath = Path().apply {
            addPath(curvePath)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(listOf(fillTop, fillBottom))
        )

        drawPath(
            path = curvePath,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        points.forEach { pt ->
            drawCircle(color = lineColor, radius = 4.dp.toPx(), center = pt)
        }
    }
}

@Composable
private fun LabeledSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = valueText,
                style = ExpressiveTypography.MonoTimecode,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled
        )
    }
}
