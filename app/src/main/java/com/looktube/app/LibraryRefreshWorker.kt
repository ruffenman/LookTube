package com.looktube.app

import android.content.Context
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
        val previousSnapshot = appContainer.syncedLibraryStore.persistedSnapshot.value

        return runCatching {
            appContainer.repository.refreshLibrary()
            val syncState = appContainer.repository.librarySyncState.value
            val latestSnapshot = appContainer.syncedLibraryStore.persistedSnapshot.value
            if (syncState.phase == SyncPhase.Success) {
                latestSnapshot?.newVideosComparedTo(previousSnapshot)?.takeIf(List<VideoSummary>::isNotEmpty)?.let {
                    appContainer.librarySyncNotifier.notifyAboutNewVideos(it)
                }
                Result.success()
            } else if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }.getOrElse {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}

private fun com.looktube.model.PersistedLibrarySnapshot?.newVideosComparedTo(
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
