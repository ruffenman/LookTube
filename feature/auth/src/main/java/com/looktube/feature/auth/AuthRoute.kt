package com.looktube.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.looktube.designsystem.LookTubeCard
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
    onSignInRequested: () -> Unit,
    onSignOutRequested: () -> Unit,
) {
    val isSigningIn = syncState.phase == SyncPhase.Refreshing
    val canSignIn = feedConfiguration.feedUrl.isNotBlank() && !isSigningIn
    val colorScheme = MaterialTheme.colorScheme
    val accountStatusLabel = when {
        accountSession.isSignedIn -> "Connected"
        feedConfiguration.feedUrl.isBlank() -> "Setup required"
        syncState.phase == SyncPhase.Error -> "Needs attention"
        else -> "Ready"
    }
    val accountStatusContainerColor = when (accountStatusLabel) {
        "Connected" -> colorScheme.primaryContainer
        "Needs attention" -> colorScheme.errorContainer
        "Ready" -> colorScheme.secondaryContainer
        else -> colorScheme.surfaceVariant
    }
    val accountStatusContentColor = when (accountStatusLabel) {
        "Connected" -> colorScheme.onPrimaryContainer
        "Needs attention" -> colorScheme.onErrorContainer
        "Ready" -> colorScheme.onSecondaryContainer
        else -> colorScheme.onSurfaceVariant
    }
    val libraryStatusLabel = when (syncState.phase) {
        SyncPhase.Idle -> "Idle"
        SyncPhase.Refreshing -> "Syncing"
        SyncPhase.Success -> "Synced"
        SyncPhase.Error -> "Error"
    }
    val libraryStatusContainerColor = when (syncState.phase) {
        SyncPhase.Idle -> colorScheme.surfaceVariant
        SyncPhase.Refreshing -> colorScheme.tertiaryContainer
        SyncPhase.Success -> colorScheme.primaryContainer
        SyncPhase.Error -> colorScheme.errorContainer
    }
    val libraryStatusContentColor = when (syncState.phase) {
        SyncPhase.Idle -> colorScheme.onSurfaceVariant
        SyncPhase.Refreshing -> colorScheme.onTertiaryContainer
        SyncPhase.Success -> colorScheme.onPrimaryContainer
        SyncPhase.Error -> colorScheme.onErrorContainer
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            if (accountSession.isSignedIn) {
                "Signed in to Giant Bomb Premium"
            } else {
                "Sign in to Giant Bomb Premium"
            },
        )

        LookTubeCard(
            title = if (accountSession.isSignedIn) {
                "Account status"
            } else {
                "Sign-in status"
            },
            body = accountSession.notes,
            containerColor = accountStatusContainerColor,
            contentColor = accountStatusContentColor,
            statusLabel = accountStatusLabel,
            statusLabelContainerColor = accountStatusContentColor,
            statusLabelContentColor = accountStatusContainerColor,
        )

        LookTubeCard(
            title = "Library sync status",
            body = syncState.message,
            containerColor = libraryStatusContainerColor,
            contentColor = libraryStatusContentColor,
            statusLabel = libraryStatusLabel,
            statusLabelContainerColor = libraryStatusContentColor,
            statusLabelContentColor = libraryStatusContainerColor,
        )

        LookTubeCard(
            title = "Supported feed input",
            body = "Paste the RSS URL copied from Giant Bomb feeds. Username and password are optional fallback fields for feed variants that still require basic auth.",
        )

        OutlinedTextField(
            value = feedConfiguration.feedUrl,
            onValueChange = onFeedUrlChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Premium feed URL") },
            singleLine = true,
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
            label = { Text("Password (optional, session only)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        LookTubeCard(
            title = "Current supported access path",
            body = "This build syncs the copied Premium feed URL directly. Session-cookie website sign-in is still planned, but not wired yet.",
        )

        Button(
            onClick = onSignInRequested,
            enabled = canSignIn,
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

        if (accountSession.isSignedIn || feedConfiguration.username.isNotBlank() || feedConfiguration.password.isNotBlank()) {
            Button(
                onClick = onSignOutRequested,
                enabled = !isSigningIn,
            ) {
                Text("Sign out")
            }
        }
    }
}
