package com.looktube.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

internal const val SplashQuoteAttributionNotice =
    "Selected public quotes from Giant Bomb cast members, past and present. Inclusion does not imply endorsement of LookTube."

@Composable
internal fun SplashQuoteSources(
    onDismiss: () -> Unit,
    onOpenSource: (String) -> Unit,
) {
    var linkError by remember { mutableStateOf(false) }
    fun openLink(url: String) {
        linkError = runCatching { onOpenSource(url) }.isFailure
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.safeDrawingPadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Splash quotes & sources", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                if (linkError) {
                    Text("Couldn't open the link. Check that a browser is installed and try again.", color = MaterialTheme.colorScheme.error)
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).testTag("splash-quotes-list"),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "attribution") {
                        Text(SplashQuoteAttributionNotice, modifier = Modifier.padding(vertical = 12.dp))
                    }
                    items(LaunchIntroQuotes, key = LaunchIntroQuote::id) { quote ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("“${quote.text}”", style = MaterialTheme.typography.bodyLarge)
                                Text("— ${quote.speaker.name}", style = MaterialTheme.typography.titleSmall)
                                Text(quote.context, style = MaterialTheme.typography.bodyMedium)
                                Text("${quote.source.title}\nPublished ${quote.source.publishedOn}", style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { openLink(quote.source.url) }) { Text("Read original source") }
                                Text(quote.speaker.role, style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { openLink(quote.speaker.evidenceUrl) }) { Text("Giant Bomb role source") }
                            }
                        }
                    }
                }
            }
        }
    }
}
