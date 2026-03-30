package com.looktube.app

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.looktube.data.GeneratedCaptionDocument
import com.looktube.data.LocalCaptionEngineRegistry
import com.looktube.data.LocalCaptionGenerationRequest
import com.looktube.data.LocalCaptionGenerator
import com.looktube.data.LocalCaptionModelManager
import com.looktube.data.VideoCaptionStore
import com.looktube.model.CaptionGenerationPhase
import com.looktube.model.CaptionGenerationStatus
import com.looktube.model.DefaultLocalCaptionModel
import com.looktube.model.LocalCaptionEngine
import com.looktube.model.LocalCaptionModelState
import com.looktube.model.VideoCaptionTrack
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64
import java.util.Collections
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
internal data class LocalCaptionEngineRuntime(
    val engine: LocalCaptionEngine,
    val modelManager: LocalCaptionModelManager,
    val generator: LocalCaptionGenerator,
)

internal class SelectableLocalCaptionEngineRegistry(
    runtimes: List<LocalCaptionEngineRuntime>,
    defaultEngineId: String = runtimes.first().engine.id,
) : LocalCaptionEngineRegistry {
    private val runtimeById = runtimes.associateBy { runtime -> runtime.engine.id }
    private val registryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val selectedEngineState = MutableStateFlow(runtimeById.getValue(defaultEngineId).engine)
    private val modelStateFlow = MutableStateFlow(
        runtimeById.getValue(defaultEngineId).modelManager.modelState.value,
    )

    override val availableEngines: StateFlow<List<LocalCaptionEngine>> =
        MutableStateFlow(runtimes.map(LocalCaptionEngineRuntime::engine)).asStateFlow()
    override val selectedEngine: StateFlow<LocalCaptionEngine> = selectedEngineState.asStateFlow()
    override val modelState: StateFlow<LocalCaptionModelState> = modelStateFlow.asStateFlow()

    init {
        registryScope.launch {
            selectedEngineState.collectLatest { engine ->
                runtimeById.getValue(engine.id).modelManager.modelState.collect { state ->
                    modelStateFlow.value = state
                }
            }
        }
    }

    override suspend fun downloadSelectedModel() {
        selectedRuntime().modelManager.downloadDefaultModel()
    }

    override suspend fun generate(
        request: LocalCaptionGenerationRequest,
        onProgress: (CaptionGenerationStatus) -> Unit,
    ): GeneratedCaptionDocument = selectedRuntime().generator.generate(request, onProgress)

    override fun selectEngine(engineId: String) {
        runtimeById[engineId]?.let { runtime ->
            selectedEngineState.value = runtime.engine
        }
    }

    private fun selectedRuntime(): LocalCaptionEngineRuntime = runtimeById.getValue(selectedEngineState.value.id)
}

internal class ManagedLocalCaptionModelManager(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LocalCaptionModelManager {
    private val modelDirectory = File(context.filesDir, "captions/models").apply { mkdirs() }
    private val modelFile = File(
        modelDirectory,
        Uri.parse(DefaultLocalCaptionModel.downloadUrl).lastPathSegment ?: "ggml-base.en-q5_1.bin",
    )
    private val modelStateFlow = MutableStateFlow(initialState())

    override val modelState: StateFlow<LocalCaptionModelState> = modelStateFlow.asStateFlow()

    override suspend fun downloadDefaultModel() {
        if (modelStateFlow.value.isDownloading || modelStateFlow.value.isReady) {
            return
        }
        withContext(ioDispatcher) {
            val tempFile = File(modelDirectory, "${modelFile.name}.download")
            try {
                modelDirectory.mkdirs()
                val connection = (URL(DefaultLocalCaptionModel.downloadUrl).openConnection() as HttpURLConnection).apply {
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
                    val totalBytes = connection.contentLengthLong.takeIf { length -> length > 0L }
                    modelStateFlow.value = LocalCaptionModelState(
                        model = DefaultLocalCaptionModel,
                        isDownloading = true,
                        downloadedBytes = 0L,
                        totalBytes = totalBytes,
                    )
                    BufferedInputStream(connection.inputStream).use { input ->
                        BufferedOutputStream(FileOutputStream(tempFile)).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var downloadedBytes = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) {
                                    break
                                }
                                output.write(buffer, 0, read)
                                downloadedBytes += read
                                modelStateFlow.value = LocalCaptionModelState(
                                    model = DefaultLocalCaptionModel,
                                    isDownloading = true,
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes,
                                )
                            }
                        }
                    }
                } finally {
                    connection.disconnect()
                }
                if (modelFile.exists()) {
                    modelFile.delete()
                }
                tempFile.copyTo(modelFile, overwrite = true)
                tempFile.delete()
                modelStateFlow.value = LocalCaptionModelState(
                    model = DefaultLocalCaptionModel,
                    downloadedBytes = modelFile.length(),
                    totalBytes = modelFile.length(),
                    localPath = modelFile.absolutePath,
                )
            } catch (exception: Exception) {
                tempFile.delete()
                modelStateFlow.value = LocalCaptionModelState(
                    model = DefaultLocalCaptionModel,
                    errorMessage = exception.message ?: "Local caption model download failed.",
                )
                throw exception
            }
        }
    }

    private fun initialState(): LocalCaptionModelState =
        if (modelFile.exists()) {
            LocalCaptionModelState(
                model = DefaultLocalCaptionModel,
                downloadedBytes = modelFile.length(),
                totalBytes = modelFile.length(),
                localPath = modelFile.absolutePath,
            )
        } else {
            LocalCaptionModelState(model = DefaultLocalCaptionModel)
        }
}

