package com.looktube.model

data class LocalCaptionModel(
    val id: String,
    val displayName: String,
    val downloadUrl: String,
    val languageTag: String,
)

val DefaultLocalCaptionModel = LocalCaptionModel(
    id = "ggml-base.en-q5_1",
    displayName = "English offline captions (base.en q5_1)",
    downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en-q5_1.bin",
    languageTag = "en-US",
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

data class VideoCaptionTrack(
    val videoId: String,
    val filePath: String,
    val generatedAtEpochMillis: Long,
    val languageTag: String = DefaultLocalCaptionModel.languageTag,
    val label: String = "English (generated)",
)
