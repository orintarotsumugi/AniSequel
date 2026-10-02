package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.data.model.ViewerProfile
import com.example.data.network.AniListOAuth
import com.example.data.repository.AuthRepositoryImpl
import com.example.data.repository.ThemeMode
import com.example.data.repository.ThemePreferences
import com.example.data.update.formatBytes
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.DownloadProgress
import com.example.ui.components.ExpressivePolygonSegmentedBar
import com.example.ui.components.ExpressiveTabBar
import com.example.ui.components.RedirectUrlHint
import com.example.ui.components.SegmentedOption
import com.example.ui.components.UpdateUiState
import com.example.ui.components.openReleasePage
import com.example.ui.components.rememberUpdateController
import com.example.ui.theme.supportsDynamicColor
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

private val MaxContentWidth = 640.dp

/**
 * The icon that carries a theme mode without reading its label.
 *
 * Defined here rather than on [ThemeMode] itself: the enum lives in the data
 * layer, next to the DataStore code, and a `data` class that imports
 * `androidx.compose.ui.graphics.vector.ImageVector` cannot be tested without
 * the Compose UI on the classpath, for a mapping that is purely a display
 * decision.
 */
private fun ThemeMode.icon(): ImageVector = when (this) {
    ThemeMode.SYSTEM -> Icons.Outlined.BrightnessAuto
    ThemeMode.LIGHT -> Icons.Outlined.LightMode
    ThemeMode.DARK -> Icons.Outlined.DarkMode
}

/** One entry in the "What's new" list. */
private data class ChangelogEntry(val title: String, val detail: String)

/**
 * What this build actually changed.
 *
 * Written out rather than generated because each line is a bug that reached a
 * user, and the wording that explains what went wrong is the point: a list of
 * "various fixes" would not have told anyone what the AniList connection error
 * on the dashboard was, or that their saved Planning entries could be dropped.
 */
private val changelog = listOf(
    ChangelogEntry(
        title = "Your anime list loads again",
        detail = "AniSequel asked AniList for each entry's season and read the " +
                "answer as a number. AniList returns a season name (\"WINTER\"), " +
                "so the whole list failed to load and the app showed a connection " +
                "error instead of your sequels."
    ),
    ChangelogEntry(
        title = "Nothing gets lost when you add to Planning",
        detail = "Two adds tapped in quick succession were collapsed into a single " +
                "request, so the second entry was never sent to AniList. Every add " +
                "now reaches AniList on its own."
    ),
    ChangelogEntry(
        title = "Sign-in no longer bounces back to the login screen",
        detail = "Returning from AniList's approval page could finish reading your " +
                "saved session after the new sign-in had already landed, undoing it."
    ),
    ChangelogEntry(
        title = "The filter badge means something again",
        detail = "It showed a count on every launch, before you had filtered " +
                "anything. It now appears only while filters are actually narrowing " +
                "the list."
    ),
    ChangelogEntry(
        title = "Errors you can act on",
        detail = "A response AniSequel could not read now explains that plainly, " +
                "instead of printing an internal field path."
    ),
    ChangelogEntry(
        title = "Settings split into Info and Edit",
        detail = "Reading the app and changing it are now separate tabs, and " +
                "appearance is a setting you can actually change - including " +
                "theming from your wallpaper."
    ),
    ChangelogEntry(
        title = "AniSequel updates itself",
        detail = "On launch the app checks GitHub for a newer build and " +
                "offers it here. Downloading and installing takes two taps; " +
                "Android still asks you to confirm the install, and it has to " +
                "be one time granted in Settings first."
    ),
    ChangelogEntry(
        title = "Material 3 shapes on the tabs and theme picker",
        detail = "The two tabs are shaped pills that grow when selected instead " +
                "of an underline, and the light/dark picker lost the oversized " +
                "segmented buttons."
    ),
    ChangelogEntry(
        title = "Saving your Client ID sticks",
        detail = "The Save button's confirmation was cleared the instant you " +
                "pressed it, because saving your own value looked like someone " +
                "else changing it."
    )
)

