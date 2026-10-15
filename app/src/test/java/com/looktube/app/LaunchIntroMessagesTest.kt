package com.looktube.app

import com.looktube.model.FeedConfiguration
import com.looktube.model.VideoSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import java.net.URI
import org.junit.Test

class LaunchIntroMessagesTest {
    @Test
    fun shuffledQuoteDeckDoesNotRepeatDuringFirstFullPass() {
        val messageIds = (0 until LaunchIntroMessageDeckSize).map { deckIndex ->
            currentLaunchIntroMessage(
                feedConfiguration = FeedConfiguration(
                    feedUrl = "",
                    launchIntroMessageDeckSeed = 42L,
                    launchIntroMessageDeckIndex = deckIndex,
                ),
                videos = emptyList(),
                previousAppOpenedAtEpochMillis = null,
            ).id
        }

        assertEquals(LaunchIntroMessageDeckSize, messageIds.distinct().size)
    }

    @Test
    fun quoteDeckWrapsSafelyWhenIndexExceedsDeckSize() {
        val wrappedMessage = currentLaunchIntroMessage(
            feedConfiguration = FeedConfiguration(
                feedUrl = "",
                launchIntroMessageDeckSeed = 42L,
                launchIntroMessageDeckIndex = LaunchIntroMessageDeckSize + 3,
            ),
            videos = emptyList(),
            previousAppOpenedAtEpochMillis = null,
        )

        assertTrue(LaunchIntroQuotes.any { quote -> quote.id == wrappedMessage.quote?.id })
    }

    @Test
    fun newVideosSincePreviousOpenKeepDigestAndAttributedQuote() {
        val message = currentLaunchIntroMessage(
            feedConfiguration = FeedConfiguration(feedUrl = ""),
            videos = listOf(
                video(
                    id = "older",
                    title = "Older video",
                    publishedAtEpochMillis = 999L,
                ),
                video(
                    id = "newest",
                    title = "Newest video",
                    publishedAtEpochMillis = 2_000L,
                ),
                video(
                    id = "newer",
                    title = "Newer video",
                    publishedAtEpochMillis = 1_500L,
                ),
            ),
            previousAppOpenedAtEpochMillis = 1_000L,
        )

        assertEquals("new-video-digest", message.id)
        assertNotNull(message.quote)
        assertEquals("2 new videos since last visit", message.headline)
        assertTrue(message.body.indexOf("Newest video") < message.body.indexOf("Newer video"))
        assertTrue(!message.body.contains("Older video"))
    }

    @Test
    fun quoteCatalogueExceedsOldDeckAndEveryEntryHasPublicSourceAndCastEvidence() {
        assertTrue(LaunchIntroQuotes.size > 20)
        assertEquals(LaunchIntroQuotes.size, LaunchIntroQuotes.map { it.id }.distinct().size)
        LaunchIntroQuotes.forEach { quote ->
            assertTrue(quote.text.isNotBlank())
            assertTrue(quote.context.isNotBlank())
            assertTrue(quote.speaker.name.isNotBlank())
            assertTrue(quote.speaker.role.isNotBlank())
            assertTrue(quote.source.title.isNotBlank())
            java.time.LocalDate.parse(quote.source.publishedOn)
            listOf(quote.source.url, quote.speaker.evidenceUrl).forEach { url ->
                assertEquals("https", URI(url).scheme)
                assertEquals("giantbomb.com", URI(url).host)
            }
        }
    }

    @Test
    fun extremeAndNegativeStoredIndicesResolveToAnAttributedQuote() {
        listOf(-1, Int.MIN_VALUE, Int.MAX_VALUE).forEach { index ->
            val message = currentLaunchIntroMessage(
                FeedConfiguration(feedUrl = "", launchIntroMessageDeckIndex = index),
                videos = emptyList(),
                previousAppOpenedAtEpochMillis = null,
            )
            assertNotNull(message.quote)
            assertEquals(message.quote!!.text, message.headline)
            assertEquals("— ${message.quote.speaker.name}", message.body)
        }
    }
}

private fun video(
    id: String,
    title: String,
    publishedAtEpochMillis: Long,
): VideoSummary = VideoSummary(
    id = id,
    title = title,
    description = "Description",
    isPremium = true,
    feedCategory = "Premium",
    playbackUrl = "https://video.example.com/$id.mp4",
    publishedAtEpochMillis = publishedAtEpochMillis,
)
