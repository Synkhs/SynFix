package com.example.ui.screens.lyrics

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.lyrics.LrcParser
import com.example.domain.model.LyricLine
import com.example.ui.theme.ExpressiveTypography
import com.example.ui.theme.SynFixMotion
import kotlinx.collections.immutable.ImmutableList

@Composable
fun SyncedLyricsView(
    lyrics: ImmutableList<LyricLine>,
    positionProvider: () -> Long,
    onSeekToLine: (Long) -> Unit,
    onOpenLyricsEditor: () -> Unit,
    modifier: Modifier = Modifier,
    centeredText: Boolean = false
) {
    val currentPos = positionProvider()
    val activeIndex = remember(lyrics, currentPos) {
        LrcParser.findActiveLineIndex(lyrics, currentPos)
    }

    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && activeIndex < lyrics.size) {
            listState.animateScrollToItem(
                index = activeIndex,
                scrollOffset = -180
            )
        }
    }

    if (lyrics.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Lyrics,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No se encontraron letras sincronizadas (.lrc)",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Coloca un archivo .lrc junto a la canción o pega las letras manualmente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                FilledTonalButton(onClick = onOpenLyricsEditor) {
                    Icon(Icons.Filled.EditNote, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Añadir / Editar LRC")
                }
            }
        }
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 80.dp, horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(
                    items = lyrics,
                    key = { idx, item -> "${idx}_${item.timestampMs}" },
                    contentType = { _, _ -> "lyric_line" }
                ) { index, line ->
                    val isActive = index == activeIndex
                    val scale by animateFloatAsState(
                        targetValue = if (isActive) 1.03f else 0.96f,
                        animationSpec = SynFixMotion.fastSpatialSpring(),
                        label = "lyricScale"
                    )
                    val color by animateColorAsState(
                        targetValue = if (isActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        },
                        animationSpec = SynFixMotion.effectsSpring(),
                        label = "lyricColor"
                    )

                    Text(
                        text = line.text,
                        style = if (isActive) ExpressiveTypography.LyricActive else ExpressiveTypography.LyricInactive,
                        color = color,
                        textAlign = if (centeredText) TextAlign.Center else TextAlign.Start,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSeekToLine(line.timestampMs) }
                            .padding(vertical = 6.dp, horizontal = 10.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                    )
                }
            }

            SmallFloatingActionButton(
                onClick = onOpenLyricsEditor,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
            ) {
                Icon(Icons.Filled.EditNote, contentDescription = "Editar LRC")
            }
        }
    }
}

@Composable
fun LyricsEditorDialog(
    initialRawLyrics: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialRawLyrics) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editor de Letras (LRC)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Formato soportado: [mm:ss.xx] Verso de la canción",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("[00:12.50] Primera línea...\n[00:16.20] Segunda línea...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(text) }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
