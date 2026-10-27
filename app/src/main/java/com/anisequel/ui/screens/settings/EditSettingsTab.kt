package com.anisequel.ui.screens.settings

import android.app.KeyguardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.ViewerProfile
import com.anisequel.data.repository.AppLockPreferences
import com.anisequel.data.repository.AuthRepositoryImpl
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.RedirectUrlHint
import com.anisequel.ui.components.StatsBanner
import com.anisequel.ui.components.openExternalUrl
import com.anisequel.ui.viewmodel.AuthUiState
import com.anisequel.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

/**
 * The AniList account page.
 *
 * It used to be the "Edit" tab and held six unrelated cards. Refresh, arriving
 * rows and quick add moved to [LibrarySettingsTab]; what is left is one subject:
 * who this app is signed in as, and who is allowed to open it. The Client ID and
 * the sign-out button are two ends of the same session, and the app lock sits
 * with them because it is the same question asked about the device.
 *
 * ## Why the profile card and the list snapshot are here
 *
 * Both were on the About page, which is why About had two sign-out buttons, a
 * sign-in prompt and a second copy of the connection check: it had become the
 * place where "account" and "about the app" overlapped. The account page is the
 * page whose whole subject is the session, so the avatar, the demo prompt and
 * the snapshot taken from that session belong here. About keeps only what is
 * true of the build regardless of who is signed in.
 */
@Composable
fun EditSettingsTab(
    authViewModel: AuthViewModel,
    appLockPreferences: AppLockPreferences,
    onNavigateToLogin: () -> Unit,
    viewer: ViewerProfile? = null,
    totalWatchedCount: Int? = null,
    totalMissedCount: Int? = null,
    modifier: Modifier = Modifier
) {
    val authState by authViewModel.uiState.collectAsState()
    val currentClientId by authViewModel.clientId.collectAsState()
    val context = LocalContext.current
    SettingsScrollColumn(modifier) {
        if (viewer != null) {
            ProfileCard(
                viewer = viewer,
                // AniList's own profile page, not a Settings route: this is the one
                // place the app can show the account as AniList sees it.
                onOpenProfile = {
                    openExternalUrl(context, "https://anilist.co/user/${viewer.name}")
                }
            )
        } else {
            // Text only. The sign-in action is the button at the foot of this
            // page, and a second one here is exactly the kind of duplicate this
            // rework removed from About - two controls, one logout(), and no way
            // to tell whether they differed.
            DemoIdentityCard()
        }

        if (totalWatchedCount != null && totalMissedCount != null) {
            ListSnapshotCard(
                totalWatchedCount = totalWatchedCount,
                totalMissedCount = totalMissedCount,
                isSignedIn = viewer != null
            )
        }

        SectionCard(
            title = "AniList Client ID",
            icon = AppVectorIcons.SectionAniList,
            subtitle = "The app identity AniList signs you in with"
        ) {
            Text(
                text = "The default works. Change it only if you registered your own " +
                    "AniList client and want AniSequel to use that one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            ClientIdEditor(
                currentClientId = currentClientId,
                onSave = { authViewModel.updateClientId(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))
            RedirectUrlHint()
        }

        AppLockCard(appLockPreferences)

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
            icon = if (authState is AuthUiState.Authenticated) {
                AppVectorIcons.Logout
            } else {
                AppVectorIcons.Login
            },
            variant = SettingsButtonVariant.Outlined,
            fillWidth = true,
            testTag = "edit_logout_button"
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * What the app is while nobody is signed in.
 *
 * Demo mode is not an error state - the dashboard still works against the demo
 * list - so this says what is available rather than what is missing. It used to
 * carry its own "Sign in with AniList" button next to the sign-out button that
 * switched to "Sign in"; both did the same thing.
 */
@Composable
private fun DemoIdentityCard() {
    SectionCard(
        title = "Demo mode",
        icon = AppVectorIcons.Login,
        subtitle = "Read-only until you sign in",
        badge = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Text(
                    text = "DEMO",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    ) {
        Text(
            text = "You are browsing a built-in sample list. Sign in with your AniList " +
                "account to scan your own completed list, follow franchise trees, and add " +
                "sequels to Planning in one tap.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Sign in with the button below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The scan the dashboard is currently showing, in one card. */
@Composable
private fun ListSnapshotCard(
    totalWatchedCount: Int,
    totalMissedCount: Int,
    isSignedIn: Boolean
) {
    SectionCard(
        title = "Your list",
        icon = AppVectorIcons.List,
        subtitle = "Snapshot of the current scan"
    ) {
        StatsBanner(
            totalWatched = totalWatchedCount,
            missedCount = totalMissedCount
        )
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(10.dp))
        InfoRow(
            label = "Planning sync",
            value = if (isSignedIn) "Write-enabled" else "Read-only on public profiles"
        )
    }
}

/**
 * The optional app lock.
 *
 * The switch is disabled, with the reason in its subtitle, when the device has
 * no secure lock. That is not a limitation invented here - the platform's
 * credential prompt has nothing to ask for without one - and offering a switch
 * that would silently fail open is the worse version of the same truth.
 */
@Composable
private fun AppLockCard(appLockPreferences: AppLockPreferences) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled by appLockPreferences.enabled.collectAsState(initial = false)
    val deviceHasSecureLock = remember {
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
    }

    SectionCard(
        title = "App lock",
        icon = AppVectorIcons.Privacy,
        subtitle = "Who can open AniSequel"
    ) {
        SwitchRow(
            title = "Lock AniSequel",
            subtitle = if (deviceHasSecureLock) {
                "Ask for your fingerprint, face or screen lock when the app opens or returns from the background."
            } else {
                "Set a screen lock on this device first - there is nothing to ask for yet."
            },
            checked = enabled,
            enabled = deviceHasSecureLock,
            onCheckedChange = { locked ->
                scope.launch { appLockPreferences.setEnabled(locked) }
            },
            modifier = Modifier.testTag("app_lock_switch")
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
