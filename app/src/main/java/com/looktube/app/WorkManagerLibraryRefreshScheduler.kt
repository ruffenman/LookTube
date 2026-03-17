package com.looktube.app

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
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

    override fun schedule() {
        val workManager = workManagerOrNull() ?: return
        val request = PeriodicWorkRequestBuilder<LibraryRefreshWorker>(
            BACKGROUND_REFRESH_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
            BACKGROUND_REFRESH_FLEX_MINUTES,
            TimeUnit.MINUTES,
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build(),
        ).build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    override fun cancel() {
        workManagerOrNull()?.cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private fun workManagerOrNull(): WorkManager? =
        runCatching { WorkManager.getInstance(appContext) }.getOrNull()

    companion object {
        private const val UNIQUE_WORK_NAME = "looktube.background.library.refresh"
        private const val BACKGROUND_REFRESH_INTERVAL_MINUTES = 15L
        private const val BACKGROUND_REFRESH_FLEX_MINUTES = 5L
    }
}
