package com.looktube.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
    val canSignIn = feedConfiguration.feedUrl.isNotBlank() &&
        feedConfiguration.username.isNotBlank() &&
        feedConfiguration.password.isNotBlank() &&
        !isSigningIn
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
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
        )

        LookTubeCard(
            title = "Library sync status",
            body = syncState.message,
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
            label = { Text("Premium username") },
            singleLine = true,
        )

        OutlinedTextField(
            value = feedConfiguration.password,
            onValueChange = onPasswordChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password (session only)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        LookTubeCard(
            title = "Current supported login path",
            body = "This build signs in through the credentialed Premium feed path. Session-cookie website sign-in is still planned, but not wired yet.",
        )

        Button(
            onClick = onSignInRequested,
            enabled = canSignIn,
        ) {
            if (isSigningIn) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 12.dp),
                )
                Text("Signing in…")
            } else if (accountSession.isSignedIn) {
                Text("Re-sync Premium library")
            } else {
                Text("Sign in and sync")
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