/**
 * Settings, in two tabs.
 *
 * Split because the two jobs are different and the old screen made both worse.
 * "Info" is read-only: who you are, what this build is, what changed in it.
 * "Edit" is the only tab with anything you can type into - the AniList Client ID
 * and appearance. Mixing them meant a screen of static text sat above a text
 * field and two switches, with no signal about which of those rows were
 * editable at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authViewModel: AuthViewModel,
    themePreferences: ThemePreferences,
    viewer: ViewerProfile?,
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    // rememberSaveable, not remember: this is the tab index, it is trivially
    // cheap to store, and losing it on rotation means landing back on Info after
    // having carefully set up the Edit tab.
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("settings_screen"),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("settings_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Material 3 Expressive shapes rather than `TabRow`. The
                // Material tab row marks the selection with a 3dp underline,
                // which in a settings header reads as a link rather than as a
                // tab - the two tabs here sit on the same row as the back
                // arrow and the title, and an underline there looks like a
                // hyperlink on "Info".
                HorizontalDivider()
                ExpressiveTabBar(
                    tabs = listOf("Info", "Edit"),
                    selectedIndex = selectedTab,
                    onSelect = { selectedTab = it },
                    modifier = Modifier.testTag("settings_tabs")
                )
            }
        }
    ) { paddingValues ->
        // Keyed on the tab so switching tabs does not carry one tab's scroll
        // position into the other, and so each tab composes fresh rather than
        // hiding a stale text field behind a tab the user cannot see.
        key(selectedTab) {
            when (selectedTab) {
                1 -> EditSettingsTab(
                    authViewModel = authViewModel,
                    themePreferences = themePreferences,
                    onNavigateBack = onNavigateBack,
                    onNavigateToLogin = onNavigateToLogin,
                    modifier = Modifier.padding(paddingValues)
                )

                else -> InfoSettingsTab(
                    viewer = viewer,
                    onNavigateBack = onNavigateBack,
                    onNavigateToLogin = onNavigateToLogin,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

/**
 * Everything you would only read: the account, this build, and what changed.
 *
 * No interactive controls below the profile card, by design - if a row on this
 * tab could be changed it belongs on Edit.
 */
