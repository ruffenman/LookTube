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
    fun transcriptionCaptionStatusInterpolatesWithinTheActiveChunk() {
        val status = transcriptionCaptionStatus(
            completedChunkCount = 1,
            totalChunks = 4,
            activeChunkProgressPercent = 50,
        )

        assertEquals(CaptionGenerationPhase.Transcribing, status.phase)
        assertEquals("Transcribing audio on this device… 38% complete", status.message)
        assertEquals(0.60625f, status.progressFraction ?: 0f, 0.0001f)
    }

    @Test
    fun transcriptionCaptionStatusCapsAtCompletion() {
        val status = transcriptionCaptionStatus(
            completedChunkCount = 4,
            totalChunks = 4,
            activeChunkProgressPercent = 100,
        )

        assertEquals("Transcribing audio on this device… 100% complete", status.message)
        assertEquals(0.95f, status.progressFraction ?: 0f, 0.0001f)
    }
}
