package com.looktube.app

import com.looktube.model.FeedConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchIntroQuotesTest {
    @Test
    fun shuffledDeckDoesNotRepeatDuringFirstFullPass() {
        val quoteIds = (0 until LaunchIntroQuoteDeckSize).map { deckIndex ->
            currentLaunchIntroQuote(
                FeedConfiguration(
                    feedUrl = "",
                    launchIntroQuoteDeckSeed = 42L,
                    launchIntroQuoteDeckIndex = deckIndex,
                ),
            ).id
        }

        assertEquals(LaunchIntroQuoteDeckSize, quoteIds.distinct().size)
    }

    @Test
    fun quoteDeckWrapsSafelyWhenIndexExceedsDeckSize() {
        val wrappedQuote = currentLaunchIntroQuote(
            FeedConfiguration(
                feedUrl = "",
                launchIntroQuoteDeckSeed = 42L,
                launchIntroQuoteDeckIndex = LaunchIntroQuoteDeckSize + 3,
            ),
        )

        assertTrue(LaunchIntroQuotes.any { quote -> quote.id == wrappedQuote.id })
    }
}
