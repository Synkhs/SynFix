package com.example.ui.screens.pro

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.domain.model.WrappedStats
import com.example.ui.theme.ExpressiveTypography

/**
 * #33 Live Local Wrapped & Streaks:
 * Reads precalculated Room summary tables (WrappedSummaryEntity & ArtistStatsEntity).
 */
@Composable
fun WrappedDialog(
    stats: WrappedStats,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Tu Wrapped Local", style = MaterialTheme.typography.headlineSmall)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    WrappedMetricTile(
                        label = "Racha",
                        value = "${stats.streakDays} días",
                        icon = Icons.Filled.LocalFireDepartment,
                        modifier = Modifier.weight(1f)
                    )
                    WrappedMetricTile(
                        label = "Minutos",
                        value = "${stats.totalMinutes}",
                        icon = Icons.Filled.Timer,
                        modifier = Modifier.weight(1f)
                    )
                    WrappedMetricTile(
                        label = "Reprod.",
                        value = "${stats.totalPlays}",
                        icon = Icons.Filled.MusicNote,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (stats.topArtists.isNotEmpty()) {
                    Text("Artistas Top", style = MaterialTheme.typography.titleMedium)
                    stats.topArtists.take(5).forEachIndexed { idx, item ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("#${idx + 1}", style = ExpressiveTypography.MonoTimecode, color = MaterialTheme.colorScheme.primary)
                                    Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text(item.artistName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(
                                    "${item.playCount}x • ${item.totalMinutes}m",
                                    style = ExpressiveTypography.MonoTimecode,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (stats.topSongs.isNotEmpty()) {
                    Text("Pistas Más Escuchadas", style = MaterialTheme.typography.titleMedium)
                    stats.topSongs.forEachIndexed { idx, s ->
                        Text(
                            text = "${idx + 1}. ${s.title} — ${s.artist} (${s.playCount}x)",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        "Reproduce canciones en SynFix para generar tus rachas y estadísticas locales en tiempo real.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = onDismiss) { Text("Cerrar") }
                }
            }
        }
    }
}

@Composable
private fun WrappedMetricTile(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(value, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
