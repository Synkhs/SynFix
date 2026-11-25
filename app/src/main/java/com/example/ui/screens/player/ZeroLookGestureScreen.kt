package com.example.ui.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.Song
import com.example.ui.theme.ExpressiveTypography
import kotlin.math.abs

/**
 * #20 Zero-Look Gesture Mode:
 * Pure #000000 OLED screen with all continuous animations suspended for extreme battery saving
 * in pocket/driving scenarios. Supports blind gestures:
 * - Single tap: Play/Pause
 * - Swipe Left / Right: Next / Previous track
 * - Double tap: Bookmark current second as Track Note
 * - Long press: Exit Zero-Look Mode
 */
@Composable
fun ZeroLookGestureScreen(
    song: Song?,
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onBookmarkNote: () -> Unit,
    onExit: () -> Unit
) {
    BackHandler(onBack = onExit)
    val haptics = LocalHapticFeedback.current
    var feedbackLabel by remember { mutableStateOf("Modo Ciego Activo") }

    Surface(
        color = Color(0xFF000000),
        contentColor = Color(0xFFD6D4E0),
        modifier = Modifier
            .fillMaxSize()
            .testTag("zero_look_screen")
    ) {
        var totalDragX by remember { mutableFloatStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF000000))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            feedbackLabel = if (isPlaying) "Pausado" else "Reproduciendo"
                            onTogglePlayPause()
                        },
                        onDoubleTap = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            feedbackLabel = "Marcador guardado"
                            onBookmarkNote()
                        },
                        onLongPress = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onExit()
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { totalDragX = 0f },
                        onDragEnd = {
                            if (abs(totalDragX) > 100f) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (totalDragX < 0f) {
                                    feedbackLabel = "Siguiente pista"
                                    onNext()
                                } else {
                                    feedbackLabel = "Pista anterior"
                                    onPrevious()
                                }
                            }
                            totalDragX = 0f
                        },
                        onDragCancel = { totalDragX = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            totalDragX += dragAmount
                        }
                    )
                }
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.TouchApp,
                    contentDescription = null,
                    tint = Color(0xFF5C5A68),
                    modifier = Modifier.size(44.dp)
                )

                Text(
                    text = song?.title ?: "SynFix",
                    style = ExpressiveTypography.SongHeroTitle,
                    color = Color(0xFFDFDDE8),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = feedbackLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF9F9CB0)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "1 Toque: Play/Pausa\nDeslizar ← / →: Siguiente / Anterior\n2 Toques: Guardar Nota • Mantener: Salir",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B687A),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
