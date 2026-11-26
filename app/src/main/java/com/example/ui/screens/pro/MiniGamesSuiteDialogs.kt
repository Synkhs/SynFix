package com.example.ui.screens.pro

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.lyrics.LrcParser
import com.example.domain.model.Song
import com.example.domain.model.TrophyItem
import com.example.ui.components.ArtworkImage
import com.example.ui.components.formatDuration
import com.example.ui.theme.ExpressiveTypography
import com.example.ui.theme.SynFixMotion
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * #61 Lyric Gap Challenge:
 * Uses real synced/embedded lyrics from user's library, blanks out a keyword,
 * and offers 4 options from the library vocabulary.
 */
@Composable
fun LyricGapGameDialog(
    songs: ImmutableList<Song>,
    onPlaySong: (Song) -> Unit,
    onDismiss: () -> Unit
) {
    val songsWithLyrics = remember(songs) {
        songs.filter { !it.embeddedLyrics.isNullOrBlank() }
    }
    var round by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var answeredWord by remember { mutableStateOf<String?>(null) }

    val challenge = remember(songsWithLyrics, round) {
        val pickedSong = songsWithLyrics.shuffled().firstOrNull()
        val lines = pickedSong?.let { LrcParser.parse(it.embeddedLyrics.orEmpty()) }.orEmpty()
            .filter { it.text.split(' ').count { w -> w.length >= 4 } >= 3 }
        if (pickedSong != null && lines.isNotEmpty()) {
            val line = lines.random()
            val words = line.text.split(' ')
            val targetWordRaw = words.filter { it.replace(Regex("[^\\p{L}]"), "").length >= 4 }.random()
            val cleanTarget = targetWordRaw.replace(Regex("[^\\p{L}]"), "")
            val masked = line.text.replaceFirst(targetWordRaw, "______")
            val distractors = lines.flatMap { it.text.split(' ') }
                .map { it.replace(Regex("[^\\p{L}]"), "") }
                .filter { it.length >= 4 && !it.equals(cleanTarget, true) }
                .distinctBy { it.lowercase() }
                .shuffled()
                .take(3)
                .ifEmpty { listOf("Noche", "Fuego", "Luz") }
            val options = (distractors + cleanTarget).distinctBy { it.lowercase() }.shuffled()
            Triple(pickedSong, masked, cleanTarget to options)
        } else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lyric Gap • Puntos: $score") },
        text = {
            if (challenge == null) {
                Text("Añade letras (.lrc o etiquetas) a al menos una canción para jugar Lyric Gap.")
            } else {
                val (song, maskedLine, answerPair) = challenge
                val (correctWord, options) = answerPair
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${song.title} — ${song.artist}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Text(
                            text = "\"$maskedLine\"",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                    options.forEach { opt ->
                        val isPicked = answeredWord == opt
                        val isCorrect = opt.equals(correctWord, true)
                        Button(
                            onClick = {
                                if (answeredWord == null) {
                                    answeredWord = opt
                                    if (isCorrect) score += 10
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when {
                                    answeredWord == null -> MaterialTheme.colorScheme.secondaryContainer
                                    isCorrect -> MaterialTheme.colorScheme.primary
                                    isPicked -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                contentColor = when {
                                    answeredWord == null -> MaterialTheme.colorScheme.onSecondaryContainer
                                    isCorrect -> MaterialTheme.colorScheme.onPrimary
                                    isPicked -> MaterialTheme.colorScheme.onError
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(opt)
                        }
                    }
                    if (answeredWord != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onPlaySong(song) }, modifier = Modifier.weight(1f)) {
                                Text("Escuchar")
                            }
                            Button(
                                onClick = {
                                    answeredWord = null
                                    round++
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Siguiente")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

/**
 * #62 Tap-the-Beat Arcade:
 * Uses precalculated beat peak timestamps from EnergyAndBeatAnalyzer for a 120 FPS rhythm pad.
 */
@Composable
fun TapBeatArcadeDialog(
    currentSong: Song?,
    positionMsProvider: () -> Long,
    onEnsureAnalyzed: suspend (Song) -> Song,
    onPlaySong: (Song) -> Unit,
    onDismiss: () -> Unit
) {
    var analyzedSong by remember(currentSong?.id) { mutableStateOf(currentSong) }
    var score by remember { mutableIntStateOf(0) }
    var combo by remember { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf("Toca al ritmo del pulso") }
    val pulseScale = remember { Animatable(1f) }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentSong?.id) {
        if (currentSong != null) {
            analyzedSong = onEnsureAnalyzed(currentSong)
        }
    }

    val peaks = analyzedSong?.peakTimestampsMs.orEmpty()
    val posMs = positionMsProvider()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tap-the-Beat Arcade") },
        text = {
            if (analyzedSong == null) {
                Text("Selecciona una pista de tu biblioteca para iniciar el arcade rítmico.")
            } else {
                val nearestDelta = remember(posMs, peaks) {
                    if (peaks.isEmpty()) 250L
                    else peaks.minOf { abs(it.toLong() - posMs) }
                }
                val inWindow = nearestDelta <= 145L

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "${analyzedSong!!.title} (${analyzedSong!!.bpm} BPM)",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("PUNTOS", style = MaterialTheme.typography.labelSmall)
                            Text("$score", style = ExpressiveTypography.MonoTimecode, color = MaterialTheme.colorScheme.primary)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("COMBO", style = MaterialTheme.typography.labelSmall)
                            Text("x$combo", style = ExpressiveTypography.MonoTimecode, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }

                    // 120 FPS draw-phase Beat Target Pad
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .graphicsLayer {
                                scaleX = pulseScale.value
                                scaleY = pulseScale.value
                            }
                            .background(
                                color = if (inWindow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(36.dp)
                            )
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch {
                                    pulseScale.snapTo(1.16f)
                                    pulseScale.animateTo(1f, SynFixMotion.fastSpatialSpring())
                                }
                                val delta = if (peaks.isEmpty()) 80L else peaks.minOf { abs(it.toLong() - positionMsProvider()) }
                                when {
                                    delta <= 95L -> {
                                        combo++
                                        score += 100 + combo * 15
                                        feedback = "¡PERFECTO! (±${delta}ms)"
                                    }
                                    delta <= 190L -> {
                                        combo++
                                        score += 50 + combo * 5
                                        feedback = "BIEN (±${delta}ms)"
                                    }
                                    else -> {
                                        combo = 0
                                        feedback = "Fuera de tiempo (${delta}ms)"
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (inWindow) "¡AHORA!" else "TAP",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (inWindow) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Text(feedback, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    OutlinedButton(onClick = { onPlaySong(analyzedSong!!) }) {
                        Text("Reproducir pista si está pausada")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

/**
 * #63 Track Bracket 1v1:
 * 8-track local knockout tournament to crown the user's session champion.
 */
@Composable
fun TrackBracketDialog(
    songs: ImmutableList<Song>,
    onPlaySong: (Song) -> Unit,
    onCreateChampionPlaylist: (Song) -> Unit,
    onDismiss: () -> Unit
) {
    val initialPool = remember(songs) { songs.shuffled().take(8) }
    var currentRoundPool by remember(initialPool) { mutableStateOf(initialPool) }
    var nextRoundWinners by remember { mutableStateOf<List<Song>>(emptyList()) }
    var pairIdx by remember { mutableIntStateOf(0) }
    var champion by remember { mutableStateOf<Song?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Torneo Track Bracket 1v1") },
        text = {
            when {
                initialPool.size < 2 -> Text("Necesitas al menos 2 canciones locales para armar el torneo.")
                champion != null -> {
                    val champ = champion!!
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                        Text("Campeona de la Sesión", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        ArtworkImage(champ.artworkUri, champ.title, 120.dp, modifier = Modifier.size(120.dp))
                        Text(champ.title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        Text(champ.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onPlaySong(champ) }, modifier = Modifier.weight(1f)) { Text("Reproducir") }
                            FilledTonalButton(onClick = { onCreateChampionPlaylist(champ) }, modifier = Modifier.weight(1f)) { Text("Guardar") }
                        }
                    }
                }
                else -> {
                    val left = currentRoundPool.getOrNull(pairIdx * 2)
                    val right = currentRoundPool.getOrNull(pairIdx * 2 + 1)
                    if (left != null && right != null) {
                        val roundTitle = when (currentRoundPool.size) {
                            2 -> "GRAN FINAL"
                            in 3..4 -> "SEMIFINAL"
                            else -> "CUARTOS DE FINAL"
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("$roundTitle • Duelo ${pairIdx + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            listOf(left, right).forEach { contender ->
                                ElevatedCard(
                                    onClick = {
                                        val updatedWinners = nextRoundWinners + contender
                                        val nextPair = pairIdx + 1
                                        if (nextPair * 2 + 1 < currentRoundPool.size) {
                                            nextRoundWinners = updatedWinners
                                            pairIdx = nextPair
                                        } else {
                                            val carried = if (currentRoundPool.size % 2 == 1) {
                                                updatedWinners + currentRoundPool.last()
                                            } else updatedWinners
                                            if (carried.size == 1) {
                                                champion = carried.first()
                                            } else {
                                                currentRoundPool = carried
                                                nextRoundWinners = emptyList()
                                                pairIdx = 0
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        ArtworkImage(contender.artworkUri, contender.title, 48.dp, modifier = Modifier.size(48.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(contender.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(contender.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                        }
                                        IconButton(onClick = { onPlaySong(contender) }) {
                                            Icon(Icons.Filled.PlayArrow, contentDescription = "Escuchar")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

/**
 * #64 Music Bingo Offline:
 * Generates a 3x3 local Bingo card from artists/genres; play & mark matching cells.
 */
@Composable
fun MusicBingoDialog(
    songs: ImmutableList<Song>,
    currentSong: Song?,
    onPlayRandom: () -> Unit,
    onDismiss: () -> Unit
) {
    val cells = remember(songs) {
        val tags = (songs.map { it.artist } + songs.map { it.genre })
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals("Unknown", true) }
            .distinct()
            .shuffled()
            .take(9)
        tags.ifEmpty { listOf("SynFix", "Hi-Fi", "Local", "Rock", "Pop", "Electro", "Jazz", "Indie", "Bass") }
    }
    val marked = remember(cells) { mutableStateListOf<Boolean>().apply { repeat(cells.size) { add(false) } } }
    val bingoWon = remember(marked.toList()) {
        marked.size == 9 && (
            (marked[0] && marked[1] && marked[2]) ||
                (marked[3] && marked[4] && marked[5]) ||
                (marked[6] && marked[7] && marked[8]) ||
                (marked[0] && marked[3] && marked[6]) ||
                (marked[1] && marked[4] && marked[7]) ||
                (marked[2] && marked[5] && marked[8])
            )
    }

    LaunchedEffect(currentSong?.id) {
        if (currentSong != null) {
            cells.forEachIndexed { idx, label ->
                if (currentSong.artist.equals(label, true) || currentSong.genre.equals(label, true)) {
                    marked[idx] = true
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (bingoWon) "¡BINGO MUSICAL!" else "Music Bingo Offline") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (currentSong != null) "Sonando: ${currentSong.artist} (${currentSong.genre})" else "Reproduce pistas aleatorias para marcar tu cartón",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                for (row in 0 until 3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        for (col in 0 until 3) {
                            val idx = row * 3 + col
                            if (idx < cells.size) {
                                val isMarked = marked[idx]
                                Surface(
                                    onClick = { marked[idx] = !isMarked },
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isMarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.weight(1f).height(64.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
                                        Text(
                                            text = cells[idx],
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isMarked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Button(onClick = onPlayRandom, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Shuffle, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sacar pista aleatoria")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

/**
 * #65 Drop Reflex Test:
 * Uses precalculated dropTimestampMs; user races to tap right when the drop hits.
 */
@Composable
fun DropReflexDialog(
    songs: ImmutableList<Song>,
    onEnsureAnalyzed: suspend (Song) -> Song,
    onSeekAndPlay: (Song, Long) -> Unit,
    onDismiss: () -> Unit
) {
    var targetSong by remember(songs) { mutableStateOf(songs.shuffled().firstOrNull()) }
    var dropMs by remember { mutableLongStateOf(30_000L) }
    var phaseText by remember { mutableStateOf("Preparando pista...") }
    var gameStartElapsed by remember { mutableLongStateOf(0L) }
    var armed by remember { mutableStateOf(false) }
    var resultMs by remember { mutableStateOf<Long?>(null) }

    val startLeadMs = 5_000L

    LaunchedEffect(targetSong?.id) {
        val s = targetSong ?: return@LaunchedEffect
        val analyzed = onEnsureAnalyzed(s)
        dropMs = analyzed.dropTimestampMs.coerceAtLeast(6_000L)
        val preDrop = (dropMs - startLeadMs).coerceAtLeast(0L)
        resultMs = null
        armed = true
        gameStartElapsed = android.os.SystemClock.elapsedRealtime()
        phaseText = "¡Escucha la subida! Pulsa justo en el DROP..."
        onSeekAndPlay(analyzed, preDrop)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Drop Reflex Test") },
        text = {
            if (targetSong == null) {
                Text("Necesitas canciones en tu biblioteca para jugar Drop Reflex.")
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(targetSong!!.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Drop calculado en ${formatDuration(dropMs)}", style = ExpressiveTypography.MonoTimecode, color = MaterialTheme.colorScheme.primary)
                    Text(phaseText, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)

                    Button(
                        onClick = {
                            if (armed) {
                                val elapsed = android.os.SystemClock.elapsedRealtime() - gameStartElapsed
                                val diff = abs(elapsed - startLeadMs)
                                resultMs = diff
                                armed = false
                                phaseText = when {
                                    diff <= 150L -> "¡REFLEJOS ÉLITE! Desviación ±${diff}ms"
                                    diff <= 400L -> "¡Muy bien! Desviación ±${diff}ms"
                                    else -> "Casi (${diff}ms de margen)"
                                }
                            }
                        },
                        enabled = armed,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.fillMaxWidth().height(72.dp)
                    ) {
                        Text("¡DROP AHORA!", style = MaterialTheme.typography.titleMedium)
                    }

                    if (resultMs != null) {
                        OutlinedButton(
                            onClick = { targetSong = songs.shuffled().firstOrNull() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Otro reto aleatorio")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

/**
 * #66 Artist Link Chain:
 * Relational Room traversal across collaborations (feat./ft./&).
 */
@Composable
fun ArtistLinkChainDialog(
    songs: ImmutableList<Song>,
    candidates: ImmutableList<Song>,
    onFindNext: (Song, Set<Long>) -> Unit,
    onPlaySong: (Song) -> Unit,
    onDismiss: () -> Unit
) {
    val chain = remember { mutableStateListOf<Song>() }

    LaunchedEffect(songs.size) {
        if (chain.isEmpty() && songs.isNotEmpty()) {
            val start = songs.firstOrNull { it.artist.contains("feat", true) || it.artist.contains("&") } ?: songs.random()
            chain.add(start)
            onFindNext(start, setOf(start.id))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Artist Link Chain • ${chain.size} eslabones") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val current = chain.lastOrNull()
                if (current == null) {
                    Text("No hay canciones indexadas.")
                } else {
                    Text("Pista actual: ${current.title} (${current.artist})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    if (candidates.isEmpty()) {
                        Text(
                            "No se hallaron más colaboraciones directas desde aquí. ¡Prueba otra pista inicial!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                val next = songs.random()
                                chain.clear()
                                chain.add(next)
                                onFindNext(next, setOf(next.id))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Reiniciar cadena") }
                    } else {
                        Text("Elige el siguiente eslabón conectado:", style = MaterialTheme.typography.labelMedium)
                        LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                            items(candidates, key = { it.id }) { cand ->
                                ListItem(
                                    headlineContent = { Text(cand.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    supportingContent = { Text(cand.artist, maxLines = 1) },
                                    trailingContent = {
                                        IconButton(onClick = { onPlaySong(cand) }) {
                                            Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir")
                                        }
                                    },
                                    modifier = Modifier.clickable {
                                        chain.add(cand)
                                        onFindNext(cand, chain.map { it.id }.toSet())
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

/**
 * #67 Chaos Library Dice 3D:
 * Animated 3D graphicsLayer die that launches a smart local mix on roll.
 */
@Composable
fun ChaosDice3dDialog(
    songs: ImmutableList<Song>,
    onPlayMix: (Song, List<Song>) -> Unit,
    onDismiss: () -> Unit
) {
    val rotX = remember { Animatable(0f) }
    val rotY = remember { Animatable(0f) }
    var outcomeLabel by remember { mutableStateOf("Toca el dado para lanzar el caos") }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    val primary = MaterialTheme.colorScheme.primaryContainer
    val onPrimary = MaterialTheme.colorScheme.onPrimaryContainer
    val dotColor = MaterialTheme.colorScheme.primary

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chaos Library Dice 3D") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .graphicsLayer {
                            rotationX = rotX.value
                            rotationY = rotY.value
                            cameraDistance = 12f * density
                        }
                        .clickable {
                            if (songs.isEmpty()) return@clickable
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch {
                                launch { rotX.animateTo(rotX.value + 540f, tween(650)) }
                                rotY.animateTo(rotY.value + 720f, tween(650))
                                val modes = listOf("Alta Energía (>120 BPM)", "Deep Chill", "Olvidadas (0 plays)", "Favoritas Mix", "Caos Total")
                                val pickedMode = modes.random()
                                val pool = when (pickedMode) {
                                    "Alta Energía (>120 BPM)" -> songs.filter { it.bpm >= 118 }.ifEmpty { songs }
                                    "Deep Chill" -> songs.filter { it.energyScore < 0.55f }.ifEmpty { songs }
                                    "Olvidadas (0 plays)" -> songs.filter { it.playCount == 0 }.ifEmpty { songs }
                                    "Favoritas Mix" -> songs.filter { it.isFavorite }.ifEmpty { songs }
                                    else -> songs
                                }.shuffled()
                                outcomeLabel = "Resultado: $pickedMode (${pool.size} pistas)"
                                onPlayMix(pool.first(), pool)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRoundRect(
                            color = primary,
                            cornerRadius = CornerRadius(28.dp.toPx(), 28.dp.toPx()),
                            size = Size(size.width, size.height)
                        )
                        val r = 10.dp.toPx()
                        val pts = listOf(
                            Offset(size.width * 0.28f, size.height * 0.28f),
                            Offset(size.width * 0.72f, size.height * 0.28f),
                            Offset(size.width * 0.50f, size.height * 0.50f),
                            Offset(size.width * 0.28f, size.height * 0.72f),
                            Offset(size.width * 0.72f, size.height * 0.72f)
                        )
                        pts.forEach { drawCircle(color = dotColor, radius = r, center = it) }
                    }
                }
                Text(outcomeLabel, style = MaterialTheme.typography.titleSmall, color = onPrimary, textAlign = TextAlign.Center)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

/**
 * #68 Offline Trophy Cabinet:
 * Persistent Room achievement board unlocked automatically from local activity.
 */
@Composable
fun TrophyCabinetDialog(
    trophies: ImmutableList<TrophyItem>,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            val unlockedCount = trophies.count { it.unlocked }
            Text("Vitrina de Trofeos ($unlockedCount/${trophies.size})")
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(trophies, key = { it.id }) { item ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (item.unlocked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    imageVector = if (item.unlocked) Icons.Filled.EmojiEvents else Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = if (item.unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, style = MaterialTheme.typography.titleSmall)
                                    Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("${item.progress}/${item.target}", style = ExpressiveTypography.MonoTimecode)
                            }
                            LinearProgressIndicator(
                                progress = { (item.progress.toFloat() / item.target.coerceAtLeast(1)).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(4.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}