internal class FileVideoCaptionStore(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : VideoCaptionStore {
    val rootDirectory: File = File(context.filesDir, "captions/tracks").apply { mkdirs() }
    private val captionsState = MutableStateFlow(loadExistingTracks())

    override val captions: StateFlow<Map<String, VideoCaptionTrack>> = captionsState.asStateFlow()

    override suspend fun saveGeneratedCaption(
        videoId: String,
        document: GeneratedCaptionDocument,
        generatedAtEpochMillis: Long,
    ): VideoCaptionTrack = withContext(ioDispatcher) {
        rootDirectory.mkdirs()
        val outputFile = fileForVideo(videoId)
        outputFile.writeText(document.webVtt, UTF_8)
        outputFile.setLastModified(generatedAtEpochMillis)
        val track = VideoCaptionTrack(
            videoId = videoId,
            filePath = outputFile.absolutePath,
            generatedAtEpochMillis = generatedAtEpochMillis,
            languageTag = document.languageTag,
            label = document.label,
            engineId = document.engineId,
        )
        captionsState.value = captionsState.value.toMutableMap().apply {
            put(videoId, track)
        }
        track
    }

    override suspend fun clear() = withContext(ioDispatcher) {
        rootDirectory.deleteRecursively()
        rootDirectory.mkdirs()
        captionsState.value = emptyMap()
    }

    private fun loadExistingTracks(): Map<String, VideoCaptionTrack> {
        if (!rootDirectory.exists()) {
            return emptyMap()
        }
        return rootDirectory.listFiles()
            .orEmpty()
            .filter { candidate -> candidate.isFile && candidate.extension.equals("vtt", ignoreCase = true) }
            .mapNotNull { file ->
                decodeVideoId(file.nameWithoutExtension)?.let { videoId ->
                    videoId to VideoCaptionTrack(
                        videoId = videoId,
                        filePath = file.absolutePath,
                        generatedAtEpochMillis = file.lastModified(),
                    )
                }
            }
            .toMap()
    }

    private fun fileForVideo(videoId: String): File = File(rootDirectory, "${encodeVideoId(videoId)}.vtt")

    private fun encodeVideoId(videoId: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(videoId.toByteArray(UTF_8))

    private fun decodeVideoId(encodedVideoId: String): String? =
        runCatching {
            String(Base64.getUrlDecoder().decode(encodedVideoId), UTF_8)
        }.getOrNull()
}

interface LocalCaptionCastUrlProvider {
    fun start()
    fun stop()
    fun buildRemoteCaptionUrl(localUri: Uri): String?
}

internal class LocalCaptionCastHttpServer(
    private val captionRootDirectory: File,
) : LocalCaptionCastUrlProvider {
    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var serverSocket: ServerSocket? = null
    @Volatile
    private var sessionToken: String = UUID.randomUUID().toString()

    override fun start() {
        if (serverSocket?.isBound == true && serverSocket?.isClosed == false) {
            return
        }
        synchronized(this) {
            if (serverSocket?.isBound == true && serverSocket?.isClosed == false) {
                return
            }
            sessionToken = UUID.randomUUID().toString()
            serverSocket = ServerSocket(0).also { socket ->
                serverScope.launch {
                    acceptLoop(socket)
                }
            }
        }
    }

    override fun stop() {
        serverSocket?.close()
        serverSocket = null
        serverScope.cancel()
    }

    override fun buildRemoteCaptionUrl(localUri: Uri): String? {
        start()
        val localPath = localUri.path ?: return null
        val file = File(localPath).canonicalFile
        val root = captionRootDirectory.canonicalFile
        if (!file.exists() || !file.path.startsWith(root.path + File.separator)) {
            return null
        }
        val hostAddress = currentSiteLocalAddress()?.hostAddress ?: return null
        val port = serverSocket?.localPort ?: return null
        return "http://$hostAddress:$port/captions/${file.name}?token=$sessionToken"
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (!socket.isClosed) {
            val client = runCatching { socket.accept() }.getOrNull() ?: continue
            serverScope.launch {
                handleClient(client)
            }
        }
    }

    private fun handleClient(client: Socket) {
        client.use { socket ->
            socket.soTimeout = 5_000
            val reader = socket.getInputStream().bufferedReader(UTF_8)
            val requestLine = reader.readLine() ?: return
            while (true) {
                val headerLine = reader.readLine() ?: return
                if (headerLine.isBlank()) {
                    break
                }
            }
            val parts = requestLine.split(' ')
            if (parts.size < 2 || parts[0] != "GET") {
                writeResponse(
                    socket = socket,
                    status = "405 Method Not Allowed",
                    body = ByteArray(0),
                )
                return
            }
            val requestTarget = parts[1]
            val path = requestTarget.substringBefore('?')
            val token = requestTarget.substringAfter("token=", missingDelimiterValue = "")
            if (token != sessionToken || !path.startsWith("/captions/")) {
                writeResponse(
                    socket = socket,
                    status = "403 Forbidden",
                    body = ByteArray(0),
                )
                return
            }
            val fileName = path.removePrefix("/captions/")
            val requestedFile = File(captionRootDirectory, fileName).canonicalFile
            val root = captionRootDirectory.canonicalFile
            if (!requestedFile.exists() || !requestedFile.path.startsWith(root.path + File.separator)) {
                writeResponse(
                    socket = socket,
                    status = "404 Not Found",
                    body = ByteArray(0),
                )
                return
            }
            val body = requestedFile.readBytes()
            writeResponse(
                socket = socket,
                status = "200 OK",
                contentType = "text/vtt; charset=utf-8",
                body = body,
            )
        }
    }

    private fun writeResponse(
        socket: Socket,
        status: String,
        contentType: String = "text/plain; charset=utf-8",
        body: ByteArray,
    ) {
        BufferedOutputStream(socket.getOutputStream()).use { output ->
            output.write("HTTP/1.1 $status\r\n".toByteArray(UTF_8))
            output.write("Connection: close\r\n".toByteArray(UTF_8))
            output.write("Content-Type: $contentType\r\n".toByteArray(UTF_8))
            output.write("Content-Length: ${body.size}\r\n".toByteArray(UTF_8))
            output.write("\r\n".toByteArray(UTF_8))
            output.write(body)
            output.flush()
        }
    }

    private fun currentSiteLocalAddress(): InetAddress? =
        (NetworkInterface.getNetworkInterfaces()?.let(Collections::list).orEmpty())
            .asSequence()
            .filter { networkInterface -> networkInterface.isUp && !networkInterface.isLoopback }
            .flatMap { networkInterface -> Collections.list(networkInterface.inetAddresses).asSequence() }
            .firstOrNull { address ->
                address is Inet4Address &&
                    !address.isLoopbackAddress &&
                    address.isSiteLocalAddress
            }
}

internal class OnDeviceLocalCaptionGenerator(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LocalCaptionGenerator {
    private val cacheDirectory = File(context.cacheDir, "captions")
    private val audioExtractor = RemoteMediaAudioExtractor()

    override suspend fun generate(
        request: LocalCaptionGenerationRequest,
        onProgress: (CaptionGenerationStatus) -> Unit,
    ): GeneratedCaptionDocument = withContext(ioDispatcher) {
        val workingDirectory = File(cacheDirectory, UUID.randomUUID().toString()).apply { mkdirs() }
        val audioFile = File(workingDirectory, "audio.pcm")
        try {
            onProgress(
                CaptionGenerationStatus(
                    phase = CaptionGenerationPhase.ExtractingAudio,
                    message = "Preparing audio extraction for offline caption generation…",
                ),
            )
            audioExtractor.extractToPcm16MonoFile(
                playbackUrl = request.playbackUrl,
                outputFile = audioFile,
                onProgress = { progress ->
                    onProgress(extractionCaptionStatus(progress))
                },
            )
            val segments = transcribeAudioFile(
                modelPath = request.modelPath,
                audioFile = audioFile,
                onProgress = onProgress,
            )
            if (segments.isEmpty()) {
                throw IOException("The offline model did not return any caption segments.")
            }
            GeneratedCaptionDocument(
                webVtt = buildWebVtt(segments),
                languageTag = DefaultLocalCaptionModel.languageTag,
                label = "English (generated with Whisper.cpp)",
                engineId = DefaultLocalCaptionModel.engine.id,
            )
        } finally {
            workingDirectory.deleteRecursively()
        }
    }

    private fun transcribeAudioFile(
        modelPath: String,
        audioFile: File,
        onProgress: (CaptionGenerationStatus) -> Unit,
    ): List<CaptionSegment> {
        val totalChunks = max(
            1,
            ((audioFile.length() + (PCM_CHUNK_BYTES - 1)) / PCM_CHUNK_BYTES).toInt(),
        )
        val totalAudioDurationSeconds = max(
            1L,
            (audioFile.length() + (PCM_BYTES_PER_SECOND - 1)) / PCM_BYTES_PER_SECOND,
        )
        val transcriptionStartedAtNanos = System.nanoTime()
        val segments = mutableListOf<CaptionSegment>()
        onProgress(
            transcriptionCaptionStatus(
                completedChunkCount = 0,
                totalChunks = totalChunks,
                processedAudioSeconds = 0L,
                totalAudioDurationSeconds = totalAudioDurationSeconds,
            ),
        )
        FileInputStream(audioFile).use { input ->
            val chunkBuffer = ByteArray(PCM_CHUNK_BYTES)
            var chunkIndex = 0
            var chunkOffsetMs = 0L
            while (true) {
                val bytesRead = readChunk(input, chunkBuffer)
                if (bytesRead <= 0) {
                    break
                }
                val audioSamples = pcm16LeToFloatArray(
                    buffer = chunkBuffer,
                    length = bytesRead,
                )
                val currentChunkDurationSeconds = max(
                    1L,
                    (audioSamples.size.toLong() + (TARGET_SAMPLE_RATE - 1).toLong()) / TARGET_SAMPLE_RATE.toLong(),
                )
                val processedAudioSeconds = chunkOffsetMs / 1_000L
                val chunkSegments = WhisperNativeBridge.transcribe(
                    modelPath = modelPath,
                    audioSamples = audioSamples,
                    numThreads = recommendedThreadCount(),
                    onProgressPercent = { progressPercent ->
                        onProgress(
                            transcriptionCaptionStatus(
                                completedChunkCount = chunkIndex,
                                totalChunks = totalChunks,
                                processedAudioSeconds = processedAudioSeconds,
                                totalAudioDurationSeconds = totalAudioDurationSeconds,
                                activeChunkDurationSeconds = currentChunkDurationSeconds,
                                activeChunkProgressPercent = progressPercent,
                                elapsedRealtimeSeconds = elapsedSince(transcriptionStartedAtNanos),
                            ),
                        )
                    },
                )
                chunkSegments.forEach { segment ->
                    segments += segment.copy(
                        startMs = chunkOffsetMs + segment.startMs,
                        endMs = chunkOffsetMs + segment.endMs,
                    )
                }
                chunkIndex += 1
                chunkOffsetMs += (audioSamples.size * 1_000L) / TARGET_SAMPLE_RATE
                onProgress(
                    transcriptionCaptionStatus(
                        completedChunkCount = chunkIndex,
                        totalChunks = totalChunks,
                        processedAudioSeconds = chunkOffsetMs / 1_000L,
                        totalAudioDurationSeconds = totalAudioDurationSeconds,
                        elapsedRealtimeSeconds = elapsedSince(transcriptionStartedAtNanos),
                    ),
                )
            }
        }
        return segments
            .filter { segment -> segment.endMs > segment.startMs && segment.text.isNotBlank() }
            .map { segment ->
                segment.copy(
                    text = segment.text.replace(Regex("\\s+"), " ").trim(),
                )
            }
    }

    private fun recommendedThreadCount(): Int =
        Runtime.getRuntime().availableProcessors()
            .coerceAtLeast(2)
            .minus(1)
            .coerceIn(1, 4)

    companion object {
        private const val TARGET_SAMPLE_RATE = 16_000
        // Whisper already decodes internally in roughly 30-second windows, so much larger
        // outer chunks increase wall time on-device without improving progress fidelity.
        private const val TRANSCRIPTION_CHUNK_SECONDS = 30
        private const val PCM_CHUNK_BYTES = TARGET_SAMPLE_RATE * TRANSCRIPTION_CHUNK_SECONDS * 2
        private const val PCM_BYTES_PER_SECOND = TARGET_SAMPLE_RATE * 2L
    }
}

internal data class AudioExtractionProgress(
    val currentPositionUs: Long,
    val durationUs: Long?,
    val decodedBytes: Long,
)

internal data class CaptionSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

internal fun extractionCaptionStatus(progress: AudioExtractionProgress): CaptionGenerationStatus {
    val currentPositionSeconds = (progress.currentPositionUs / 1_000_000L).coerceAtLeast(0L)
    val durationSeconds = progress.durationUs
        ?.takeIf { it > 0L }
        ?.let { durationUs -> (durationUs / 1_000_000L).coerceAtLeast(1L) }
    val message = durationSeconds?.let { totalSeconds ->
        "Extracting mono audio for offline caption generation… ${formatCaptionProgressTime(currentPositionSeconds)} / ${formatCaptionProgressTime(totalSeconds)}"
    } ?: "Extracting mono audio for offline caption generation… ${formatCaptionProgressTime(currentPositionSeconds)} decoded so far"
    val progressFraction = durationSeconds?.let { totalSeconds ->
        (CAPTION_EXTRACTION_PROGRESS_START + (
            (currentPositionSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f) *
                CAPTION_EXTRACTION_PROGRESS_RANGE
            ))
            .coerceIn(
                CAPTION_EXTRACTION_PROGRESS_START,
                CAPTION_EXTRACTION_PROGRESS_START + CAPTION_EXTRACTION_PROGRESS_RANGE,
            )
    }
    return CaptionGenerationStatus(
        phase = CaptionGenerationPhase.ExtractingAudio,
        message = message,
        progressFraction = progressFraction,
    )
}

internal fun transcriptionProgressFraction(
    completedChunkCount: Int,
    totalChunks: Int,
): Float {
    val safeTotalChunks = totalChunks.coerceAtLeast(1)
    return (CAPTION_TRANSCRIPTION_PROGRESS_START + (
        (completedChunkCount.toFloat() / safeTotalChunks.toFloat()).coerceIn(0f, 1f) *
            CAPTION_TRANSCRIPTION_PROGRESS_RANGE
        ))
        .coerceIn(
            CAPTION_TRANSCRIPTION_PROGRESS_START,
            CAPTION_TRANSCRIPTION_PROGRESS_START + CAPTION_TRANSCRIPTION_PROGRESS_RANGE,
        )
}

internal fun transcriptionCaptionStatus(
    completedChunkCount: Int,
    totalChunks: Int,
    processedAudioSeconds: Long,
    totalAudioDurationSeconds: Long,
    activeChunkDurationSeconds: Long = 0L,
    activeChunkProgressPercent: Int? = null,
    elapsedRealtimeSeconds: Long? = null,
): CaptionGenerationStatus {
    val safeTotalChunks = totalChunks.coerceAtLeast(1)
    val boundedCompletedChunkCount = completedChunkCount.coerceIn(0, safeTotalChunks)
    val boundedChunkProgressPercent = activeChunkProgressPercent?.coerceIn(0, 100)
    val totalAudioSeconds = totalAudioDurationSeconds.coerceAtLeast(1L)
    val activeProcessedAudioSeconds = if (boundedChunkProgressPercent == null) {
        0L
    } else {
        ((activeChunkDurationSeconds.coerceAtLeast(0L).toDouble() * boundedChunkProgressPercent.toDouble()) / 100.0)
            .toLong()
    }
    val effectiveProcessedAudioSeconds = (
        processedAudioSeconds.coerceAtLeast(0L) + activeProcessedAudioSeconds
        )
        .coerceIn(0L, totalAudioSeconds)
    val overallCompletionFraction = (
        effectiveProcessedAudioSeconds.toFloat() / totalAudioSeconds.toFloat()
        )
        .coerceIn(0f, 1f)
    val currentChunkNumber = when {
        boundedCompletedChunkCount >= safeTotalChunks -> safeTotalChunks
        else -> (boundedCompletedChunkCount + 1).coerceAtMost(safeTotalChunks)
    }
    if (effectiveProcessedAudioSeconds <= 0L) {
        return CaptionGenerationStatus(
            phase = CaptionGenerationPhase.Transcribing,
            message = "Transcribing chunk $currentChunkNumber of $safeTotalChunks… 0:00 of ${formatCaptionProgressTime(totalAudioSeconds)} processed",
            progressFraction = null,
        )
    }
    val overallCompletionPercent = (overallCompletionFraction * 100f).roundToInt()
    val estimatedRemainingSeconds = elapsedRealtimeSeconds
        ?.takeIf {
            it > 0L &&
                effectiveProcessedAudioSeconds >= ETA_MIN_PROCESSED_AUDIO_SECONDS &&
                boundedCompletedChunkCount >= ETA_MIN_COMPLETED_CHUNKS
        }
        ?.let { elapsedSeconds ->
            val secondsPerAudioSecond = elapsedSeconds.toDouble() / effectiveProcessedAudioSeconds.toDouble()
            ((totalAudioSeconds - effectiveProcessedAudioSeconds).toDouble() * secondsPerAudioSecond)
                .roundToLong()
                .coerceAtLeast(0L)
        }
    val progressMessage = buildString {
        append("Transcribing chunk ")
        append(currentChunkNumber)
        append(" of ")
        append(safeTotalChunks)
        append("… ")
        append(formatCaptionProgressTime(effectiveProcessedAudioSeconds))
        append(" of ")
        append(formatCaptionProgressTime(totalAudioSeconds))
        append(" processed")
        append(" • ")
        append(overallCompletionPercent)
        append("% complete")
        estimatedRemainingSeconds?.let { remainingSeconds ->
            append(" • ETA ~")
            append(formatCaptionProgressTime(remainingSeconds))
        }
    }
    return CaptionGenerationStatus(
        phase = CaptionGenerationPhase.Transcribing,
        message = progressMessage,
        progressFraction = (
            CAPTION_TRANSCRIPTION_PROGRESS_START +
                (overallCompletionFraction * CAPTION_TRANSCRIPTION_PROGRESS_RANGE)
            )
            .coerceIn(
                CAPTION_TRANSCRIPTION_PROGRESS_START,
                CAPTION_TRANSCRIPTION_PROGRESS_START + CAPTION_TRANSCRIPTION_PROGRESS_RANGE,
            ),
    )
}

private fun elapsedSince(startedAtNanos: Long): Long =
    ((System.nanoTime() - startedAtNanos).coerceAtLeast(0L) / 1_000_000_000L)

internal fun formatCaptionProgressTime(totalSeconds: Long): String {
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val remainingSeconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, remainingSeconds)
    } else {
        "%d:%02d".format(minutes, remainingSeconds)
    }
}

private const val CAPTION_EXTRACTION_PROGRESS_START = 0.02f
private const val CAPTION_EXTRACTION_PROGRESS_RANGE = 0.33f
private const val CAPTION_TRANSCRIPTION_PROGRESS_START = 0.4f
private const val CAPTION_TRANSCRIPTION_PROGRESS_RANGE = 0.55f
private const val ETA_MIN_PROCESSED_AUDIO_SECONDS = 120L
private const val ETA_MIN_COMPLETED_CHUNKS = 1

private object WhisperNativeBridge {
    init {
        System.loadLibrary("looktube_whisper")
    }

    @Volatile
    private var cachedModelPath: String? = null
    @Volatile
    private var cachedContextPointer: Long = 0L
    @Volatile
    private var nativeProgressListener: ((Int) -> Unit)? = null

    @Synchronized
    fun transcribe(
        modelPath: String,
        audioSamples: FloatArray,
        numThreads: Int,
        onProgressPercent: (Int) -> Unit = {},
    ): List<CaptionSegment> {
        val contextPointer = obtainContext(modelPath)
        val previousProgressListener = nativeProgressListener
        nativeProgressListener = onProgressPercent
        try {
            nativeFullTranscribe(
                contextPointer = contextPointer,
                numThreads = numThreads,
                audioData = audioSamples,
            )
        } finally {
            nativeProgressListener = previousProgressListener
        }
        return buildList {
            val segmentCount = nativeGetSegmentCount(contextPointer)
            for (index in 0 until segmentCount) {
                add(
                    CaptionSegment(
                        startMs = nativeGetSegmentStartTicks(contextPointer, index) * 10L,
                        endMs = nativeGetSegmentEndTicks(contextPointer, index) * 10L,
                        text = nativeGetSegmentText(contextPointer, index),
                    ),
                )
            }
        }
    }

    @Synchronized
    private fun obtainContext(modelPath: String): Long {
        if (cachedContextPointer != 0L && cachedModelPath == modelPath) {
            return cachedContextPointer
        }
        if (cachedContextPointer != 0L) {
            nativeFreeContext(cachedContextPointer)
            cachedContextPointer = 0L
            cachedModelPath = null
        }
        val createdContextPointer = nativeInitContext(modelPath)
        require(createdContextPointer != 0L) {
            "Failed to initialize the offline caption model."
        }
        cachedContextPointer = createdContextPointer
        cachedModelPath = modelPath
        return createdContextPointer
    }

    @JvmStatic
    fun dispatchNativeProgress(progressPercent: Int) {
        nativeProgressListener?.invoke(progressPercent.coerceIn(0, 100))
    }

    private external fun nativeInitContext(modelPath: String): Long
    private external fun nativeFreeContext(contextPointer: Long)
    private external fun nativeFullTranscribe(
        contextPointer: Long,
        numThreads: Int,
        audioData: FloatArray,
    )
    private external fun nativeGetSegmentCount(contextPointer: Long): Int
    private external fun nativeGetSegmentText(contextPointer: Long, index: Int): String
    private external fun nativeGetSegmentStartTicks(contextPointer: Long, index: Int): Long
    private external fun nativeGetSegmentEndTicks(contextPointer: Long, index: Int): Long
}

internal class RemoteMediaAudioExtractor {
    fun extractToPcm16MonoFile(
        playbackUrl: String,
        outputFile: File,
        onProgress: (AudioExtractionProgress) -> Unit = {},
    ) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        BufferedOutputStream(FileOutputStream(outputFile)).use { output ->
            try {
                extractor.setDataSource(playbackUrl, emptyMap())
                val trackIndex = findAudioTrackIndex(extractor)
                require(trackIndex >= 0) {
                    "This video did not expose an audio track that the local caption path can decode."
                }
                extractor.selectTrack(trackIndex)
                val inputFormat = extractor.getTrackFormat(trackIndex)
                val trackDurationUs = inputFormat.durationUsOrNull()
                val mimeType = inputFormat.getString(MediaFormat.KEY_MIME)
                    ?: throw IOException("The selected audio track did not provide a MIME type.")
                decoder = MediaCodec.createDecoderByType(mimeType).apply {
                    configure(inputFormat, null, null, 0)
                    start()
                }
                val bufferInfo = MediaCodec.BufferInfo()
                var sawInputEnd = false
                var sawOutputEnd = false
                var outputFormat = inputFormat
                var lastProgressAtNanos = System.nanoTime()
                var lastReportedPositionUs = Long.MIN_VALUE
                var decodedBytes = 0L
                onProgress(
                    AudioExtractionProgress(
                        currentPositionUs = 0L,
                        durationUs = trackDurationUs,
                        decodedBytes = 0L,
                    ),
                )
                while (!sawOutputEnd) {
                    var madeForwardProgress = false
                    if (!sawInputEnd) {
                        val inputBufferIndex = decoder!!.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                        if (inputBufferIndex >= 0) {
                            val inputBuffer = decoder!!.getInputBuffer(inputBufferIndex)
                                ?: throw IOException("The audio decoder did not return an input buffer.")
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                decoder!!.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                )
                                sawInputEnd = true
                                madeForwardProgress = true
                                lastProgressAtNanos = System.nanoTime()
                            } else {
                                decoder!!.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    sampleSize,
                                    extractor.sampleTime,
                                    0,
                                )
                                madeForwardProgress = true
                                lastProgressAtNanos = System.nanoTime()
                                extractor.advance()
                            }
                        }
                    }
                    when (val outputBufferIndex = decoder!!.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)) {
                        MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            outputFormat = decoder!!.outputFormat
                            madeForwardProgress = true
                            lastProgressAtNanos = System.nanoTime()
                        }
                        else -> {
                            if (outputBufferIndex >= 0) {
                                val outputBuffer = decoder!!.getOutputBuffer(outputBufferIndex)
                                    ?: throw IOException("The audio decoder did not return an output buffer.")
                                if (bufferInfo.size > 0) {
                                    outputBuffer.position(bufferInfo.offset)
                                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                    val pcmBytes = ByteArray(bufferInfo.size)
                                    outputBuffer.get(pcmBytes)
                                    writeResampledPcm16Mono(
                                        output = output,
                                        pcmBytes = pcmBytes,
                                        outputFormat = outputFormat,
                                    )
                                    decodedBytes += bufferInfo.size
                                    madeForwardProgress = true
                                    lastProgressAtNanos = System.nanoTime()
                                    val currentPositionUs = bufferInfo.presentationTimeUs.coerceAtLeast(0L)
                                    if (
                                        currentPositionUs == 0L ||
                                            currentPositionUs - lastReportedPositionUs >= EXTRACTION_PROGRESS_REPORT_INTERVAL_US
                                    ) {
                                        lastReportedPositionUs = currentPositionUs
                                        onProgress(
                                            AudioExtractionProgress(
                                                currentPositionUs = currentPositionUs,
                                                durationUs = trackDurationUs,
                                                decodedBytes = decodedBytes,
                                            ),
                                        )
                                    }
                                }
                                sawOutputEnd = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                                decoder!!.releaseOutputBuffer(outputBufferIndex, false)
                            }
                        }
                    }
                    if (sawOutputEnd) {
                        onProgress(
                            AudioExtractionProgress(
                                currentPositionUs = max(lastReportedPositionUs, trackDurationUs ?: 0L),
                                durationUs = trackDurationUs,
                                decodedBytes = decodedBytes,
                            ),
                        )
                    } else if (
                        !madeForwardProgress &&
                            System.nanoTime() - lastProgressAtNanos > EXTRACTION_STALL_TIMEOUT_NS
                    ) {
                        throw IOException(
                            "Audio extraction stalled before offline captions could continue. Try again after playback is stable or pick another video.",
                        )
                    }
                }
            } catch (exception: Exception) {
                throw IOException(
                    "Local caption audio extraction failed: ${exception.message ?: "unknown error" }",
                    exception,
                )
            } finally {
                decoder?.stop()
                decoder?.release()
                extractor.release()
            }
        }
    }

    private fun MediaFormat.durationUsOrNull(): Long? =
        if (containsKey(MediaFormat.KEY_DURATION)) {
            getLong(MediaFormat.KEY_DURATION).takeIf { it > 0L }
        } else {
            null
        }
    private fun findAudioTrackIndex(extractor: MediaExtractor): Int =
        (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index)
                .getString(MediaFormat.KEY_MIME)
                ?.startsWith("audio/") == true
        } ?: -1

    private fun writeResampledPcm16Mono(
        output: BufferedOutputStream,
        pcmBytes: ByteArray,
        outputFormat: MediaFormat,
    ) {
        val sourceSampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val pcmEncoding = if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
            outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
        } else {
            AudioFormat.ENCODING_PCM_16BIT
        }
        val monoSamples = decodeMonoSamples(
            pcmBytes = pcmBytes,
            channelCount = channelCount,
            pcmEncoding = pcmEncoding,
        )
        val resampled = resampleTo16k(monoSamples, sourceSampleRate)
        resampled.forEach { sample ->
            val clamped = sample.coerceIn(-1f, 1f)
            val shortValue = (clamped * 32767f).roundToInt().toShort()
            output.write(shortValue.toInt() and 0xff)
            output.write((shortValue.toInt() shr 8) and 0xff)
        }
    }

    private fun decodeMonoSamples(
        pcmBytes: ByteArray,
        channelCount: Int,
        pcmEncoding: Int,
    ): FloatArray {
        return when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_16BIT -> {
                val byteBuffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
                val frameCount = pcmBytes.size / (2 * channelCount)
                FloatArray(frameCount) { frameIndex ->
                    var sampleSum = 0f
                    repeat(channelCount) {
                        sampleSum += byteBuffer.short / 32768f
                    }
                    sampleSum / channelCount.toFloat()
                }
            }
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val byteBuffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
                val frameCount = pcmBytes.size / (4 * channelCount)
                FloatArray(frameCount) {
                    var sampleSum = 0f
                    repeat(channelCount) {
                        sampleSum += byteBuffer.float
                    }
                    sampleSum / channelCount.toFloat()
                }
            }
            else -> {
                throw IOException("Unsupported PCM encoding for offline caption extraction: $pcmEncoding.")
            }
        }
    }

    private fun resampleTo16k(
        sourceSamples: FloatArray,
        sourceSampleRate: Int,
    ): FloatArray {
        if (sourceSamples.isEmpty() || sourceSampleRate == 16_000) {
            return sourceSamples
        }
        val outputSampleCount = max(
            1,
            ((sourceSamples.size.toLong() * 16_000L) / sourceSampleRate.toLong()).toInt(),
        )
        val outputSamples = FloatArray(outputSampleCount)
        val step = sourceSampleRate.toDouble() / 16_000.0
        for (outputIndex in 0 until outputSampleCount) {
            val sourcePosition = outputIndex * step
            val leftIndex = sourcePosition.toInt()
            val rightIndex = min(leftIndex + 1, sourceSamples.lastIndex)
            val fraction = (sourcePosition - leftIndex).toFloat()
            val left = sourceSamples[leftIndex]
            val right = sourceSamples[rightIndex]
            outputSamples[outputIndex] = left + ((right - left) * fraction)
        }
        return outputSamples
    }

    private companion object {
        private const val DEQUEUE_TIMEOUT_US = 10_000L
        private const val EXTRACTION_PROGRESS_REPORT_INTERVAL_US = 500_000L
        private const val EXTRACTION_STALL_TIMEOUT_NS = 15_000_000_000L
    }
}