@Composable
private fun InfoSettingsTab(
    viewer: ViewerProfile?,
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    SettingsScrollColumn(modifier) {
        if (viewer != null) {
            ProfileCard(
                viewer = viewer,
                onOpenProfile = {
                    openUrl(context, "https://anilist.co/user/${viewer.name}")
                }
            )
        } else {
            SectionCard(title = "Not signed in") {
                Text(
                    text = "You are browsing AniSequel without connecting an account. " +
                            "Sign in to scan your own list and add sequels to your " +
                            "AniList Planning list.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SectionCard(title = "About") {
            InfoRow(label = "Version", value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            Spacer(modifier = Modifier.height(10.dp))
            InfoRow(label = "Package", value = BuildConfig.APPLICATION_ID)
            Spacer(modifier = Modifier.height(10.dp))
            InfoRow(label = "Data source", value = "AniList GraphQL")

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "AniSequel walks the franchise relations of everything on your completed lists and surfaces the sequels you never started. Nothing is stored on a server - the app talks to AniList directly from your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        UpdateSettingsCard()

        SectionCard(title = "What's new in ${BuildConfig.VERSION_NAME}") {
            Text(
                text = "Fixes in this build. If you hit the AniList connection error, this is the list that explains it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            changelog.forEachIndexed { index, entry ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp, end = 10.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Column {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = entry.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        SectionCard(title = "Help") {
            Text(
                text = "AniSequel only reads the parts of your AniList list it needs to " +
                        "find sequels. It never writes to your list except when you tap " +
                        "\"Add to Planning\".",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = { openUrl(context, AniListOAuth.DEVELOPER_SETTINGS_URL) }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("AniList API documentation")
            }
        }

        SignOutButton(
            isSignedIn = viewer != null,
            onNavigateBack = onNavigateBack,
            onNavigateToLogin = onNavigateToLogin
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Check for a newer AniSequel, download it, and hand it to the installer.
 *
 * On the Info tab rather than Edit because an update is a *fact about the
 * build*, not a preference - this is the panel that reports the version, the
 * package and what changed, and "is this the newest one?" belongs beside it.
 *
 * The same check runs silently at launch and prompts there if it finds
 * something, so this card is the manual retry: for when the launch check
 * happened on a train, or was dismissed with "Later".
 */
@Composable
private fun UpdateSettingsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = rememberUpdateController()
    val state = controller.state

    // Checking here too would race the launch check that is already in flight
    // and could put a second dialog in front of the user.
    LaunchedEffect(Unit) {
        if (state == UpdateUiState.Idle) controller.check(scope)
    }

    SectionCard(title = "Updates") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when (state) {
                    UpdateUiState.Idle, UpdateUiState.Checking ->
                        "Checking GitHub for a newer build…"

                    is UpdateUiState.UpToDate ->
                        "You're on ${state.installedVersion}, the latest release."

                    is UpdateUiState.Available ->
                        "AniSequel ${state.manifest.version} is available " +
                                "(${formatBytes(state.manifest.sizeBytes)})."

                    is UpdateUiState.ReadyToInstall ->
                        "Downloaded. Ready to install."

                    is UpdateUiState.NeedsInstallPermission ->
                        "Downloaded. AniSequel needs permission to install apps."

                    is UpdateUiState.Failed ->
                        state.message

                    is UpdateUiState.Downloading -> "Downloading…"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }

        when (state) {
            is UpdateUiState.Downloading -> {
                Spacer(modifier = Modifier.height(12.dp))
                DownloadProgress(state = state)
            }

            is UpdateUiState.Available -> {
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { controller.reset() },
                        modifier = Modifier.testTag("dismiss_update_button")
                    ) { Text("Not now") }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { controller.download(scope) },
                        modifier = Modifier.testTag("download_update_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download")
                    }
                }

                // The release page is where the notes the workflow wrote
                // actually are. Without this the dialog offers a version bump
                // and a build number, which is not a reason to install one.
                state.manifest.releaseUrl?.takeIf { it.isNotBlank() }?.let { releaseUrl ->
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = { openReleasePage(context, releaseUrl) },
                        modifier = Modifier.testTag("release_notes_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("What's changed in ${state.manifest.version}")
                    }
                }
            }

            is UpdateUiState.ReadyToInstall -> {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { controller.install(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("install_update_button")
                ) { Text("Install now") }
            }

            is UpdateUiState.NeedsInstallPermission -> {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Android will not let AniSequel open the installer " +
                            "until you allow it to install apps. This is a " +
                            "one-time permission in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { controller.requestInstallPermission(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("grant_install_permission_button")
                ) { Text("Open Settings") }
            }

            else -> Unit
        }

        when (state) {
            // A check that finished is worth repeating, whichever way it went.
            is UpdateUiState.UpToDate, is UpdateUiState.Failed -> {
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = { controller.check(scope) },
                    modifier = Modifier.testTag("check_updates_button")
                ) { Text("Check again") }
            }

            else -> Unit
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "AniSequel checks github.com/LOST-4EVER/AniSequel for new " +
                    "releases. Nothing about your AniList account is sent - the " +
                    "check is a single request for a public file.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Everything you can change.
 *
 * Two sections only, both of which change how the app behaves rather than what
 * it is: appearance, and the AniList app it authorizes against. Anything that
 * only reports a value lives on the Info tab instead.
 */
@Composable
private fun EditSettingsTab(
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
        SectionCard(title = "Appearance") {
            Text(
                text = "Light, dark, or whatever your phone is set to.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Expressive polygons rather than `SingleChoiceSegmentedButtonRow`.
            // The Material segmented control is a 48dp outlined pill that, at
            // full width on a phone, is a very large target for a three-way
            // choice - and the checkmark it draws in the selected segment
            // repeats information the fill colour already carries. The icons
            // are what make Light vs Dark readable without relying on colour.
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
                title = "Theme from my wallpaper",
                subtitle = if (supportsDynamicColor) {
                    "Material You. AniSequel picks up the colours of your wallpaper " +
                            "and updates when you change it."
                } else {
                    "Needs Android 12 or newer. This device is on Android " +
                            "${android.os.Build.VERSION.RELEASE}."
                },
                checked = themeSettings.useDynamicColor,
                // Disabled rather than hidden on older devices: the row explains
                // *why* it cannot be used, which a missing switch cannot.
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
                TextButton(
                    onClick = { scope.launch { themePreferences.reset() } },
                    modifier = Modifier.testTag("reset_appearance_button")
                ) { Text("Reset appearance") }
            }
        }

        SectionCard(title = "AniList connection") {
            Text(
                text = "AniSequel signs you in with the OAuth Client ID registered to " +
                        "your own AniList developer account. You only need this if you " +
                        "are self-hosting a fork.",
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
            TextButton(
                onClick = { openUrl(context, AniListOAuth.DEVELOPER_SETTINGS_URL) }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Where do I find my Client ID?")
            }
        }

        SignOutButton(
            isSignedIn = authState is AuthUiState.Authenticated,
            onNavigateBack = onNavigateBack,
            onNavigateToLogin = onNavigateToLogin
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * The Client ID field, its Reset button and its Save button.
 *
 * Split out so the save state is `rememberSaveable` within a composable that is
 * recreated when the tab changes - previously the editing state lived in the
 * screen's own scope, so a half-typed Client ID survived neither a rotation nor
 * anything else that recomposed the parent.
 */
@Composable
private fun ClientIdEditor(
    currentClientId: String,
    onSave: (String) -> Unit
) {
    var editingClientId by rememberSaveable { mutableStateOf(currentClientId) }
    var hasSavedClientId by rememberSaveable { mutableStateOf(false) }

    // A Client ID changed *elsewhere* (the login screen's dialog) has to
    // replace whatever is in the field, or the user is shown a stale value that
    // the Save button then refuses to act on because it no longer differs.
    //
    // The `savedValue` guard is the fix for a bug this effect caused: saving
    // your own Client ID changes `currentClientId`, which re-ran the effect,
    // which cleared `hasSavedClientId` - so the button flipped from "Saved"
    // straight back to "Save" on the very tap that saved it. Tracking the last
    // value we saved means the effect only fires for a change we did not make.
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
                    "Using the default. Change it if you forked this app."
                } else {
                    "Custom: $currentClientId"
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
        // Only offered when there is something to go back to. The old Reset just
        // put the *current* value back in the box, so once a fork owner had
        // saved a custom ID there was no way back to the built-in one - the
        // field could only ever be reset to itself.
        if (!isDefault) {
            TextButton(
                onClick = { editingClientId = AuthRepositoryImpl.DEFAULT_CLIENT_ID },
                modifier = Modifier.testTag("reset_client_id_button")
            ) { Text("Use default") }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Button(
            onClick = {
                savedValue = trimmed
                onSave(trimmed)
                hasSavedClientId = true
            },
            enabled = canSave,
            modifier = Modifier.testTag("save_client_id_button")
        ) {
            if (hasSavedClientId) {
                Icon(
                    imageVector = Icons.Filled.CheckCircleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(if (hasSavedClientId) "Saved" else "Save")
        }
    }
}

@Composable
private fun SignOutButton(
    isSignedIn: Boolean,
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    OutlinedButton(
        onClick = {
            if (isSignedIn) {
                onNavigateBack()
            } else {
                // Demo and username-scan sessions have no token to clear, so
                // "sign out" would otherwise do nothing visible.
                onNavigateToLogin()
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("logout_button"),
        shape = MaterialTheme.shapes.small
    ) {
        Icon(
            imageVector = if (isSignedIn) AppVectorIcons.Logout else AppVectorIcons.Login,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isSignedIn) "Sign out" else "Back to sign in",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * A titled group of settings rows.
 *
 * `mergeDescendants` is deliberately *not* used: merging the whole card into
 * one node would make a screen reader read "Appearance" together with every
 * switch and field inside it as a single sentence, which is worse than reading
 * them as separate rows. The title stays its own node.
 */
@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun ProfileCard(
    viewer: ViewerProfile,
    onOpenProfile: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val avatar = viewer.avatar?.large
                if (avatar != null) {
                    AsyncImage(
                        model = avatar,
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppVectorIcons.Tv,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = viewer.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Signed in to AniList",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onOpenProfile) {
                    Icon(
                        imageVector = AppVectorIcons.OpenInBrowser,
                        contentDescription = "Open AniList profile"
                    )
                }
            }

            viewer.statistics?.anime?.count?.let { count ->
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Anime on your list",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.semantics { role = Role.Switch }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End
        )
    }
}

/**
 * The scrolling, width-limited column both tabs sit in.
 *
 * Shared so the two tabs cannot drift apart in padding or max width, which is
 * how the old screen ended up with the text fields stopping short of the cards
 * above them.
 */
@Composable
private fun SettingsScrollColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = MaxContentWidth)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}