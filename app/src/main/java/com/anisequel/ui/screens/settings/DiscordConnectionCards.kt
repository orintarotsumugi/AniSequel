package com.anisequel.ui.screens.settings

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.anisequel.data.discord.DiscordAccount
import com.anisequel.data.discord.DiscordRpcClient
import com.anisequel.data.discord.DiscordRuntimeState
import com.anisequel.domain.discord.DiscordLogin
import com.anisequel.domain.discord.DiscordSettings
import com.anisequel.ui.components.AppVectorIcons
import kotlinx.coroutines.delay
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
                    androidx.compose.material3.Icon(
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
                    text = if (settings.linked && settings.token.isNotBlank()) {
                        "Token saved on this device · ${DiscordLogin.maskedToken(settings.token)}"
                    } else {
                        "Use official web login or paste your Discord token"
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

@Composable
fun DiscordLoginDialog(
    settings: DiscordSettings,
    client: DiscordRpcClient,
    onDismiss: () -> Unit,
    onLinked: (token: String, account: DiscordAccount) -> Unit
) {
    var token by remember { mutableStateOf(settings.token) }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(LoginMode.CHOICE) }
    var webStatus by remember { mutableStateOf("Loading official Discord login…") }
    val scope = rememberCoroutineScope()

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
                when (mode) {
                    LoginMode.CHOICE -> {
                        Text(
                            text = "Choose how you want to connect. Web login opens Discord's official login page inside the app and captures your token locally after you sign in.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        SettingsButton(
                            text = "Official web login",
                            onClick = { mode = LoginMode.WEB },
                            icon = AppVectorIcons.Discord,
                            fillWidth = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsButton(
                            text = "Paste token manually",
                            onClick = { mode = LoginMode.TOKEN },
                            variant = SettingsButtonVariant.Outlined,
                            fillWidth = true
                        )
                    }

                    LoginMode.WEB -> {
                        Text(
                            text = "Sign in on discord.com. Once login completes, AniSequel will detect your token on this device and verify it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = webStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            tonalElevation = 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(360.dp)
                        ) {
                            DiscordWebLoginView(
                                onStatus = { webStatus = it },
                                onToken = { found ->
                                    if (!working) {
                                        token = found
                                        mode = LoginMode.TOKEN
                                        submitToken(found)
                                    }
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { mode = LoginMode.TOKEN }, enabled = !working) {
                            Text("Use token field instead")
                        }
                    }

                    LoginMode.TOKEN -> {
                        Text(
                            text = "Paste your Discord token. It is verified directly with Discord and stored only on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
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
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { mode = LoginMode.WEB }, enabled = !working) {
                            Text("Open official web login")
                        }
                    }
                }

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
                onClick = {
                    when (mode) {
                        LoginMode.CHOICE -> mode = LoginMode.WEB
                        LoginMode.WEB -> mode = LoginMode.TOKEN
                        LoginMode.TOKEN -> submitToken(token)
                    }
                },
                enabled = !working && (mode != LoginMode.TOKEN || token.isNotBlank())
            ) {
                if (working) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verifying…")
                } else {
                    Text(
                        when (mode) {
                            LoginMode.CHOICE -> "Continue"
                            LoginMode.WEB -> "I have the token"
                            LoginMode.TOKEN -> "Verify & connect"
                        }
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !working) { Text("Cancel") }
        }
    )
}

private enum class LoginMode { CHOICE, WEB, TOKEN }

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun DiscordWebLoginView(
    onStatus: (String) -> Unit,
    onToken: (String) -> Unit
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var captured by remember { mutableStateOf(false) }
    val tokenScript = remember {
        """
        (function() {
          try {
            var token = null;
            try { token = window.localStorage.getItem('token'); } catch(e) {}
            if (!token && window.webpackChunkdiscord_app) {
              window.webpackChunkdiscord_app.push([
                [Math.random()],
                {},
                req => {
                  for (const m of Object.keys(req.c).map(x => req.c[x].exports).filter(x => x)) {
                    if (m.default && m.default.getToken !== undefined) { token = m.default.getToken(); }
                    if (m.getToken !== undefined) { token = m.getToken(); }
                  }
                }
              ]);
              window.webpackChunkdiscord_app.pop();
            }
            if (token) return String(token).replace(/^"|"$/g, '');
          } catch(e) {}
          return '';
        })();
        """.trimIndent()
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.userAgentString = settings.userAgentString.replace("; wv", "")
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (!captured) onStatus("Signed in? If not, complete login and AniSequel will detect the token.")
                    }
                }
                loadUrl("https://discord.com/login")
                webViewRef = this
            }
        },
        update = { webViewRef = it }
    )

    LaunchedEffect(webViewRef, captured) {
        val view = webViewRef ?: return@LaunchedEffect
        while (!captured) {
            view.evaluateJavascript(tokenScript) { value ->
                if (captured) return@evaluateJavascript
                val cleaned = value?.trim()?.trim('"')?.takeIf { it != "null" && it.isNotBlank() }
                val normalized = cleaned?.let { DiscordLogin.normalizeToken(it) }
                if (!normalized.isNullOrBlank() && DiscordLogin.looksLikeToken(normalized)) {
                    captured = true
                    onStatus("Token detected. Verifying with Discord…")
                    runCatching { view.stopLoading() }
                    onToken(normalized)
                }
            }
            delay(1200L)
        }
    }
}
