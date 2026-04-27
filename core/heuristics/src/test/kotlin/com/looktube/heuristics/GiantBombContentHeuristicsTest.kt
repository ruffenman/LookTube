package com.looktube.heuristics

import com.looktube.model.VideoSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GiantBombContentHeuristicsTest {
    @Test
    fun derivesCanonicalShowTitlesFromNumberedEpisodes() {
        assertEquals("Giant Bombcast", "Giant Bombcast 901: Ranking the Best Sandwiches in Games".toHeuristicShowTitleOrNull())
        assertEquals("Game Mess Mornings", "Game Mess Mornings 3/15/2026".toHeuristicShowTitleOrNull())
        assertEquals("9 Lives of Mister Mistofelees", "9 Lives of Mister Mistofelees | 04".toHeuristicShowTitleOrNull())
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
    fun infersTopicHeuristicsFromVideoMetadata() {
        val video = VideoSummary(
            id = "1",
            title = "Giant Bombcast 901: Ranking the Best Sandwiches in Games",
            description = "A discussion about the spiciest gaming sandwiches.",
            isPremium = true,
            feedCategory = "Premium",
            playbackUrl = null,
        )

        assertEquals("Giant Bombcast", video.displaySeriesTitle)
        assertEquals("Best Sandwiches", video.topicGroupingTitle)
    }

    @Test
    fun parserFacingHeuristicsStayCentralized() {
        assertTrue("Premium".isHeuristicGenericFeedCategory())
        assertEquals(
            "Quick Look",
            inferSeriesTitleFromFeedMetadata(
                feedCategory = "Premium",
                pageUrl = null,
                title = "Quick Look: Future Cop L.A.P.D.",
            ),
        )
        assertEquals(
            "https://image.example.com/quick-look-1.jpg",
            """<p>Preview.</p><img src="https://image.example.com/quick-look-1.jpg" />""".extractFirstImageUrlFromHtml(),
        )
    }
}
