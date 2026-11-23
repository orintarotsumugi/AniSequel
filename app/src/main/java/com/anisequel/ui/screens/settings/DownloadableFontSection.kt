package com.anisequel.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anisequel.data.repository.CustomFontStore
import com.anisequel.data.repository.DownloadedFamily
import com.anisequel.data.repository.GoogleFontCatalog
import com.anisequel.data.repository.GoogleFontDownloader
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import com.anisequel.ui.components.expressive.ExpressiveMotion
import kotlinx.coroutines.launch

/**
 * The Font tab's free downloads.
 *
 * The search filters a short local index of Google Fonts' most-used families and
 * falls back to the typed name, so a family outside the index is still
 * downloadable without shipping Google's 2.7 MB metadata document. The chosen
 * family is fetched from Google Fonts' free CSS endpoint on demand and kept in
 * the app's private font directory, never packaged in the APK.
 *
 * Only the regular and bold cuts are fetched. The type scale asks for those two;
 * a family's italics and display weights would be dead bytes on every device.
 *
 * ## Downloaded is a list, not a badge
 *
 * A family used to be reachable only from the search results that happened to
 * contain it: download Lora, switch to Inter, and the Lora row was gone from the
 * page until it was searched for again - so "downloaded" was indistinguishable
 * from "deleted" for anyone who did not know the file was still there. The list
 * above the search reads the font directory instead of a remembered set, so every
 * stored family is a row that can be applied again, with no download, after a
 * restart or an update.
 */
@Composable
internal fun DownloadableFontSection(
    themePreferences: ThemePreferences,
    currentFontPath: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloader = GoogleFontDownloader.shared
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query) { GoogleFontCatalog.search(query) }
    var downloadingId by remember { mutableStateOf<String?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    // `null` until the directory has been read, so "nothing downloaded yet" and
    // "not answered yet" are not the same empty list - the card would otherwise
    // appear and disappear on the first frame. The read itself is a handful of
    // `listFiles` calls on the IO dispatcher, not a decode.
    var storedFamilies by remember { mutableStateOf<List<DownloadedFamily>?>(null) }
    var storedRevision by remember { mutableIntStateOf(0) }
    LaunchedEffect(storedRevision) {
        // Off the main thread, as the comment above always claimed. This is a
        // directory walk of the font folder plus a decode of each family's name,
        // and it ran on the frame that draws the page.
        storedFamilies = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            CustomFontStore.downloadedFamilies(context)
        }
    }

    val stored = storedFamilies.orEmpty()
    if (stored.isNotEmpty()) {
        DownloadedFontsCard(
            families = stored,
            currentFontPath = currentFontPath,
            onUse = { family ->
                scope.launch {
                    themePreferences.setCustomFont(
                        path = family.path,
                        name = family.displayName,
                        boldPath = family.boldPath
                    )
                }
            }
        )
    }

    SectionCard(
        title = "Download fonts",
        icon = AppVectorIcons.Download,
        subtitle = "Free Google Fonts, fetched once and kept in the app",
        modifier = modifier
    ) {
        CollapsibleText(
            text = "Search Google Fonts by name. A family is fetched when you tap " +
                "Download, so it does not add to the APK. Only the regular and bold " +
                "files are kept, which is what the app actually draws.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("google_font_search"),
            label = { Text("Search Google Fonts") },
            placeholder = { Text("Inter, Lora, JetBrains Mono…") },
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = AppVectorIcons.Search,
                    contentDescription = null
                )
            },
            trailingIcon = if (query.isNotBlank()) {
                {
                    IconButton(onClick = { query = "" }) {
                        Icon(
                            imageVector = AppVectorIcons.Close,
                            contentDescription = "Clear search"
                        )
                    }
                }
            } else {
                null
            }
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (query.isBlank()) "Popular downloads" else "Google Fonts results",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))

        results.forEachIndexed { index, font ->
            if (index > 0) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }
            val applied = downloadedFontId(currentFontPath) == font.id
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = font.displayName,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = font.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                if (downloadingId == font.id) {
                    ExpressiveLoadingIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("download_font_loading_${font.id}")
                    )
                } else {
                    FilledTonalButton(
                        onClick = {
                            downloadError = null
                            downloadingId = font.id
                            scope.launch {
                                try {
                                    downloader.download(context, font).fold(
                                        onSuccess = { downloaded ->
                                            themePreferences.setCustomFont(
                                                path = downloaded.path,
                                                name = downloaded.name,
                                                boldPath = downloaded.boldPath
                                            )
                                            // The new family belongs in the stored
                                            // list on this same frame, not after the
                                            // next visit to the page.
                                            storedRevision++
                                        },
                                        onFailure = { failure ->
                                            downloadError = failure.message
                                                ?: "That font could not be downloaded."
                                        }
                                    )
                                } finally {
                                    downloadingId = null
                                }
                            }
                        },
                        enabled = downloadingId == null && !applied,
                        modifier = Modifier.testTag("download_font_${font.id}")
                    ) {
                        Text(if (applied) "Applied" else "Download")
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = downloadError != null,
            enter = fadeIn(animationSpec = ExpressiveMotion.FastEffects),
            exit = fadeOut(animationSpec = ExpressiveMotion.FastEffects)
        ) {
            downloadError?.let { message ->
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .testTag("download_font_error")
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }
    }
}

/**
 * The families already stored, each one applicable with a tap.
 *
 * Deliberately the same row shape as a search result - name, a line of detail, one
 * button - so a downloaded family looks like a font rather than like a receipt for
 * one. There is no delete action: removing a family is not something the page
 * offers, and a row that only ever applies cannot lose a file by accident.
 */
@Composable
private fun DownloadedFontsCard(
    families: List<DownloadedFamily>,
    currentFontPath: String?,
    onUse: (DownloadedFamily) -> Unit
) {
    SectionCard(
        title = "Downloaded fonts",
        icon = AppVectorIcons.Download,
        subtitle = "Kept in the app, so a family is only ever downloaded once"
    ) {
        families.forEachIndexed { index, family ->
            if (index > 0) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }
            val applied = currentFontPath == family.path
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = family.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (family.boldPath != null) {
                            "Google Fonts · regular and bold"
                        } else {
                            "Google Fonts · regular, bold is synthesised"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                FilledTonalButton(
                    onClick = { onUse(family) },
                    enabled = !applied,
                    modifier = Modifier.testTag("apply_downloaded_font_${family.id}")
                ) {
                    Text(if (applied) "Applied" else "Use")
                }
            }
        }
    }
}

/**
 * The catalog id of the downloaded family currently active, if any.
 *
 * Paths are written by [GoogleFontDownloader] as
 * `google_<id>_400.ttf`; keeping the id in the name is what lets Settings show
 * "Applied" without another stored preference that could disagree with the file.
 */
private fun downloadedFontId(path: String?): String? {
    if (!CustomFontStore.isDownloaded(path)) return null
    return path
        ?.substringAfterLast('/')
        ?.removePrefix("google_")
        ?.substringBefore("_400")
        ?.takeIf { it.isNotBlank() }
}
