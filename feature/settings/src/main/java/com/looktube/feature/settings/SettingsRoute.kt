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

@Composable
fun SettingsRoute(
    paddingValues: PaddingValues,
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

        items(diagnosticsCards) { (title, body) ->
            LookTubeCard(
                title = title,
                body = body,
            )
        }
    }
}
