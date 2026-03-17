package com.looktube.data

interface LibraryRefreshScheduler {
    fun schedule()

    fun cancel()
}

object NoOpLibraryRefreshScheduler : LibraryRefreshScheduler {
    override fun schedule() = Unit

    override fun cancel() = Unit
}
