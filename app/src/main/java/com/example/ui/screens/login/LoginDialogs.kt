package com.example.ui.screens.login

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.components.expressive.bouncyPress

@Composable
fun ClientIdDialog(
    currentClientId: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var inputClientId by remember(currentClientId) { mutableStateOf(currentClientId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure AniList Client ID") },
        text = {
            Column {
                Text(
                    text = "AniList OAuth Client ID (active: $currentClientId):",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = inputClientId,
                    onValueChange = { inputClientId = it },
                    label = { Text("Client ID") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("client_id_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (inputClientId.isNotBlank()) onSave(inputClientId.trim())
                    onDismiss()
                },
                modifier = Modifier.bouncyPress(pressedScale = 0.94f)
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.bouncyPress(pressedScale = 0.94f)
            ) { Text("Cancel") }
        }
    )
}

@Composable
fun ManualTokenDialog(
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var inputToken by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter AniList Token") },
        text = {
            Column {
                Text(
                    text = "Paste your Bearer access token copied from AniList:",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = inputToken,
                    onValueChange = { inputToken = it },
                    label = { Text("Access Token") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("token_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (inputToken.isNotBlank()) onSave(inputToken.trim())
                    onDismiss()
                },
                modifier = Modifier.bouncyPress(pressedScale = 0.94f)
            ) { Text("Login") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.bouncyPress(pressedScale = 0.94f)
            ) { Text("Cancel") }
        }
    )
}
