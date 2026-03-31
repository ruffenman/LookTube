package com.looktube.feature.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.looktube.designsystem.LookTubeCard
import com.looktube.designsystem.LookTubePageHeader
import com.looktube.model.AccountSession
import com.looktube.model.FeedConfiguration
import com.looktube.model.LibrarySyncState
import com.looktube.model.LocalCaptionEngine
import com.looktube.model.LocalCaptionModelState
import com.looktube.model.SyncPhase
data class CaptionDataManagementItem(
    val videoId: String,
    val title: String,
    val stateLabel: String,
    val supportingText: String,
)

@Composable
fun AuthRoute(
    paddingValues: PaddingValues,
    accountSession: AccountSession,
    feedConfiguration: FeedConfiguration,
    syncState: LibrarySyncState,
    availableLocalCaptionEngines: List<LocalCaptionEngine>,
    selectedLocalCaptionEngine: LocalCaptionEngine,
    localCaptionModelState: LocalCaptionModelState,
    captionDataItems: List<CaptionDataManagementItem>,
    onFeedUrlChanged: (String) -> Unit,
    onAutoGenerateCaptionsForNewVideosChanged: (Boolean) -> Unit,
    onSignInRequested: () -> Unit,
    onLocalCaptionEngineSelected: (String) -> Unit,
    onDownloadLocalCaptionModel: () -> Unit,
    onOpenCaptionDataVideoRequested: (String) -> Unit,
    onClearSyncedDataRequested: () -> Unit,
    onClearCaptionDataRequested: () -> Unit,
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
    var captionDataMenuExpanded by remember { mutableStateOf(false) }

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
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
                    tonalElevation = 0.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.78f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "Auto-generate captions for new videos",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = if (localCaptionModelState.isReady) {
                                    "When new feed videos are discovered, LookTube will generate offline captions automatically during sync or background refresh, including while the app is backgrounded or the device is locked."
                                } else {
                                    "Enable this now if you want future new videos to be captioned automatically once the offline caption model is ready on this device."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = feedConfiguration.autoGenerateCaptionsForNewVideos,
                            onCheckedChange = onAutoGenerateCaptionsForNewVideosChanged,
                        )
                    }
                }
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
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.54f),
                )
                CaptionDataManagementPanel(
                    captionDataItems = captionDataItems,
                    menuExpanded = captionDataMenuExpanded,
                    onMenuExpandedChanged = { captionDataMenuExpanded = it },
                    onOpenCaptionDataVideoRequested = onOpenCaptionDataVideoRequested,
                    onClearCaptionDataRequested = onClearCaptionDataRequested,
                )
            }
        }
    }
}

@Composable
private fun CaptionDataManagementPanel(
    captionDataItems: List<CaptionDataManagementItem>,
    menuExpanded: Boolean,
    onMenuExpandedChanged: (Boolean) -> Unit,
    onOpenCaptionDataVideoRequested: (String) -> Unit,
    onClearCaptionDataRequested: () -> Unit,
) {
    val hasCaptionData = captionDataItems.isNotEmpty()
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Caption data",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = if (hasCaptionData) {
                "${captionDataItems.size} ${if (captionDataItems.size == 1) "video has" else "videos have"} saved or partial caption-generation data. Open one in Player without autoplay to inspect or delete it there."
            } else {
                "No caption-generation data is saved on this device yet. Generate captions from Player or enable automatic generation for newly discovered videos."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = hasCaptionData) { onMenuExpandedChanged(true) },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                tonalElevation = 0.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.78f)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = if (hasCaptionData) "Inspect caption data in Player" else "No caption data available",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (hasCaptionData) {
                                "Completed and partial entries are listed here."
                            } else {
                                "This list will fill in after captions start or finish generating."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = if (menuExpanded && hasCaptionData) "▲" else "▼",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            DropdownMenu(
                expanded = menuExpanded && hasCaptionData,
                onDismissRequest = { onMenuExpandedChanged(false) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
            ) {
                captionDataItems.forEachIndexed { index, item ->
                    DropdownMenuItem(
                        text = {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = item.title,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${item.stateLabel} • ${item.supportingText}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        onClick = {
                            onMenuExpandedChanged(false)
                            onOpenCaptionDataVideoRequested(item.videoId)
                        },
                    )
                    if (index != captionDataItems.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
                        )
                    }
                }
            }
        }
        OutlinedButton(
            onClick = onClearCaptionDataRequested,
            enabled = hasCaptionData,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text("Clear all caption data")
        }
    }
}
