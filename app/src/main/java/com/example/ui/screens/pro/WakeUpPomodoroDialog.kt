package com.example.ui.screens.pro

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.domain.model.WakeUpAlarmState
import com.example.ui.components.formatDuration
import com.example.ui.theme.ExpressiveTypography
import kotlin.math.roundToInt

/**
 * #36 Smart Wake-Up & Pomodoro Routines:
 * Exact alarm via AlarmManager.setExactAndAllowWhileIdle + gradual fade-in,
 * plus 25/5 Pomodoro focus music timer.
 */
@Composable
fun WakeUpPomodoroDialog(
    state: WakeUpAlarmState,
    onConfigureWakeUp: (Boolean, Int, Int) -> Unit,
    onTogglePomodoro: () -> Unit,
    onDismiss: () -> Unit
) {
    var hour by remember(state.hour) { mutableIntStateOf(state.hour) }
    var minute by remember(state.minute) { mutableIntStateOf(state.minute) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Wake-Up & Pomodoro", style = MaterialTheme.typography.headlineSmall)

                // Smart Wake-Up Alarm
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Despertador Fade-In", style = MaterialTheme.typography.titleSmall)
                            }
                            Text(
                                text = String.format("%02d:%02d", hour, minute),
                                style = ExpressiveTypography.MonoTimecode,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text("Hora ($hour h)", style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = hour.toFloat(),
                            onValueChange = { hour = it.roundToInt().coerceIn(0, 23) },
                            valueRange = 0f..23f
                        )
                        Text("Minuto ($minute m)", style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = minute.toFloat(),
                            onValueChange = { minute = it.roundToInt().coerceIn(0, 59) },
                            valueRange = 0f..59f
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onConfigureWakeUp(true, hour, minute) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (state.enabled) "Actualizar Alarma" else "Activar Exacta")
                            }
                            if (state.enabled) {
                                OutlinedButton(onClick = { onConfigureWakeUp(false, hour, minute) }) {
                                    Text("Apagar")
                                }
                            }
                        }
                    }
                }

                // Pomodoro Focus Routine
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.Timer, contentDescription = null)
                                Text(
                                    text = if (state.pomodoroIsBreak) "Descanso 5m" else "Pomodoro 25m",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Text(
                                text = formatDuration(state.pomodoroRemainingMs),
                                style = ExpressiveTypography.MonoTimecode,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Button(onClick = onTogglePomodoro) {
                            Text(if (state.pomodoroActive) "Detener" else "Iniciar Focus")
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
