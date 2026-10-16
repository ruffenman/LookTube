package com.looktube.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.compositeOver

/** Shared structural panels across Settings, Library and Player. Artwork keeps its own overlays. */
object LookTubeStyle {
    val panelShape = RoundedCornerShape(24.dp)
    val insetShape = RoundedCornerShape(16.dp)
    val panelColor @Composable get() = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f).compositeOver(MaterialTheme.colorScheme.surface)
    val panelBorder @Composable get() = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
}
