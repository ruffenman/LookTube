package com.looktube.app

import com.looktube.model.FeedConfiguration
import com.looktube.model.VideoSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchIntroMessagesTest {
    @Test
    fun shuffledGenericDeckDoesNotRepeatDuringFirstFullPass() {
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
    fun genericMessageDeckWrapsSafelyWhenIndexExceedsDeckSize() {
        val wrappedMessage = currentLaunchIntroMessage(
            feedConfiguration = FeedConfiguration(
                feedUrl = "",
                launchIntroMessageDeckSeed = 42L,
                launchIntroMessageDeckIndex = LaunchIntroMessageDeckSize + 3,
            ),
            videos = emptyList(),
            previousAppOpenedAtEpochMillis = null,
        )

        assertTrue(GenericLaunchIntroMessages.any { message -> message.id == wrappedMessage.id })
    }

    @Test
    fun newVideosSincePreviousOpenUseDigestInsteadOfGenericMessage() {
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
        assertEquals("2 new videos since last visit", message.headline)
        assertTrue(message.body.indexOf("Newest video") < message.body.indexOf("Newer video"))
        assertTrue(!message.body.contains("Older video"))
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
