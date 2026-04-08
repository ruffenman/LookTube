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
        assertEquals(listOf(18.dp, 36.dp, 54.dp), offsets)
        assertEquals(54.dp, collapsedHeaderPeekReveal(videoCount = 5))
    }

    @Test
    fun collapsedHeaderPeeksOnlyCountVideosBehindTheLeadCard() {
        assertEquals(emptyList<Dp>(), collapsedHeaderPeekOffsets(videoCount = 1))
        assertEquals(listOf(18.dp), collapsedHeaderPeekOffsets(videoCount = 2))
    }

    @Test
    fun backdropTileSpecsAreDeterministicPerSectionKey() {
        assertEquals(
            groupHeaderBackdropTileSpecs("show:Giant Bombcast"),
            groupHeaderBackdropTileSpecs("show:Giant Bombcast"),
        )
    }

    @Test
    fun backdropTileSpecsStayWithinExpectedBounds() {
        val specs = groupHeaderBackdropTileSpecs("show:Portal Pals")
        assertEquals(12, specs.size)
        assertTrue(specs.all { it.xFraction in -0.14f..0.82f })
        assertTrue(specs.all { it.yFraction in -0.12f..0.7f })
        assertTrue(specs.all { it.widthFraction in 0.16f..0.36f })
        assertTrue(specs.all { it.heightFraction in 0.28f..0.56f })
    }
}
