package com.example.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaScannerConnection
import android.net.wifi.WifiManager
import android.os.Environment
import com.example.domain.model.QrServerState
import com.example.domain.model.Song
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Locale

/**
 * #80 Local QR Web Server:
 * Ephemeral local HTTP server on Dispatchers.IO that allows transferring audio files
 * between desktop/phone browsers and SynFix without internet or cables.
 * Shuts down cleanly and deletes sockets when closed.
 */
class LocalQrWebServer(private val context: Context) {

    private val _state = MutableStateFlow(QrServerState())
    val state: StateFlow<QrServerState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    @Volatile
    private var songsProvider: () -> List<Song> = { emptyList() }
    @Volatile
    private var onUploadCompleted: (() -> Unit)? = null

    fun start(
        songsProvider: () -> List<Song>,
        onFileUploaded: () -> Unit
    ) {
        if (_state.value.isRunning) return
        this.songsProvider = songsProvider
        this.onUploadCompleted = onFileUploaded

        serverJob = scope.launch {
            runCatching {
                val ss = ServerSocket(0)
                serverSocket = ss
                val port = ss.localPort
                val ip = resolveLocalIpv4Address()
                val url = "http://$ip:$port"
                _state.value = QrServerState(isRunning = true, url = url, port = port, uploadedCount = 0)

                while (isActive && !ss.isClosed) {
                    val client = runCatching { ss.accept() }.getOrNull() ?: break
                    launch { handleClient(client) }
                }
            }.onFailure {
                stop()
            }
        }
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
        _state.value = QrServerState()
    }

