package com.looktube.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.looktube.designsystem.LookTubeCard
import com.looktube.model.AccountSession
import com.looktube.model.AuthMode
import com.looktube.model.FeedConfiguration
import com.looktube.model.LibrarySyncState

@Composable
fun AuthRoute(
    paddingValues: PaddingValues,
    accountSession: AccountSession,
    feedConfiguration: FeedConfiguration,
    syncState: LibrarySyncState,
    onAuthModeSelected: (AuthMode) -> Unit,
    onFeedUrlChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onRefreshRequested: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Choose the sign-in strategy to validate first.")

        LookTubeCard(
            title = "Current auth spike status",
            body = accountSession.notes,
        )

        LookTubeCard(
            title = "Current library sync status",
            body = syncState.message,
        )

        Button(
            onClick = { onAuthModeSelected(AuthMode.CredentialedFeed) },
        ) {
            Text("Validate credentialed feed mode")
        }

        Button(
            onClick = { onAuthModeSelected(AuthMode.SessionCookie) },
        ) {
            Text("Validate session-cookie mode")
        }

        OutlinedTextField(
            value = feedConfiguration.feedUrl,
            onValueChange = onFeedUrlChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Feed URL") },
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

        Button(
            onClick = onRefreshRequested,
        ) {
            Text("Sync configured feed")
        }
    }
}
