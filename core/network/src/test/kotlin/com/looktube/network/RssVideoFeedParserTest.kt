package com.looktube.network

import com.looktube.testing.loadFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RssVideoFeedParserTest {
    private val parser = RssVideoFeedParser()

    @Test
    fun parsesPremiumFeedFixtureIntoVideoSummaries() {
        val fixture = loadFixture("fixtures/giantbomb-premium-feed.xml")

        val videos = parser.parse(fixture)

        assertEquals(2, videos.size)
        assertTrue(videos.all { it.isPremium })
        assertEquals("https://video.example.com/premium-1.m3u8", videos.first().playbackUrl)
        assertEquals("Blight Club", videos.first().seriesTitle)
        assertEquals("https://image.example.com/blight-club-1.jpg", videos.first().thumbnailUrl)
    }

    @Test
    fun fallsBackToEnclosureUrlWhenMediaContentIsMissing() {
        val fixture = """
            <rss version="2.0">
                <channel>
                    <item>
                        <guid>enclosure-1</guid>
                        <title>Premium fallback enclosure</title>
                        <description>Fallback parser coverage.</description>
                        <category>Premium</category>
                        <enclosure url="https://video.example.com/enclosure-1.mp4" />
                    </item>
                </channel>
            </rss>
        """.trimIndent()

        val videos = parser.parse(fixture)

        assertEquals(1, videos.size)
        assertEquals("https://video.example.com/enclosure-1.mp4", videos.single().playbackUrl)
    }

    @Test
    fun infersSeriesAndThumbnailFromDescriptionWhenFeedCategoryIsGeneric() {
        val fixture = """
            <rss version="2.0">
                <channel>
                    <item>
                        <guid>quick-look-1</guid>
                        <title>Quick Look: Future Cop L.A.P.D.</title>
                        <description><![CDATA[<p>Preview.</p><img src="https://image.example.com/quick-look-1.jpg" />]]></description>
                        <category>Premium</category>
                        <enclosure url="https://video.example.com/quick-look-1.mp4" />
                    </item>
                </channel>
            </rss>
        """.trimIndent()

        val videos = parser.parse(fixture)

        assertEquals("Quick Look", videos.single().seriesTitle)
        assertEquals("https://image.example.com/quick-look-1.jpg", videos.single().thumbnailUrl)
    }

    @Test
    fun stripsEpisodeNumbersFromHeuristicShowTitles() {
        val fixture = """
            <rss version="2.0">
                <channel>
                    <item>
                        <guid>bombcast-1</guid>
                        <title>Giant Bombcast 901: Ranking the Best Sandwiches in Games</title>
                        <description>Podcast coverage.</description>
                        <category>Premium</category>
                        <enclosure url="https://video.example.com/bombcast-1.mp4" />
                    </item>
                </channel>
            </rss>
        """.trimIndent()

        val videos = parser.parse(fixture)

        assertEquals("Giant Bombcast", videos.single().seriesTitle)
    }

    @Test
    fun infersShowFromGiantBombShowPageLinkWhenTitleIsOnlyEpisodeNumber() {
        val fixture = """
            <rss version="2.0">
                <channel>
                    <item>
                        <guid>bombcast-link-1</guid>
                        <title>931: Bleepbloop Remote</title>
                        <description>Podcast coverage.</description>
                        <category>Premium</category>
                        <link>https://www.giantbomb.com/shows/giant-bombcast-931-bleepbloop-remote/2970-99999</link>
                        <enclosure url="https://video.example.com/bombcast-link-1.mp4" />
                    </item>
                </channel>
            </rss>
        """.trimIndent()

        val videos = parser.parse(fixture)

        assertEquals("Giant Bombcast", videos.single().seriesTitle)
    }

    @Test
    fun infersVoicemailDumpTruckForBonusDumpTitles() {
        val fixture = """
            <rss version="2.0">
                <channel>
                    <item>
                        <guid>dump-1</guid>
                        <title>183 BONUS DUMP</title>
                        <description>Extra speakeasy dumpin'.</description>
                        <category>Premium</category>
                        <link>https://www.giantbomb.com/shows/183-bonus-dump/2970-23712/premium-video</link>
                        <enclosure url="https://video.example.com/dump-1.mp4" />
                    </item>
                </channel>
            </rss>
        """.trimIndent()

        val videos = parser.parse(fixture)

        assertEquals("Voicemail Dump Truck", videos.single().seriesTitle)
    }
}
