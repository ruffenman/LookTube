package com.looktube.data

import com.looktube.model.CaptionGenerationStatus
import com.looktube.model.LocalCaptionModelState
import com.looktube.model.VideoCaptionTrack
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
