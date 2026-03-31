package com.looktube.app

import com.looktube.model.CaptionGenerationPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

class LocalCaptionsTest {
    @Test
    fun extractionCaptionStatusUsesElapsedCopyWhenDurationIsUnknown() {
        val status = extractionCaptionStatus(
            AudioExtractionProgress(
                currentPositionUs = 92_000_000L,
                durationUs = null,
                decodedBytes = 4_096L,
            ),
        )

        assertEquals(CaptionGenerationPhase.ExtractingAudio, status.phase)
        assertEquals(
            "Extracting mono audio for offline caption generation… 1:32 decoded so far",
            status.message,
        )
        assertNull(status.progressFraction)
    }

    @Test
    fun extractionCaptionStatusUsesBoundedProgressWhenDurationIsKnown() {
        val status = extractionCaptionStatus(
            AudioExtractionProgress(
                currentPositionUs = 30_000_000L,
                durationUs = 120_000_000L,
                decodedBytes = 16_384L,
            ),
        )

        assertEquals(
            "Extracting mono audio for offline caption generation… 0:30 / 2:00",
            status.message,
        )
        assertEquals(0.1025f, status.progressFraction ?: 0f, 0.0001f)
    }

    @Test
    fun transcriptionProgressFractionStartsAndEndsInTheExpectedRange() {
        assertEquals(0.4f, transcriptionProgressFraction(0, 4), 0.0001f)
        assertEquals(0.95f, transcriptionProgressFraction(4, 4), 0.0001f)
    }
    @Test
    fun transcriptionCaptionStatusStartsWithChunkAndDurationCopyBeforeMeasuredProgressArrives() {
        val status = transcriptionCaptionStatus(
            completedChunkCount = 0,
            totalChunks = 4,
            processedAudioSeconds = 0L,
            totalAudioDurationSeconds = 120L,
            activeChunkProgressPercent = 0,
        )

        assertEquals(CaptionGenerationPhase.Transcribing, status.phase)
        assertEquals("Transcribing chunk 1 of 4… 0:00 of 2:00 speech processed", status.message)
        assertNull(status.progressFraction)
    }

    @Test
    fun transcriptionCaptionStatusInterpolatesWithinTheActiveChunk() {
        val status = transcriptionCaptionStatus(
            completedChunkCount = 1,
            totalChunks = 4,
            processedAudioSeconds = 30L,
            totalAudioDurationSeconds = 120L,
            activeChunkDurationSeconds = 30L,
            activeChunkProgressPercent = 50,
            elapsedRealtimeSeconds = 180L,
        )

        assertEquals(CaptionGenerationPhase.Transcribing, status.phase)
        assertEquals(
            "Transcribing chunk 2 of 4… 0:45 of 2:00 speech processed • 38% complete • speed 0.25x realtime",
            status.message,
        )
        assertEquals(0.60625f, status.progressFraction ?: 0f, 0.0001f)
    }

    @Test
    fun transcriptionCaptionStatusCapsAtCompletion() {
        val status = transcriptionCaptionStatus(
            completedChunkCount = 4,
            totalChunks = 4,
            processedAudioSeconds = 120L,
            totalAudioDurationSeconds = 120L,
            activeChunkProgressPercent = 100,
        )
        assertEquals("Transcribing chunk 4 of 4… 2:00 of 2:00 speech processed • 100% complete", status.message)
        assertEquals(0.95f, status.progressFraction ?: 0f, 0.0001f)
    }

    @Test
    fun transcriptionCaptionStatusAddsEtaOnlyAfterEnoughMeasuredProgress() {
        val status = transcriptionCaptionStatus(
            completedChunkCount = 1,
            totalChunks = 4,
            processedAudioSeconds = 120L,
            totalAudioDurationSeconds = 480L,
            activeChunkDurationSeconds = 120L,
            activeChunkProgressPercent = 50,
            elapsedRealtimeSeconds = 180L,
        )

        assertEquals(
            "Transcribing chunk 2 of 4… 3:00 of 8:00 speech processed • 38% complete • speed 1.00x realtime • ETA ~5:00",
            status.message,
        )
        assertEquals(0.60625f, status.progressFraction ?: 0f, 0.0001f)
    }

    @Test
    fun transcriptionCaptionStatusIncludesLastChunkWallTimeAndNativeTimings() {
        val status = transcriptionCaptionStatus(
            completedChunkCount = 2,
            totalChunks = 4,
            processedAudioSeconds = 240L,
            totalAudioDurationSeconds = 480L,
            elapsedRealtimeSeconds = 480L,
            lastCompletedChunkDurationSeconds = 120L,
            lastCompletedChunkWallSeconds = 150L,
            lastCompletedChunkTimings = WhisperNativeTimings(
                sampleMs = 2_000f,
                encodeMs = 96_000f,
                decodeMs = 12_000f,
                batchDecodeMs = 0f,
                promptMs = 0f,
            ),
        )

        assertEquals(
            "Transcribing chunk 3 of 4… 4:00 of 8:00 speech processed • 50% complete • speed 0.50x realtime • ETA ~8:00 • last 2:00 chunk 2:30 wall (enc 1:36, dec 0:12)",
            status.message,
        )
        assertEquals(0.675f, status.progressFraction ?: 0f, 0.0001f)
    }

