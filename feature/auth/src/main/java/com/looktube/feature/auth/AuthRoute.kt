package com.looktube.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.looktube.designsystem.LookTubeCard
import com.looktube.designsystem.LookTubePageHeader
import com.looktube.model.AccountSession
import com.looktube.model.FeedConfiguration
import com.looktube.model.LibrarySyncState
import com.looktube.model.LocalCaptionEngine
import com.looktube.model.LocalCaptionModelState
import com.looktube.model.SyncPhase

@Composable
fun AuthRoute(
    paddingValues: PaddingValues,
    accountSession: AccountSession,
    feedConfiguration: FeedConfiguration,
    syncState: LibrarySyncState,
    availableLocalCaptionEngines: List<LocalCaptionEngine>,
    selectedLocalCaptionEngine: LocalCaptionEngine,
    localCaptionModelState: LocalCaptionModelState,
    onFeedUrlChanged: (String) -> Unit,
    onSignInRequested: () -> Unit,
    onLocalCaptionEngineSelected: (String) -> Unit,
    onDownloadLocalCaptionModel: () -> Unit,
    onClearSyncedDataRequested: () -> Unit,
) {
    val isSigningIn = syncState.phase == SyncPhase.Refreshing
    val canSignIn = feedConfiguration.feedUrl.isNotBlank() && !isSigningIn
    val colorScheme = MaterialTheme.colorScheme
    val statusLabel = when {
        isSigningIn -> "Syncing"
        accountSession.isSignedIn -> "Synced"
        feedConfiguration.feedUrl.isBlank() -> "Setup required"
        syncState.phase == SyncPhase.Error -> "Needs attention"
        else -> "Ready"
    }
    val statusContainerColor = when (statusLabel) {
        "Synced" -> colorScheme.primaryContainer
        "Syncing" -> colorScheme.tertiaryContainer
        "Needs attention" -> colorScheme.errorContainer
        "Ready" -> colorScheme.secondaryContainer
        else -> colorScheme.surfaceVariant
    }
    val statusContentColor = when (statusLabel) {
        "Synced" -> colorScheme.onPrimaryContainer
        "Syncing" -> colorScheme.onTertiaryContainer
        "Needs attention" -> colorScheme.onErrorContainer
        "Ready" -> colorScheme.onSecondaryContainer
        else -> colorScheme.onSurfaceVariant
    }
    val primaryInstruction = when {
        isSigningIn -> "Wait while LookTube refreshes your copied Premium feed."
        feedConfiguration.feedUrl.isBlank() -> "Paste the RSS URL copied from Giant Bomb feeds, then sync your library."
        accountSession.isSignedIn -> "Your synced library is already saved on this device. Sync again any time to refresh it."
        else -> "Your feed URL is ready. Tap Sync Premium feed to load the library."
    }
    val feedUrlSupportingText = when {
        feedConfiguration.feedUrl.isBlank() -> "Paste the copied Premium RSS feed URL from Giant Bomb."
        isSigningIn -> "Keep this feed URL saved while the current sync finishes."
        else -> "This copied feed URL stays saved on this device for the next sync."
    }
    val statusBody = buildString {
        append(syncState.message)
        syncState.lastSuccessfulSyncSummary?.let { summary ->
            append("\n\n")
            append(summary)
        }
        if (accountSession.isSignedIn || syncState.phase == SyncPhase.Success) {
            append("\n\nClear synced data removes the cached library and saved progress but keeps your feed settings ready for the next sync.")
        }
    }
    val canClearData = !isSigningIn && (
        accountSession.isSignedIn ||
            syncState.lastSuccessfulSyncSummary != null
        )
    val captionStatusLabel = when {
        localCaptionModelState.isReady -> "Ready"
        localCaptionModelState.isDownloading -> "Downloading"
        !localCaptionModelState.errorMessage.isNullOrBlank() -> "Needs attention"
        else -> "Model required"
    }
    val captionStatusBody = when {
        localCaptionModelState.isReady -> "${selectedLocalCaptionEngine.displayName} is ready on this device and can generate captions without any external provider."
        localCaptionModelState.isDownloading -> "Downloading the ${selectedLocalCaptionEngine.displayName} model that powers on-device captions for this target."
        !localCaptionModelState.errorMessage.isNullOrBlank() -> localCaptionModelState.errorMessage
            ?: "Offline caption model setup needs attention."
        else -> "Download the ${selectedLocalCaptionEngine.displayName} model once to unlock offline-first caption generation in Player."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp, vertical = 18.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        LookTubePageHeader(
            title = "Connect your Giant Bomb Premium feed",
            subtitle = "Paste the feed URL you copied from Giant Bomb, then sync your library. LookTube uses the copied feed directly and does not automate website sign-in.",
        )

        LookTubeCard(
            title = "Next step",
            body = primaryInstruction,
        )

        LookTubeCard(
            title = "Premium feed status",
            body = statusBody,
            containerColor = statusContainerColor,
            contentColor = statusContentColor,
            statusLabel = statusLabel,
            statusLabelContainerColor = statusContentColor,
            statusLabelContentColor = statusContainerColor,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
            tonalElevation = 1.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Feed access",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                OutlinedTextField(
                    value = feedConfiguration.feedUrl,
                    onValueChange = onFeedUrlChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Premium feed URL") },
                    supportingText = { Text(feedUrlSupportingText) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                )

                Button(
                    onClick = onSignInRequested,
                    enabled = canSignIn,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    if (isSigningIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 12.dp),
                        )
                        Text("Syncing…")
                    } else if (accountSession.isSignedIn) {
                        Text("Re-sync Premium library")
                    } else {
                        Text("Sync Premium feed")
                    }
                }

                if (canClearData) {
                    OutlinedButton(
                        onClick = onClearSyncedDataRequested,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text("Clear synced data")
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
            tonalElevation = 1.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Offline captions",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Selected engine: ${selectedLocalCaptionEngine.displayName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (availableLocalCaptionEngines.size > 1) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        availableLocalCaptionEngines.forEach { engine ->
                            FilterChip(
                                selected = engine.id == selectedLocalCaptionEngine.id,
                                onClick = { onLocalCaptionEngineSelected(engine.id) },
                                label = {
                                    Text(engine.displayName)
                                },
                            )
                        }
                    }
                }
                Text(
                    text = "$captionStatusLabel • $captionStatusBody",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (localCaptionModelState.isDownloading) {
                    LinearProgressIndicator(
                        progress = { localCaptionModelState.downloadProgressFraction ?: 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Button(
                    onClick = onDownloadLocalCaptionModel,
                    enabled = !localCaptionModelState.isReady && !localCaptionModelState.isDownloading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                ) {
                    Text(
                        when {
                            localCaptionModelState.isReady -> "${selectedLocalCaptionEngine.displayName} ready on this device"
                            localCaptionModelState.isDownloading -> "Downloading ${selectedLocalCaptionEngine.displayName} model…"
                            else -> "Download ${selectedLocalCaptionEngine.displayName} model"
                        },
                    )
                }
            }
        }
    }
}