internal fun pcm16LeToFloatArray(
    buffer: ByteArray,
    length: Int,
): FloatArray {
    val sampleCount = length / 2
    val samples = FloatArray(sampleCount)
    var inputIndex = 0
    for (sampleIndex in 0 until sampleCount) {
        val low = buffer[inputIndex].toInt() and 0xff
        val high = buffer[inputIndex + 1].toInt()
        val shortValue = ((high shl 8) or low).toShort()
        samples[sampleIndex] = shortValue / 32768f
        inputIndex += 2
    }
    return samples
}

private fun readChunk(
    input: InputStream,
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

internal fun buildWebVtt(segments: List<CaptionSegment>): String = buildString {
    appendLine("WEBVTT")
    appendLine()
    segments.forEachIndexed { index, segment ->
        appendLine(index + 1)
        appendLine("${formatWebVttTimestamp(segment.startMs)} --> ${formatWebVttTimestamp(segment.endMs)}")
        appendLine(segment.text.replace(Regex("\\s+"), " ").trim())
        appendLine()
    }
}

private fun formatWebVttTimestamp(timestampMs: Long): String {
    val normalizedTimestampMs = timestampMs.coerceAtLeast(0L)
    val hours = normalizedTimestampMs / 3_600_000L
    val minutes = (normalizedTimestampMs % 3_600_000L) / 60_000L
    val seconds = (normalizedTimestampMs % 60_000L) / 1_000L
    val milliseconds = normalizedTimestampMs % 1_000L
    return String.format(
        Locale.US,
        "%02d:%02d:%02d.%03d",
        hours,
        minutes,
        seconds,
        milliseconds,
    )
}
