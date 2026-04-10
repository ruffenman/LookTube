package com.looktube.feature.library
import androidx.compose.ui.unit.Dp

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryGroupedHeaderGeometryTest {
    @Test
    fun collapsedHeaderPeekOffsetsUseEqualRevealSteps() {
        val offsets = collapsedHeaderPeekOffsets(videoCount = 5)
        assertEquals(listOf(22.dp, 44.dp, 66.dp), offsets)
        assertEquals(66.dp, collapsedHeaderPeekReveal(videoCount = 5))
    }

    @Test
    fun collapsedHeaderPeeksOnlyCountVideosBehindTheLeadCard() {
        assertEquals(emptyList<Dp>(), collapsedHeaderPeekOffsets(videoCount = 1))
        assertEquals(listOf(22.dp), collapsedHeaderPeekOffsets(videoCount = 2))
    }

    @Test
    fun mosaicTilesAreDeterministicPerSectionKey() {
        assertEquals(
            generateMosaicTiles("show:Giant Bombcast", 5),
            generateMosaicTiles("show:Giant Bombcast", 5),
        )
    }

    @Test
    fun mosaicTilesStayWithinExpectedBounds() {
        val specs = generateMosaicTiles("show:Portal Pals", 3)
        assertEquals(18, specs.size)
        assertTrue(specs.all { it.widthFraction in 0.2f..0.6f })
        assertTrue(specs.all { it.heightFraction in 0.25f..0.8f })
        assertTrue(specs.all { it.videoIndex in 0..2 })
    }
}
