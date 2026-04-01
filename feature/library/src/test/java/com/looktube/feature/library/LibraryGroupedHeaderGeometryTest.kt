package com.looktube.feature.library

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryGroupedHeaderGeometryTest {
    @Test
    fun collapsedHeaderPeekOffsetsUseEqualRevealSteps() {
        val offsets = collapsedHeaderPeekOffsets(videoCount = 5)

        assertEquals(listOf(10.dp, 20.dp, 30.dp), offsets)
        assertEquals(30.dp, collapsedHeaderPeekReveal(videoCount = 5))
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
        assertEquals(11, specs.size)
        assertTrue(specs.all { it.xFraction in -0.12f..0.86f })
        assertTrue(specs.all { it.yFraction in -0.12f..0.72f })
        assertTrue(specs.all { it.widthFraction in 0.18f..0.32f })
        assertTrue(specs.all { it.heightFraction in 0.22f..0.4f })
    }
}
