package com.example.player

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

/**
 * #45 Shake-to-Skip / Drop & #47 Gyro Parallax Depth Art:
 * Registers SensorEventListener ONLY during ON_RESUME and unregisters immediately on ON_PAUSE.
 * Caps gyro telemetry rate at <= 30 Hz (33ms) and writes into StateFlows read inside graphicsLayer.
 */
class SynFixSensorController(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val gyroSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _tiltX = MutableStateFlow(0f)
    val tiltX: StateFlow<Float> = _tiltX.asStateFlow()

    private val _tiltY = MutableStateFlow(0f)
    val tiltY: StateFlow<Float> = _tiltY.asStateFlow()

    @Volatile private var gyroEnabled = false
    @Volatile private var shakeEnabled = false
    @Volatile private var isResumed = false

    private var lastGyroEmitMs = 0L
    private var lastShakeTimeMs = 0L
    private var onShakeDetected: (() -> Unit)? = null

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    fun configure(
        gyroParallaxEnabled: Boolean,
        shakeToSkipEnabled: Boolean,
        onShake: () -> Unit
    ) {
        gyroEnabled = gyroParallaxEnabled
        shakeEnabled = shakeToSkipEnabled
        onShakeDetected = onShake
        updateRegistrations()
    }

    fun setLifecycleResumed(resumed: Boolean) {
        isResumed = resumed
        updateRegistrations()
    }

    private fun updateRegistrations() {
        val sm = sensorManager ?: return
        sm.unregisterListener(this)
        if (!isResumed) {
            _tiltX.value = 0f
            _tiltY.value = 0f
            return
        }
        if (gyroEnabled && gyroSensor != null) {
            // ~33_333 us == 30 Hz max rate
            sm.registerListener(this, gyroSensor, 33_333)
        } else {
            _tiltX.value = 0f
            _tiltY.value = 0f
        }
        if (shakeEnabled && accelSensor != null) {
            sm.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val now = android.os.SystemClock.elapsedRealtime()
        when (event.sensor.type) {
            Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_ROTATION_VECTOR -> {
                if (!gyroEnabled || now - lastGyroEmitMs < 33L) return
                lastGyroEmitMs = now
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                // Pitch & roll normalized cleanly into [-1f, +1f]
                val pitch = (orientationAngles[1] / 0.55f).coerceIn(-1f, 1f)
                val roll = (orientationAngles[2] / 0.55f).coerceIn(-1f, 1f)
                _tiltX.value = roll
                _tiltY.value = pitch
            }

            Sensor.TYPE_ACCELEROMETER -> {
                if (!shakeEnabled) return
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val gForce = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
                if (gForce > 2.35f && now - lastShakeTimeMs > 1200L) {
                    lastShakeTimeMs = now
                    onShakeDetected?.invoke()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    fun destroy() {
        isResumed = false
        sensorManager?.unregisterListener(this)
    }
}

/**
 * #49 Beat-Sync Party Strobe:
 * Drives screen edge pulse intensity in draw-phase and optional rear camera torch flash
 * strictly clamped to <= 12 Hz (>= 84ms between flashes) and disabled automatically on ON_PAUSE.
 */
class PartyStrobeController(context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val torchCameraId: String? = runCatching {
        cameraManager?.cameraIdList?.firstOrNull { id ->
            val chars = cameraManager.getCameraCharacteristics(id)
            chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    }.getOrNull()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var strobeJob: Job? = null
    private var lastTorchToggleMs = 0L
    private var torchCurrentlyOn = false

    private val _strobePulse = MutableStateFlow(0f)
    val strobePulse: StateFlow<Float> = _strobePulse.asStateFlow()

    @Volatile private var strobeEnabled = false
    @Volatile private var torchEnabled = false
    @Volatile private var isResumed = false
    @Volatile private var isPlaying = false

    fun updateConfig(enabled: Boolean, useTorch: Boolean, playing: Boolean, resumed: Boolean) {
        strobeEnabled = enabled
        torchEnabled = useTorch
        isPlaying = playing
        isResumed = resumed
        if (!enabled || !playing || !resumed) {
            stopStrobe()
        }
    }

    fun startObserving(subBassFlow: StateFlow<Float>) {
        if (strobeJob != null) return
        strobeJob = scope.launch {
            subBassFlow.collect { energy ->
                if (!strobeEnabled || !isPlaying || !isResumed) {
                    if (_strobePulse.value != 0f) _strobePulse.value = 0f
                    if (torchCurrentlyOn) setTorch(false)
                    return@collect
                }
                val pulse = if (energy > 0.34f) energy.coerceIn(0f, 1f) else (energy * 0.25f)
                _strobePulse.value = pulse

                if (torchEnabled) {
                    val now = android.os.SystemClock.elapsedRealtime()
                    // Clamp hardware torch frequency <= 12 Hz (>= 84ms minimum interval)
                    if (now - lastTorchToggleMs >= 84L) {
                        lastTorchToggleMs = now
                        val wantOn = energy > 0.42f && !torchCurrentlyOn
                        setTorch(wantOn)
                    }
                } else if (torchCurrentlyOn) {
                    setTorch(false)
                }
            }
        }
    }

    private fun stopStrobe() {
        _strobePulse.value = 0f
        if (torchCurrentlyOn) setTorch(false)
    }

    private fun setTorch(on: Boolean) {
        val id = torchCameraId ?: return
        runCatching {
            cameraManager?.setTorchMode(id, on)
            torchCurrentlyOn = on
        }
    }

    fun destroy() {
        stopStrobe()
        strobeJob?.cancel()
        strobeJob = null
        scope.cancel()
    }
}
