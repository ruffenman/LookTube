package com.looktube.model

data class LibrarySyncState(
    val phase: SyncPhase,
    val message: String,
    val lastSuccessfulSyncSummary: String? = null,
)

enum class SyncPhase {
    Idle,
    Refreshing,
    Success,
    Error,
}
