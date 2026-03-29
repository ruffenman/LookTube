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
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

internal class ManagedMoonshineCaptionModelManager(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LocalCaptionModelManager {
    private val modelDirectory = File(context.filesDir, "captions/models/moonshine-base-en").apply { mkdirs() }
    private val modelFiles = listOf(
        ModelAsset(
            fileName = "decoder_model_merged.ort",
            url = "https://huggingface.co/UsefulSensors/moonshine/resolve/main/onnx/merged/base/quantized/decoder_model_merged.ort",
        ),
        ModelAsset(
            fileName = "encoder_model.ort",
            url = "https://huggingface.co/UsefulSensors/moonshine/resolve/main/onnx/merged/base/quantized/encoder_model.ort",
        ),
        ModelAsset(
            fileName = "tokenizer.bin",
            url = "https://huggingface.co/UsefulSensors/moonshine/resolve/main/onnx/merged/base/quantized/tokenizer.bin",
        ),
    )
    private val modelStateFlow = MutableStateFlow(initialState())

    override val modelState: StateFlow<LocalCaptionModelState> = modelStateFlow.asStateFlow()

    override suspend fun downloadDefaultModel() {
        if (modelStateFlow.value.isDownloading || modelStateFlow.value.isReady) {
            return
        }
        withContext(ioDispatcher) {
            val tempDirectory = File(modelDirectory.parentFile, "${modelDirectory.name}.download").apply {
                deleteRecursively()
                mkdirs()
            }
            var downloadedBytes = 0L
            try {
                modelDirectory.mkdirs()
                val totalBytes = modelFiles.sumOf { asset -> asset.contentLength() ?: 0L }
                    .takeIf { bytes -> bytes > 0L }
                modelStateFlow.value = LocalCaptionModelState(
                    model = MoonshineBaseEnglishCaptionModel,
                    isDownloading = true,
                    downloadedBytes = 0L,
                    totalBytes = totalBytes,
                )
                modelFiles.forEach { asset ->
                    val tempFile = File(tempDirectory, asset.fileName)
                    downloadFile(
                        url = asset.url,
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
                val finalSize = modelFiles.sumOf { asset -> File(modelDirectory, asset.fileName).length() }
                modelStateFlow.value = LocalCaptionModelState(
                    model = MoonshineBaseEnglishCaptionModel,
                    downloadedBytes = finalSize,
                    totalBytes = finalSize,
                    localPath = modelDirectory.absolutePath,
                )
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

    private fun initialState(): LocalCaptionModelState =
        if (modelFiles.all { asset -> File(modelDirectory, asset.fileName).exists() }) {
            val totalBytes = modelFiles.sumOf { asset -> File(modelDirectory, asset.fileName).length() }
            LocalCaptionModelState(
                model = MoonshineBaseEnglishCaptionModel,
                downloadedBytes = totalBytes,
                totalBytes = totalBytes,
                localPath = modelDirectory.absolutePath,
            )
        } else {
            LocalCaptionModelState(model = MoonshineBaseEnglishCaptionModel)
        }
}

internal class MoonshineLocalCaptionGenerator(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LocalCaptionGenerator {
    private val cacheDirectory = File(context.cacheDir, "captions")
    private val audioExtractor = RemoteMediaAudioExtractor()

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
            )
            onProgress(
                CaptionGenerationStatus(
                    phase = CaptionGenerationPhase.Transcribing,
                    message = "Transcribing audio with Moonshine on this device…",
                    progressFraction = 0f,
                ),
            )
            val transcript = MoonshineTranscriberPool.transcribe(
                modelPath = request.modelPath,
                audioSamples = pcm16FileToFloatArray(audioFile),
            )
            val segments = transcript.lines
                .orEmpty()
                .mapNotNull { line ->
                    val text = line.text?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
                    if (text.isBlank()) {
                        null
                    } else {
                        CaptionSegment(
                            startMs = (line.startTime * 1_000f).toLong(),
                            endMs = ((line.startTime + line.duration) * 1_000f).toLong(),
                            text = text,
                        )
                    }
                }
                .filter { segment -> segment.endMs > segment.startMs }
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

private data class ModelAsset(
    val fileName: String,
    val url: String,
)

private fun ModelAsset.contentLength(): Long? = runCatching {
    (URL(url).openConnection() as HttpURLConnection).run {
        requestMethod = "HEAD"
        connectTimeout = 15_000
        readTimeout = 15_000
        connect()
        try {
            contentLengthLong.takeIf { length -> length > 0L }
        } finally {
            disconnect()
        }
    }
}.getOrNull()

private fun downloadFile(
    url: String,
    outputFile: File,
    onChunkDownloaded: (Long) -> Unit,
): Long {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = 15_000
        readTimeout = 60_000
        requestMethod = "GET"
        connect()
    }
    try {
        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            throw IOException("Model download returned HTTP $responseCode.")
        }
        var downloadedBytes = 0L
        BufferedInputStream(connection.inputStream).use { input ->
            BufferedOutputStream(FileOutputStream(outputFile)).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) {
                        break
                    }
                    output.write(buffer, 0, read)
                    downloadedBytes += read
                    onChunkDownloaded(read.toLong())
                }
            }
        }
        return downloadedBytes
    } finally {
        connection.disconnect()
    }
}

private fun pcm16FileToFloatArray(file: File): FloatArray {
    val bytes = FileInputStream(file).use { input -> input.readBytes() }
    return pcm16LeToFloatArray(
        buffer = bytes,
        length = bytes.size,
    )
}
