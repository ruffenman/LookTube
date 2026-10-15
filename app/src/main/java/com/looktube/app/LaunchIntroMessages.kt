package com.looktube.app

import com.looktube.model.FeedConfiguration
import com.looktube.model.VideoSummary
import kotlin.random.Random

internal data class LaunchIntroMessage(
    val id: String,
    val headline: String,
    val body: String,
    val quote: LaunchIntroQuote? = null,
)

internal val LaunchIntroMessageDeckSize: Int
    get() = LaunchIntroQuotes.size

internal fun currentLaunchIntroMessage(
    feedConfiguration: FeedConfiguration,
    videos: List<VideoSummary>,
    previousAppOpenedAtEpochMillis: Long?,
): LaunchIntroMessage {
    val deck = LaunchIntroQuotes.shuffled(Random(feedConfiguration.launchIntroMessageDeckSeed))
    val quote = deck.takeIf { it.isNotEmpty() }?.let {
        it[Math.floorMod(feedConfiguration.launchIntroMessageDeckIndex, it.size)]
    }
    val newVideos = videos.asSequence()
        .filter { previousAppOpenedAtEpochMillis != null && (it.publishedAtEpochMillis ?: Long.MIN_VALUE) > previousAppOpenedAtEpochMillis }
        .sortedByDescending { it.publishedAtEpochMillis }
        .toList()
    if (newVideos.isNotEmpty()) {
        val previewTitles = newVideos.take(3).map(VideoSummary::title)
        val remainingCount = newVideos.size - previewTitles.size
        return LaunchIntroMessage(
            id = "new-video-digest",
            headline = if (newVideos.size == 1) "1 new video since last visit" else "${newVideos.size} new videos since last visit",
            body = previewTitles.joinToString("\n") + if (remainingCount > 0) "\n+$remainingCount more" else "",
            quote = quote,
        )
    }
    return LaunchIntroMessage(
        id = quote?.id ?: "fallback",
        headline = quote?.text ?: "Looking good",
        body = quote?.let { "— ${it.speaker.name}" } ?: "Your library is ready when you are.",
        quote = quote,
    )
}
