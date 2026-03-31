package com.looktube.app

import java.io.BufferedInputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import kotlin.math.max
import kotlin.math.roundToInt

internal data class CaptionAudioSpan(
    val startSampleIndex: Long,
    val sampleCount: Long,
) {
    val endSampleIndex: Long
        get() = startSampleIndex + sampleCount
}

internal data class CaptionAudioChunkDescriptor(
    val startSampleIndex: Long,
    val sampleCount: Int,
) {
    val startMs: Long
        get() = (startSampleIndex * 1_000L) / CAPTION_AUDIO_SAMPLE_RATE

    val durationSeconds: Long
        get() = max(
            1L,
            (sampleCount.toLong() + (CAPTION_AUDIO_SAMPLE_RATE - 1).toLong()) / CAPTION_AUDIO_SAMPLE_RATE.toLong(),
        )
}

internal data class CaptionAudioPlan(
    val totalAudioDurationSeconds: Long,
    val speechDurationSeconds: Long,
    val spans: List<CaptionAudioSpan>,
) {
    val speechSpanCount: Int
        get() = spans.size

    val speechCoverageFraction: Float
        get() = if (totalAudioDurationSeconds <= 0L) {
            1f
        } else {
            (speechDurationSeconds.toFloat() / totalAudioDurationSeconds.toFloat()).coerceIn(0f, 1f)
        }
}

internal fun buildCaptionAudioPlan(audioFile: File): CaptionAudioPlan {
    val totalSamples = (audioFile.length() / PCM_BYTES_PER_SAMPLE.toLong()).coerceAtLeast(0L)
    if (totalSamples <= 0L) {
        return CaptionAudioPlan(
            totalAudioDurationSeconds = 0L,
            speechDurationSeconds = 0L,
            spans = emptyList(),
        )
    }
    val rawSpans = mutableListOf<CaptionAudioSpan>()
    BufferedInputStream(FileInputStream(audioFile)).use { input ->
        val frameBuffer = ByteArray(CAPTION_SEGMENTATION_FRAME_BYTES)
        var consumedSamples = 0L
        var candidateSpeechStartSampleIndex: Long? = null
        var consecutiveSpeechSamples = 0L
        var consecutiveSilenceSamples = 0L
        var currentSpeechStartSampleIndex = 0L
        var lastSpeechSampleEndIndex = 0L
        var inSpeech = false
        while (true) {
            val bytesRead = readCaptionAudioFrame(input, frameBuffer)
            if (bytesRead <= 0) {
                break
            }
            val samplesInFrame = (bytesRead / PCM_BYTES_PER_SAMPLE).toLong()
            val frameStartSampleIndex = consumedSamples
            val frameEndSampleIndex = frameStartSampleIndex + samplesInFrame
            val meanAbsoluteAmplitude = meanAbsoluteAmplitude(frameBuffer, bytesRead)
            if (inSpeech) {
                if (meanAbsoluteAmplitude >= CAPTION_SEGMENTATION_END_THRESHOLD) {
                    consecutiveSilenceSamples = 0L
                    lastSpeechSampleEndIndex = frameEndSampleIndex
                } else {
                    consecutiveSilenceSamples += samplesInFrame
                    if (consecutiveSilenceSamples >= CAPTION_MIN_SILENCE_SAMPLES) {
                        rawSpans += CaptionAudioSpan(
                            startSampleIndex = currentSpeechStartSampleIndex,
                            sampleCount = (lastSpeechSampleEndIndex - currentSpeechStartSampleIndex).coerceAtLeast(0L),
                        )
                        inSpeech = false
                        candidateSpeechStartSampleIndex = null
                        consecutiveSpeechSamples = 0L
                        consecutiveSilenceSamples = 0L
                    }
                }
            } else {
                if (meanAbsoluteAmplitude >= CAPTION_SEGMENTATION_START_THRESHOLD) {
                    if (candidateSpeechStartSampleIndex == null) {
                        candidateSpeechStartSampleIndex = frameStartSampleIndex
                    }
                    consecutiveSpeechSamples += samplesInFrame
                    if (consecutiveSpeechSamples >= CAPTION_MIN_SPEECH_SAMPLES) {
                        inSpeech = true
                        currentSpeechStartSampleIndex = candidateSpeechStartSampleIndex ?: frameStartSampleIndex
                        lastSpeechSampleEndIndex = frameEndSampleIndex
                        consecutiveSilenceSamples = 0L
                    }
                } else {
                    candidateSpeechStartSampleIndex = null
                    consecutiveSpeechSamples = 0L
                }
            }
            consumedSamples = frameEndSampleIndex
        }
        if (inSpeech) {
            rawSpans += CaptionAudioSpan(
                startSampleIndex = currentSpeechStartSampleIndex,
                sampleCount = (consumedSamples - currentSpeechStartSampleIndex).coerceAtLeast(0L),
            )
        }
    }
    val normalizedSpans = normalizeCaptionAudioSpans(
        rawSpans = rawSpans,
        totalSamples = totalSamples,
    ).ifEmpty {
        listOf(
            CaptionAudioSpan(
                startSampleIndex = 0L,
                sampleCount = totalSamples,
            ),
        )
    }
    val totalAudioDurationSeconds = max(
        1L,
        (totalSamples + (CAPTION_AUDIO_SAMPLE_RATE - 1).toLong()) / CAPTION_AUDIO_SAMPLE_RATE.toLong(),
    )
    val speechDurationSeconds = max(
        1L,
        (normalizedSpans.sumOf(CaptionAudioSpan::sampleCount) + (CAPTION_AUDIO_SAMPLE_RATE - 1).toLong()) /
            CAPTION_AUDIO_SAMPLE_RATE.toLong(),
    )
    return CaptionAudioPlan(
        totalAudioDurationSeconds = totalAudioDurationSeconds,
        speechDurationSeconds = speechDurationSeconds,
        spans = normalizedSpans,
    )
}

