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
}
