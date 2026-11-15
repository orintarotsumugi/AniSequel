package com.anisequel.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.anisequel.data.repository.DiscordPreferences
import com.anisequel.domain.discord.DISCORD_TEXT_LIMIT
import com.anisequel.domain.discord.DiscordActivityKind
import com.anisequel.domain.discord.DiscordJokeInterval
import com.anisequel.domain.discord.DiscordPhrasing
import com.anisequel.domain.discord.DiscordSettings
import com.anisequel.domain.jokes.JokeTone
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.discord.DiscordJokes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The phrasing list, one row per activity.
 *
 * The row shows the template, not a resolved sentence: "$anime" is the thing the
 * reader is learning to write, and replacing it with a sample title on the index
 * would hide the only editable part.
 */
@Composable
fun DiscordPhrasingCard(
    settings: DiscordSettings,
    onEdit: (DiscordActivityKind) -> Unit
) {
    SectionCard(
        title = "Activity phrasing",
        icon = AppVectorIcons.TextBold,
        subtitle = "The two lines Discord shows, per activity"
    ) {
        Column {
            DiscordActivityKind.phrasingOrder.forEachIndexed { index, kind ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
                val phrasing = settings.phrasingFor(kind)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onEdit(kind) }
                        .padding(vertical = 10.dp)
                        .testTag("discord_phrasing_${kind.storageValue}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = kind.icon(),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = kind.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = phrasing.details.ifBlank { "No first line" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun DiscordActivityKind.icon() = when (this) {
    DiscordActivityKind.WATCHING_ANIME -> AppVectorIcons.Movie
    DiscordActivityKind.VIEWING_ANIME_DETAILS -> AppVectorIcons.Info
    DiscordActivityKind.READING_MANGA -> AppVectorIcons.SourceBook
    DiscordActivityKind.VIEWING_MANGA_DETAILS -> AppVectorIcons.Info
    DiscordActivityKind.READING_NOVEL -> AppVectorIcons.SourceBook
    DiscordActivityKind.VIEWING_NOVEL_DETAILS -> AppVectorIcons.Info
    DiscordActivityKind.BROWSING -> AppVectorIcons.Search
}

/** The editor behind one row. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscordPhrasingDialog(
    kind: DiscordActivityKind,
    phrasing: DiscordPhrasing,
    onDismiss: () -> Unit,
    onSave: (DiscordPhrasing) -> Unit,
    onReset: () -> Unit
) {
    var details by remember { mutableStateOf(phrasing.details) }
    var state by remember { mutableStateOf(phrasing.state) }
    var activeField by remember { mutableStateOf(PhrasingField.DETAILS) }
    val detailsBlank = details.isBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${kind.displayName} phrasing") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Discord's first line is the details line, the second is the " +
                        "state line. Tap a token to add it to the field you are editing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingsFieldLabel("First line · details")
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = details,
                    onValueChange = { if (it.length <= DISCORD_TEXT_LIMIT) details = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { if (it.isFocused) activeField = PhrasingField.DETAILS }
                        .testTag("discord_details_field"),
                    singleLine = true,
                    isError = detailsBlank,
                    supportingText = {
                        Text(if (detailsBlank) "A first line is required." else "${details.length}/$DISCORD_TEXT_LIMIT")
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsFieldLabel("Second line · state")
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = state,
                    onValueChange = { if (it.length <= DISCORD_TEXT_LIMIT) state = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { if (it.isFocused) activeField = PhrasingField.STATE }
                        .testTag("discord_state_field"),
                    singleLine = true,
                    supportingText = { Text("${state.length}/$DISCORD_TEXT_LIMIT") }
                )
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DiscordTokens.forEach { token ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    val target = if (activeField == PhrasingField.DETAILS) details else state
                                    val next = appendToken(target, token)
                                    if (next.length <= DISCORD_TEXT_LIMIT) {
                                        if (activeField == PhrasingField.DETAILS) details = next
                                        else state = next
                                    }
                                }
                        ) {
                            Text(
                                text = token,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = onReset) {
                    Text("Reset this activity to the shipped phrasing")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(DiscordPhrasing(details.trim(), state.trim())) },
                enabled = !detailsBlank
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private enum class PhrasingField { DETAILS, STATE }

private val DiscordTokens = listOf(
    "\$anime",
    "\$manga",
    "\$novel",
    "\$status",
    "\$episode",
    "\$stats",
    "\$joke"
)

private fun appendToken(current: String, token: String): String =
    if (current.isBlank()) token else "$current $token"

/**
 * The jokes switch, the rotation, the tone filter and the reader's own lines.
 *
 * The interval and the tone filter are real dropdowns, because both are lists the
 * reader compares against each other; the card that replaced them with a cycling
 * button made "every 15 minutes" a thing to tap through rather than choose.
 */
@Composable
fun DiscordJokesCard(
    settings: DiscordSettings,
    currentJoke: String?,
    scope: CoroutineScope,
    preferences: DiscordPreferences,
    onAdd: () -> Unit
) {
    SectionCard(
        title = "Jokes in the presence",
        icon = AppVectorIcons.AnimeSparkle,
        subtitle = "Ani-chan, on a timer, in somebody else's friends list"
    ) {
        currentJoke?.takeIf { it.isNotBlank() }?.let { line ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "NOW QUOTING",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        SwitchRow(
            title = "Show a joke",
            subtitle = "Off leaves the stats line alone.",
            checked = settings.showJokes,
            onCheckedChange = { scope.launch { preferences.setShowJokes(it) } }
        )

        DiscordDropdownRow(
            label = "Refresh the line",
            value = settings.jokeInterval.displayName,
            enabled = settings.showJokes
        ) { dismiss ->
            DiscordJokeInterval.entries.forEach { interval ->
                DropdownMenuItem(
                    text = { Text(interval.displayName) },
                    trailingIcon = { if (interval == settings.jokeInterval) CheckMark() },
                    onClick = {
                        scope.launch { preferences.setJokeInterval(interval) }
                        dismiss()
                    }
                )
            }
        }

        DiscordDropdownRow(
            label = "Tones",
            value = DiscordJokes.toneSummary(settings.activeJokeTones),
            enabled = settings.showJokes
        ) { dismiss ->
            DropdownMenuItem(
                text = { Text("Mixed (every tone)") },
                trailingIcon = {
                    if (settings.activeJokeTones.size == JokeTone.entries.size) CheckMark()
                },
                onClick = {
                    scope.launch { preferences.setJokeTones(JokeTone.entries.toSet()) }
                    dismiss()
                }
            )
            HorizontalDivider()
            JokeTone.entries.forEach { tone ->
                val selected = tone in settings.activeJokeTones
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(tone.displayName)
                            Text(
                                text = tone.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    trailingIcon = { if (selected) CheckMark() },
                    onClick = {
                        // The last tone cannot be switched off: an empty set is
                        // read as "everything" everywhere else, so allowing it
                        // would make the switch look broken rather than broad.
                        if (!selected || settings.activeJokeTones.size > 1) {
                            val next = if (selected) {
                                settings.activeJokeTones - tone
                            } else {
                                settings.activeJokeTones + tone
                            }
                            scope.launch { preferences.setJokeTones(next) }
                        }
                        dismiss()
                    }
                )
            }
        }

        SwitchRow(
            title = "Put the joke on the first line",
            subtitle = "Off keeps it under the stats, where a long joke has room.",
            checked = settings.jokeInDetails,
            enabled = settings.showJokes,
            onCheckedChange = { scope.launch { preferences.setJokeInDetails(it) } }
        )

        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Your own lines",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onAdd, modifier = Modifier.testTag("discord_add_joke")) {
                Text("Add a line")
            }
        }
        if (settings.customJokes.isEmpty()) {
            Text(
                text = "The app's own pool is used until you add one. A custom line is " +
                    "kept even when its tone is switched off.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            settings.customJokes.forEach { line ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 2
                    )
                    IconButton(
                        onClick = { scope.launch { preferences.removeCustomJoke(line) } },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove this line",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            TextButton(onClick = { scope.launch { preferences.clearCustomJokes() } }) {
                Text("Clear my lines")
            }
        }
    }
}

@Composable
private fun CheckMark() {
    Icon(
        imageVector = Icons.Default.Check,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(18.dp)
    )
}

/**
 * A settings row that opens a menu.
 *
 * `DropdownMenu` anchored to the row rather than an `ExposedDropdownMenuBox`: the
 * menu is the row's action, the row already carries its label and value, and the
 * exposed variant would draw a second outlined field around a value that is not
 * editable as text.
 */
@Composable
private fun DiscordDropdownRow(
    label: String,
    value: String,
    enabled: Boolean,
    content: @Composable (dismiss: () -> Unit) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable(enabled = enabled) { open = true }
                .padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            content { open = false }
        }
    }
}

/** Add a custom line, with the same blunt length limit the composer enforces. */
@Composable
fun DiscordAddJokeDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var line by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a Discord line") },
        text = {
            Column {
                Text(
                    text = "This line can appear in your presence. It is stored on this " +
                        "phone, and it is not sent anywhere except to the relay as part " +
                        "of the card.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = line,
                    onValueChange = { if (it.length <= 160) line = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("discord_custom_joke_field"),
                    placeholder = { Text("One more episode won't hurt.") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { if (line.isNotBlank()) onAdd(line) }
                    ),
                    supportingText = { Text("${line.length}/160") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(line) },
                enabled = line.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

