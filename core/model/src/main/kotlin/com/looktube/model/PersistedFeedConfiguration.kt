package com.looktube.model

data class PersistedFeedConfiguration(
    val feedUrl: String,
    val autoGenerateCaptionsForNewVideos: Boolean = false,
    val dailyOpenPointCount: Int = 0,
    val lastOpenedLocalEpochDay: Long? = null,
    val launchIntroQuoteDeckSeed: Long = 1L,
    val launchIntroQuoteDeckIndex: Int = 0,
)

fun PersistedFeedConfiguration.toRuntime(): FeedConfiguration =
    FeedConfiguration(
        feedUrl = feedUrl,
        autoGenerateCaptionsForNewVideos = autoGenerateCaptionsForNewVideos,
        dailyOpenPointCount = dailyOpenPointCount,
        launchIntroQuoteDeckSeed = launchIntroQuoteDeckSeed,
        launchIntroQuoteDeckIndex = launchIntroQuoteDeckIndex,
    )

fun PersistedFeedConfiguration.advanceLaunchIntroQuoteDeck(
    deckSize: Int,
    nextDeckSeed: Long,
): PersistedFeedConfiguration {
    if (deckSize <= 0) {
        return this
    }
    val nextIndex = (launchIntroQuoteDeckIndex + 1).coerceAtLeast(0)
    return if (nextIndex >= deckSize) {
        copy(
            launchIntroQuoteDeckSeed = nextDeckSeed,
            launchIntroQuoteDeckIndex = 0,
        )
    } else {
        copy(launchIntroQuoteDeckIndex = nextIndex)
    }
}
