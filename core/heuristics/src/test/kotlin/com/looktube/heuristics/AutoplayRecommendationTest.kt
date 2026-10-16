package com.looktube.heuristics

import com.looktube.model.ManualWatchState
import com.looktube.model.PlaybackProgress
import com.looktube.model.VideoEngagementRecord
import com.looktube.model.VideoSummary
import org.junit.Assert.*
import org.junit.Test

class AutoplayRecommendationTest {
    private fun video(id: String, date: Long? = 1, show: String = "Endurance Run", title: String = "Episode $id") =
        VideoSummary(id, title, "", true, show, "https://example.com/$id.mp4", show, publishedAtEpochMillis = date)

    @Test fun selectsNextChronologicalUnwatchedEpisodeRegardlessOfFeedOrder() {
        val videos = listOf(video("later", 40), video("watched", 20), video("current", 10), video("next", 30))
        val watched = mapOf("watched" to VideoEngagementRecord("watched", manualWatchState = ManualWatchState.Watched))
        assertEquals("next", recommendNextVideo("current", videos, emptyMap(), watched)?.video?.id)
    }

    @Test fun matchingTopicWithinShowWinsOverAnotherGamesNextEpisode() {
        val current = video("current", 10, "Endurance Run", "Endurance Run: Persona 4 - Part 1")
        val other = video("other", 20, "Endurance Run", "Endurance Run: Deadly Premonition - Part 2")
        val next = video("next", 30, "Endurance Run", "Endurance Run: Persona 4 - Part 2")
        assertEquals(current.topicGroupingKey, next.topicGroupingKey)
        assertNotEquals(current.topicGroupingKey, other.topicGroupingKey)
        assertEquals("next", recommendNextVideo("current", listOf(current, other, next), emptyMap(), emptyMap())?.video?.id)
    }

    @Test fun backfillsEarlierUnwatchedEpisodeBeforeLeavingShow() {
        val videos = listOf(video("current", 30), video("older", 10), video("other", 40, "Bombcast"))
        assertEquals("older", recommendNextVideo("current", videos, emptyMap(), emptyMap())?.video?.id)
    }

    @Test fun usesRealRecentViewingInsteadOfManualWatchMarksForFallback() {
        val videos = listOf(video("current"), video("history", 2, "Bombcast"), video("favorite", 3, "Bombcast"),
            video("manual", 4, "Quick Look"), video("newest", 100, "Quick Look"))
        val records = mapOf("history" to VideoEngagementRecord("history", lastPlayedAtEpochMillis = 90, completedAtEpochMillis = 99),
            "manual" to VideoEngagementRecord("manual", manualWatchState = ManualWatchState.Watched))
        val next = recommendNextVideo("current", videos, emptyMap(), records)
        assertEquals("favorite", next?.video?.id)
        assertTrue(next!!.reason.contains("recent"))
    }

    @Test fun resumesInProgressVideoInPreferredShowBeforeStartingAnother() {
        val videos = listOf(video("current"), video("history", 1, "Bombcast"), video("old", 2, "Bombcast"), video("resume", 3, "Bombcast"))
        val records = mapOf("history" to VideoEngagementRecord("history", lastPlayedAtEpochMillis = 90, completedAtEpochMillis = 99))
        assertEquals("resume", recommendNextVideo("current", videos,
            mapOf("resume" to PlaybackProgress("resume", 10, 100)), records)?.video?.id)
    }

    @Test fun noHistoryUsesNewestPlayableUnwatchedVideoAndStableTieBreak() {
        val videos = listOf(video("current"), video("b", 3, "Bombcast"), video("a", 3, "Quick Look"),
            video("unknown", null, "Quick Look"), video("broken", 5, "Quick Look").copy(playbackUrl = ""))
        assertEquals("a", recommendNextVideo("current", videos, emptyMap(), emptyMap())?.video?.id)
    }

    @Test fun completedAndSessionExcludedVideosAreNeverRepeated() {
        val videos = listOf(video("current"), video("completed"), video("session"))
        assertNull(recommendNextVideo("current", videos,
            mapOf("completed" to PlaybackProgress("completed", 100, 100)), emptyMap(), setOf("session")))
        assertNull(recommendNextVideo("absent", videos, emptyMap(), emptyMap()))
    }

    @Test fun explicitUnwatchedOverridesHistoricalCompletion() {
        val videos = listOf(video("current"), video("unwatched"))
        assertEquals("unwatched", recommendNextVideo("current", videos,
            mapOf("unwatched" to PlaybackProgress("unwatched", 100, 100)),
            mapOf("unwatched" to VideoEngagementRecord("unwatched", manualWatchState = ManualWatchState.Unwatched)))?.video?.id)
    }
}
