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
import androidx.compose.runtime.LaunchedEffect
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
import com.anisequel.domain.discord.DISCORD_PROJECT_URL
import com.anisequel.domain.discord.DISCORD_TEXT_LIMIT
import com.anisequel.domain.discord.DiscordActivityKind
import com.anisequel.domain.discord.DiscordActivityType
import com.anisequel.domain.discord.DiscordJokeInterval
import com.anisequel.domain.discord.DiscordOnlineStatus
import com.anisequel.domain.discord.DiscordPhrasing
import com.anisequel.domain.discord.DiscordSettings
import com.anisequel.domain.jokes.JokeTone
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.discord.DiscordJokes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DiscordCustomizationCard(
    settings: DiscordSettings,
    scope: CoroutineScope,
    preferences: DiscordPreferences
) {
    SectionCard(
        title = "Presence customization",
        icon = AppVectorIcons.Tune,
        subtitle = "Fine-tune how your activity appears on Discord"
    ) {
        DiscordDropdownRow(
            label = "Activity type",
            value = settings.activityType.displayName,
            enabled = true
        ) { dismiss ->
            DiscordActivityType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.displayName) },
                    trailingIcon = { if (type == settings.activityType) CheckMark() },
                    onClick = {
                        scope.launch { preferences.setActivityType(type) }
                        dismiss()
                    }
                )
            }
        }

        DiscordDropdownRow(
            label = "Online status",
            value = settings.onlineStatus.displayName,
            enabled = true
        ) { dismiss ->
            DiscordOnlineStatus.entries.forEach { status ->
                DropdownMenuItem(
                    text = { Text(status.displayName) },
                    trailingIcon = { if (status == settings.onlineStatus) CheckMark() },
                    onClick = {
                        scope.launch { preferences.setOnlineStatus(status) }
                        dismiss()
                    }
                )
            }
        }

        // "Invisible" is a real choice, but Discord drops the activity with it:
        // the gateway payload sends an empty activity list so the presence is not
        // visible. Saying so here is cheaper than a reader filing the silence as a
        // broken feature.
        if (settings.onlineStatus == DiscordOnlineStatus.INVISIBLE) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Invisible keeps you offline to friends, so the activity is hidden too.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        SettingsFieldLabel("Activity name")
        Spacer(modifier = Modifier.height(4.dp))
        // Draft-backed, not straight-to-DataStore: these fields used to write on
        // every keystroke, so the value came back through the preferences flow a
        // round trip later and the caret lost characters on a fast typist. A
        // debounce writes once the reader stops, and the stored value still wins
        // when it changes from anywhere else.
        DiscordDraftField(
            value = settings.activityName,
            limit = 48,
            placeholder = "AniSequel",
            testTag = "discord_activity_name_field",
            onCommit = { value -> scope.launch { preferences.setActivityName(value) } }
        )

        Spacer(modifier = Modifier.height(8.dp))
        SettingsFieldLabel("Primary button label (title link)")
        Spacer(modifier = Modifier.height(4.dp))
        DiscordDraftField(
            value = settings.primaryButtonLabel,
            limit = 32,
            placeholder = "View on AniList",
            testTag = "discord_primary_button_field",
            onCommit = { value -> scope.launch { preferences.setPrimaryButtonLabel(value) } }
        )

        Spacer(modifier = Modifier.height(8.dp))
        SettingsFieldLabel("Secondary button label")
        Spacer(modifier = Modifier.height(4.dp))
        DiscordDraftField(
            value = settings.secondaryButtonLabel,
            limit = 32,
            placeholder = "AniSequel on GitHub",
            testTag = "discord_secondary_button_field",
            onCommit = { value ->
                scope.launch { preferences.setSecondaryButton(value, settings.secondaryButtonUrl) }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))
        SettingsFieldLabel("Secondary button URL")
        Spacer(modifier = Modifier.height(4.dp))
        DiscordDraftField(
            value = settings.secondaryButtonUrl,
            limit = 512,
            placeholder = DISCORD_PROJECT_URL,
            testTag = "discord_secondary_url_field",
            // Discord only renders an http(s) button. The composer already drops
            // anything else, so the field says so instead of the preview quietly
            // missing a button the reader thinks they configured.
            validate = { it.isBlank() || it.startsWith("https://") || it.startsWith("http://") },
            invalidMessage = "Discord needs a full http:// or https:// URL.",
            onCommit = { value ->
                scope.launch { preferences.setSecondaryButton(settings.secondaryButtonLabel, value) }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))
        // One action for the whole card. The per-activity phrasing has its own
        // reset inside the editor, and account/token are deliberately untouched:
        // this restores the card the reader is looking at, nothing else.
        TextButton(
            onClick = { scope.launch { preferences.resetCustomization() } },
            modifier = Modifier.testTag("discord_reset_customization")
        ) {
            Text("Reset presence customization")
        }
    }
}

