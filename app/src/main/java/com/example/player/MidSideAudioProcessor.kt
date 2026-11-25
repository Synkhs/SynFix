package com.example.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * #10 Mid/Side Stem Control:
 * Real-time stereo Mid/Side processor operating on direct ByteBuffers without heap allocations.
 * - stemBalance in [-1f, 0f): Karaoke / Instrumental mode (attenuates center Mid channel).
 * - stemBalance == 0f: Transparent bypass.
 * - stemBalance in (0f, +1f]: Vocal Boost mode (emphasizes Mid channel, attenuates Side).
 */
@OptIn(UnstableApi::class)
class MidSideAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var stemBalance: Float = 0.0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.channelCount != 2 || inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun isActive(): Boolean {
        return super.isActive() && kotlin.math.abs(stemBalance) > 0.03f
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val frameSize = 4 // 2 channels * 16-bit PCM
        val size = limit - position
        if (size <= 0) return

        val outputBuffer = replaceOutputBuffer(size)
        val balance = stemBalance.coerceIn(-1f, 1f)

        // Mid/Side multipliers
        val midGain = if (balance < 0f) 1f + (balance * 0.88f) else 1f + (balance * 0.45f)
        val sideGain = if (balance < 0f) 1f + (-balance * 0.35f) else 1f - (balance * 0.65f)

        var offset = position
        while (offset + frameSize <= limit) {
            val left = inputBuffer.getShort(offset).toFloat()
            val right = inputBuffer.getShort(offset + 2).toFloat()

            val mid = (left + right) * 0.5f * midGain
            val side = (left - right) * 0.5f * sideGain

            val outLeft = (mid + side).coerceIn(-32768f, 32767f).toInt().toShort()
            val outRight = (mid - side).coerceIn(-32768f, 32767f).toInt().toShort()

            outputBuffer.putShort(outLeft)
            outputBuffer.putShort(outRight)
            offset += frameSize
        }

        inputBuffer.position(limit)
        outputBuffer.flip()
    }
}
