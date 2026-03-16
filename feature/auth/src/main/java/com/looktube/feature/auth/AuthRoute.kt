package com.looktube.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.looktube.designsystem.LookTubeCard
import com.looktube.designsystem.LookTubePageHeader
import com.looktube.model.AccountSession
import com.looktube.model.FeedConfiguration
import com.looktube.model.LibrarySyncState
import com.looktube.model.SyncPhase

@Composable
fun AuthRoute(
    paddingValues: PaddingValues,
    accountSession: AccountSession,
    feedConfiguration: FeedConfiguration,
    syncState: LibrarySyncState,
    onFeedUrlChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onRememberPasswordChanged: (Boolean) -> Unit,
    onSignInRequested: () -> Unit,
    onClearSyncedDataRequested: () -> Unit,
    onForgetSavedCredentialsRequested: () -> Unit,
) {
    val isSigningIn = syncState.phase == SyncPhase.Refreshing
    val canSignIn = feedConfiguration.feedUrl.isNotBlank() && !isSigningIn
    var optionalCredentialsExpanded by rememberSaveable {
        mutableStateOf(
            feedConfiguration.username.isNotBlank() ||
                feedConfiguration.password.isNotBlank() ||
                feedConfiguration.rememberPassword,
        )
    }
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
    val statusBody = buildString {
        append(syncState.message)
        syncState.lastSuccessfulSyncSummary?.let { summary ->
            append("\n\n")
            append(summary)
        }
        if (feedConfiguration.password.isNotBlank()) {
            append("\n\n")
            append(
                if (feedConfiguration.rememberPassword) {
                    "The optional password is saved securely on this device."
                } else {
                    "The optional password is stored only for this app session."
                },
            )
        }
        if (accountSession.isSignedIn || syncState.phase == SyncPhase.Success) {
            append("\n\nClear synced data removes the cached library and saved progress but keeps your feed settings ready for the next sync.")
        }
        if (
            feedConfiguration.username.isNotBlank() ||
            feedConfiguration.rememberPassword ||
            feedConfiguration.password.isNotBlank()
        ) {
            append("\n\nForget saved credentials removes the saved username and any remembered password but keeps the feed URL.")
        }
    }
    val canClearData = !isSigningIn && (
        accountSession.isSignedIn ||
            syncState.lastSuccessfulSyncSummary != null
        )
    val canForgetSavedCredentials = !isSigningIn && (
        feedConfiguration.username.isNotBlank() ||
            feedConfiguration.rememberPassword ||
            feedConfiguration.password.isNotBlank()
        )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LookTubePageHeader(
            title = "Connect your Giant Bomb Premium feed",
            subtitle = "Paste the feed URL you copied from Giant Bomb. Advanced credentials are only for direct-feed fallback if that URL still fails.",
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

        OutlinedTextField(
            value = feedConfiguration.feedUrl,
            onValueChange = onFeedUrlChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Premium feed URL") },
            singleLine = true,
        )

        FilterChip(
            selected = optionalCredentialsExpanded,
            onClick = { optionalCredentialsExpanded = !optionalCredentialsExpanded },
            label = {
                Text(
                    if (optionalCredentialsExpanded) {
                        "Hide advanced feed fallback"
                    } else {
                        "Advanced feed fallback"
                    },
                )
            },
        )

        if (optionalCredentialsExpanded) {
            LookTubeCard(
                title = "Advanced direct-feed fallback",
                body = "Most copied Giant Bomb feed URLs should work on their own. Only fill these fields if the feed itself still requires HTTP Basic auth. LookTube does not sign into the Giant Bomb website or automate a browser session.",
            )

            OutlinedTextField(
                value = feedConfiguration.username,
                onValueChange = onUsernameChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Premium username (optional)") },
                singleLine = true,
            )

            OutlinedTextField(
                value = feedConfiguration.password,
                onValueChange = onPasswordChanged,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        if (feedConfiguration.rememberPassword) {
                            "Password (optional, remembered)"
                        } else {
                            "Password (optional)"
                        },
                    )
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = feedConfiguration.rememberPassword,
                        role = Role.Checkbox,
                        onValueChange = onRememberPasswordChanged,
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Checkbox(
                    checked = feedConfiguration.rememberPassword,
                    onCheckedChange = null,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Remember password on this device")
                    Text(
                        text = "Uses encrypted-at-rest app storage and stays limited to the direct-feed fallback path.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onSignInRequested,
                enabled = canSignIn,
                shape = RoundedCornerShape(14.dp),
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
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("Clear synced data")
                }
            }
        }

        if (canForgetSavedCredentials) {
            OutlinedButton(
                onClick = onForgetSavedCredentialsRequested,
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Forget saved credentials")
            }
        }
    }
}
