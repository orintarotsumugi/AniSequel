package com.anisequel.ui.screens.extensions

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisequel.BuildConfig
import com.anisequel.data.extensions.ExtensionManager
import com.anisequel.data.extensions.InstallOutcome
import com.anisequel.ui.components.AppVectorIcons
import kotlinx.coroutines.launch

/**
 * The Extension Manager behind the Extensions page's wrench.
 *
 * It is a *status* page first: what is installed, whether Android will let the
 * app hand an APK to its installer, and what the runtime can actually load. The
 * controls that exist here all do something real - the permission row opens the
 * platform screen, the storage row opens a document picker, and Restart is the
 * same restart the install banner offers. There is no Rollback Version switch:
 * a control that cannot roll back is worse than a missing one, and the phase
 * document records that as future work.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionManagerScreen(
    manager: ExtensionManager,
    onBack: () -> Unit,
    onOpenPlayerSettings: () -> Unit,
    onOpenExtensions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val repositories by manager.repositories.collectAsStateWithLifecycle()
    val installed by manager.installed.collectAsStateWithLifecycle()
    val restartRequired by manager.restartRequired.collectAsStateWithLifecycle()
    val showNsfw by manager.showNsfw.collectAsStateWithLifecycle()
    var canInstall by remember { mutableStateOf(manager.canInstallPackages()) }

    LifecycleResumeEffect(Unit) {
        canInstall = manager.canInstallPackages()
        onPauseOrDispose { }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = displayNameOf(context, uri)
            when (val outcome = manager.installFromUri(uri, name)) {
                is InstallOutcome.Installed -> snackbar.showSnackbar(outcome.message)
                is InstallOutcome.NeedsPermission -> {
                    snackbar.showSnackbar(outcome.message)
                    manager.openInstallPermissionSettings()
                }
                is InstallOutcome.Failed -> snackbar.showSnackbar(outcome.message)
            }
            canInstall = manager.canInstallPackages()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("extension_manager_screen"),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Extension Manager", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppVectorIcons.Back, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "plugin") {
                PluginCard(
                    repositories = repositories.size,
                    installed = installed.size,
                    restartRequired = restartRequired
                )
            }

            item(key = "sources_header") { SectionHeader("EXTENSION SOURCES") }
            item(key = "repositories") {
                SettingsRow(
                    icon = AppVectorIcons.CloudRepo,
                    title = "Repositories",
                    subtitle = "${repositories.size} indexes supplying the catalogue",
                    onClick = onOpenExtensions,
                    trailing = { Chevron() }
                )
            }
            item(key = "installed") {
                SettingsRow(
                    icon = AppVectorIcons.Extensions,
                    title = "Installed extensions",
                    subtitle = "${installed.size} records on this device",
                    onClick = onOpenExtensions,
                    trailing = { Chevron() }
                )
            }
            item(key = "nsfw") {
                SettingsRow(
                    icon = AppVectorIcons.Visibility,
                    title = "Show NSFW extensions",
                    subtitle = "Repositories hide their 18+ entries by default",
                    onClick = { manager.preferences.setShowNsfw(!showNsfw) },
                    trailing = {
                        Switch(
                            checked = showNsfw,
                            onCheckedChange = { manager.preferences.setShowNsfw(it) }
                        )
                    }
                )
            }

            item(key = "install_header") { SectionHeader("PLUGIN INSTALLATION & SYNC") }
            item(key = "permission") {
                SettingsRow(
                    icon = AppVectorIcons.Shield,
                    title = "Install unknown apps",
                    subtitle = if (canInstall) {
                        "Allowed - Android's installer opens for extension APKs"
                    } else {
                        "Needs permission - tap to open this app's install settings"
                    },
                    onClick = { manager.openInstallPermissionSettings() },
                    trailing = {
                        StatusPill(
                            label = if (canInstall) "ALLOWED" else "NEEDS PERMISSION",
                            good = canInstall
                        )
                    }
                )
            }
            item(key = "local_apk") {
                SettingsRow(
                    icon = AppVectorIcons.Folder,
                    title = "Load plugin APK from storage",
                    subtitle = "Pick an .apk or .js file from this device",
                    onClick = { picker.launch(arrayOf("*/*")) },
                    trailing = { Chevron() }
                )
            }
            item(key = "player_settings") {
                SettingsRow(
                    icon = AppVectorIcons.VideoSettings,
                    title = "Player settings",
                    subtitle = "Decoder, renderer, audio engine and enhancement",
                    onClick = onOpenPlayerSettings,
                    trailing = { Chevron() }
                )
            }
            item(key = "restart") {
                SettingsRow(
                    icon = AppVectorIcons.Refresh,
                    title = "Restart AniSequel",                        subtitle = if (restartRequired) {
                            "An extension changed - start again to register it"
                        } else {
                        "Close every screen and start the app in a fresh process"
                    },
                    onClick = {
                        if (!manager.restart()) {
                            scope.launch { snackbar.showSnackbar("Could not restart the app from here.") }
                        }
                    },
                    trailing = { Chevron() }
                )
            }

            item(key = "notes_header") { SectionHeader("BETA NOTES") }
            item(key = "notes") {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "What the beta can load",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Catalogue discovery, APK/module installation, the install-permission " +
                                "handoff, source search, episode discovery, servers and playback all " +
                                "work. Playback runs through the built-in Internet Archive source; " +
                                "Aniyomi and CloudStream APK code is installed but not executed " +
                                "until the sandboxed runtime in Phase 2 lands.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                AppVectorIcons.Info,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Extension framework 0.1.0-beta \u00b7 app ${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginCard(
    repositories: Int,
    installed: Int,
    restartRequired: Boolean
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        AppVectorIcons.Check,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp).size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Extension catalogue ready",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "Aniyomi & CloudStream catalogues ready",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                StatusPill(
                    label = if (restartRequired) "RESTART" else "BETA",
                    good = !restartRequired
                )
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f))
            Spacer(Modifier.height(12.dp))
            Row {
                Stat(label = "Repositories", value = repositories.toString())
                Spacer(Modifier.width(28.dp))
                Stat(label = "Installed", value = installed.toString())
                Spacer(Modifier.width(28.dp))
                Stat(label = "Version", value = "0.1.0-beta")
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
        )
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable RowScope.() -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp).size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
private fun Chevron() {
    Icon(
        AppVectorIcons.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp)
    )
}

@Composable
private fun StatusPill(label: String, good: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (good) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        }
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (good) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

private fun displayNameOf(context: Context, uri: Uri): String {
    val name = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull()
    return name?.takeIf { it.isNotBlank() } ?: "local-extension.apk"
}