    @Test
    fun captionAudioPlanStatusHighlightsSpeechCoverageWhenSilenceIsSkipped() {
        val status = captionAudioPlanStatus(
            engineLabel = "Whisper.cpp",
            audioPlan = CaptionAudioPlan(
                totalAudioDurationSeconds = 10L,
                speechDurationSeconds = 4L,
                spans = listOf(
                    CaptionAudioSpan(startSampleIndex = 0L, sampleCount = 32_000L),
                    CaptionAudioSpan(startSampleIndex = 96_000L, sampleCount = 32_000L),
                ),
            ),
        )

        assertEquals(CaptionGenerationPhase.Transcribing, status.phase)
        assertEquals(
            "Preparing Whisper.cpp transcription from 2 speech spans covering 0:04 of 0:10 total audio…",
            status.message,
        )
        assertEquals(0.4f, status.progressFraction ?: 0f, 0.0001f)
    }

    @Test
    fun buildCaptionAudioPlanFindsMultipleSpeechSpans() {
        val audioFile = File.createTempFile("caption-audio-plan", ".pcm")
        try {
            writePcm16Mono(
                file = audioFile,
                samples = concatenateSamples(
                    silenceSamples(1.0),
                    toneSamples(1.0, amplitude = 0.6f),
                    silenceSamples(1.0),
                    toneSamples(0.8, amplitude = 0.5f),
                    silenceSamples(1.0),
                ),
            )

            val audioPlan = buildCaptionAudioPlan(audioFile)

            assertEquals(5L, audioPlan.totalAudioDurationSeconds)
            assertEquals(2, audioPlan.speechSpanCount)
            assertTrue(audioPlan.speechDurationSeconds in 2L..3L)
            assertTrue(audioPlan.speechCoverageFraction < 0.8f)
        } finally {
            audioFile.delete()
        }
    }

    @Test
    fun buildCaptionAudioChunksSplitsLongSpeechSpansIntoBoundedWindows() {
        val chunks = buildCaptionAudioChunks(
            audioPlan = CaptionAudioPlan(
                totalAudioDurationSeconds = 75L,
                speechDurationSeconds = 75L,
                spans = listOf(
                    CaptionAudioSpan(
                        startSampleIndex = 0L,
                        sampleCount = 75L * 16_000L,
                    ),
                ),
            ),
            maxChunkDurationSeconds = 30,
        )

        assertEquals(listOf(480_000, 480_000, 240_000), chunks.map { chunk -> chunk.sampleCount })
        assertEquals(listOf(0L, 30_000L, 60_000L), chunks.map { chunk -> chunk.startMs })
    }

    @Test
    fun preferredWhisperThreadCountUsesPrimePlusBigCoreClustersWhenAvailable() {
        val threadCount = preferredWhisperThreadCount(
            availableProcessors = 8,
            cpuMaxFrequenciesKhz = listOf(3_000_000, 2_600_000, 2_600_000, 2_600_000, 1_800_000, 1_800_000, 1_800_000, 1_800_000),
        )

        assertEquals(4, threadCount)
    }

    @Test
    fun adaptiveWhisperAudioContextSizeShrinksForShortChunks() {
        assertEquals(384, adaptiveWhisperAudioContextSize(1))
        assertEquals(428, adaptiveWhisperAudioContextSize(6))
        assertEquals(1_500, adaptiveWhisperAudioContextSize(30))
    }
}

private fun concatenateSamples(vararg arrays: FloatArray): FloatArray {
    val totalSize = arrays.sumOf { samples -> samples.size }
    val result = FloatArray(totalSize)
    var offset = 0
    arrays.forEach { samples ->
        samples.copyInto(result, destinationOffset = offset)
        offset += samples.size
    }
    return result
}

private fun silenceSamples(durationSeconds: Double): FloatArray =
    FloatArray((durationSeconds * 16_000.0).toInt())

private fun toneSamples(
    durationSeconds: Double,
    amplitude: Float,
): FloatArray {
    val sampleCount = (durationSeconds * 16_000.0).toInt()
    return FloatArray(sampleCount) { index ->
        (sin((2.0 * PI * 220.0 * index.toDouble()) / 16_000.0) * amplitude).toFloat()
    }
}

private fun writePcm16Mono(
    file: File,
    samples: FloatArray,
) {
    val bytes = ByteArray(samples.size * 2)
    var byteIndex = 0
    samples.forEach { sample ->
        val shortValue = (sample.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        bytes[byteIndex] = (shortValue.toInt() and 0xff).toByte()
        bytes[byteIndex + 1] = ((shortValue.toInt() shr 8) and 0xff).toByte()
        byteIndex += 2
    }
    file.writeBytes(bytes)
}
