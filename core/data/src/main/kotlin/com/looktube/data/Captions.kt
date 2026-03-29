package com.looktube.data

import com.looktube.model.CaptionGenerationStatus
import com.looktube.model.LocalCaptionEngine
import com.looktube.model.LocalCaptionModelState
import com.looktube.model.VideoCaptionTrack
import com.looktube.model.WhisperCppLocalCaptionEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LocalCaptionGenerationRequest(
    val videoId: String,
    val playbackUrl: String,
    val modelPath: String,
)

data class GeneratedCaptionDocument(
    val webVtt: String,
    val languageTag: String,
    val label: String,
    val engineId: String,
)

interface LocalCaptionModelManager {
    val modelState: StateFlow<LocalCaptionModelState>

    suspend fun downloadDefaultModel()
}

interface VideoCaptionStore {
    val captions: StateFlow<Map<String, VideoCaptionTrack>>

    suspend fun saveGeneratedCaption(
        videoId: String,
        document: GeneratedCaptionDocument,
        generatedAtEpochMillis: Long = System.currentTimeMillis(),
    ): VideoCaptionTrack

    suspend fun clear()
}

interface LocalCaptionGenerator {
    suspend fun generate(
        request: LocalCaptionGenerationRequest,
        onProgress: (CaptionGenerationStatus) -> Unit = {},
    ): GeneratedCaptionDocument
}

interface LocalCaptionEngineRegistry {
    val availableEngines: StateFlow<List<LocalCaptionEngine>>
    val selectedEngine: StateFlow<LocalCaptionEngine>
    val modelState: StateFlow<LocalCaptionModelState>

    suspend fun downloadSelectedModel()
    suspend fun generate(
        request: LocalCaptionGenerationRequest,
        onProgress: (CaptionGenerationStatus) -> Unit = {},
    ): GeneratedCaptionDocument

    fun selectEngine(engineId: String)
}

object NoOpLocalCaptionModelManager : LocalCaptionModelManager {
    private val state = MutableStateFlow(LocalCaptionModelState())

    override val modelState: StateFlow<LocalCaptionModelState> = state.asStateFlow()

    override suspend fun downloadDefaultModel() = Unit
}

object NoOpVideoCaptionStore : VideoCaptionStore {
    private val state = MutableStateFlow(emptyMap<String, VideoCaptionTrack>())

    override val captions: StateFlow<Map<String, VideoCaptionTrack>> = state.asStateFlow()

    override suspend fun saveGeneratedCaption(
        videoId: String,
        document: GeneratedCaptionDocument,
        generatedAtEpochMillis: Long,
    ): VideoCaptionTrack {
        return VideoCaptionTrack(
            videoId = videoId,
            filePath = "",
            generatedAtEpochMillis = generatedAtEpochMillis,
            languageTag = document.languageTag,
            label = document.label,
            engineId = document.engineId,
        )
    }

    override suspend fun clear() = Unit
}

object UnsupportedLocalCaptionGenerator : LocalCaptionGenerator {
    override suspend fun generate(
        request: LocalCaptionGenerationRequest,
        onProgress: (CaptionGenerationStatus) -> Unit,
    ): GeneratedCaptionDocument {
        throw IllegalStateException("Local caption generation is not configured for this repository.")
    }
}

object NoOpLocalCaptionEngineRegistry : LocalCaptionEngineRegistry {
    private val availableEnginesState = MutableStateFlow(listOf(WhisperCppLocalCaptionEngine))
    private val selectedEngineState = MutableStateFlow(WhisperCppLocalCaptionEngine)

    override val availableEngines: StateFlow<List<LocalCaptionEngine>> = availableEnginesState.asStateFlow()
    override val selectedEngine: StateFlow<LocalCaptionEngine> = selectedEngineState.asStateFlow()
    override val modelState: StateFlow<LocalCaptionModelState> = NoOpLocalCaptionModelManager.modelState

    override suspend fun downloadSelectedModel() = NoOpLocalCaptionModelManager.downloadDefaultModel()

    override suspend fun generate(
        request: LocalCaptionGenerationRequest,
        onProgress: (CaptionGenerationStatus) -> Unit,
    ): GeneratedCaptionDocument = UnsupportedLocalCaptionGenerator.generate(request, onProgress)

    override fun selectEngine(engineId: String) = Unit
}
