package com.looktube.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoSummaryHeuristicsTest {
    @Test
    fun derivesCanonicalShowTitlesFromNumberedEpisodes() {
        assertEquals("Giant Bombcast", "Giant Bombcast 901: Ranking the Best Sandwiches in Games".toHeuristicShowTitleOrNull())
        assertEquals("Game Mess Mornings", "Game Mess Mornings 3/15/2026".toHeuristicShowTitleOrNull())
        assertEquals("Voicemail Dump Truck", "183 BONUS DUMP".toHeuristicShowTitleOrNull())
        assertEquals("Voicemail Dump Truck", "177 | Shot O'Clock.mp3".toHeuristicShowTitleOrNull())
        assertEquals("Unprofessional Fridays", "UPF 03/15/2026".toHeuristicShowTitleOrNull())
        assertEquals(
            "Giant Bombcast",
            "https://www.giantbomb.com/shows/giant-bombcast-931-bleepbloop-remote/2970-99999".toHeuristicShowTitleFromUrlOrNull(),
        )
        assertEquals(
            "Unprofessional Fridays",
            "https://www.giantbomb.com/shows/upf-03152026/2970-55555".toHeuristicShowTitleFromUrlOrNull(),
        )
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
