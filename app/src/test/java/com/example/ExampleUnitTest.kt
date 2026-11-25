package com.example

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.example.data.lyrics.LrcParser
import com.example.data.worker.CamelotWheelAnalyzer
import com.example.domain.model.Song
import com.example.player.LoFiTapeAudioProcessor
import com.example.player.M3u8PlaylistManager
import com.example.player.MidSideAudioProcessor
import com.example.ui.components.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class ExampleUnitTest {

    @Test
    fun lrcParser_parsesMultiTimestampsAndOffset() {
        val raw = """
            [offset:500]
            [00:10.00][00:25.50]Chorus line
            [00:15.25]Second verse
        """.trimIndent()

        val parsed = LrcParser.parse(raw)
        assertEquals(3, parsed.size)
        assertEquals(10_500L, parsed[0].timestampMs)
        assertEquals("Chorus line", parsed[0].text)
        assertEquals(15_750L, parsed[1].timestampMs)
        assertEquals(26_000L, parsed[2].timestampMs)

        val activeIdx = LrcParser.findActiveLineIndex(parsed, 16_000L)
        assertEquals(1, activeIdx)
    }

    @Test
    fun formatDuration_formatsMinutesAndHours() {
        assertEquals("03:45", formatDuration(225_000L))
        assertEquals("1:01:05", formatDuration(3_665_000L))
    }

    @Test
    fun midSideProcessor_attenuatesCenterInKaraokeMode() {
        val processor = MidSideAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT))
        processor.stemBalance = -1.0f
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)

        // Pure center (Mid) signal: L = 10000, R = 10000
        val input = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).apply {
            putShort(10_000)
            putShort(10_000)
            flip()
        }
        processor.queueInput(input)
        val out = processor.output
        val outL = out.getShort()
        val outR = out.getShort()

        assertTrue("Karaoke mode should strongly attenuate center vocals", abs(outL.toInt()) < 2000)
        assertEquals(outL, outR)
    }

    @Test
    fun lofiTapeProcessor_processesPcmThrough4KbCircularBuffer() {
        val processor = LoFiTapeAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT))
        processor.tapeEnabled = true
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)

        val sampleCount = 16
        val input = ByteBuffer.allocateDirect(sampleCount * 4).order(ByteOrder.nativeOrder())
        repeat(sampleCount) {
            input.putShort(12_000)
            input.putShort((-12_000).toShort())
        }
        input.flip()

        processor.queueInput(input)
        val out = processor.output
        assertEquals(sampleCount * 4, out.remaining())

        val firstOutL = out.getShort()
        val firstOutR = out.getShort()
        assertNotEquals(12_000.toShort(), firstOutL)
        assertNotEquals((-12_000).toShort(), firstOutR)
    }

    @Test
    fun camelotWheelAnalyzer_findsCompatibleHarmonicKeys() {
        val compatible = CamelotWheelAnalyzer.getCompatibleKeys("8A")
        assertEquals(listOf("8A", "8B", "7A", "9A"), compatible)

        // Strong A-minor triad energy (A=9, C=0, E=4)
        val chroma = FloatArray(12).apply {
            this[9] = 1.0f
            this[0] = 0.85f
            this[4] = 0.80f
        }
        val classified = CamelotWheelAnalyzer.classifyChromaToCamelot(chroma)
        assertTrue(classified in CamelotWheelAnalyzer.ALL_CAMELOT_KEYS)
    }

    @Test
    fun m3u8PlaylistManager_relinksMovedTrackByFingerprint() {
        val playlistText = """
            #EXTM3U
            #EXTINF:210,SynFix Artist - Midnight Pulse
            /storage/emulated/0/OldFolder/midnight_pulse_moved.flac
        """.trimIndent()

        val entries = M3u8PlaylistManager.parseM3u8Content(playlistText)
        assertEquals(1, entries.size)

        val library = listOf(
            Song(
                id = 42L,
                title = "Midnight Pulse (Remastered)",
                artist = "SynFix Artist",
                album = "Night",
                albumId = 7L,
                durationMs = 210_500L,
                dataPath = "/storage/emulated/0/Music/NewFolder/midnight_pulse_new.flac",
                folderPath = "/storage/emulated/0/Music/NewFolder",
                dateAdded = 1000L
            )
        )

        val result = M3u8PlaylistManager.relinkEntries("Importadas", entries, library)
        assertEquals(0, result.exactMatches)
        assertEquals(1, result.fingerprintRelinked)
        assertEquals(0, result.unmatchedCount)
        assertEquals(1, result.resolvedSongs.size)
        assertEquals(42L, result.resolvedSongs.first().id)
    }
}
