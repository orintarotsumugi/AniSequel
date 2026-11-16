package com.anisequel.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.anisequel.domain.discord.DiscordOAuth
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.openExternalUrlOrCopy

/**
 * The one-tap Discord sign-in.
 *
 * ## What this replaces
 *
 * The page used to ask the reader to open `discord.com/login` in their browser,
 * find their own token, and paste it back. Discord gives a browser no way to hand
 * a token to an app, so that was the only option without an OAuth application -
 * and it is why the account card could sit at "Preview only" while looking like a
 * login that had simply failed.
 *
 * With an application ID this is a single tap: Discord's page opens, the reader
 * approves, Discord redirects to `anisequel://discord`, and the app exchanges the
 * code for a token with a verifier that never left the device. The paste path
 * stays below as the fallback for an install with no ID configured, because
 * removing it would leave those readers with no way to link anything.
 *
 * ## Why the ID is a field
 *
 * A Discord OAuth flow needs an application whose registered redirect is
 * [DiscordOAuth.REDIRECT_URI]. That is not something an app can invent, and this
 * one ships without: the same shape as AniList's client ID, which the reader
 * supplies on the sign-in page. The field remembers what it is given, and the
 * sign-in button stays disabled until it looks like a real snowflake, so a typo
 * fails with a sentence here instead of Discord's "Invalid OAuth2 URL" page.
 */
@Composable
internal fun DiscordSignInPanel(
    clientId: String,
    error: String?,
    waiting: Boolean,
    busy: Boolean,
    onClientIdChange: (String) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val valid = DiscordOAuth.isValidClientId(clientId)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Sign in on Discord's own page. AniSequel gets an access token back " +
                "and links the account itself - there is no token to copy.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        SettingsFieldLabel("Discord application ID")
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = clientId,
            onValueChange = onClientIdChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("discord_oauth_client_id"),
            placeholder = { Text("123456789012345678") },
            singleLine = true,
            isError = clientId.isNotBlank() && !valid,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            supportingText = {
                Text(
                    text = if (clientId.isBlank() || valid) {
                        "The application whose redirect URI is ${DiscordOAuth.REDIRECT_URI}."
                    } else {
                        "A Discord application ID is digits only."
                    }
                )
            }
        )
        Spacer(modifier = Modifier.height(2.dp))
        SettingsButton(
            text = "Where do I get an ID?",
            onClick = { openExternalUrlOrCopy(context, DiscordOAuth.DEVELOPER_PORTAL_URL) },
            variant = SettingsButtonVariant.Text,
            testTag = "discord_developer_portal"
        )
        Spacer(modifier = Modifier.height(8.dp))
        SettingsButton(
            text = when {
                busy -> "Finishing sign-in…"
                waiting -> "Waiting for Discord…"
                else -> "Sign in with Discord"
            },
            onClick = onSignIn,
            icon = AppVectorIcons.Discord,
            enabled = valid && !waiting && !busy,
            fillWidth = true,
            testTag = "discord_oauth_sign_in"
        )
        if (waiting || busy) {
            Spacer(modifier = Modifier.height(8.dp))
            CircularProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth(0.08f)
                    .testTag("discord_oauth_progress"),
                strokeWidth = 2.dp
            )
        }
        AnimatedVisibility(visible = error != null) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("discord_oauth_error")
                )
            }
        }
    }
}
