package com.example.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.network.AniListOAuth
import com.example.data.repository.AuthRepositoryImpl
import com.example.data.repository.ThemeMode
import com.example.data.repository.ThemePreferences
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.RedirectUrlHint
import com.example.ui.components.expressive.ExpressivePolygonSegmentedBar
import com.example.ui.components.expressive.SegmentedOption
import com.example.ui.theme.supportsDynamicColor
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

private fun ThemeMode.icon(): ImageVector = when (this) {
    ThemeMode.SYSTEM -> Icons.Outlined.BrightnessAuto
    ThemeMode.LIGHT -> Icons.Outlined.LightMode
    ThemeMode.DARK -> Icons.Outlined.DarkMode
}

@Composable
fun EditSettingsTab(
    authViewModel: AuthViewModel,
    themePreferences: ThemePreferences,
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authState by authViewModel.uiState.collectAsState()
    val currentClientId by authViewModel.clientId.collectAsState()
    val themeSettings by themePreferences.settings.collectAsState(
        initial = ThemePreferences.ThemeSettings(
            themeMode = ThemeMode.DEFAULT,
            useDynamicColor = false
        )
    )

    SettingsScrollColumn(modifier) {
        SectionCard(title = "Appearance & Theme") {
            Text(
                text = "Choose your preferred interface theme or match device settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            ExpressivePolygonSegmentedBar(
                options = ThemeMode.entries.map { mode ->
                    SegmentedOption(label = mode.displayName, icon = mode.icon())
                },
                selectedIndex = ThemeMode.entries.indexOf(themeSettings.themeMode),
                onSelect = { index ->
                    scope.launch { themePreferences.setThemeMode(ThemeMode.entries[index]) }
                },
                modifier = Modifier.testTag("theme_mode_row")
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            SwitchRow(
                title = "Material You Dynamic Colors",
                subtitle = if (supportsDynamicColor) {
                    "Derives color accents dynamically from your Android wallpaper palette."
                } else {
                    "Requires Android 12 or newer. Device is on Android ${android.os.Build.VERSION.RELEASE}."
                },
                checked = themeSettings.useDynamicColor,
                enabled = supportsDynamicColor,
                onCheckedChange = { enabled ->
                    scope.launch { themePreferences.setUseDynamicColor(enabled) }
                },
                modifier = Modifier.testTag("dynamic_color_switch")
            )

            val appearanceIsCustom = themeSettings.themeMode != ThemeMode.DEFAULT ||
                themeSettings.useDynamicColor

            if (appearanceIsCustom) {
                Spacer(modifier = Modifier.height(6.dp))
                SettingsButton(
                    text = "Reset to default appearance",
                    onClick = { scope.launch { themePreferences.reset() } },
                    variant = SettingsButtonVariant.Text,
                    fillWidth = true,
                    testTag = "reset_appearance_button"
                )
            }
        }

        SectionCard(title = "AniList API Configuration") {
            Text(
                text = "AniSequel uses standard OAuth Client ID to authenticate against AniList. You can configure a custom developer Client ID if self-hosting.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            ClientIdEditor(
                currentClientId = currentClientId,
                onSave = { authViewModel.updateClientId(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))
            RedirectUrlHint()

            Spacer(modifier = Modifier.height(8.dp))
            SettingsButton(
                text = "Manage AniList Developer Clients",
                onClick = { openExternalUrl(context, AniListOAuth.DEVELOPER_SETTINGS_URL) },
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                variant = SettingsButtonVariant.Text,
                fillWidth = true
            )
        }

        SettingsButton(
            text = if (authState is AuthUiState.Authenticated) "Sign out" else "Back to sign in",
            onClick = {
                if (authState is AuthUiState.Authenticated) {
                    onNavigateBack()
                } else {
                    onNavigateToLogin()
                }
            },
            icon = if (authState is AuthUiState.Authenticated) AppVectorIcons.Logout else AppVectorIcons.Login,
            variant = SettingsButtonVariant.Outlined,
            fillWidth = true,
            testTag = "logout_button"
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ClientIdEditor(
    currentClientId: String,
    onSave: (String) -> Unit
) {
    var editingClientId by rememberSaveable { mutableStateOf(currentClientId) }
    var hasSavedClientId by rememberSaveable { mutableStateOf(false) }
    var savedValue by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(currentClientId) {
        if (currentClientId != savedValue) {
            editingClientId = currentClientId
            hasSavedClientId = false
        }
        savedValue = null
    }

    val trimmed = editingClientId.trim()
    val isDefault = trimmed == AuthRepositoryImpl.DEFAULT_CLIENT_ID
    val canSave = trimmed.isNotBlank() && trimmed != currentClientId

    OutlinedTextField(
        value = editingClientId,
        onValueChange = {
            editingClientId = it
            hasSavedClientId = false
        },
        label = { Text("Client ID") },
        supportingText = {
            Text(
                if (currentClientId == AuthRepositoryImpl.DEFAULT_CLIENT_ID) {
                    "Using the default AniSequel Client ID"
                } else {
                    "Custom ID: $currentClientId"
                }
            )
        },
        modifier = Modifier.fillMaxWidth().testTag("settings_client_id_input"),
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        isError = trimmed.isNotEmpty() && !trimmed.all { it.isDigit() }
    )

    Spacer(modifier = Modifier.height(8.dp))
    Row(
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (!isDefault) {
            SettingsButton(
                text = "Use default",
                onClick = { editingClientId = AuthRepositoryImpl.DEFAULT_CLIENT_ID },
                variant = SettingsButtonVariant.Text,
                testTag = "reset_client_id_button"
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        SettingsButton(
            text = if (hasSavedClientId) "Saved" else "Save",
            onClick = {
                savedValue = trimmed
                onSave(trimmed)
                hasSavedClientId = true
            },
            icon = if (hasSavedClientId) Icons.Filled.CheckCircleOutline else null,
            enabled = canSave,
            variant = if (hasSavedClientId) SettingsButtonVariant.Tonal else SettingsButtonVariant.Filled,
            testTag = "save_client_id_button"
        )
    }
}
