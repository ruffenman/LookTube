package com.looktube.model

data class PersistedFeedConfiguration(
    val feedUrl: String,
    val autoGenerateCaptionsForNewVideos: Boolean = false,
    val dailyOpenPointCount: Int = 0,
    val lastOpenedLocalEpochDay: Long? = null,
    val launchIntroMessageDeckSeed: Long = 1L,
    val launchIntroMessageDeckIndex: Int = 0,
    val lastOpenedAtEpochMillis: Long? = null,
)

fun PersistedFeedConfiguration.toRuntime(): FeedConfiguration =
    FeedConfiguration(
        feedUrl = feedUrl,
        autoGenerateCaptionsForNewVideos = autoGenerateCaptionsForNewVideos,
        dailyOpenPointCount = dailyOpenPointCount,
        lastOpenedAtEpochMillis = lastOpenedAtEpochMillis,
        launchIntroMessageDeckSeed = launchIntroMessageDeckSeed,
        launchIntroMessageDeckIndex = launchIntroMessageDeckIndex,
    )

fun PersistedFeedConfiguration.advanceLaunchIntroMessageDeck(
    deckSize: Int,
    nextDeckSeed: Long,
): PersistedFeedConfiguration {
    if (deckSize <= 0) {
        return this
    }
    val nextIndex = (launchIntroMessageDeckIndex + 1).coerceAtLeast(0)
    return if (nextIndex >= deckSize) {
        copy(
            launchIntroMessageDeckSeed = nextDeckSeed,
            launchIntroMessageDeckIndex = 0,
        )
    } else {
        copy(launchIntroMessageDeckIndex = nextIndex)
    }
}
