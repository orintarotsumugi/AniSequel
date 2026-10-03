package com.example.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.outlined.BrightnessAuto
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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
    ThemeMode.LIGHT -> AppVectorIcons.ThemeLight
    ThemeMode.DARK -> AppVectorIcons.ThemeDark
}

@Composable
fun EditSettingsTab(
    authViewModel: AuthViewModel,
    themePreferences: ThemePreferences,
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
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
        SectionCard(
            title = "Appearance",
            icon = AppVectorIcons.SectionAppearance,
            subtitle = "Theme and accent colours."
        ) {
            SettingsFieldLabel(text = "Theme")
            Spacer(modifier = Modifier.height(8.dp))

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

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(4.dp))

            SwitchRow(
                title = "Dynamic colours",
                subtitle = if (supportsDynamicColor) {
                    "Match your wallpaper."
                } else {
                    "Needs Android 12+. This device is on Android ${android.os.Build.VERSION.RELEASE}."
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
                Spacer(modifier = Modifier.height(4.dp))
                SettingsButton(
                    text = "Reset appearance",
                    onClick = { scope.launch { themePreferences.reset() } },
                    icon = AppVectorIcons.Restore,
                    variant = SettingsButtonVariant.Outlined,
                    fillWidth = true,
                    testTag = "reset_appearance_button"
                )
            }
        }

        SectionCard(
            title = "AniList",
            icon = AppVectorIcons.SectionAniList,
            subtitle = "Sign in and API credentials."
        ) {
            ClientIdEditor(
                currentClientId = currentClientId,
                onSave = { authViewModel.updateClientId(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))
            RedirectUrlHint()
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

/**
 * The Client ID field and the two actions that belong to it.
 *
 * Three things were wrong here. `OutlinedTextField(label = ...)` notches the
 * top stroke to make room for the label, which put "Client ID" on the border
 * line and read as a collision; the label is now [SettingsFieldLabel] above the
 * field. "Use default" and "Save" were laid out with `Arrangement.End`, so
 * Save sat alone on the right with an arbitrary width and looked disabled even
 * when it was not - they now share the row evenly. And the second
 * "Manage AniList Developer Clients" button duplicated a link that
 * [RedirectUrlHint] already offers, so it is gone.
 */
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
    val isNotNumeric = trimmed.isNotEmpty() && !trimmed.all { it.isDigit() }

    SettingsFieldLabel(text = "Client ID")
    Spacer(modifier = Modifier.height(8.dp))

    Column {
        OutlinedTextField(
            value = editingClientId,
            onValueChange = {
                editingClientId = it
                hasSavedClientId = false
            },
            modifier = Modifier.fillMaxWidth().testTag("settings_client_id_input"),
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            isError = isNotNumeric,
            supportingText = {
                Text(
                    text = when {
                        isNotNumeric -> "AniList Client IDs are numbers."
                        currentClientId == AuthRepositoryImpl.DEFAULT_CLIENT_ID ->
                            "Using the default AniSequel Client ID."
                        else -> "Custom ID: $currentClientId"
                    }
                )
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Kept mounted even when it has nothing to do, so the row does not
            // reflow from two buttons to one the moment the value changes.
            SettingsButton(
                text = "Use default",
                onClick = { editingClientId = AuthRepositoryImpl.DEFAULT_CLIENT_ID },
                enabled = !isDefault && !isNotNumeric,
                variant = SettingsButtonVariant.Outlined,
                modifier = Modifier.weight(1f),
                testTag = "reset_client_id_button"
            )
            SettingsButton(
                text = if (hasSavedClientId) "Saved" else "Save",
                onClick = {
                    savedValue = trimmed
                    onSave(trimmed)
                    hasSavedClientId = true
                },
                icon = if (hasSavedClientId) Icons.Filled.CheckCircleOutline else null,
                enabled = canSave && !isNotNumeric,
                variant = if (hasSavedClientId) SettingsButtonVariant.Tonal else SettingsButtonVariant.Filled,
                modifier = Modifier.weight(1f),
                testTag = "save_client_id_button"
            )
        }
    }
}