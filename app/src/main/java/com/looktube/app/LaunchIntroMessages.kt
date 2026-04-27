package com.looktube.app

import com.looktube.model.FeedConfiguration
import com.looktube.model.VideoSummary
import kotlin.random.Random

internal data class LaunchIntroMessage(
    val id: String,
    val headline: String,
    val body: String,
)

internal val GenericLaunchIntroMessages = listOf(
    LaunchIntroMessage(
        id = "looking-good",
        headline = "Looking good",
        body = "Your library is ready when you are.",
    ),
    LaunchIntroMessage(
        id = "ready-to-roll",
        headline = "Ready to roll",
        body = "Pick a video and settle in.",
    ),
    LaunchIntroMessage(
        id = "nice-to-see-you",
        headline = "Nice to see you",
        body = "Everything is set for another session.",
    ),
    LaunchIntroMessage(
        id = "library-ready",
        headline = "Library ready",
        body = "Fresh playback is only a tap away.",
    ),
    LaunchIntroMessage(
        id = "good-to-go",
        headline = "Good to go",
        body = "Your videos are queued up.",
    ),
    LaunchIntroMessage(
        id = "welcome-back",
        headline = "Welcome back",
        body = "The player is warmed up.",
    ),
    LaunchIntroMessage(
        id = "all-set",
        headline = "All set",
        body = "Jump back in whenever you are ready.",
    ),
    LaunchIntroMessage(
        id = "smooth-sailing",
        headline = "Smooth sailing",
        body = "Browse the library or continue watching.",
    ),
)

internal val LaunchIntroMessageDeckSize: Int
    get() = GenericLaunchIntroMessages.size

internal fun currentLaunchIntroMessage(
    feedConfiguration: FeedConfiguration,
    videos: List<VideoSummary>,
    previousAppOpenedAtEpochMillis: Long?,
): LaunchIntroMessage {
    val newVideos = videos.newVideosPublishedSince(previousAppOpenedAtEpochMillis)
    if (newVideos.isNotEmpty()) {
        return newVideos.toLaunchDigestMessage()
    }
    return currentGenericLaunchIntroMessage(feedConfiguration)
}

private fun List<VideoSummary>.newVideosPublishedSince(
    previousAppOpenedAtEpochMillis: Long?,
): List<VideoSummary> {
    val previousOpen = previousAppOpenedAtEpochMillis ?: return emptyList()
    return asSequence()
        .filter { video -> (video.publishedAtEpochMillis ?: Long.MIN_VALUE) > previousOpen }
        .sortedByDescending { video -> video.publishedAtEpochMillis }
        .toList()
}

private fun List<VideoSummary>.toLaunchDigestMessage(): LaunchIntroMessage {
    val previewTitles = take(3).map(VideoSummary::title)
    val remainingCount = size - previewTitles.size
    val body = buildString {
        append(previewTitles.joinToString(separator = "\n"))
        if (remainingCount > 0) {
            append("\n+")
            append(remainingCount)
            append(" more")
        }
    }
    return LaunchIntroMessage(
        id = "new-video-digest",
        headline = if (size == 1) {
            "1 new video since last visit"
        } else {
            "$size new videos since last visit"
        },
        body = body,
    )
}

private fun currentGenericLaunchIntroMessage(
    feedConfiguration: FeedConfiguration,
): LaunchIntroMessage {
    if (GenericLaunchIntroMessages.isEmpty()) {
        return LaunchIntroMessage(
            id = "fallback",
            headline = "Looking good",
            body = "Your library is ready when you are.",
        )
    }
    val deck = GenericLaunchIntroMessages.toMutableList().apply {
        shuffle(Random(feedConfiguration.launchIntroMessageDeckSeed))
    }
    val normalizedIndex = ((feedConfiguration.launchIntroMessageDeckIndex % deck.size) + deck.size) % deck.size
    return deck[normalizedIndex]
}
