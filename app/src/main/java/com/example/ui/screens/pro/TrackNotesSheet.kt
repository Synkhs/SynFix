package com.example.ui.screens.pro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.domain.model.TrackNote
import com.example.ui.components.formatDuration
import com.example.ui.theme.ExpressiveTypography
import kotlinx.collections.immutable.ImmutableList

/**
 * #19 Timestamped Track Notes:
 * Manage personal per-second notes saved in Room and pinned on the wavy seekbar Canvas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackNotesSheet(
    songTitle: String,
    currentPositionMs: Long,
    notes: ImmutableList<TrackNote>,
    onAddNote: (timestampMs: Long, text: String) -> Unit,
    onSeekToNote: (Long) -> Unit,
    onDeleteNote: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var noteInput by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Filled.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Column {
                    Text("Notas por Segundo", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = songTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("Nota en ${formatDuration(currentPositionMs)}") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.weight(1f)
                )
                FilledIconButton(
                    onClick = {
                        if (noteInput.isNotBlank()) {
                            onAddNote(currentPositionMs, noteInput)
                            noteInput = ""
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.AddComment, contentDescription = "Añadir nota")
                }
            }

            HorizontalDivider()

            if (notes.isEmpty()) {
                Text(
                    text = "Sin marcadores todavía. Añade una nota en el segundo exacto que quieras recordar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(items = notes, key = { it.id }) { note ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSeekToNote(note.timestampMs) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Text(
                                        text = formatDuration(note.timestampMs),
                                        style = ExpressiveTypography.MonoTimecode,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Text(
                                    text = note.noteText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { onDeleteNote(note.id) }) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Eliminar")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
