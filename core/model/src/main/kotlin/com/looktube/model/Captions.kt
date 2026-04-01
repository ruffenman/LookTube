package com.looktube.model

data class LocalCaptionEngine(
    val id: String,
    val displayName: String,
    val description: String,
)

val WhisperCppLocalCaptionEngine = LocalCaptionEngine(
    id = "whisper_cpp",
    displayName = "Whisper.cpp",
    description = "Baseline offline engine supported by the default LookTube build target.",
)

val MoonshineLocalCaptionEngine = LocalCaptionEngine(
    id = "moonshine",
    displayName = "Moonshine",
    description = "Higher-spec local engine available only in the Moonshine-capable build target.",
)

data class LocalCaptionModel(
    val id: String,
    val displayName: String,
    val downloadUrl: String,
    val languageTag: String,
    val engine: LocalCaptionEngine,
)

val DefaultLocalCaptionModel = LocalCaptionModel(
    id = "ggml-base.en-q5_1",
    displayName = "English offline captions (base.en q5_1)",
    downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en-q5_1.bin",
    languageTag = "en-US",
    engine = WhisperCppLocalCaptionEngine,
)

val MoonshineBaseEnglishCaptionModel = LocalCaptionModel(
    id = "moonshine-base-en-quantized",
    displayName = "English offline captions (Moonshine base quantized)",
    downloadUrl = "https://huggingface.co/UsefulSensors/moonshine/resolve/main/onnx/merged/base/quantized/decoder_model_merged.ort",
    languageTag = "en-US",
    engine = MoonshineLocalCaptionEngine,
)

data class LocalCaptionModelState(
    val model: LocalCaptionModel = DefaultLocalCaptionModel,
    val isDownloading: Boolean = false,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val localPath: String? = null,
    val errorMessage: String? = null,
) {
    val isReady: Boolean
        get() = !localPath.isNullOrBlank()

    val downloadProgressFraction: Float?
        get() = totalBytes
            ?.takeIf { total -> total > 0L }
            ?.let { total -> downloadedBytes.toFloat() / total.toFloat() }
}

data class CaptionGenerationMetric(
    val label: String,
    val value: String,
)

enum class CaptionGenerationPhase {
    Idle,
    ExtractingAudio,
    Transcribing,
    Saving,
    Completed,
    Error,
}

data class CaptionGenerationStatus(
    val phase: CaptionGenerationPhase,
    val message: String,
    val progressFraction: Float? = null,
    val detailMetrics: List<CaptionGenerationMetric> = emptyList(),
) {
    val isTerminal: Boolean
        get() = phase == CaptionGenerationPhase.Completed || phase == CaptionGenerationPhase.Error

    companion object {
        val Idle = CaptionGenerationStatus(
            phase = CaptionGenerationPhase.Idle,
            message = "",
        )
    }
}

data class VideoCaptionData(
    val videoId: String,
    val updatedAtEpochMillis: Long,
    val lastPhase: CaptionGenerationPhase,
    val lastMessage: String,
    val hasSavedCaptionTrack: Boolean,
    val captionTrackPath: String? = null,
    val engineId: String? = null,
) {
    val stateLabel: String
        get() = if (hasSavedCaptionTrack) "Completed" else "Partial"
}

data class VideoCaptionTrack(
    val videoId: String,
    val filePath: String,
    val generatedAtEpochMillis: Long,
    val languageTag: String = DefaultLocalCaptionModel.languageTag,
    val label: String = "English (generated)",
    val engineId: String = DefaultLocalCaptionModel.engine.id,
)
