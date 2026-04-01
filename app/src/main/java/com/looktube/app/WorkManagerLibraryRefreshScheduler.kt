package com.looktube.app

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.looktube.data.LibraryRefreshScheduler
import java.util.concurrent.TimeUnit
@UnstableApi

class WorkManagerLibraryRefreshScheduler(
    context: Context,
) : LibraryRefreshScheduler {
    private val appContext = context.applicationContext
    private val schedulePreferences = appContext.getSharedPreferences(
        SCHEDULER_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun schedule() {
        val workManager = workManagerOrNull() ?: return
        val request = buildPeriodicLibraryRefreshWorkRequest()
        val catchUpRequest = buildCatchUpLibraryRefreshWorkRequest()
        val periodicWorkPolicy = periodicWorkPolicy(
            storedVersion = schedulePreferences.getInt(
                PERIODIC_WORK_VERSION_PREFERENCE_KEY,
                0,
            ),
        )

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            periodicWorkPolicy,
            request,
        )
        workManager.enqueueUniqueWork(
            CATCH_UP_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            catchUpRequest,
        )
        schedulePreferences.edit()
            .putInt(PERIODIC_WORK_VERSION_PREFERENCE_KEY, PERIODIC_WORK_SPEC_VERSION)
            .apply()
    }

    override fun cancel() {
        workManagerOrNull()?.apply {
            cancelUniqueWork(UNIQUE_WORK_NAME)
            cancelUniqueWork(CATCH_UP_WORK_NAME)
        }
        schedulePreferences.edit()
            .remove(PERIODIC_WORK_VERSION_PREFERENCE_KEY)
            .apply()
    }

    private fun workManagerOrNull(): WorkManager? =
        runCatching { WorkManager.getInstance(appContext) }.getOrNull()

    companion object {
        private const val UNIQUE_WORK_NAME = "looktube.background.library.refresh"
        private const val CATCH_UP_WORK_NAME = "looktube.background.library.refresh.catch_up"
        private const val SCHEDULER_PREFERENCES_NAME = "looktube.background.refresh.scheduler"
        private const val PERIODIC_WORK_VERSION_PREFERENCE_KEY = "periodic_work_spec_version"
        private const val PERIODIC_WORK_SPEC_VERSION = 2
        private const val BACKGROUND_REFRESH_INTERVAL_MINUTES = 15L
        private const val BACKGROUND_REFRESH_FLEX_MINUTES = 5L
        internal val PERIODIC_WORK_POLICY = ExistingPeriodicWorkPolicy.KEEP
        internal val LEGACY_PERIODIC_WORK_POLICY = ExistingPeriodicWorkPolicy.UPDATE
    }
}

@UnstableApi
internal fun periodicWorkPolicy(storedVersion: Int): ExistingPeriodicWorkPolicy =
    if (storedVersion < 2) {
        WorkManagerLibraryRefreshScheduler.LEGACY_PERIODIC_WORK_POLICY
    } else {
        WorkManagerLibraryRefreshScheduler.PERIODIC_WORK_POLICY
    }

internal fun buildPeriodicLibraryRefreshWorkRequest() =
    PeriodicWorkRequestBuilder<LibraryRefreshWorker>(
        15L,
        TimeUnit.MINUTES,
        5L,
        TimeUnit.MINUTES,
    ).setConstraints(libraryRefreshConstraints()).build()

internal fun buildCatchUpLibraryRefreshWorkRequest() =
    OneTimeWorkRequestBuilder<LibraryRefreshWorker>()
        .setConstraints(libraryRefreshConstraints())
        .build()

private fun libraryRefreshConstraints() =
    Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
