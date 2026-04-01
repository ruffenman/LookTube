package com.looktube.feature.library

import androidx.compose.ui.unit.dp
import com.looktube.model.PlaybackProgress
import com.looktube.model.VideoSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun latestSectionSortBreaksTimestampTiesBySectionTitleInsteadOfEpisodeTitle() {
        val zetaSection = section(
            title = "Zeta Show",
            sortAnchor = video(
                id = "zeta-newest",
                title = "Alpha Episode",
                seriesTitle = "Zeta Show",
                publishedAtEpochMillis = 2_000L,
            ),
        )
        val alphaSection = section(
            title = "Alpha Show",
            sortAnchor = video(
                id = "alpha-newest",
                title = "Zulu Episode",
                seriesTitle = "Alpha Show",
                publishedAtEpochMillis = 2_000L,
            ),
        )

        val sortedTitles = listOf(zetaSection, alphaSection)
            .sortedWith(sectionComparator(LibrarySortOption.Latest))
            .map(SeriesSection::title)

        assertEquals(listOf("Alpha Show", "Zeta Show"), sortedTitles)
    }

    @Test
    fun latestSortPlacesUndatedVideosAfterDatedVideos() {
        val videos = listOf(
            video(
                id = "undated",
                title = "Undated Episode",
                seriesTitle = "Alpha Show",
                publishedAtEpochMillis = null,
            ),
            video(
                id = "newest",
                title = "Newest Episode",
                seriesTitle = "Beta Show",
                publishedAtEpochMillis = 3_000L,
            ),
            video(
                id = "older",
                title = "Older Episode",
                seriesTitle = "Gamma Show",
                publishedAtEpochMillis = 1_000L,
            ),
        )

        val sortedIds = videos.sortedWith(videoComparator(LibrarySortOption.Latest)).map(VideoSummary::id)

        assertEquals(listOf("newest", "older", "undated"), sortedIds)
    }

    @Test
    fun latestSectionSortPlacesUndatedGroupsAfterDatedGroups() {
        val datedSection = section(
            title = "Dated Show",
            videos = listOf(
                video(
                    id = "dated-newest",
                    title = "Newest",
                    seriesTitle = "Dated Show",
                    publishedAtEpochMillis = 3_000L,
                ),
            ),
        )
        val undatedSection = section(
            title = "Undated Show",
            videos = listOf(
                video(
                    id = "undated-only",
                    title = "Only Episode",
                    seriesTitle = "Undated Show",
                    publishedAtEpochMillis = null,
                ),
            ),
        )

        val sortedTitles = listOf(undatedSection, datedSection)
            .sortedWith(sectionComparator(LibrarySortOption.Latest))
            .map(SeriesSection::title)

        assertEquals(listOf("Dated Show", "Undated Show"), sortedTitles)
    }

    @Test
    fun showSectionSortUsesAlphabeticalGroupsAndNewestEpisodesFirstWithinEachGroup() {
        val alphaNewest = video(
            id = "alpha-newest",
            title = "Newest Alpha",
            seriesTitle = "Alpha Show",
            publishedAtEpochMillis = 2_000L,
        )
        val alphaOlder = video(
            id = "alpha-older",
            title = "Older Alpha",
            seriesTitle = "Alpha Show",
            publishedAtEpochMillis = 1_000L,
        )
        val betaEpisode = video(
            id = "beta-episode",
            title = "Beta Episode",
            seriesTitle = "Beta Show",
            publishedAtEpochMillis = 3_000L,
        )

        val alphaSection = section(
            title = "Alpha Show",
            videos = listOf(alphaOlder, alphaNewest).sortedWith(videoComparator(LibrarySortOption.Show)),
        )
        val betaSection = section(
            title = "Beta Show",
            videos = listOf(betaEpisode).sortedWith(videoComparator(LibrarySortOption.Show)),
        )

        val sortedSections = listOf(betaSection, alphaSection)
            .sortedWith(sectionComparator(LibrarySortOption.Show))

        assertEquals(listOf("Alpha Show", "Beta Show"), sortedSections.map(SeriesSection::title))
        assertEquals(listOf("alpha-newest", "alpha-older"), sortedSections.first().videos.map(VideoSummary::id))
    }

    @Test
    fun latestSectionSortUsesNewestEpisodeAcrossGroupsAndKeepsEpisodesNewestFirst() {
        val alphaNewest = video(
            id = "alpha-newest",
            title = "Alpha New",
            seriesTitle = "Alpha Show",
            publishedAtEpochMillis = 2_000L,
        )
        val alphaOlder = video(
            id = "alpha-older",
            title = "Alpha Old",
            seriesTitle = "Alpha Show",
            publishedAtEpochMillis = 1_000L,
        )
        val betaNewest = video(
            id = "beta-newest",
            title = "Beta New",
            seriesTitle = "Beta Show",
            publishedAtEpochMillis = 3_000L,
        )

        val alphaSection = section(
            title = "Alpha Show",
            videos = listOf(alphaOlder, alphaNewest).sortedWith(videoComparator(LibrarySortOption.Latest)),
        )
        val betaSection = section(
            title = "Beta Show",
            videos = listOf(betaNewest).sortedWith(videoComparator(LibrarySortOption.Latest)),
        )

        val sortedSections = listOf(alphaSection, betaSection)
            .sortedWith(sectionComparator(LibrarySortOption.Latest))

        assertEquals(listOf("Beta Show", "Alpha Show"), sortedSections.map(SeriesSection::title))
        assertEquals(listOf("alpha-newest", "alpha-older"), sortedSections[1].videos.map(VideoSummary::id))
    }

    @Test
    fun oldestSortPlacesUndatedVideosAfterDatedVideos() {
        val videos = listOf(
            video(
                id = "undated",
                title = "Undated Episode",
                seriesTitle = "Alpha Show",
                publishedAtEpochMillis = null,
            ),
            video(
                id = "oldest",
                title = "Oldest Episode",
                seriesTitle = "Beta Show",
                publishedAtEpochMillis = 1_000L,
            ),
            video(
                id = "newer",
                title = "Newer Episode",
                seriesTitle = "Gamma Show",
                publishedAtEpochMillis = 3_000L,
            ),
        )

        val sortedIds = videos.sortedWith(videoComparator(LibrarySortOption.Oldest)).map(VideoSummary::id)

        assertEquals(listOf("oldest", "newer", "undated"), sortedIds)
    }

    @Test
    fun oldestSectionSortPlacesUndatedGroupsAfterDatedGroups() {
        val datedSection = section(
            title = "Dated Show",
            videos = listOf(
                video(
                    id = "dated-oldest",
                    title = "Oldest",
                    seriesTitle = "Dated Show",
                    publishedAtEpochMillis = 1_000L,
                ),
            ),
        )
        val undatedSection = section(
            title = "Undated Show",
            videos = listOf(
                video(
                    id = "undated-only",
                    title = "Only Episode",
                    seriesTitle = "Undated Show",
                    publishedAtEpochMillis = null,
                ),
            ),
        )

        val sortedTitles = listOf(undatedSection, datedSection)
            .sortedWith(sectionComparator(LibrarySortOption.Oldest))
            .map(SeriesSection::title)

        assertEquals(listOf("Dated Show", "Undated Show"), sortedTitles)
    }

    @Test
    fun oldestSectionSortBreaksTimestampTiesBySectionTitleInsteadOfEpisodeTitle() {
        val zetaSection = section(
            title = "Zeta Show",
            sortAnchor = video(
                id = "zeta-oldest",
                title = "Alpha Episode",
                seriesTitle = "Zeta Show",
                publishedAtEpochMillis = 1_000L,
            ),
        )
        val alphaSection = section(
            title = "Alpha Show",
            sortAnchor = video(
                id = "alpha-oldest",
                title = "Zulu Episode",
                seriesTitle = "Alpha Show",
                publishedAtEpochMillis = 1_000L,
            ),
        )

        val sortedTitles = listOf(zetaSection, alphaSection)
            .sortedWith(sectionComparator(LibrarySortOption.Oldest))
            .map(SeriesSection::title)

        assertEquals(listOf("Alpha Show", "Zeta Show"), sortedTitles)
    }

    @Test
    fun displayedSectionsRespectCollapsedKeys() {
        val sections = listOf(
            section(
                title = "Alpha Show",
                videos = listOf(
                    video(
                        id = "alpha-1",
                        title = "Alpha 1",
                        seriesTitle = "Alpha Show",
                        publishedAtEpochMillis = 1_000L,
                    ),
                ),
            ),
            section(
                title = "Beta Show",
                videos = listOf(
                    video(
                        id = "beta-1",
                        title = "Beta 1",
                        seriesTitle = "Beta Show",
                        publishedAtEpochMillis = 2_000L,
                    ),
                ),
            ),
        )

        val displayedSections = buildDisplayedSections(
            sections = sections,
            collapsedSectionKeys = setOf("section-Beta Show"),
        )

        assertTrue(displayedSections.first().isExpanded)
        assertFalse(displayedSections.last().isExpanded)
    }

    @Test
    fun groupedSectionCardsEachConsumeOneJumpRailRow() {
        val displayedSections = listOf(
            DisplayedSeriesSection(
                section = section(
                    title = "Alpha Show",
                    videos = listOf(
                        video(
                            id = "alpha-1",
                            title = "Alpha 1",
                            seriesTitle = "Alpha Show",
                            publishedAtEpochMillis = 1_000L,
                        ),
                        video(
                            id = "alpha-2",
                            title = "Alpha 2",
                            seriesTitle = "Alpha Show",
                            publishedAtEpochMillis = 2_000L,
                        ),
                    ),
                ),
                isExpanded = true,
            ),
            DisplayedSeriesSection(
                section = section(
                    title = "Beta Show",
                    videos = listOf(
                        video(
                            id = "beta-1",
                            title = "Beta 1",
                            seriesTitle = "Beta Show",
                            publishedAtEpochMillis = 3_000L,
                        ),
                        video(
                            id = "beta-2",
                            title = "Beta 2",
                            seriesTitle = "Beta Show",
                            publishedAtEpochMillis = 4_000L,
                        ),
                    ),
                ),
                isExpanded = false,
            ),
            DisplayedSeriesSection(
                section = section(
                    title = "Gamma Show",
                    videos = listOf(
                        video(
                            id = "gamma-1",
                            title = "Gamma 1",
                            seriesTitle = "Gamma Show",
                            publishedAtEpochMillis = 5_000L,
                        ),
                    ),
                ),
                isExpanded = true,
            ),
        )

        assertEquals(listOf(2, 3, 4), buildSectionStartIndices(displayedSections))
    }

    @Test
    fun watchToggleActionLabelReflectsCurrentState() {
        assertEquals("Mark as Watched", watchToggleActionLabel(isWatched = false))
        assertEquals("Mark as Unwatched", watchToggleActionLabel(isWatched = true))
    }

    @Test
    fun jumpRailFadeDelayIsShortAfterScrollingAndLongerAfterRailSelections() {
        assertEquals(200L, jumpRailFadeDelayMs(JumpRailInteraction.Scroll))
        assertEquals(1_000L, jumpRailFadeDelayMs(JumpRailInteraction.TargetSelection))
    }

    @Test
    fun collapsedHeaderPeekCountCapsAtThreeAndRespectsGroupSize() {
        assertEquals(0, collapsedHeaderPeekCount(0))
        assertEquals(1, collapsedHeaderPeekCount(2))
        assertEquals(3, collapsedHeaderPeekCount(5))
    }

    @Test
    fun collapsedHeaderPeekRevealOnlyUsesNeededDepth() {
        assertEquals(0.dp, collapsedHeaderPeekReveal(0))
        assertEquals(0.dp, collapsedHeaderPeekReveal(1))
        assertEquals(20.dp, collapsedHeaderPeekReveal(3))
        assertEquals(30.dp, collapsedHeaderPeekReveal(5))
    }

    @Test
    fun displayedPlaybackProgressFillsToTheEndForWatchedVideos() {
        val video = video(
            id = "alpha-1",
            title = "Alpha 1",
            seriesTitle = "Alpha Show",
            publishedAtEpochMillis = 1_000L,
            durationSeconds = 4_200L,
        )
        val progress = PlaybackProgress(
            videoId = "alpha-1",
            positionSeconds = 1_250L,
            durationSeconds = 4_200L,
        )

        val displayedProgress = displayedPlaybackProgress(
            video = video,
            progress = progress,
            isWatched = true,
        )

        assertEquals(4_200L, displayedProgress?.positionSeconds)
        assertEquals(4_200L, displayedProgress?.durationSeconds)
    }

    @Test
    fun displayedPlaybackProgressPreservesPartialProgressForUnwatchedVideos() {
        val video = video(
            id = "alpha-1",
            title = "Alpha 1",
            seriesTitle = "Alpha Show",
            publishedAtEpochMillis = 1_000L,
            durationSeconds = 4_200L,
        )
        val progress = PlaybackProgress(
            videoId = "alpha-1",
            positionSeconds = 1_250L,
            durationSeconds = 4_200L,
        )

        val displayedProgress = displayedPlaybackProgress(
            video = video,
            progress = progress,
            isWatched = false,
        )

        assertEquals(progress, displayedProgress)
    }

    @Test
    fun displayedPlaybackProgressUsesFeedDurationForWatchedVideosWithoutSavedProgress() {
        val video = video(
            id = "alpha-1",
            title = "Alpha 1",
            seriesTitle = "Alpha Show",
            publishedAtEpochMillis = 1_000L,
            durationSeconds = 4_200L,
        )

        val displayedProgress = displayedPlaybackProgress(
            video = video,
            progress = null,
            isWatched = true,
        )

        assertEquals(4_200L, displayedProgress?.positionSeconds)
        assertEquals(4_200L, displayedProgress?.durationSeconds)
    }

    @Test
    fun displayedPlaybackProgressStaysAbsentWhenAnUnwatchedVideoHasNoProgress() {
        val video = video(
            id = "alpha-1",
            title = "Alpha 1",
            seriesTitle = "Alpha Show",
            publishedAtEpochMillis = 1_000L,
            durationSeconds = 4_200L,
        )

        val displayedProgress = displayedPlaybackProgress(
            video = video,
            progress = null,
            isWatched = false,
        )

        assertNull(displayedProgress)
    }

    private fun video(
        id: String,
        title: String,
        seriesTitle: String,
        publishedAtEpochMillis: Long?,
        durationSeconds: Long? = null,
    ) = VideoSummary(
        id = id,
        title = title,
        description = "",
        isPremium = true,
        feedCategory = "Premium",
        playbackUrl = null,
        seriesTitle = seriesTitle,
        publishedAtEpochMillis = publishedAtEpochMillis,
        durationSeconds = durationSeconds,
    )

    private fun section(
        title: String,
        sortAnchor: VideoSummary,
    ) = section(title = title, videos = listOf(sortAnchor))

    private fun section(
        title: String,
        videos: List<VideoSummary>,
    ) = SeriesSection(
        key = "section-$title",
        title = title,
        kindLabel = "Show",
        videos = videos,
        sortAnchor = videos.first(),
    )
}
