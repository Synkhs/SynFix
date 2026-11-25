package com.example.player

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow

/**
 * #24 Sub-Bass Haptic Engine:
 * Emits crisp haptic pulses synced with 20-60 Hz sub-bass energy via VibrationEffect.Composition.
 * Automatically disables itself when battery drops below 15%.
 */
class SubBassHapticEngine(private val context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var collectorJob: Job? = null

    private var enabled: Boolean = false
    private var isPlaying: Boolean = false
    private var lastTriggerMs: Long = 0L
    private var prevEnergy: Float = 0f

    fun startObserving(subBassFlow: StateFlow<Float>) {
        if (collectorJob?.isActive == true) return
        collectorJob = scope.launch {
            subBassFlow.collect { energy ->
                if (!enabled || !isPlaying) {
                    prevEnergy = energy
                    return@collect
                }
                val now = System.currentTimeMillis()
                val isTransientPeak = energy > 0.42f && (energy - prevEnergy) > 0.08f
                if (isTransientPeak && now - lastTriggerMs >= 115L && !isLowBattery()) {
                    lastTriggerMs = now
                    triggerSubBassPulse(((energy - 0.3f) / 0.7f).coerceIn(0.25f, 1.0f))
                }
                prevEnergy = energy
            }
        }
    }

    fun updateState(enabled: Boolean, isPlaying: Boolean) {
        this.enabled = enabled
        this.isPlaying = isPlaying
    }

    fun triggerScratchTick() {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)
            ) {
                val comp = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.55f)
                    .compose()
                vib.vibrate(comp)
            } else {
                vib.vibrate(VibrationEffect.createOneShot(10L, 90))
            }
        }
    }

    private fun isLowBattery(): Boolean {
        val pct = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
        return pct in 1..14
    }

    private fun triggerSubBassPulse(strength: Float) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val primitive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD)
                ) {
                    VibrationEffect.Composition.PRIMITIVE_THUD
                } else if (vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)) {
                    VibrationEffect.Composition.PRIMITIVE_LOW_TICK
                } else {
                    -1
                }
                if (primitive != -1) {
                    val comp = VibrationEffect.startComposition()
                        .addPrimitive(primitive, strength)
                        .compose()
                    vib.vibrate(comp)
                    return
                }
            }
            val amp = (strength * 200).toInt().coerceIn(30, 255)
            vib.vibrate(VibrationEffect.createOneShot(18L, amp))
        }
    }

    fun destroy() {
        collectorJob?.cancel()
        scope.cancel()
    }
}
