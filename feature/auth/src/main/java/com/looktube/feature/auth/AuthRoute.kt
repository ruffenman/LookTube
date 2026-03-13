package com.looktube.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.looktube.designsystem.LookTubeCard
import com.looktube.model.AccountSession
import com.looktube.model.AuthMode

@Composable
fun AuthRoute(
    paddingValues: PaddingValues,
    accountSession: AccountSession,
    onAuthModeSelected: (AuthMode) -> Unit,
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
    }
}