    private fun handleClient(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 15_000
            val input = BufferedInputStream(s.getInputStream())
            val out = s.getOutputStream()

            val headerLine = readLineAscii(input) ?: return
            val parts = headerLine.split(' ')
            if (parts.size < 2) return
            val method = parts[0].uppercase(Locale.ROOT)
            val path = parts[1]

            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readLineAscii(input) ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    headers[line.substring(0, colon).trim().lowercase(Locale.ROOT)] =
                        line.substring(colon + 1).trim()
                }
            }

            when {
                method == "GET" && path == "/" -> {
                    val html = buildHtmlPage(songsProvider().take(80))
                    val bytes = html.toByteArray(Charsets.UTF_8)
                    val respHeaders = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                    out.write(respHeaders.toByteArray(Charsets.US_ASCII))
                    out.write(bytes)
                    out.flush()
                }

                method == "GET" && path.startsWith("/download/") -> {
                    val songId = path.removePrefix("/download/").substringBefore('?').toLongOrNull()
                    val song = songsProvider().find { it.id == songId }
                    val file = song?.let { File(it.dataPath) }?.takeIf { it.exists() && it.canRead() }
                    if (file == null) {
                        sendSimpleResponse(out, "404 Not Found", "Archivo no encontrado")
                    } else {
                        val safeName = file.name.replace("\"", "")
                        val respHeaders = "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Disposition: attachment; filename=\"$safeName\"\r\nContent-Length: ${file.length()}\r\nConnection: close\r\n\r\n"
                        out.write(respHeaders.toByteArray(Charsets.US_ASCII))
                        FileInputStream(file).use { fis ->
                            val buf = ByteArray(16 * 1024)
                            while (true) {
                                val r = fis.read(buf)
                                if (r <= 0) break
                                out.write(buf, 0, r)
                            }
                        }
                        out.flush()
                    }
                }

                method == "POST" && path.startsWith("/upload") -> {
                    val contentLength = headers["content-length"]?.toIntOrNull()?.coerceAtMost(80 * 1024 * 1024) ?: 0
                    val rawName = path.substringAfter("name=", "track_${System.currentTimeMillis()}.mp3")
                    val decodedName = URLDecoder.decode(rawName, "UTF-8")
                        .replace(Regex("[^a-zA-Z0-9._\\- ()]"), "_")
                        .ifBlank { "synfix_${System.currentTimeMillis()}.mp3" }

                    if (contentLength <= 0) {
                        sendSimpleResponse(out, "400 Bad Request", "Archivo vacío")
                        return
                    }

                    val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                        ?.takeIf { it.exists() || it.mkdirs() }
                        ?: File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "SynFix")
                    val targetDir = File(musicDir, "SynFix_WiFi").apply { mkdirs() }
                    val outFile = File(targetDir, decodedName)

                    val body = ByteArray(16 * 1024)
                    var remaining = contentLength
                    FileOutputStream(outFile).use { fos ->
                        while (remaining > 0) {
                            val toRead = minOf(body.size, remaining)
                            val r = input.read(body, 0, toRead)
                            if (r <= 0) break
                            fos.write(body, 0, r)
                            remaining -= r
                        }
                    }

                    MediaScannerConnection.scanFile(context, arrayOf(outFile.absolutePath), null, null)
                    _state.value = _state.value.copy(uploadedCount = _state.value.uploadedCount + 1)
                    onUploadCompleted?.invoke()
                    sendSimpleResponse(out, "200 OK", "Recibido: ${outFile.name}")
                }

                else -> sendSimpleResponse(out, "404 Not Found", "Ruta no válida")
            }
        }
    }

    private fun sendSimpleResponse(out: java.io.OutputStream, status: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val headers = "HTTP/1.1 $status\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        out.write(headers.toByteArray(Charsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }

    private fun readLineAscii(input: BufferedInputStream): String? {
        val baos = ByteArrayOutputStream()
        var b = input.read()
        if (b == -1) return null
        while (b != -1 && b != '\n'.code) {
            if (b != '\r'.code) baos.write(b)
            b = input.read()
        }
        return baos.toString("US-ASCII")
    }

    private fun buildHtmlPage(songs: List<Song>): String {
        val rows = songs.joinToString("") { song ->
            "<li><span>${escapeHtml(song.title)} — <em>${escapeHtml(song.artist)}</em></span>" +
                "<a href='/download/${song.id}'>Descargar</a></li>"
        }
        return """
            <!doctype html>
            <html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>SynFix Wi-Fi Drop</title>
            <style>
            body{font-family:system-ui,sans-serif;background:#101218;color:#eef0f7;margin:0;padding:24px;}
            .card{background:#1b1f2a;border-radius:20px;padding:20px;margin-bottom:18px;max-width:720px}
            button{background:#a8c7fa;color:#062e6f;border:none;padding:12px 20px;border-radius:99px;font-weight:700;cursor:pointer}
            ul{list-style:none;padding:0;max-height:360px;overflow:auto}
            li{display:flex;justify-content:space-between;padding:10px 0;border-bottom:1px solid #2b3142;gap:12px}
            a{color:#a8c7fa;text-decoration:none;font-weight:600}
            </style></head><body>
            <div class="card"><h2>SynFix Wi-Fi Transfer</h2>
            <p>Envía música FLAC, MP3, WAV u OPUS directamente a tu dispositivo sin cables.</p>
            <input type="file" id="f" accept="audio/*" multiple><br><br>
            <button onclick="upload()">Enviar a SynFix</button>
            <p id="st"></p></div>
            <div class="card"><h3>Biblioteca (${songs.size})</h3><ul>$rows</ul></div>
            <script>
            async function upload(){
              const files=document.getElementById('f').files;
              const st=document.getElementById('st');
              for(const file of files){
                st.textContent='Subiendo '+file.name+'...';
                await fetch('/upload?name='+encodeURIComponent(file.name),{method:'POST',body:file});
              }
              st.textContent=files.length?('Transferidos '+files.length+' archivo(s) a SynFix_WiFi'):'Selecciona archivos';
            }
            </script></body></html>
        """.trimIndent()
    }

    private fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun resolveLocalIpv4Address(): String {
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val nif = interfaces.nextElement()
                if (!nif.isUp || nif.isLoopback) continue
                val addrs = nif.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        if (!ip.startsWith("127.")) return ip
                    }
                }
            }
        }
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        @Suppress("DEPRECATION")
        val ipInt = wm?.connectionInfo?.ipAddress ?: 0
        if (ipInt != 0) {
            return String.format(
                Locale.US,
                "%d.%d.%d.%d",
                ipInt and 0xff,
                ipInt shr 8 and 0xff,
                ipInt shr 16 and 0xff,
                ipInt shr 24 and 0xff
            )
        }
        return "127.0.0.1"
    }

    companion object {
        fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap? {
            if (content.isBlank()) return null
            return runCatching {
                val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
                val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
                for (x in 0 until sizePx) {
                    for (y in 0 until sizePx) {
                        bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
                    }
                }
                bmp
            }.getOrNull()
        }
    }
}
