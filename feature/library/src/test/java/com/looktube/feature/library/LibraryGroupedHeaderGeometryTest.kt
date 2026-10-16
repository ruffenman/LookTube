package com.looktube.feature.library
import androidx.compose.ui.unit.Dp

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
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

}
