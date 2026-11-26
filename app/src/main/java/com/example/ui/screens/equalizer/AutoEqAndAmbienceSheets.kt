package com.example.ui.screens.equalizer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.AmbienceMixState
import com.example.domain.model.AutoEqProfile
import com.example.domain.model.BluetoothMacProfile
import com.example.domain.model.EqualizerPreset
import com.example.ui.theme.ExpressiveTypography
import kotlinx.collections.immutable.ImmutableList
import kotlin.math.roundToInt

/**
 * #54 AutoEQ Headphone Calibration Sheet:
 * Searchable picker over 24+ bundled AutoEQ measurement curves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoEqPickerSheet(
    profiles: ImmutableList<AutoEqProfile>,
    activePresetId: String,
    onSelectProfile: (AutoEqProfile) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(profiles, query) {
        if (query.isBlank()) profiles
        else profiles.filter {
            it.model.contains(query, ignoreCase = true) || it.category.contains(query, ignoreCase = true)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Filled.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text("Calibración AutoEQ", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Curvas paramétricas de referencia para modelos populares",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar modelo (Sony, Sennheiser, AirPods...)") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            )

            LazyColumn(
                modifier = Modifier.heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filtered, key = { it.id }, contentType = { "autoeq" }) { profile ->
                    val selected = profile.id == activePresetId
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectProfile(profile)
                                onDismiss()
                            }
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(profile.model, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Text("${profile.category} • Preamp ${String.format("%+.1f dB", profile.preampDb)}")
                            },
                            trailingContent = {
                                if (selected) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = "Activo", tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}

/**
 * #52 Focus & Sleep Ambience 5-Channel Procedural Mixer Sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmbienceMixerSheet(
    state: AmbienceMixState,
    standaloneRunning: Boolean,
    onUpdate: (AmbienceMixState) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("Mezclador Focus & Sleep", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (standaloneRunning) "Síntesis independiente (0 MB archivos)"
                            else "Síntesis procedural en RAM mezclada en vivo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = state.enabled,
                    onCheckedChange = { onUpdate(state.copy(enabled = it)) }
                )
            }

            AmbienceSliderRow("Lluvia Suave", state.rainLevel) { onUpdate(state.copy(rainLevel = it)) }
            AmbienceSliderRow("Chimenea Cálida", state.fireLevel) { onUpdate(state.copy(fireLevel = it)) }
            AmbienceSliderRow("Ruido Rosa (Focus)", state.pinkNoiseLevel) { onUpdate(state.copy(pinkNoiseLevel = it)) }
            AmbienceSliderRow("Binaural 40 Hz (Gamma)", state.binaural40HzLevel) { onUpdate(state.copy(binaural40HzLevel = it)) }
            AmbienceSliderRow("Binaural 4 Hz (Sueño Theta)", state.binaural4HzLevel) { onUpdate(state.copy(binaural4HzLevel = it)) }
        }
    }
}

@Composable
private fun AmbienceSliderRow(
    label: String,
    value: Float,
    onChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("${(value * 100).roundToInt()}%", style = ExpressiveTypography.MonoTimecode, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onChange, valueRange = 0f..1f)
    }
}

/**
 * #82 Per-MAC Bluetooth Auto-Profile Dialog.
 */
@Composable
fun BluetoothMacProfilesDialog(
    connectedDevice: Pair<String, String>?,
    profiles: ImmutableList<BluetoothMacProfile>,
    presets: List<EqualizerPreset>,
    onSaveProfile: (BluetoothMacProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var macInput by remember(connectedDevice) { mutableStateOf(connectedDevice?.first ?: "AA:BB:CC:DD:EE:01") }
    var nameInput by remember(connectedDevice) { mutableStateOf(connectedDevice?.second ?: "Mis Auriculares BT") }
    var selectedPresetId by remember { mutableStateOf(presets.firstOrNull()?.id ?: "bass_boost") }
    var initialVolume by remember { mutableFloatStateOf(65f) }
    var presetDropdownOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Perfiles Bluetooth por MAC") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (connectedDevice != null) {
                    AssistChip(
                        onClick = {},
                        leadingIcon = { Icon(Icons.Filled.BluetoothConnected, contentDescription = null) },
                        label = { Text("Conectado: ${connectedDevice.second}") }
                    )
                }
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Nombre del dispositivo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = macInput,
                    onValueChange = { macInput = it },
                    label = { Text("Dirección MAC / ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Box {
                    val presetName = presets.find { it.id == selectedPresetId }?.name ?: selectedPresetId
                    OutlinedButton(onClick = { presetDropdownOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("EQ Automático: $presetName")
                    }
                    DropdownMenu(expanded = presetDropdownOpen, onDismissRequest = { presetDropdownOpen = false }) {
                        presets.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset.name) },
                                onClick = {
                                    selectedPresetId = preset.id
                                    presetDropdownOpen = false
                                }
                            )
                        }
                    }
                }
                Text("Volumen inicial seguro: ${initialVolume.roundToInt()}%", style = MaterialTheme.typography.labelLarge)
                Slider(value = initialVolume, onValueChange = { initialVolume = it }, valueRange = 10f..100f)

                Button(
                    onClick = {
                        if (macInput.isNotBlank()) {
                            onSaveProfile(
                                BluetoothMacProfile(
                                    macAddress = macInput.trim(),
                                    deviceName = nameInput.ifBlank { "Bluetooth Audio" },
                                    eqPresetId = selectedPresetId,
                                    initialVolumePercent = initialVolume.roundToInt()
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Guardar perfil para este dispositivo")
                }

                if (profiles.isNotEmpty()) {
                    HorizontalDivider()
                    LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                        items(profiles, key = { it.macAddress }) { prof ->
                            ListItem(
                                headlineContent = { Text(prof.deviceName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text("${prof.macAddress} • ${prof.initialVolumePercent}% • ${prof.eqPresetId}") },
                                trailingContent = {
                                    IconButton(onClick = { onDeleteProfile(prof.macAddress) }) {
                                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Eliminar")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}
