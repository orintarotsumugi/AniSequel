package com.anisequel.ui.screens.extensions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisequel.data.extensions.ExtensionContent
import com.anisequel.data.extensions.ExtensionKind
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.rememberFullHeightSheetState
import com.anisequel.ui.navigation.mainBarClearance
import com.anisequel.ui.viewmodel.ExtensionsViewModel

/**
 * The Extensions tab: the catalogue, the reader's repositories and the installs.
 *
 * ## The two state axes
 *
 * The screenshot has four ecosystem tabs (LNReader, Sora, Aniyomi, CloudStream),
 * a content row (Anime/Manga/Novel), an Installed/Available switch and a search
 * field. Those are four ways of narrowing one list, not four lists: everything is
 * derived from [ExtensionsViewModel.state] and the install map, which is what
 * makes a repository added while the page is open appear without a reload of the
 * whole screen.
 *
 * The ecosystem chips under the search field are the same choice as the row above
 * it, so "All" clears the ecosystem filter instead of inventing a fifth one.
 * That redundancy is in the reference app too; the chips are the one-tap way to
 * jump between ecosystems without reading the scrollable tab row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreen(
    viewModel: ExtensionsViewModel,
    onOpenManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showRepositories by rememberSaveable { mutableStateOf(false) }
    var selectedKind by rememberSaveable { mutableStateOf(ExtensionKind.ANIYOMI) }
    var allKinds by rememberSaveable { mutableStateOf(false) }
    var selectedContent by rememberSaveable { mutableStateOf(ExtensionContent.ANIME) }
    var showInstalled by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val sheetState = rememberFullHeightSheetState()

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(
            message = message,
            actionLabel = if (message.startsWith("Allow AniSequel")) "Settings" else null,
            withDismissAction = true
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.openInstallPermissionSettings()
        }
        viewModel.consumeMessage()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("extensions_screen"),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Extensions", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "${state.installed.size} installed \u00b7 " +
                                "${state.repositories.size} repositories",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showRepositories = true },
                        modifier = Modifier.testTag("extensions_repositories_button")
                    ) {
                        Icon(AppVectorIcons.CloudRepo, contentDescription = "Repositories")
                    }
                    IconButton(
                        onClick = onOpenManager,
                        modifier = Modifier.testTag("extensions_manager_button")
                    ) {
                        Icon(AppVectorIcons.Tools, contentDescription = "Extension Manager")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (state.restartRequired) {
                RestartBanner(onRestart = viewModel::restart)
            }

            EcosystemTabs(
                selected = selectedKind,
                allKinds = allKinds,
                onSelect = { kind ->
                    selectedKind = kind
                    allKinds = false
                }
            )

            ContentTabs(
                selected = selectedContent,
                onSelect = { selectedContent = it }
            )

            InstalledSwitch(
                installed = showInstalled,
                onSelect = { showInstalled = it }
            )

            SearchField(
                query = query,
                onQueryChange = { query = it }
            )

            EcosystemChips(
                selectedKind = selectedKind,
                allKinds = allKinds,
                onAll = { allKinds = true },
                onKind = { kind ->
                    selectedKind = kind
                    allKinds = false
                }
            )

            if (state.isLoading && state.catalog.isCold) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            if (showInstalled) {
                InstalledList(
                    state = state,
                    selectedKind = selectedKind,
                    allKinds = allKinds,
                    selectedContent = selectedContent,
                    query = query,
                    onUninstall = viewModel::uninstall,
                    onOpenManager = onOpenManager,
                    bottomPadding = mainBarClearance()
                )
            } else {
                AvailableList(
                    state = state,
                    selectedKind = selectedKind,
                    allKinds = allKinds,
                    selectedContent = selectedContent,
                    query = query,
                    onInstall = viewModel::install,
                    onOpenRepositories = { showRepositories = true },
                    onRefresh = { viewModel.loadCatalog(force = true) },
                    bottomPadding = mainBarClearance()
                )
            }
        }
    }

    if (showRepositories) {
        ExtensionRepositoriesSheet(
            state = state,
            sheetState = sheetState,
            onDismiss = { showRepositories = false },
            onAdd = { url ->
                showRepositories = false
                viewModel.addRepository(url)
            },
            onRemove = viewModel::removeRepository
        )
    }
}

@Composable
private fun RestartBanner(onRestart: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                AppVectorIcons.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Restart required",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    "Restart AniSequel to finish registering the install.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                onClick = onRestart,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error
            ) {
                Text(
                    "Restart",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun EcosystemTabs(
    selected: ExtensionKind,
    allKinds: Boolean,
    onSelect: (ExtensionKind) -> Unit
) {
    val order = listOf(
        ExtensionKind.LNREADER,
        ExtensionKind.SORA,
        ExtensionKind.ANIYOMI,
        ExtensionKind.CLOUDSTREAM
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        order.forEach { kind ->
            val isSelected = !allKinds && kind == selected
            Surface(
                onClick = { onSelect(kind) },
                shape = CircleShape,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    Color.Transparent
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = AppVectorIcons.Extensions,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = kind.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ContentTabs(
    selected: ExtensionContent,
    onSelect: (ExtensionContent) -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            ExtensionContent.entries.forEach { content ->
                val isSelected = content == selected
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                Color.Transparent
                            }
                        )
                        .clickable { onSelect(content) }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (content) {
                            ExtensionContent.ANIME -> AppVectorIcons.Movie
                            ExtensionContent.MANGA -> AppVectorIcons.SourceBook
                            ExtensionContent.NOVEL -> AppVectorIcons.SourceBook
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = content.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun InstalledSwitch(
    installed: Boolean,
    onSelect: (Boolean) -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            listOf(false to "Available", true to "Installed").forEach { (value, label) ->
                val isSelected = installed == value
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent
                        )
                        .clickable { onSelect(value) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text("Search extensions\u2026") },
        leadingIcon = { Icon(AppVectorIcons.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(AppVectorIcons.Close, contentDescription = "Clear search")
                }
            }
        },
        shape = CircleShape,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

@Composable
private fun EcosystemChips(
    selectedKind: ExtensionKind,
    allKinds: Boolean,
    onAll: () -> Unit,
    onKind: (ExtensionKind) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterPill("All", allKinds, onAll)
        listOf(
            ExtensionKind.ANIYOMI,
            ExtensionKind.CLOUDSTREAM,
            ExtensionKind.SORA,
            ExtensionKind.LNREADER
        ).forEach { kind ->
            FilterPill(
                label = kind.label,
                selected = !allKinds && selectedKind == kind,
                onClick = { onKind(kind) }
            )
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}
