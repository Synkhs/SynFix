package com.example.ui.screens.pro

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.domain.model.ArtistAliasGroup
import kotlinx.collections.immutable.ImmutableList

/**
 * #39 Virtual Artist Alias & Batch Cleaner:
 * Detects near-duplicate artist variants & whitespace tags, unifying them inside a single Room Transaction.
 */
@Composable
fun ArtistBatchCleanerDialog(
    aliasGroups: ImmutableList<ArtistAliasGroup>,
    onScanAliases: () -> Unit,
    onMergeAlias: (List<String>, String) -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) { onScanAliases() }

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
                    Icon(Icons.Filled.CleaningServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Alias & Limpiador Lote", style = MaterialTheme.typography.headlineSmall)
                }

                Text(
                    "Unifica variantes de artistas y limpia espacios sobrantes en una transacción atómica SQL.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (aliasGroups.isEmpty()) {
                        item {
                            Text(
                                "No se detectaron alias duplicados pendientes. Puedes ejecutar limpieza de espacios igualmente.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(aliasGroups, key = { it.canonicalName + it.variants.joinToString() }) { group ->
                            var targetName by remember(group.canonicalName) { mutableStateOf(group.canonicalName) }
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Variantes (${group.songCount} pistas): ${group.variants.joinToString(" | ")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedTextField(
                                        value = targetName,
                                        onValueChange = { targetName = it },
                                        label = { Text("Nombre unificado") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    FilledTonalButton(
                                        onClick = { onMergeAlias(group.variants, targetName) },
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Icon(Icons.Filled.Merge, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Unificar")
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(onClick = { onMergeAlias(emptyList(), "") }) {
                        Text("Limpiar Espacios")
                    }
                    TextButton(onClick = onDismiss) { Text("Cerrar") }
                }
            }
        }
    }
}
