package com.example.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * #29 Lo-Fi Tape & Vinyl Simulator:
 * Warm analog tape saturation, gentle high-frequency roll-off via a fixed 4 KB circular
 * PCM ring buffer in RAM, and subtle deterministic vinyl crackle — zero heap allocations
 * during audio rendering.
 */
@OptIn(UnstableApi::class)
class LoFiTapeAudioProcessor : BaseAudioProcessor() {

    companion object {
        const val RING_BUFFER_BYTES = 4096
    }

    @Volatile
    var tapeEnabled: Boolean = false

    // Fixed 4 KB circular PCM buffer in RAM
    private val circularBuffer = ByteArray(RING_BUFFER_BYTES)
    private var writePos = 0
    private var prevLeft = 0f
    private var prevRight = 0f
    private var crackleSeed = 0x5A17C0DE

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.channelCount != 2 || inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun isActive(): Boolean {
        return super.isActive() && tapeEnabled
    }

    override fun onFlush() {
        circularBuffer.fill(0)
        writePos = 0
        prevLeft = 0f
        prevRight = 0f
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val frameSize = 4 // 2 channels * 16-bit PCM
        val size = limit - position
        if (size <= 0) return

        val outputBuffer = replaceOutputBuffer(size)
        var offset = position

        while (offset + frameSize <= limit) {
            val b0 = inputBuffer.get(offset)
            val b1 = inputBuffer.get(offset + 1)
            val b2 = inputBuffer.get(offset + 2)
            val b3 = inputBuffer.get(offset + 3)

            // Store raw frame inside fixed 4 KB circular buffer
            val baseIdx = writePos
            circularBuffer[baseIdx] = b0
            circularBuffer[(baseIdx + 1) and (RING_BUFFER_BYTES - 1)] = b1
            circularBuffer[(baseIdx + 2) and (RING_BUFFER_BYTES - 1)] = b2
            circularBuffer[(baseIdx + 3) and (RING_BUFFER_BYTES - 1)] = b3

            // Read a 512-byte-delayed tap (~2.9ms at 44.1kHz stereo) from the 4 KB circular ring for tape flutter warmth
            val tapIdx = (writePos - 512) and (RING_BUFFER_BYTES - 1)
            val tapLeft = ((circularBuffer[(tapIdx + 1) and (RING_BUFFER_BYTES - 1)].toInt() shl 8) or
                (circularBuffer[tapIdx].toInt() and 0xFF)).toShort() / 32768f
            val tapRight = ((circularBuffer[(tapIdx + 3) and (RING_BUFFER_BYTES - 1)].toInt() shl 8) or
                (circularBuffer[(tapIdx + 2) and (RING_BUFFER_BYTES - 1)].toInt() and 0xFF)).toShort() / 32768f

            writePos = (writePos + frameSize) and (RING_BUFFER_BYTES - 1)

            val rawLeft = inputBuffer.getShort(offset) / 32768f
            val rawRight = inputBuffer.getShort(offset + 2) / 32768f

            // Gentle tape low-pass + delayed warmth blend
            val lpLeft = prevLeft * 0.34f + rawLeft * 0.58f + tapLeft * 0.08f
            val lpRight = prevRight * 0.34f + rawRight * 0.58f + tapRight * 0.08f
            prevLeft = lpLeft
            prevRight = lpRight

            // Soft analog saturation: x * 1.35 / (1 + 0.45 * |x|)
            val satLeft = (lpLeft * 1.32f) / (1f + 0.42f * abs(lpLeft))
            val satRight = (lpRight * 1.32f) / (1f + 0.42f * abs(lpRight))

            // Subtle vinyl dust crackle (fast zero-alloc LCG)
            crackleSeed = crackleSeed * 1664525 + 1013904223
            val noiseBits = crackleSeed ushr 16
            val crackle = if (noiseBits == 0x7FF0) 0.032f else if ((noiseBits and 0x3FF) == 0) 0.008f else 0f
            val hiss = (((crackleSeed and 0xFF) - 128) / 128f) * 0.0018f

            val finalLeft = ((satLeft + crackle + hiss) * 32767f).coerceIn(-32768f, 32767f).toInt().toShort()
            val finalRight = ((satRight - crackle * 0.7f + hiss) * 32767f).coerceIn(-32768f, 32767f).toInt().toShort()

            outputBuffer.putShort(finalLeft)
            outputBuffer.putShort(finalRight)
            offset += frameSize
        }

        inputBuffer.position(limit)
        outputBuffer.flip()
    }
}
