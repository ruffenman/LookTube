package com.looktube.model

data class PersistedLibrarySnapshot(
    val feedUrl: String,
    val videos: List<VideoSummary>,
    val lastSuccessfulSyncSummary: String?,
)
