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
}
