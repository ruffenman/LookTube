package com.looktube.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

@Composable
fun LookTubeCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    statusLabel: String? = null,
    statusLabelContainerColor: Color = Color.Unspecified,
    statusLabelContentColor: Color = Color.Unspecified,
) {
    val resolvedContainerColor = if (containerColor == Color.Unspecified) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        containerColor
    }
    val resolvedContentColor = if (contentColor == Color.Unspecified) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        contentColor
    }
    val resolvedStatusLabelContainerColor = if (statusLabelContainerColor == Color.Unspecified) {
        MaterialTheme.colorScheme.surface
    } else {
        statusLabelContainerColor
    }
    val resolvedStatusLabelContentColor = if (statusLabelContentColor == Color.Unspecified) {
        MaterialTheme.colorScheme.onSurface
    } else {
        statusLabelContentColor
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = resolvedContainerColor,
            contentColor = resolvedContentColor,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                if (statusLabel != null) {
                    Surface(
                        color = resolvedStatusLabelContainerColor,
                        contentColor = resolvedStatusLabelContentColor,
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}