internal fun buildCaptionAudioChunks(
    audioPlan: CaptionAudioPlan,
    maxChunkDurationSeconds: Int,
): List<CaptionAudioChunkDescriptor> {
    val maxChunkSamples = CAPTION_AUDIO_SAMPLE_RATE * maxChunkDurationSeconds.coerceAtLeast(1)
    return buildList {
        audioPlan.spans.forEach { span ->
            var chunkStartSampleIndex = span.startSampleIndex
            var remainingSamples = span.sampleCount
            while (remainingSamples > 0L) {
                val chunkSampleCount = minOf(remainingSamples, maxChunkSamples.toLong()).toInt()
                add(
                    CaptionAudioChunkDescriptor(
                        startSampleIndex = chunkStartSampleIndex,
                        sampleCount = chunkSampleCount,
                    ),
                )
                chunkStartSampleIndex += chunkSampleCount
                remainingSamples -= chunkSampleCount
            }
        }
    }
}

internal class CaptionPcmChunkReader(
    audioFile: File,
) : Closeable {
    private val randomAccessFile = RandomAccessFile(audioFile, "r")

    fun readChunk(descriptor: CaptionAudioChunkDescriptor): FloatArray {
        val chunkBytes = descriptor.sampleCount * PCM_BYTES_PER_SAMPLE
        val buffer = ByteArray(chunkBytes)
        randomAccessFile.seek(descriptor.startSampleIndex * PCM_BYTES_PER_SAMPLE.toLong())
        randomAccessFile.readFully(buffer)
        return pcm16LeToFloatArray(
            buffer = buffer,
            length = buffer.size,
        )
    }

    override fun close() {
        randomAccessFile.close()
    }
}

internal fun preferredWhisperThreadCount(
    availableProcessors: Int = Runtime.getRuntime().availableProcessors(),
    cpuMaxFrequenciesKhz: List<Int> = readCpuMaxFrequenciesKhz(),
): Int {
    val performanceCoreCount = clusteredPerformanceCoreCount(cpuMaxFrequenciesKhz)
    if (performanceCoreCount != null) {
        return performanceCoreCount.coerceIn(2, 5)
    }
    return availableProcessors
        .coerceAtLeast(2)
        .minus(1)
        .coerceIn(2, 4)
}

internal fun adaptiveWhisperAudioContextSize(
    chunkDurationSeconds: Long,
): Int = (
    ((chunkDurationSeconds.coerceAtLeast(1L).toFloat() / 30f) * 1_500f) + 128f
    )
    .roundToInt()
    .coerceIn(384, 1_500)

