package com.example.ui.screens.pro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.domain.model.Song
import kotlinx.collections.immutable.ImmutableList

/**
 * #40 Biometric Hidden Vault:
 * Protects hidden songs/folders behind BiometricPrompt + indexed SQLite `isHidden` flag.
 */
@Composable
fun HiddenVaultDialog(
    hiddenSongs: ImmutableList<Song>,
    onPlaySong: (Song, List<Song>) -> Unit,
    onUnhideSong: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var unlocked by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        BiometricVaultHelper.authenticate(
            context = context,
            onSuccess = {
                unlocked = true
                authError = null
            },
            onError = { err -> authError = err }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Bóveda Biométrica", style = MaterialTheme.typography.headlineSmall)
                }

                if (!unlocked) {
                    Text(
                        text = authError ?: "Esperando autenticación biométrica...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (authError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            BiometricVaultHelper.authenticate(
                                context = context,
                                onSuccess = {
                                    unlocked = true
                                    authError = null
                                },
                                onError = { err -> authError = err }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.LockOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Desbloquear Bóveda")
                    }
                } else {
                    Text(
                        text = "${hiddenSongs.size} pistas ocultas protegidas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (hiddenSongs.isEmpty()) {
                            item {
                                Text(
                                    "No tienes canciones ocultas. Usa el menú de tres puntos en cualquier canción para moverla aquí.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            items(hiddenSongs, key = { it.id }) { song ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onPlaySong(song, hiddenSongs) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(song.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                        }
                                        IconButton(onClick = { onUnhideSong(song.id) }) {
                                            Icon(Icons.Filled.Visibility, contentDescription = "Restaurar a biblioteca")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cerrar") }
                }
            }
        }
    }
}
