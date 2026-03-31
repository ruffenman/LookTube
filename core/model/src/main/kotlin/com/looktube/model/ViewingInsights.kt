package com.looktube.model

data class VideoEngagementRecord(
    val videoId: String,
    val lastPlayedAtEpochMillis: Long? = null,
    val completedAtEpochMillis: Long? = null,
    val manualWatchState: ManualWatchState? = null,
)

enum class ManualWatchState {
    Watched,
    Unwatched,
}

data class SeriesCompletionSummary(
    val seriesTitle: String,
    val watchedVideoCount: Int,
    val totalVideoCount: Int,
) {
    val isCompleted: Boolean
        get() = totalVideoCount > 0 && watchedVideoCount == totalVideoCount
}

data class LookPointsSummary(
    val totalPoints: Int,
    val watchedVideoCount: Int,
    val totalVideoCount: Int,
    val completedShowCount: Int,
    val totalShowCount: Int,
    val videoPoints: Int,
    val dailyOpenPoints: Int,
) {
    companion object {
        val Empty = LookPointsSummary(
            totalPoints = 0,
            watchedVideoCount = 0,
            totalVideoCount = 0,
            completedShowCount = 0,
            totalShowCount = 0,
            videoPoints = 0,
            dailyOpenPoints = 0,
        )
    }
}

data class RecentPlaybackVideo(
    val video: VideoSummary,
    val playbackProgress: PlaybackProgress?,
    val isWatched: Boolean,
    val lastPlayedAtEpochMillis: Long,
)

fun PlaybackProgress.isCompletedByProgress(): Boolean =
    durationSeconds > 0 && positionSeconds.coerceAtLeast(0L) * 100 >= durationSeconds * WATCHED_PROGRESS_COMPLETION_PERCENT

fun VideoEngagementRecord?.isWatched(playbackProgress: PlaybackProgress?): Boolean = when (this?.manualWatchState) {
    ManualWatchState.Watched -> true
    ManualWatchState.Unwatched -> false
    null -> this?.completedAtEpochMillis != null || playbackProgress?.isCompletedByProgress() == true
}

fun buildSeriesCompletionSummaries(
    videos: List<VideoSummary>,
    playbackProgress: Map<String, PlaybackProgress>,
    engagementRecords: Map<String, VideoEngagementRecord>,
): Map<String, SeriesCompletionSummary> = videos
    .groupBy { video -> video.seriesCompletionTitle() }
    .mapValues { (seriesTitle, seriesVideos) ->
        SeriesCompletionSummary(
            seriesTitle = seriesTitle,
            watchedVideoCount = seriesVideos.count { video ->
                engagementRecords[video.id].isWatched(playbackProgress[video.id])
            },
            totalVideoCount = seriesVideos.size,
        )
    }

fun buildLookPointsSummary(
    videos: List<VideoSummary>,
    playbackProgress: Map<String, PlaybackProgress>,
    engagementRecords: Map<String, VideoEngagementRecord>,
    dailyOpenPointCount: Int = 0,
): LookPointsSummary {
    if (videos.isEmpty()) {
        return LookPointsSummary.Empty.copy(
            totalPoints = dailyOpenPointCount.coerceAtLeast(0),
            dailyOpenPoints = dailyOpenPointCount.coerceAtLeast(0),
        )
    }
    val watchedVideoCount = videos.count { video ->
        engagementRecords[video.id].isWatched(playbackProgress[video.id])
    }
    val seriesCompletionSummaries = buildSeriesCompletionSummaries(
        videos = videos,
        playbackProgress = playbackProgress,
        engagementRecords = engagementRecords,
    )
    val completedShowCount = seriesCompletionSummaries.values.count(SeriesCompletionSummary::isCompleted)
    val videoPoints = watchedVideoCount * LOOK_POINTS_PER_WATCHED_VIDEO
    val dailyOpenPoints = dailyOpenPointCount.coerceAtLeast(0)
    return LookPointsSummary(
        totalPoints = videoPoints + dailyOpenPoints,
        watchedVideoCount = watchedVideoCount,
        totalVideoCount = videos.size,
        completedShowCount = completedShowCount,
        totalShowCount = seriesCompletionSummaries.size,
        videoPoints = videoPoints,
        dailyOpenPoints = dailyOpenPoints,
    )
}

fun buildRecentPlaybackVideos(
    videos: List<VideoSummary>,
    playbackProgress: Map<String, PlaybackProgress>,
    engagementRecords: Map<String, VideoEngagementRecord>,
    limit: Int = DEFAULT_RECENT_PLAYBACK_LIMIT,
): List<RecentPlaybackVideo> {
    if (limit <= 0) {
        return emptyList()
    }
    val videosById = videos.associateBy(VideoSummary::id)
    return engagementRecords.values
        .mapNotNull { record ->
            val lastPlayedAtEpochMillis = record.lastPlayedAtEpochMillis ?: return@mapNotNull null
            val video = videosById[record.videoId] ?: return@mapNotNull null
            RecentPlaybackVideo(
                video = video,
                playbackProgress = playbackProgress[record.videoId],
                isWatched = record.isWatched(playbackProgress[record.videoId]),
                lastPlayedAtEpochMillis = lastPlayedAtEpochMillis,
            )
        }
        .sortedByDescending(RecentPlaybackVideo::lastPlayedAtEpochMillis)
        .take(limit)
}

private const val WATCHED_PROGRESS_COMPLETION_PERCENT = 90L
const val LOOK_POINTS_PER_WATCHED_VIDEO = 12
const val DEFAULT_RECENT_PLAYBACK_LIMIT = 6

private fun VideoSummary.seriesCompletionTitle(): String =
    seriesTitle?.takeIf(String::isNotBlank)
        ?: feedCategory.takeIf(String::isNotBlank)
        ?: title
