package com.example.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.repository.AuthRepositoryImpl
import com.example.data.repository.ArrivingPreferences
import com.example.data.repository.RefreshInterval
import com.example.data.repository.RefreshIntervalPreferences
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.RedirectUrlHint
import com.example.ui.components.expressive.ExpressiveShapes
import com.example.ui.components.expressive.bouncyPress
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

@Composable
fun EditSettingsTab(
    authViewModel: AuthViewModel,
    refreshIntervalPreferences: RefreshIntervalPreferences,
    arrivingPreferences: ArrivingPreferences,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val authState by authViewModel.uiState.collectAsState()
    val currentClientId by authViewModel.clientId.collectAsState()
    SettingsScrollColumn(modifier) {
        SectionCard(
            title = "AniList",
            icon = AppVectorIcons.SectionAniList,
            subtitle = "Your AniList Client ID."
        ) {
            ClientIdEditor(
                currentClientId = currentClientId,
                onSave = { authViewModel.updateClientId(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))
            RedirectUrlHint()
        }

        ListRefreshCard(refreshIntervalPreferences)

        ArrivingSectionCard(arrivingPreferences)

        SettingsButton(
            text = if (authState is AuthUiState.Authenticated) "Sign out" else "Sign in",
            onClick = {
                if (authState is AuthUiState.Authenticated) {
                    // Clears the token, the detail cache and the viewer
                    // session. This button used to only navigate back, which
                    // means "Sign out" signed the user out of nothing - they
                    // stayed authenticated and only got dropped one screen.
                    authViewModel.logout()
                } else {
                    onNavigateToLogin()
                }
            },
            icon = if (authState is AuthUiState.Authenticated) AppVectorIcons.Logout else AppVectorIcons.Login,
            variant = SettingsButtonVariant.Outlined,
            fillWidth = true,
            testTag = "edit_logout_button"
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * How often the saved list is re-read from AniList.
 *
 * The setting exists because of a bug, not a preference. Nothing used to watch
 * for the app returning to the foreground, and the list was cached for a flat
 * hour, so closing and reopening AniSequel - which Android does by resuming the
 * process rather than restarting it - showed the identical dashboard every time.
 * Finishing something on AniList in another app changed nothing until an hour
 * had passed or the refresh button was tapped.
 *
 * ## Why chips and not `ExpressivePolygonSegmentedBar`
 *
 * The segmented bar is how the Theme tab picks one of two or three values. There
 * are five here, and every one of them is short but not equally short - "Always",
 * "15 min", "30 min", "1 hour", "Manual" - squeezed into equal halves of a
 * phone-width row. A wrapping [FlowRow] of pills is the same interaction with a
 * layout that cannot clip a label, which is what `PaletteSwatch` does for the
 * same reason.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ListRefreshCard(refreshIntervalPreferences: RefreshIntervalPreferences) {
    val scope = rememberCoroutineScope()
    val interval by refreshIntervalPreferences.interval.collectAsState(
        initial = RefreshInterval.DEFAULT
    )

    SectionCard(
        title = "List refresh",
        icon = AppVectorIcons.Schedule,
        subtitle = "How fresh your scan stays."
    ) {
        Text(
            text = "AniSequel re-reads your list when you come back to the app if it is " +
                    "older than this, and on its own while it stays open. Pull down or " +
                    "use the refresh button any time to check immediately.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(14.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.testTag("refresh_interval_row")
        ) {
            RefreshInterval.entries.forEach { option ->
                RefreshIntervalChip(
                    interval = option,
                    selected = option == interval,
                    onClick = { scope.launch { refreshIntervalPreferences.setInterval(option) } }
                )
            }
        }

        if (interval != RefreshInterval.DEFAULT) {
            Spacer(modifier = Modifier.height(16.dp))
            SettingsButton(
                text = "Use AniSequel's default",
                onClick = { scope.launch { refreshIntervalPreferences.reset() } },
                icon = AppVectorIcons.Restore,
                variant = SettingsButtonVariant.Text,
                fillWidth = true,
                testTag = "reset_refresh_interval_button"
            )
        }
    }
}

/**
 * How the dashboard's airing and upcoming rows behave.
 *
 * Two switches because they answer different questions: hiding the section
 * removes the rows above search entirely (and with them every poster load on
 * the path to a list), while compact keeps the information in plain rows that
 * fetch no artwork - the entire cost of those cards is the image. Compact is
 * meaningless on a hidden section, so it disables along with it.
 */
@Composable
private fun ArrivingSectionCard(arrivingPreferences: ArrivingPreferences) {
    val scope = rememberCoroutineScope()
    val settings by arrivingPreferences.settings.collectAsState(
        initial = ArrivingPreferences.ArrivingSettings()
    )

    SectionCard(
        title = "Dashboard",
        icon = AppVectorIcons.Tv,
        subtitle = "The Currently arriving rows."
    ) {
        SwitchRow(
            title = "Currently arriving",
            subtitle = "Show airing and upcoming rows above search.",
            checked = settings.showArrivingSection,
            onCheckedChange = { enabled ->
                scope.launch { arrivingPreferences.setShowArrivingSection(enabled) }
            },
            modifier = Modifier.testTag("arriving_section_switch")
        )
        SwitchRow(
            title = "Compact arriving rows",
            subtitle = "Plain rows that load no artwork - less data, less battery.",
            checked = settings.compactArrivingCards,
            enabled = settings.showArrivingSection,
            onCheckedChange = { compact ->
                scope.launch { arrivingPreferences.setCompactArrivingCards(compact) }
            },
            modifier = Modifier.testTag("compact_arriving_switch")
        )
    }
}

/** One refresh interval, selected or not. */
@Composable
private fun RefreshIntervalChip(
    interval: RefreshInterval,
    selected: Boolean,
    onClick: () -> Unit
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .clip(ExpressiveShapes.pill)
            .background(container)
            .border(
                BorderStroke(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                ),
                shape = ExpressiveShapes.pill
            )
            .bouncyPress(pressedScale = 0.94f)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics {
                this.selected = selected
                role = Role.RadioButton
            }
            .testTag("refresh_interval_${interval.storageValue}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = interval.displayName,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = content
        )
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