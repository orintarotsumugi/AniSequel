package com.anisequel.ui.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.discord.DiscordAccount
import com.anisequel.data.discord.DiscordOAuthFlow
import com.anisequel.data.discord.DiscordRpcClient
import com.anisequel.data.discord.DiscordRuntimeState
import com.anisequel.domain.discord.DiscordLogin
import com.anisequel.domain.discord.DiscordOAuth
import com.anisequel.domain.discord.DiscordSettings
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.openExternalUrlOrCopy
import kotlinx.coroutines.launch

@Composable
fun DiscordConnectionCard(
    settings: DiscordSettings,
    runtime: DiscordRuntimeState,
    onLogin: () -> Unit,
    onLogout: () -> Unit
) {
    SectionCard(
        title = "Connection",
        icon = AppVectorIcons.SignalOn,
        subtitle = "Direct Discord login for Rich Presence"
    ) {
        AnimatedContent(
            targetState = runtime,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "discord_connection_state"
        ) { state ->
            Text(
                text = when (state) {
                    DiscordRuntimeState.Disabled -> "Rich Presence is off."
                    DiscordRuntimeState.PreviewOnly -> "Preview only. Log in to Discord to go live."
                    DiscordRuntimeState.Connecting -> "Connecting to Discord…"
                    is DiscordRuntimeState.Live -> "Live on Discord as ${state.account}."
                    is DiscordRuntimeState.Failed -> state.message
                },
                style = MaterialTheme.typography.bodyMedium,
                color = when (state) {
                    is DiscordRuntimeState.Failed -> MaterialTheme.colorScheme.error
                    is DiscordRuntimeState.Live -> MaterialTheme.colorScheme.primary
                    DiscordRuntimeState.Connecting -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }

        if (runtime is DiscordRuntimeState.Connecting) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (settings.avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = settings.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF5865F2), Color(0xFF3A44C9))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppVectorIcons.Discord,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = settings.username.ifBlank { "No account linked" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = when {
                        settings.linked && settings.token.isNotBlank() ->
                            "Token saved on this device · ${DiscordLogin.maskedToken(settings.token)}"
                        settings.canSignInWithDiscord ->
                            "Sign in with Discord - no token to copy"
                        else ->
                            "Sign in with Discord in your browser, then paste your token"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "AniSequel connects directly to Discord from this device using your account token. " +
                "No relay server and no local build tools are required. Your token stays on this device and is only sent to Discord.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsButton(
                text = if (settings.linked) "Change account" else "Log in to Discord",
                onClick = onLogin,
                icon = AppVectorIcons.Discord,
                modifier = Modifier.weight(1f),
                testTag = "discord_connection_login"
            )
            if (settings.linked) {
                SettingsButton(
                    text = "Log out",
                    onClick = onLogout,
                    variant = SettingsButtonVariant.Outlined,
                    modifier = Modifier.weight(1f),
                    testTag = "discord_connection_logout"
                )
            }
        }
    }
}

/**
 * Links a Discord account without an embedded browser.
 *
 * ## Why the web view is gone
 *
 * This dialog used to host a `WebView` pointed at `discord.com/login` and poll it
 * with injected JavaScript until a token turned up in the page's storage. That is
 * a full Chromium renderer, a login page rendered inside an app that the reader
 * cannot inspect an address bar of, and a script reaching into another company's
 * session - and every one of those was a way for this dialog to break without a
 * line of this app changing. It also had to wipe `WebStorage` for the whole app
 * profile on dismissal, because a renderer left behind is a leak.
 *
 * The sign-in page now opens in the device's own browser, where the reader can see
 * the host, use the password manager, and close the tab. Discord gives a browser no
 * way to hand a token back to an app, so the token field below is how the account
 * is actually linked. That is a smaller flow, not a prettier one: it asks the reader
 * for the credential instead of pretending an in-app page captured it silently.
 *
 * ## What sits above it now
 *
 * An OAuth2 authorization-code flow with PKCE, which is the one way Discord *does*
 * hand a token back: the code comes in on `anisequel://discord`, and this device
 * exchanges it. [DiscordSignInPanel] draws it, and the exchange itself is handled
 * by the page rather than by this dialog - the dialog can be dismissed while the
 * reader is in the browser, and the callback has to survive that.
 */
@Composable
fun DiscordLoginDialog(
    settings: DiscordSettings,
    client: DiscordRpcClient,
    onDismiss: () -> Unit,
    onLinked: (token: String, account: DiscordAccount) -> Unit,
    onSaveClientId: (String) -> Unit = {},
    oauthError: String? = null,
    oauthBusy: Boolean = false
) {
    var token by remember { mutableStateOf(settings.token) }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var clientId by remember { mutableStateOf(settings.oauthClientId) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Read from the flow object rather than a local flag: the session outlives this
    // composition, and a `remember`ed boolean would reset to "not waiting" as soon
    // as the reader came back from the browser on a rebuilt Activity.
    val pending by DiscordOAuthFlow.pending.collectAsState()

    fun startOAuth() {
        if (!DiscordOAuth.isValidClientId(clientId)) {
            error = "Enter the Discord application ID first."
            return
        }
        error = null
        onSaveClientId(clientId.trim())
        val session = DiscordOAuthFlow.begin(clientId)
        openExternalUrlOrCopy(
            context,
            DiscordOAuth.authorizationUrl(
                clientId = session.clientId,
                codeChallenge = session.challenge,
                state = session.state
            )
        )
    }

    fun submitToken(candidate: String) {
        val normalized = DiscordLogin.normalizeToken(candidate)
        if (!DiscordLogin.looksLikeToken(normalized)) {
            error = "Paste a valid Discord token."
            return
        }
        working = true
        error = null
        scope.launch {
            client.verify(normalized).fold(
                onSuccess = { account ->
                    working = false
                    onLinked(normalized, account)
                },
                onFailure = { failure ->
                    working = false
                    error = failure.message ?: "Discord could not verify that token."
                }
            )
        }
    }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(if (settings.linked) "Change Discord account" else "Log in to Discord") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                DiscordSignInPanel(
                    clientId = clientId,
                    error = oauthError ?: error,
                    waiting = pending != null,
                    busy = oauthBusy,
                    onClientIdChange = {
                        clientId = it.filter(Char::isDigit).take(22)
                        error = null
                    },
                    onSignIn = { startOAuth() }
                )
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "No application ID? Sign in on Discord's own page, which opens " +
                        "in your device's browser - not inside AniSequel - and paste the " +
                        "token it gives you. It is verified with Discord directly and " +
                        "stored only on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingsButton(
                    text = "Open Discord in your browser",
                    onClick = {
                        openExternalUrlOrCopy(context, DiscordLogin.BROWSER_LOGIN_URL)
                    },
                    icon = AppVectorIcons.Discord,
                    variant = SettingsButtonVariant.Outlined,
                    fillWidth = true,
                    testTag = "discord_browser_login"
                )
                Spacer(modifier = Modifier.height(14.dp))
                SettingsFieldLabel("Discord token")
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = {
                        token = it
                        error = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("discord_token_field"),
                    placeholder = { Text("Paste your token") },
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )

                AnimatedVisibility(visible = error != null) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { submitToken(token) },
                enabled = !working && token.isNotBlank()
            ) {
                if (working) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verifying…")
                } else {
                    Text("Verify & connect")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !working) { Text("Cancel") }
        }
    )
}
