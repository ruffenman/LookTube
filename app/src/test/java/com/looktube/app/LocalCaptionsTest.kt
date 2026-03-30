package com.looktube.app

import com.looktube.model.CaptionGenerationPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
        assertEquals("Transcribing chunk 1 of 4… 0:00 of 2:00 processed", status.message)
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
            "Transcribing chunk 2 of 4… 0:45 of 2:00 processed • 38% complete • speed 0.25x realtime",
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
        assertEquals("Transcribing chunk 4 of 4… 2:00 of 2:00 processed • 100% complete", status.message)
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
            "Transcribing chunk 2 of 4… 3:00 of 8:00 processed • 38% complete • speed 1.00x realtime • ETA ~5:00",
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
            "Transcribing chunk 3 of 4… 4:00 of 8:00 processed • 50% complete • speed 0.50x realtime • ETA ~8:00 • last 2:00 chunk 2:30 wall (enc 1:36, dec 0:12)",
            status.message,
        )
        assertEquals(0.675f, status.progressFraction ?: 0f, 0.0001f)
    }
}
