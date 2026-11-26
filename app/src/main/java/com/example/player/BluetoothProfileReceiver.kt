package com.example.player

import android.Manifest
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.SynFixApplication
import com.example.domain.model.BluetoothMacProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * #82 Per-MAC Bluetooth Auto-Profile:
 * Passive BroadcastReceiver + AudioDeviceCallback controller.
 * Checks BLUETOOTH_CONNECT safely and applies saved initial volume + EQ preset per device MAC/name.
 */
class BluetoothProfileManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _connectedDevice = MutableStateFlow<Pair<String, String>?>(null) // macOrId to name
    val connectedDevice: StateFlow<Pair<String, String>?> = _connectedDevice.asStateFlow()

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            val bt = addedDevices?.firstOrNull { isBluetoothAudioType(it.type) } ?: return
            val id = resolveDeviceKey(bt)
            val name = bt.productName?.toString()?.ifBlank { "Bluetooth Audio" } ?: "Bluetooth Audio"
            _connectedDevice.value = id to name
            applySavedProfileForKey(id)
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            val btRemoved = removedDevices?.any { isBluetoothAudioType(it.type) } == true
            if (btRemoved) {
                refreshConnectedFromSystem()
            }
        }
    }

    init {
        runCatching { audioManager.registerAudioDeviceCallback(deviceCallback, null) }
        refreshConnectedFromSystem()
    }

    fun refreshConnectedFromSystem() {
        val bt = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { isBluetoothAudioType(it.type) }
        if (bt != null) {
            val key = resolveDeviceKey(bt)
            val name = bt.productName?.toString()?.ifBlank { "Bluetooth Audio" } ?: "Bluetooth Audio"
            _connectedDevice.value = key to name
        } else {
            _connectedDevice.value = null
        }
    }

    fun onBluetoothDeviceConnected(mac: String, name: String) {
        _connectedDevice.value = mac to name
        applySavedProfileForKey(mac)
    }

    fun applyProfileNow(profile: BluetoothMacProfile) {
        scope.launch {
            val app = context.applicationContext as? SynFixApplication ?: return@launch
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val targetVol = ((profile.initialVolumePercent.coerceIn(5, 100) / 100f) * maxVol)
                .toInt().coerceIn(0, maxVol)
            runCatching {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
            }

            val presets = BuiltInEqPresets.PRESETS +
                app.database.musicDao().getCustomEqPresetsOnce().map { it.toDomain() }
            val preset = presets.find { it.id == profile.eqPresetId } ?: return@launch
            app.preferences.saveEqState(
                enabled = true,
                presetId = preset.id,
                bandsDb = preset.bandsDb,
                preampDb = preset.preampDb,
                bassBoost = preset.bassBoost,
                virtualizer = preset.virtualizer
            )
        }
    }

    private fun applySavedProfileForKey(key: String) {
        scope.launch {
            val app = context.applicationContext as? SynFixApplication ?: return@launch
            val profile = app.repository.getBluetoothProfileByMac(key) ?: return@launch
            applyProfileNow(profile)
        }
    }

    private fun resolveDeviceKey(info: AudioDeviceInfo): String {
        val addr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.address.takeIf { it.isNotBlank() && it != "02:00:00:00:00:00" }
        } else null
        return addr ?: "BT_${info.productName?.toString()?.trim()?.uppercase() ?: info.id}"
    }

    private fun isBluetoothAudioType(type: Int): Boolean =
        type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            type == AudioDeviceInfo.TYPE_BLE_SPEAKER

    fun destroy() {
        runCatching { audioManager.unregisterAudioDeviceCallback(deviceCallback) }
    }
}

class BluetoothProfileReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED) {
            val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
            if (state != BluetoothProfile.STATE_CONNECTED) return
        } else if (action != BluetoothDevice.ACTION_ACL_CONNECTED) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

        val mac = runCatching { device?.address }.getOrNull()?.takeIf { it.isNotBlank() } ?: return
        val name = runCatching { device?.name }.getOrNull()?.ifBlank { "Auriculares BT" } ?: "Auriculares BT"
        val app = context.applicationContext as? SynFixApplication ?: return
        app.btProfileManager.onBluetoothDeviceConnected(mac, name)
    }
}
