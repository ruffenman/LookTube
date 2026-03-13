package com.looktube.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.looktube.designsystem.LookTubeCard
import com.looktube.model.FeedConfiguration
import com.looktube.model.LibrarySyncState

@Composable
fun SettingsRoute(
    paddingValues: PaddingValues,
    feedConfiguration: FeedConfiguration,
    syncState: LibrarySyncState,
) {
    val diagnosticsCards = listOf(
        "Fast Ralph loop" to ".\\gradlew.bat verifyFast",
        "Full local gate" to ".\\gradlew.bat verifyLocal -PskipManagedDevice=true",
        "Premium feed probe" to ".\\gradlew.bat integrationProbeGiantBomb",
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item {
            Text("Diagnostics and operating notes")
        }

        item {
            LookTubeCard(
                title = "Persisted feed identity",
                body = buildString {
                    appendLine("Auth mode: ${feedConfiguration.authMode ?: "Unconfigured"}")
                    appendLine("Feed URL: ${feedConfiguration.feedUrl.ifBlank { "Not set" }}")
                    appendLine("Username: ${feedConfiguration.username.ifBlank { "Not set" }}")
                    append("Password persistence: session only")
                },
            )
        }

        item {
            LookTubeCard(
                title = "Latest sync status",
                body = syncState.message,
            )
        }

        items(diagnosticsCards) { (title, body) ->
            LookTubeCard(
                title = title,
                body = body,
            )
        }
    }
}
