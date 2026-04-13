package com.looktube.app

import android.content.Context
import ai.moonshine.voice.JNI
import ai.moonshine.voice.Transcriber
import com.looktube.data.GeneratedCaptionDocument
import com.looktube.data.LocalCaptionGenerationRequest
import com.looktube.data.LocalCaptionGenerator
import com.looktube.data.LocalCaptionModelManager
import com.looktube.model.CaptionGenerationPhase
import com.looktube.model.CaptionGenerationStatus
import com.looktube.model.LocalCaptionModelState
import com.looktube.model.MoonshineBaseEnglishCaptionModel
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class ManagedMoonshineCaptionModelManager(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LocalCaptionModelManager {
    private val modelDirectory = File(context.filesDir, "captions/models/moonshine-base-en").apply { mkdirs() }
    private val verificationScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val modelStateFlow = MutableStateFlow(LocalCaptionModelState(model = MoonshineBaseEnglishCaptionModel))

    override val modelState: StateFlow<LocalCaptionModelState> = modelStateFlow.asStateFlow()

    init {
        verificationScope.launch {
            refreshStateFromDisk()
        }
    }

    override suspend fun downloadDefaultModel() {
        if (modelStateFlow.value.isDownloading) {
            return
        }
        withContext(ioDispatcher) {
            verifiedStateForInstalledModel()?.let { state ->
                modelStateFlow.value = state
                if (state.isReady) {
                    return@withContext
                }
            }
            val tempDirectory = File(modelDirectory.parentFile, "${modelDirectory.name}.download").apply {
                deleteRecursively()
                mkdirs()
            }
            var downloadedBytes = 0L
            try {
                modelDirectory.mkdirs()
                val totalBytes = verifiedModelAssetsTotalBytes(MoonshineBaseEnglishCaptionModelAssets)
                modelStateFlow.value = LocalCaptionModelState(
                    model = MoonshineBaseEnglishCaptionModel,
                    isDownloading = true,
                    downloadedBytes = 0L,
                    totalBytes = totalBytes,
                )
                MoonshineBaseEnglishCaptionModelAssets.forEach { asset ->
                    val tempFile = File(tempDirectory, asset.fileName)
                    downloadVerifiedModelAsset(
                        asset = asset,
                        outputFile = tempFile,
                        onChunkDownloaded = { chunkBytes ->
                            downloadedBytes += chunkBytes
                            modelStateFlow.value = LocalCaptionModelState(
                                model = MoonshineBaseEnglishCaptionModel,
                                isDownloading = true,
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes,
                            )
                        },
                    )
                }
                modelDirectory.deleteRecursively()
                tempDirectory.copyRecursively(modelDirectory, overwrite = true)
                tempDirectory.deleteRecursively()
                modelStateFlow.value = requireNotNull(verifiedStateForInstalledModel()) {
                    "Verified Moonshine model state should exist after install."
                }
            } catch (exception: Exception) {
                tempDirectory.deleteRecursively()
                modelStateFlow.value = LocalCaptionModelState(
                    model = MoonshineBaseEnglishCaptionModel,
                    errorMessage = exception.message ?: "Moonshine model download failed.",
                )
                throw exception
            }
        }
    }

    private suspend fun refreshStateFromDisk() = withContext(ioDispatcher) {
        modelStateFlow.value = verifiedStateForInstalledModel() ?: LocalCaptionModelState(model = MoonshineBaseEnglishCaptionModel)
    }

    private fun verifiedStateForInstalledModel(): LocalCaptionModelState? {
        val modelFiles = MoonshineBaseEnglishCaptionModelAssets.map { asset ->
            asset to File(modelDirectory, asset.fileName)
        }
        if (modelFiles.all { (asset, file) -> file.matchesVerifiedModelAsset(asset) }) {
            val totalBytes = modelFiles.sumOf { (_, file) -> file.length() }
            return LocalCaptionModelState(
                model = MoonshineBaseEnglishCaptionModel,
                downloadedBytes = totalBytes,
                totalBytes = totalBytes,
                localPath = modelDirectory.absolutePath,
            )
        }
        val hasAnyInstalledContent = modelFiles.any { (_, file) -> file.exists() } ||
            modelDirectory.listFiles().orEmpty().isNotEmpty()
        if (hasAnyInstalledContent) {
            modelDirectory.deleteRecursively()
            return LocalCaptionModelState(
                model = MoonshineBaseEnglishCaptionModel,
                errorMessage = "Saved Moonshine model files failed integrity verification and were removed. Download them again.",
            )
        }
        return null
    }
}

internal class MoonshineLocalCaptionGenerator(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LocalCaptionGenerator {
    private val cacheDirectory = File(context.cacheDir, "captions")
    private val audioExtractor = RemoteMediaAudioExtractor()

    private companion object {
        private const val MOONSHINE_SAMPLE_RATE = 16_000
        private const val TRANSCRIPTION_CHUNK_SECONDS = 30
    }

    override suspend fun generate(
        request: LocalCaptionGenerationRequest,
        onProgress: (CaptionGenerationStatus) -> Unit,
    ): GeneratedCaptionDocument = withContext(ioDispatcher) {
        val workingDirectory = File(cacheDirectory, "moonshine-${System.currentTimeMillis()}").apply { mkdirs() }
        val audioFile = File(workingDirectory, "audio.pcm")
        try {
            onProgress(
                CaptionGenerationStatus(
                    phase = CaptionGenerationPhase.ExtractingAudio,
                    message = "Extracting mono audio for Moonshine caption generation…",
                ),
            )
            audioExtractor.extractToPcm16MonoFile(
                playbackUrl = request.playbackUrl,
                outputFile = audioFile,
                onProgress = { progress ->
                    onProgress(extractionCaptionStatus(progress))
                },
            )
            val audioPlan = buildCaptionAudioPlan(audioFile)
            onProgress(
                captionAudioPlanStatus(
                    engineLabel = "Moonshine",
                    audioPlan = audioPlan,
                ),
            )
            val chunks = buildCaptionAudioChunks(
                audioPlan = audioPlan,
                maxChunkDurationSeconds = TRANSCRIPTION_CHUNK_SECONDS,
            )
            require(chunks.isNotEmpty()) {
                "Moonshine caption generation could not plan any audio chunks."
            }
            val totalAudioDurationSeconds = audioPlan.speechDurationSeconds
            val transcriptionStartedAtNanos = System.nanoTime()
            var processedSpeechSamples = 0L
            var lastCompletedChunkDurationSeconds: Long? = null
            var lastCompletedChunkWallSeconds: Long? = null
            val segments = mutableListOf<CaptionSegment>()
            CaptionPcmChunkReader(audioFile).use { chunkReader ->
                chunks.forEachIndexed { chunkIndex, chunk ->
                    onProgress(
                        transcriptionCaptionStatus(
                            completedChunkCount = chunkIndex,
                            totalChunks = chunks.size,
                            processedAudioSeconds = processedSpeechSamples / MOONSHINE_SAMPLE_RATE.toLong(),
                            totalAudioDurationSeconds = totalAudioDurationSeconds,
                            elapsedRealtimeSeconds = elapsedSince(transcriptionStartedAtNanos),
                            lastCompletedChunkDurationSeconds = lastCompletedChunkDurationSeconds,
                            lastCompletedChunkWallSeconds = lastCompletedChunkWallSeconds,
                        ),
                    )
                    val chunkStartedAtNanos = System.nanoTime()
                    val transcript = MoonshineTranscriberPool.transcribe(
                        modelPath = request.modelPath,
                        audioSamples = chunkReader.readChunk(chunk),
                    )
                    val chunkWallSeconds = elapsedSince(chunkStartedAtNanos)
                    transcript.lines
                        .orEmpty()
                        .mapNotNull { line ->
                            val text = line.text?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
                            if (text.isBlank()) {
                                null
                            } else {
                                CaptionSegment(
                                    startMs = chunk.startMs + (line.startTime * 1_000f).toLong(),
                                    endMs = chunk.startMs + ((line.startTime + line.duration) * 1_000f).toLong(),
                                    text = text,
                                )
                            }
                        }
                        .filter { segment -> segment.endMs > segment.startMs }
                        .forEach { segment ->
                            segments += segment
                        }
                    processedSpeechSamples += chunk.sampleCount.toLong()
                    lastCompletedChunkDurationSeconds = chunk.durationSeconds
                    lastCompletedChunkWallSeconds = chunkWallSeconds
                    onProgress(
                        transcriptionCaptionStatus(
                            completedChunkCount = chunkIndex + 1,
                            totalChunks = chunks.size,
                            processedAudioSeconds = processedSpeechSamples / MOONSHINE_SAMPLE_RATE.toLong(),
                            totalAudioDurationSeconds = totalAudioDurationSeconds,
                            elapsedRealtimeSeconds = elapsedSince(transcriptionStartedAtNanos),
                            lastCompletedChunkDurationSeconds = lastCompletedChunkDurationSeconds,
                            lastCompletedChunkWallSeconds = lastCompletedChunkWallSeconds,
                        ),
                    )
                }
            }
            if (segments.isEmpty()) {
                throw IOException("Moonshine did not return any caption segments.")
            }
            GeneratedCaptionDocument(
                webVtt = buildWebVtt(segments),
                languageTag = MoonshineBaseEnglishCaptionModel.languageTag,
                label = "English (generated with Moonshine)",
                engineId = MoonshineBaseEnglishCaptionModel.engine.id,
            )
        } finally {
            workingDirectory.deleteRecursively()
        }
    }
}

private object MoonshineTranscriberPool {
    @Volatile
    private var cachedModelPath: String? = null
    @Volatile
    private var cachedTranscriber: Transcriber? = null

    @Synchronized
    fun transcribe(modelPath: String, audioSamples: FloatArray) =
        obtainTranscriber(modelPath).transcribeWithoutStreaming(audioSamples, MOONSHINE_SAMPLE_RATE)

    @Synchronized
    private fun obtainTranscriber(modelPath: String): Transcriber {
        cachedTranscriber?.takeIf { cachedModelPath == modelPath }?.let { transcriber ->
            return transcriber
        }
        val transcriber = Transcriber().apply {
            loadFromFiles(modelPath, JNI.MOONSHINE_MODEL_ARCH_BASE)
        }
        cachedModelPath = modelPath
        cachedTranscriber = transcriber
        return transcriber
    }

    private const val MOONSHINE_SAMPLE_RATE = 16_000
}
