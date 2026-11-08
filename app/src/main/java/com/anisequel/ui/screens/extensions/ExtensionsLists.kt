package com.anisequel.ui.screens.extensions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.extensions.CatalogExtension
import com.anisequel.data.extensions.ExtensionContent
import com.anisequel.data.extensions.ExtensionKind
import com.anisequel.data.extensions.InstalledExtension
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.viewmodel.ExtensionsUiState
import java.util.Locale

@Composable
internal fun AvailableList(
    state: ExtensionsUiState,
    selectedKind: ExtensionKind,
    allKinds: Boolean,
    selectedContent: ExtensionContent,
    query: String,
    onInstall: (CatalogExtension) -> Unit,
    onOpenRepositories: () -> Unit,
    onRefresh: () -> Unit,
    bottomPadding: Dp
) {
    val installedKeys = state.installed.keys
    val filtered = state.entries
        .asSequence()
        .filter { it.key !in installedKeys }
        .filter { allKinds || it.kind == selectedKind }
        .filter { it.content == selectedContent }
        .filter { state.showNsfw || !it.nsfw }
        .filter { extensionMatches(it, query) }
        .sortedWith(compareBy({ it.language.lowercase() }, { it.name.lowercase() }))
        .toList()

    if (state.repositories.isEmpty()) {
        EmptyCatalogue(
            title = "No repositories yet",
            body = "Add an Aniyomi, CloudStream, Sora or LNReader index URL to browse extensions.",
            action = "Add Repo",
            onAction = onOpenRepositories,
            bottomPadding = bottomPadding
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = bottomPadding + 24.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (filtered.isEmpty()) {
            item {
                EmptyCatalogue(
                    title = if (state.catalog.errors.isNotEmpty()) {
                        "No extensions could be read"
                    } else {
                        "Nothing here yet"
                    },
                    body = if (state.catalog.errors.isNotEmpty()) {
                        state.catalog.errors.first().message
                    } else {
                        "This tab has no extensions for the current filters. " +
                            "Try another ecosystem or add a repository."
                    },
                    action = "Refresh",
                    onAction = onRefresh,
                    bottomPadding = 0.dp
                )
            }
        }

        if (state.catalog.errors.isNotEmpty()) {
            item(key = "failures") {
                CatalogueFailures(errors = state.catalog.errors, onRetry = onRefresh)
            }
        }

        val groups = filtered.groupBy { it.language.ifBlank { "en" }.lowercase() }
        groups.toSortedMap().forEach { (language, entries) ->
            item(key = "header_$language") {
                LanguageHeader(language = language, count = entries.size)
            }
            items(entries, key = { it.key }) { entry ->
                CatalogueRow(
                    entry = entry,
                    installed = null,
                    installingKey = state.installingKey,
                    progress = state.installProgress,
                    onInstall = { onInstall(entry) },
                    onUninstall = {}
                )
            }
        }

        item(key = "count") {
            Text(
                text = "${filtered.size} extensions",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
internal fun InstalledList(
    state: ExtensionsUiState,
    selectedKind: ExtensionKind,
    allKinds: Boolean,
    selectedContent: ExtensionContent,
    query: String,
    onUninstall: (InstalledExtension) -> Unit,
    onOpenManager: () -> Unit,
    bottomPadding: Dp
) {
    val records = state.installed.values
        .asSequence()
        .filter { allKinds || it.kind == selectedKind }
        .filter { it.content == selectedContent }
        .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        .sortedBy { it.name.lowercase() }
        .toList()

    if (records.isEmpty()) {
        EmptyCatalogue(
            title = "Nothing installed yet",
            body = "Extensions you install appear here. The built-in Internet Archive " +
                "source is already available on the Watch page.",
            action = null,
            onAction = {},
            bottomPadding = bottomPadding
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = bottomPadding + 24.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item(key = "installed_hint") {
            Text(
                text = "Extensions installed on this device."
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }
        items(records, key = { it.key }) { record ->
            InstalledRow(
                record = record,
                onUninstall = { onUninstall(record) },
                onOpenManager = onOpenManager
            )
        }
        item(key = "installed_count") {
            Text(
                text = "${records.size} installed",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun CatalogueRow(
    entry: CatalogExtension,
    installed: InstalledExtension?,
    installingKey: String?,
    progress: Float,
    onInstall: () -> Unit,
    onUninstall: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExtensionArtwork(
                    iconUrl = entry.iconUrl,
                    name = entry.name,
                    modifier = Modifier.size(52.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(5.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Badge(languageLabel(entry.language))
                        Badge("v${entry.version}")
                        if (entry.nsfw) NsfwBadge()
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (installingKey == entry.key) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.size(26.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (installed != null) {
                    IconButton(onClick = onUninstall) {
                        Icon(
                            AppVectorIcons.Delete,
                            contentDescription = "Uninstall ${entry.name}",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    Surface(
                        onClick = onInstall,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            AppVectorIcons.Download,
                            contentDescription = "Install ${entry.name}",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
            if (installingKey == entry.key) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 10.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                )
            }
        }
    }
}

@Composable
private fun InstalledRow(
    record: InstalledExtension,
    onUninstall: () -> Unit,
    onOpenManager: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExtensionArtwork(
                iconUrl = record.iconUrl,
                name = record.name,
                modifier = Modifier.size(52.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(5.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Badge(record.kind.label)
                    Badge("v${record.version}")
                    if (record.needsRestart) NsfwBadge(label = "RESTART")
                }
            }
            IconButton(onClick = onUninstall) {
                Icon(
                    AppVectorIcons.Delete,
                    contentDescription = "Uninstall ${record.name}",
                    tint = MaterialTheme.colorScheme.error
                )
            }
            IconButton(onClick = onOpenManager) {
                Icon(AppVectorIcons.Settings, contentDescription = "Extension settings")
            }
        }
    }
}

@Composable
private fun ExtensionArtwork(
    iconUrl: String?,
    name: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = name.take(1).uppercase(Locale.ENGLISH),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!iconUrl.isNullOrBlank()) {
            AsyncImage(
                model = iconUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        }
    }
}

@Composable
private fun Badge(label: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun NsfwBadge(label: String = "18+") {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun LanguageHeader(language: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = languageLabel(language),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CatalogueFailures(
    errors: List<com.anisequel.data.extensions.CatalogFailure>,
    onRetry: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    AppVectorIcons.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${errors.size} repository read failed",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            errors.take(3).forEach { failure ->
                Text(
                    text = "${failure.repository.displayHost}: ${failure.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.2f)
            )
            Text(
                text = "Try again",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.clickable(onClick = onRetry)
            )
        }
    }
}

@Composable
private fun EmptyCatalogue(
    title: String,
    body: String,
    action: String?,
    onAction: () -> Unit,
    bottomPadding: Dp
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp + bottomPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Icon(
                AppVectorIcons.Extensions,
                contentDescription = null,
                modifier = Modifier.padding(18.dp).size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            Surface(
                onClick = onAction,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    text = action,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }
    }
}

private fun extensionMatches(entry: CatalogExtension, query: String): Boolean {
    if (query.isBlank()) return true
    val needle = query.trim()
    return entry.name.contains(needle, ignoreCase = true) ||
        entry.packageName.contains(needle, ignoreCase = true) ||
        entry.language.contains(needle, ignoreCase = true)
}

/** "en" -> "ENGLISH", "pt-BR" -> "PORTUGUESE (BRAZIL)". */
internal fun languageLabel(code: String): String {
    if (code.isBlank()) return "OTHER"
    val locale = if (code.contains("-")) {
        Locale.forLanguageTag(code)
    } else {
        Locale(code)
    }
    val name = locale.getDisplayLanguage(Locale.ENGLISH)
    if (name.isBlank() || name == code) return code.uppercase(Locale.ENGLISH)
    val country = locale.getDisplayCountry(Locale.ENGLISH)
    return if (country.isBlank()) name.uppercase(Locale.ENGLISH)
    else "${name.uppercase(Locale.ENGLISH)} (${country.uppercase(Locale.ENGLISH)})"
}
