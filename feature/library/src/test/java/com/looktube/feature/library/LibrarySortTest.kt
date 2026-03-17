package com.looktube.feature.library

import com.looktube.model.VideoSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySortTest {
    @Test
    fun latestSortDoesNotReGroupFlatListsByShowWhenTimestampsMatch() {
        val videos = listOf(
            video(
                id = "b",
                title = "Bravo",
                seriesTitle = "Zeta Show",
                publishedAtEpochMillis = 2_000L,
            ),
            video(
                id = "a",
                title = "Alpha",
                seriesTitle = "Alpha Show",
                publishedAtEpochMillis = 2_000L,
            ),
            video(
                id = "c",
                title = "Charlie",
                seriesTitle = "Beta Show",
                publishedAtEpochMillis = 1_000L,
            ),
        )

        val sortedIds = videos.sortedWith(videoComparator(LibrarySortOption.Latest)).map(VideoSummary::id)

        assertEquals(listOf("a", "b", "c"), sortedIds)
    }

    @Test
    fun oldestSortDoesNotReGroupFlatListsByShowWhenTimestampsMatch() {
        val videos = listOf(
            video(
                id = "c",
                title = "Charlie",
                seriesTitle = "Zeta Show",
                publishedAtEpochMillis = 1_000L,
            ),
            video(
                id = "a",
                title = "Alpha",
                seriesTitle = "Alpha Show",
                publishedAtEpochMillis = 1_000L,
            ),
            video(
                id = "b",
                title = "Bravo",
                seriesTitle = "Beta Show",
                publishedAtEpochMillis = 2_000L,
            ),
        )

        val sortedIds = videos.sortedWith(videoComparator(LibrarySortOption.Oldest)).map(VideoSummary::id)

        assertEquals(listOf("a", "c", "b"), sortedIds)
    }

    @Test
    fun showSortStillGroupsByShowBeforePublishedDate() {
        val videos = listOf(
            video(
                id = "b",
                title = "Newest Alpha",
                seriesTitle = "Alpha Show",
                publishedAtEpochMillis = 2_000L,
            ),
            video(
                id = "a",
                title = "Older Alpha",
                seriesTitle = "Alpha Show",
                publishedAtEpochMillis = 1_000L,
            ),
            video(
                id = "c",
                title = "Beta Episode",
                seriesTitle = "Beta Show",
                publishedAtEpochMillis = 3_000L,
            ),
        )

        val sortedIds = videos.sortedWith(videoComparator(LibrarySortOption.Show)).map(VideoSummary::id)

        assertEquals(listOf("b", "a", "c"), sortedIds)
    }

    private fun video(
        id: String,
        title: String,
        seriesTitle: String,
        publishedAtEpochMillis: Long,
    ) = VideoSummary(
        id = id,
        title = title,
        description = "",
        isPremium = true,
        feedCategory = "Premium",
        playbackUrl = null,
        seriesTitle = seriesTitle,
        publishedAtEpochMillis = publishedAtEpochMillis,
    )
}
