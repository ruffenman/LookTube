package com.looktube.heuristics

import com.looktube.model.PlaybackProgress
import com.looktube.model.VideoEngagementRecord
import com.looktube.model.VideoSummary
import com.looktube.model.isWatched

data class AutoplayRecommendation(val video: VideoSummary, val reason: String)

/** Local, deterministic recommendations; never depend on the current library filter or sort. */
fun recommendNextVideo(
    currentVideoId: String,
    videos: List<VideoSummary>,
    progress: Map<String, PlaybackProgress>,
    engagement: Map<String, VideoEngagementRecord>,
    excludedVideoIds: Set<String> = emptySet(),
): AutoplayRecommendation? {
    val current = videos.firstOrNull { it.id == currentVideoId } ?: return null
    val candidates = videos.distinctBy { it.id }.filter {
        it.id != currentVideoId && it.id !in excludedVideoIds &&
            !it.playbackUrl.isNullOrBlank() && !engagement[it.id].isWatched(progress[it.id])
    }
    if (candidates.isEmpty()) return null
    val chronological = compareBy<VideoSummary> { it.publishedAtEpochMillis ?: Long.MAX_VALUE }
        .thenBy { it.id }
    val sameShow = candidates.filter { it.seriesGroupingKey == current.seriesGroupingKey }
    val forward = sameShow.filter {
        val published = it.publishedAtEpochMillis
        val currentPublished = current.publishedAtEpochMillis
        published != null && currentPublished != null && published > currentPublished
    }
    val next = forward.filter { it.topicGroupingKey == current.topicGroupingKey }.minWithOrNull(chronological)
        ?: forward.minWithOrNull(chronological)
        ?: sameShow.filter { it.topicGroupingKey == current.topicGroupingKey }.minWithOrNull(chronological)
        ?: sameShow.minWithOrNull(chronological)
    if (next != null) {
        return AutoplayRecommendation(next, "Unwatched continuation of ${current.displaySeriesTitle}")
    }

    // Only actual play history influences affinity; manually marking watched is not a play.
    val byId = videos.associateBy { it.id }
    val affinity = mutableMapOf<String, Int>()
    engagement.values.filter { it.lastPlayedAtEpochMillis != null }
        .sortedWith(compareByDescending<VideoEngagementRecord> { it.lastPlayedAtEpochMillis }.thenBy { it.videoId })
        .take(20).forEachIndexed { index, record ->
            val key = byId[record.videoId]?.seriesGroupingKey ?: return@forEachIndexed
            affinity[key] = (affinity[key] ?: 0) + (20 - index)
        }
    val familiar = candidates.filter { (affinity[it.seriesGroupingKey] ?: 0) > 0 }
        .sortedWith(compareByDescending<VideoSummary> { affinity[it.seriesGroupingKey] ?: 0 }
            .thenByDescending { (progress[it.id]?.positionSeconds ?: 0) > 0 }
            .then(chronological)).firstOrNull()
    if (familiar != null) {
        val resume = (progress[familiar.id]?.positionSeconds ?: 0) > 0
        return AutoplayRecommendation(familiar,
            if (resume) "Continue watching ${familiar.displaySeriesTitle}"
            else "Based on your recent ${familiar.displaySeriesTitle} viewing")
    }
    return candidates.sortedWith(compareByDescending<VideoSummary> { it.publishedAtEpochMillis ?: Long.MIN_VALUE }
        .thenBy { it.id }).firstOrNull()?.let { AutoplayRecommendation(it, "Newest unwatched video") }
}
