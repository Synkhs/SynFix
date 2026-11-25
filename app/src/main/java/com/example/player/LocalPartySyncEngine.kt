package com.example.player

import android.content.Context
import android.net.wifi.WifiManager
import com.example.domain.model.PartySyncMode
import com.example.domain.model.PartySyncState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.math.abs

/**
 * #35 Local Party Sync:
 * Synchronizes playback across nearby devices on the same LAN via lightweight UDP
 * control timestamps (port 47832), steering guest playback speed within +/-1% (0.99x..1.01x).
 */
class LocalPartySyncEngine(private val context: Context) {

    companion object {
        const val UDP_PORT = 47832
        private const val PACKET_PREFIX = "SYNFIX1"
    }

    private val _state = MutableStateFlow(PartySyncState())
    val state: StateFlow<PartySyncState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncJob: Job? = null
    private var socket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun startHost(
        titleProvider: () -> String,
        positionProvider: () -> Long,
        isPlayingProvider: () -> Boolean
    ) {
        stop()
        _state.value = PartySyncState(
            mode = PartySyncMode.HOST,
            statusLabel = "Transmitiendo pulso UDP :$UDP_PORT",
            speedCorrection = 1.0f
        )

        syncJob = scope.launch {
            acquireMulticastLock()
            val sock = runCatching { DatagramSocket().apply { broadcast = true } }.getOrNull()
            if (sock == null) {
                _state.value = PartySyncState(PartySyncMode.OFF, "Error al abrir socket UDP")
                return@launch
            }
            socket = sock
            val broadcastAddr = InetAddress.getByName("255.255.255.255")

            while (isActive) {
                val payload = "$PACKET_PREFIX|${titleProvider().replace("|", " ")}|${positionProvider()}|${if (isPlayingProvider()) 1 else 0}|${System.currentTimeMillis()}"
                val bytes = payload.toByteArray(Charsets.UTF_8)
                runCatching {
                    val packet = DatagramPacket(bytes, bytes.size, broadcastAddr, UDP_PORT)
                    sock.send(packet)
                }
                delay(750L)
            }
        }
    }

    fun startGuest(
        positionProvider: () -> Long,
        onSeekToMs: (Long) -> Unit,
        onSyncPlayState: (Boolean) -> Unit
    ) {
        stop()
        _state.value = PartySyncState(
            mode = PartySyncMode.GUEST,
            statusLabel = "Escuchando anfitrión en UDP :$UDP_PORT...",
            speedCorrection = 1.0f
        )

        syncJob = scope.launch {
            acquireMulticastLock()
            val sock = runCatching {
                DatagramSocket(UDP_PORT).apply {
                    reuseAddress = true
                    soTimeout = 2000
                }
            }.getOrNull()

            if (sock == null) {
                _state.value = PartySyncState(PartySyncMode.OFF, "Puerto $UDP_PORT ocupado")
                return@launch
            }
            socket = sock
            val buf = ByteArray(512)

            while (isActive) {
                val packet = DatagramPacket(buf, buf.size)
                val received = runCatching {
                    sock.receive(packet)
                    String(packet.data, 0, packet.length, Charsets.UTF_8)
                }.getOrNull() ?: continue

                val parts = received.split('|')
                if (parts.size >= 5 && parts[0] == PACKET_PREFIX) {
                    val hostTitle = parts[1]
                    val hostPosMs = parts[2].toLongOrNull() ?: continue
                    val hostPlaying = parts[3] == "1"
                    val localPosMs = positionProvider()
                    val driftMs = hostPosMs - localPosMs

                    withContext(Dispatchers.Main) {
                        onSyncPlayState(hostPlaying)
                        val speedFactor = when {
                            abs(driftMs) > 1600L -> {
                                onSeekToMs(hostPosMs)
                                1.0f
                            }
                            driftMs > 80L -> 1.01f
                            driftMs < -80L -> 0.99f
                            else -> 1.0f
                        }
                        PlaybackRuntimeCoordinator.setPartySyncSpeedFactor(speedFactor)
                        _state.value = PartySyncState(
                            mode = PartySyncMode.GUEST,
                            statusLabel = "Sincronizado con \"$hostTitle\"",
                            speedCorrection = speedFactor,
                            lastDriftMs = driftMs
                        )
                    }
                }
            }
        }
    }

    fun stop() {
        syncJob?.cancel()
        syncJob = null
        runCatching { socket?.close() }
        socket = null
        releaseMulticastLock()
        PlaybackRuntimeCoordinator.setPartySyncSpeedFactor(1.0f)
        _state.value = PartySyncState()
    }

    private fun acquireMulticastLock() {
        runCatching {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wm?.createMulticastLock("synfix_party_sync")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseMulticastLock() {
        runCatching {
            if (multicastLock?.isHeld == true) multicastLock?.release()
        }
        multicastLock = null
    }

    fun destroy() {
        stop()
        scope.cancel()
    }
}