/**
 * A preference text field that writes on a pause rather than on every keystroke.
 *
 * The value is a local draft until it is committed, so typing never waits on a
 * `DataStore` round trip. A commit that lands is indistinguishable from the stored
 * value, which is what keeps the sync effect from overwriting the caret mid-word;
 * a change made elsewhere (Reset, another screen) still flows back in.
 */
@Composable
private fun DiscordDraftField(
    value: String,
    limit: Int,
    placeholder: String,
    testTag: String,
    validate: (String) -> Boolean = { true },
    invalidMessage: String? = null,
    onCommit: (String) -> Unit
) {
    var draft by remember { mutableStateOf(value) }

    LaunchedEffect(value) {
        if (value != draft) draft = value
    }
    LaunchedEffect(draft) {
        if (draft == value) return@LaunchedEffect
        delay(450L)
        onCommit(draft)
    }

    val valid = validate(draft)
    OutlinedTextField(
        value = draft,
        onValueChange = { next -> if (next.length <= limit) draft = next },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        singleLine = true,
        placeholder = { Text(placeholder) },
        isError = !valid,
        supportingText = {
            Text(
                text = if (!valid && invalidMessage != null) {
                    invalidMessage
                } else {
                    "${draft.length}/$limit"
                }
            )
        }
    )
}

@Composable
fun DiscordPhrasingCard(
    settings: DiscordSettings,
    onEdit: (DiscordActivityKind) -> Unit
) {
    SectionCard(
        title = "Activity phrasing",
        icon = AppVectorIcons.TextBold,
        subtitle = "Customize the two lines Discord shows for each activity"
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
                    text = "Discord's first line is the details line, the second is the state line. Tap a token to insert it.",
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
                                        if (activeField == PhrasingField.DETAILS) details = next else state = next
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
                TextButton(onClick = onReset) { Text("Reset this activity to the shipped phrasing") }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(DiscordPhrasing(details.trim(), state.trim())) },
                enabled = !detailsBlank
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private enum class PhrasingField { DETAILS, STATE }

private val DiscordTokens = listOf("\$anime", "\$manga", "\$novel", "\$status", "\$episode", "\$stats", "\$joke")

private fun appendToken(current: String, token: String): String =
    if (current.isBlank()) token else "$current $token"

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
        subtitle = "Rotate custom or built-in lines in your presence"
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
                    Text(text = line, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        SwitchRow(
            title = "Show a joke",
            subtitle = "Off leaves the stats/activity lines alone.",
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
                trailingIcon = { if (settings.activeJokeTones.size == JokeTone.entries.size) CheckMark() },
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
                        if (!selected || settings.activeJokeTones.size > 1) {
                            val next = if (selected) settings.activeJokeTones - tone else settings.activeJokeTones + tone
                            scope.launch { preferences.setJokeTones(next) }
                        }
                        dismiss()
                    }
                )
            }
        }

        SwitchRow(
            title = "Put the joke on the first line",
            subtitle = "Off keeps it on the second line with more room.",
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
            TextButton(onClick = onAdd, modifier = Modifier.testTag("discord_add_joke")) { Text("Add a line") }
        }
        if (settings.customJokes.isEmpty()) {
            Text(
                text = "Custom lines are stored on this device and used in the Discord presence when jokes are enabled.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            settings.customJokes.forEach { line ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = line, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 2)
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
            TextButton(onClick = { scope.launch { preferences.clearCustomJokes() } }) { Text("Clear my lines") }
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
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(text = value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    text = "This line can appear in your Discord presence. It is stored on this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = line,
                    onValueChange = { if (it.length <= 160) line = it },
                    modifier = Modifier.fillMaxWidth().testTag("discord_custom_joke_field"),
                    placeholder = { Text("One more episode won't hurt.") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (line.isNotBlank()) onAdd(line) }),
                    supportingText = { Text("${line.length}/160") }
                )
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(line) }, enabled = line.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
