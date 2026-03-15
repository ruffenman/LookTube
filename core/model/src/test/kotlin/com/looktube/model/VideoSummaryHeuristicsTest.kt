package com.looktube.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoSummaryHeuristicsTest {
    @Test
    fun derivesCanonicalShowTitlesFromNumberedEpisodes() {
        assertEquals("Giant Bombcast", "Giant Bombcast 901: Ranking the Best Sandwiches in Games".toHeuristicShowTitleOrNull())
        assertEquals("Game Mess Mornings", "Game Mess Mornings 3/15/2026".toHeuristicShowTitleOrNull())
    }

    @Test
    fun infersCastAndTopicHeuristicsFromVideoMetadata() {
        val video = VideoSummary(
            id = "1",
            title = "Giant Bombcast 901: Ranking the Best Sandwiches in Games",
            description = "Jeff Grubb and Jan Ochoa debate the spiciest gaming sandwiches.",
            isPremium = true,
            feedCategory = "Premium",
            playbackUrl = null,
        )

        assertEquals("Giant Bombcast", video.displaySeriesTitle)
        assertEquals("Jeff Grubb", video.castGroupingTitle)
        assertEquals("Best Sandwiches", video.topicGroupingTitle)
    }
}
