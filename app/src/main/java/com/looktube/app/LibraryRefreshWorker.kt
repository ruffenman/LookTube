package com.looktube.app

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.media3.common.util.UnstableApi
import com.looktube.model.SyncPhase
import com.looktube.model.VideoSummary
@UnstableApi

class LibraryRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val appContainer = (applicationContext as? LookTubeApplication)?.appContainer
            ?: return Result.failure()
        appContainer.repository.bootstrap()
        val autoCaptionEnabled = appContainer.repository.feedConfiguration.value.autoGenerateCaptionsForNewVideos
        if (autoCaptionEnabled) {
            setForeground(
                appContainer.backgroundMaintenanceNotifier.foregroundInfo(),
            )
        }
        val previousSnapshot = appContainer.syncedLibraryStore.persistedSnapshot.value

        return runCatching {
            Log.i(TAG, "Starting background library refresh attempt=${runAttemptCount + 1}")
            appContainer.repository.refreshLibrary()
            val syncState = appContainer.repository.librarySyncState.value
            val latestSnapshot = appContainer.syncedLibraryStore.persistedSnapshot.value
            if (syncState.phase == SyncPhase.Success) {
                val newVideos = latestSnapshot.newVideosComparedTo(previousSnapshot)
                if (newVideos.isNotEmpty()) {
                    Log.i(
                        TAG,
                        "Background refresh found ${newVideos.size} new videos; latestVideoId=${newVideos.first().id}",
                    )
                    appContainer.librarySyncNotifier.notifyAboutNewVideos(newVideos)
                } else {
                    Log.i(TAG, "Background refresh succeeded with no newly discovered videos.")
                }
                Result.success()
            } else if (runAttemptCount < 3) {
                Log.w(TAG, "Background refresh did not reach success state; scheduling retry.")
                Result.retry()
            } else {
                Log.w(TAG, "Background refresh exhausted retries without a successful sync state.")
                Result.failure()
            }
        }.getOrElse {
            Log.w(TAG, "Background refresh failed on attempt=${runAttemptCount + 1}", it)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }.also {
            if (appContainer.repository.feedConfiguration.value.feedUrl.isNotBlank()) {
                maintainRollingCatchUpLibraryRefresh(applicationContext)
            }
            if (autoCaptionEnabled) {
                appContainer.backgroundMaintenanceNotifier.dismiss()
            }
        }
    }

    companion object {
        private const val TAG = "LibraryRefreshWorker"
    }
}

internal fun com.looktube.model.PersistedLibrarySnapshot?.newVideosComparedTo(
    previousSnapshot: com.looktube.model.PersistedLibrarySnapshot?,
): List<VideoSummary> {
    if (this == null || previousSnapshot == null) {
        return emptyList()
    }
    if (previousSnapshot.feedUrl != feedUrl || previousSnapshot.videos.isEmpty()) {
        return emptyList()
    }
    val previousIds = previousSnapshot.videos.asSequence().map(VideoSummary::id).toSet()
    return videos.filterNot { video -> video.id in previousIds }
}
