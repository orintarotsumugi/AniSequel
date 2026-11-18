package com.anisequel.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
import java.util.Locale

/** The search field and its empty-query recommendations. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .testTag("settings_search_field"),
            placeholder = { Text("Search settings") },
            leadingIcon = {
                Icon(
                    imageVector = AppVectorIcons.Search,
                    contentDescription = null
                )
            },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = AppVectorIcons.Close,
                            contentDescription = "Clear search"
                        )
                    }
                }
            } else {
                null
            },
            singleLine = true,
            shape = ExpressiveShapes.pill,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
        )

        AnimatedVisibility(
            visible = focused && query.isBlank(),
            enter = fadeIn(animationSpec = ExpressiveMotion.FastEffects) +
                expandVertically(animationSpec = ExpressiveMotion.FastSpatialSize),
            exit = fadeOut(animationSpec = ExpressiveMotion.FastEffects) +
                shrinkVertically(animationSpec = ExpressiveMotion.FastSpatialSize)
        ) {
            Column {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Suggested searches",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SettingsSearch.emptyStateQueries().forEach { suggestion ->
                        FilterChip(
                            selected = false,
                            onClick = { onQueryChange(suggestion) },
                            label = { Text(suggestion) },
                            modifier = Modifier.testTag(
                                "settings_search_chip_${suggestion.lowercase(Locale.ROOT).replace(' ', '_')}"
                            )
                        )
                    }
                }
            }
        }
    }
}

/** The ranked result shelf shown in place of the normal Settings rows. */
@Composable
internal fun SettingsSearchResults(
    query: String,
    onQueryChange: (String) -> Unit,
    onOpen: (SettingsSearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val results = remember(query) { SettingsSearch.search(query) }
    val focusManager = LocalFocusManager.current
    val highlight = highlightStyle()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        if (results.isEmpty()) {
            SettingsSearchEmpty(
                query = query,
                onQueryChange = onQueryChange
            )
            return@Column
        }

        val fuzzy = results.any { it.fuzzy }
        val bestCount = if (fuzzy) minOf(5, results.size) else minOf(4, results.size)
        val best = results.take(bestCount)
        val more = results.drop(bestCount)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp)
                .animateContentSize(animationSpec = ExpressiveMotion.FastSpatialSize),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (fuzzy) "Closest matches" else "Search results",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (fuzzy) "Try a different word for an exact match" else "Matched settings and individual controls",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = ExpressiveShapes.pill,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = results.size.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
        SettingsHubGroup(
            entries = best.map { result ->
                searchEntry(result, query, highlight) {
                    focusManager.clearFocus()
                    onOpen(result)
                }
            }
        )

        if (more.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            SettingsSectionLabel(text = "More settings", compact = true)
            SettingsHubGroup(
                entries = more.map { result ->
                    searchEntry(result, query, highlight) {
                        focusManager.clearFocus()
                        onOpen(result)
                    }
                }
            )
        }

        Text(
            text = if (results.size == 1) "1 setting found" else "${results.size} settings found",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )
    }
}

@Composable
private fun highlightStyle(): SpanStyle {
    val primary = MaterialTheme.colorScheme.primary
    return SpanStyle(
        color = primary,
        background = primary.copy(alpha = 0.16f),
        fontWeight = FontWeight.SemiBold
    )
}

private fun searchEntry(
    result: SettingsSearchResult,
    query: String,
    highlight: SpanStyle,
    onClick: () -> Unit
): SettingsHubEntry = SettingsHubEntry(
    icon = result.icon,
    title = result.title,
    subtitle = result.subtitle,
    testTag = "settings_search_result_${result.id}",
    onClick = onClick,
    eyebrow = result.eyebrow,
    titleHighlight = highlighted(result.title, query, highlight),
    subtitleHighlight = highlighted(result.subtitle, query, highlight)
)

private fun highlighted(
    text: String,
    query: String,
    style: SpanStyle
): AnnotatedString {
    val ranges = SettingsSearch.highlightRanges(text, query)
    if (ranges.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        ranges.forEach { range ->
            addStyle(style, range.first, range.last + 1)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsSearchEmpty(
    query: String,
    onQueryChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 34.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = AppVectorIcons.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "No settings found for “$query”",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Try a different word, or one of these searches.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SettingsSearch.emptyStateQueries().forEach { suggestion ->
                FilterChip(
                    selected = false,
                    onClick = { onQueryChange(suggestion) },
                    label = { Text(suggestion) }
                )
            }
        }
    }
}
