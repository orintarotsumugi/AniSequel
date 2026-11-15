package com.anisequel.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.anisequel.data.discord.DiscordRpcClient
import com.anisequel.data.discord.DiscordRuntimeState
import com.anisequel.domain.discord.DiscordRelay
import com.anisequel.domain.discord.DiscordSettings
import com.anisequel.ui.components.AppVectorIcons
import kotlinx.coroutines.launch

/**
 * Where the presence actually goes, and the one limitation the app cannot hide.
 *
 * Kept beside the link dialog rather than in the phrasing/jokes card file: the
 * card and the dialog are one setup flow, and the two cards that talk about copy
 * and rotation do not need the transport's imports.
 */
@Composable
fun DiscordConnectionCard(
    settings: DiscordSettings,
    runtime: DiscordRuntimeState,
    onLink: () -> Unit,
    onUnlink: () -> Unit
) {
    SectionCard(
        title = "Connection",
        icon = AppVectorIcons.SignalOn,
        subtitle = "The relay that carries your presence to Discord"
    ) {
        Text(
            text = when (runtime) {
                DiscordRuntimeState.Disabled -> "Rich Presence is off."
                DiscordRuntimeState.PreviewOnly ->
                    "Composed on this phone. Link a relay to send it."
                DiscordRuntimeState.Publishing -> "Sending your presence…"
                is DiscordRuntimeState.Live -> "Your presence is live."
                is DiscordRuntimeState.Failed -> runtime.message
            },
            style = MaterialTheme.typography.bodyMedium,
            color = when (runtime) {
                is DiscordRuntimeState.Failed -> MaterialTheme.colorScheme.error
                is DiscordRuntimeState.Live -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Relay",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = settings.relayUrl.ifBlank { "Not linked yet" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Application ID",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = settings.applicationId.ifBlank { "Not set" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Android has no Discord Rich Presence API a Compose app can call " +
                "directly: Discord's own Android support ships as a native Social SDK. " +
                "So the app sends only the card above to a small https relay you run, " +
                "and the relay speaks to Discord. No Discord token is stored here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsButton(
                text = if (settings.linked) "Change relay" else "Link relay",
                onClick = onLink,
                icon = AppVectorIcons.Discord,
                modifier = Modifier.weight(1f),
                testTag = "discord_connection_link"
            )
            if (settings.linked) {
                SettingsButton(
                    text = "Unlink",
                    onClick = onUnlink,
                    variant = SettingsButtonVariant.Outlined,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Link the reader's relay, and prove it answers before believing it.
 *
 * The app id is checked locally first because a malformed one is the most common
 * mistake and does not need a round trip. The probe then settles the URL, which is
 * the only way to know "Connected" is true rather than typed.
 */
@Composable
fun DiscordLinkDialog(
    settings: DiscordSettings,
    client: DiscordRpcClient,
    onDismiss: () -> Unit,
    onLinked: (relay: String, applicationId: String, user: String) -> Unit
) {
    var relay by remember { mutableStateOf(settings.relayUrl) }
    var appId by remember { mutableStateOf(settings.applicationId) }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text("Link your Discord relay") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Use the https address of the relay you run, and the " +
                        "Application ID from your Discord developer app. The check only " +
                        "asks the relay for its health; nothing is published yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingsFieldLabel("Relay URL")
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = relay,
                    onValueChange = {
                        relay = it
                        error = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("discord_relay_field"),
                    placeholder = { Text("https://relay.example") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                Spacer(modifier = Modifier.height(10.dp))
                SettingsFieldLabel("Discord Application ID")
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = appId,
                    onValueChange = {
                        appId = it.filter { character -> character.isDigit() }.take(20)
                        error = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("discord_app_id_field"),
                    placeholder = { Text("123456789012345678") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
                error?.let { message ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val normalized = DiscordRelay.normalizeBaseUrl(relay)
                    if (normalized == null) {
                        error = "Use a full https URL, with no username or password in it."
                        return@TextButton
                    }
                    if (!DiscordRelay.isValidApplicationId(appId)) {
                        error = "A Discord Application ID is 17-20 digits."
                        return@TextButton
                    }
                    working = true
                    error = null
                    scope.launch {
                        client.probe(normalized).fold(
                            onSuccess = { user ->
                                working = false
                                onLinked(normalized, appId, user)
                            },
                            onFailure = { failure ->
                                working = false
                                error = failure.message ?: "The relay did not answer."
                            }
                        )
                    }
                },
                enabled = !working && relay.isNotBlank() && appId.isNotBlank()
            ) {
                if (working) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Checking…")
                } else {
                    Text("Test & link")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !working) { Text("Cancel") }
        }
    )
}