private fun normalizeCaptionAudioSpans(
    rawSpans: List<CaptionAudioSpan>,
    totalSamples: Long,
): List<CaptionAudioSpan> {
    if (rawSpans.isEmpty()) {
        return emptyList()
    }
    val paddedSpans = rawSpans
        .filter { span -> span.sampleCount > 0L }
        .map { span ->
            val paddedStart = (span.startSampleIndex - CAPTION_SEGMENTATION_PADDING_SAMPLES).coerceAtLeast(0L)
            val paddedEnd = (span.endSampleIndex + CAPTION_SEGMENTATION_PADDING_SAMPLES).coerceAtMost(totalSamples)
            CaptionAudioSpan(
                startSampleIndex = paddedStart,
                sampleCount = (paddedEnd - paddedStart).coerceAtLeast(0L),
            )
        }
        .sortedBy(CaptionAudioSpan::startSampleIndex)
    if (paddedSpans.isEmpty()) {
        return emptyList()
    }
    val mergedSpans = mutableListOf<CaptionAudioSpan>()
    paddedSpans.forEach { nextSpan ->
        val previousSpan = mergedSpans.lastOrNull()
        if (
            previousSpan != null &&
            nextSpan.startSampleIndex <= previousSpan.endSampleIndex + CAPTION_SEGMENTATION_MERGE_GAP_SAMPLES
        ) {
            val mergedEnd = max(previousSpan.endSampleIndex, nextSpan.endSampleIndex)
            mergedSpans[mergedSpans.lastIndex] = previousSpan.copy(
                sampleCount = (mergedEnd - previousSpan.startSampleIndex).coerceAtLeast(0L),
            )
        } else {
            mergedSpans += nextSpan
        }
    }
    return mergedSpans
}

private fun clusteredPerformanceCoreCount(
    cpuMaxFrequenciesKhz: List<Int>,
): Int? {
    val positiveFrequencies = cpuMaxFrequenciesKhz.filter { frequency -> frequency > 0 }
    if (positiveFrequencies.isEmpty()) {
        return null
    }
    val clusters = positiveFrequencies
        .groupingBy { frequency -> frequency }
        .eachCount()
        .toList()
        .sortedByDescending { (frequency, _) -> frequency }
    val (highestFrequency, highestCount) = clusters.first()
    if (clusters.size == 1) {
        return highestCount
    }
    val (secondHighestFrequency, secondHighestCount) = clusters[1]
    return if (
        highestCount == 1 &&
        secondHighestCount >= 2 &&
        secondHighestFrequency * 100 >= highestFrequency * 85
    ) {
        highestCount + secondHighestCount
    } else {
        highestCount
    }
}

private fun readCpuMaxFrequenciesKhz(): List<Int> = runCatching {
    File("/sys/devices/system/cpu")
        .listFiles()
        .orEmpty()
        .filter { file -> file.isDirectory && file.name.matches(Regex("cpu\\d+")) }
        .mapNotNull { cpuDirectory ->
            File(cpuDirectory, "cpufreq/cpuinfo_max_freq")
                .takeIf(File::exists)
                ?.readText()
                ?.trim()
                ?.toIntOrNull()
        }
}.getOrDefault(emptyList())

private fun meanAbsoluteAmplitude(
    buffer: ByteArray,
    length: Int,
): Float {
    val sampleCount = length / PCM_BYTES_PER_SAMPLE
    if (sampleCount <= 0) {
        return 0f
    }
    var sum = 0f
    var inputIndex = 0
    repeat(sampleCount) {
        val low = buffer[inputIndex].toInt() and 0xff
        val high = buffer[inputIndex + 1].toInt()
        val shortValue = ((high shl 8) or low).toShort()
        sum += kotlin.math.abs(shortValue / 32768f)
        inputIndex += PCM_BYTES_PER_SAMPLE
    }
    return sum / sampleCount.toFloat()
}

private fun readCaptionAudioFrame(
    input: BufferedInputStream,
    buffer: ByteArray,
): Int {
    var totalBytesRead = 0
    while (totalBytesRead < buffer.size) {
        val bytesRead = input.read(buffer, totalBytesRead, buffer.size - totalBytesRead)
        if (bytesRead <= 0) {
            break
        }
        totalBytesRead += bytesRead
    }
    return totalBytesRead
}

private const val CAPTION_AUDIO_SAMPLE_RATE = 16_000
private const val PCM_BYTES_PER_SAMPLE = 2
private const val CAPTION_SEGMENTATION_FRAME_SAMPLES = 320
private const val CAPTION_SEGMENTATION_FRAME_BYTES = CAPTION_SEGMENTATION_FRAME_SAMPLES * 2
private const val CAPTION_SEGMENTATION_START_THRESHOLD = 0.012f
private const val CAPTION_SEGMENTATION_END_THRESHOLD = 0.006f
private const val CAPTION_MIN_SPEECH_SAMPLES = 2_880L
private const val CAPTION_MIN_SILENCE_SAMPLES = 6_720L
private const val CAPTION_SEGMENTATION_PADDING_SAMPLES = 2_880L
private const val CAPTION_SEGMENTATION_MERGE_GAP_SAMPLES = 5_600L
