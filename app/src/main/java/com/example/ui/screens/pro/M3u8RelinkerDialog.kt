package com.example.ui.screens.pro

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.domain.model.Song
import com.example.player.M3u8RelinkResult
import com.example.ui.theme.ExpressiveTypography

/**
 * #41 Smart .m3u8 Playlist Relinker:
 * SAF import/export with fast fingerprint relinking (filename + duration + normalized title).
 */
@Composable
fun M3u8RelinkerDialog(
    librarySongs: List<Song>,
    lastResult: M3u8RelinkResult?,
    onImportM3u8: (Uri, String) -> Unit,
    onExportM3u8: (Uri, List<Song>) -> Unit,
    onDismiss: () -> Unit
) {
    var playlistName by remember { mutableStateOf("Playlist Relink SynFix") }

    val openM3u8Launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportM3u8(uri, playlistName)
        }
    }

    val createM3u8Launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri ->
        if (uri != null) {
            onExportM3u8(uri, librarySongs)
        }
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
                    Icon(Icons.Filled.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Relinker .m3u8 Inteligente", style = MaterialTheme.typography.headlineSmall)
                }

                Text(
                    "Importa o exporta listas UTF-8 .m3u8 usando SAF y re-vincula archivos movidos por duración + título.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text("Nombre de playlist al importar") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { openM3u8Launcher.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Importar .m3u8")
                    }
                    FilledTonalButton(
                        onClick = { createM3u8Launcher.launch("synfix_library.m3u8") },
                        enabled = librarySongs.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Exportar")
                    }
                }

                lastResult?.let { res ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(res.playlistName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Exactas: ${res.exactMatches} | Re-vinculadas: ${res.fingerprintRelinked} | Sin hallar: ${res.unmatchedCount}",
                                style = ExpressiveTypography.MonoTimecode,
                                color = MaterialTheme.colorScheme.primary
                            )
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
