package com.looktube.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun AutoplaySettings(
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    nextVideoTitle: String? = null,
    nextVideoReason: String? = null,
    showNextVideo: Boolean = false,
    onPlayNext: () -> Unit = {},
) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Autoplay next video", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = enabled, onCheckedChange = onEnabledChanged,
                    modifier = Modifier.testTag("autoplay-toggle").semantics { contentDescription = "Autoplay next video" })
            }
            Text("When a video finishes, continue with an unwatched video from the same show, then use your recent viewing history. Recommendations stay on this device.",
                style = MaterialTheme.typography.bodyMedium)
            if (showNextVideo) {
                Text(if (enabled) "Up next" else "Suggested next", style = MaterialTheme.typography.labelLarge)
                Text(nextVideoTitle ?: "No unwatched playable videos available", style = MaterialTheme.typography.bodyLarge)
                nextVideoReason?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (nextVideoTitle != null) {
                    OutlinedButton(onClick = onPlayNext) { Text("Play next now") }
                }
                if (!enabled) Text("Autoplay is off. Playback stops after this video.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
