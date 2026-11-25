package com.example.ui.screens.pro

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.domain.model.PartySyncMode
import com.example.domain.model.PartySyncState
import com.example.ui.theme.ExpressiveTypography

/**
 * #35 Local Party Sync Sheet:
 * Host / Guest UDP timestamp synchronization with +/-1% speed micro-correction.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartySyncSheet(
    state: PartySyncState,
    onStartHost: () -> Unit,
    onStartGuest: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Filled.WifiTethering, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text("Local Party Sync (UDP)", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Sincroniza múltiples dispositivos por Wi-Fi ajustando velocidad ±1%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Estado: ${state.mode.label}", style = MaterialTheme.typography.titleSmall)
                    Text(state.statusLabel, style = MaterialTheme.typography.bodySmall)
                    if (state.mode == PartySyncMode.GUEST) {
                        Text(
                            text = "Deriva: ${state.lastDriftMs} ms • Velocidad: ${String.format("%.2fx", state.speedCorrection)}",
                            style = ExpressiveTypography.MonoTimecode,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onStartHost,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Podcasts, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Anfitrión")
                }
                FilledTonalButton(
                    onClick = onStartGuest,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Hub, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Invitado")
                }
            }

            if (state.mode != PartySyncMode.OFF) {
                OutlinedButton(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Detener Party Sync")
                }
            }
        }
    }
}
