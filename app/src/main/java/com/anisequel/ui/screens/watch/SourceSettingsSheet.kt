package com.anisequel.ui.screens.watch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anisequel.data.extensions.SourceSettings
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.bouncyPress

/** Which source setting is being edited in a dialog. */
private enum class SourceSettingDialog {
    TYPE,
    QUALITY,
    SERVER_TYPE,
    AUDIO_TYPE,
    EXCLUDE_AUDIO
}

/**
 * The source's own settings page, reached from the gear on the source card.
 *
 * It follows the reference's Gotaku/Sora page: the filters are rows in one
 * grouped card, a picker row opens a radio list, and the two exclusions are
 * switches. Changes are written immediately rather than behind a Confirm button,
 * because this page has no draft state to lose and the reference behaves the
 * same way.
 *
 * The page does not pretend every source reports every label. Internet Archive
 * publishes direct video files and a quality, so the quality preference can pick
 * a 1080p file; it publishes no audio language or hoster type, so those filters
 * are stored for the extension runtimes that do. The note at the bottom says
 * exactly that.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SourceSettingsSheet(
    sheetState: SheetState,
    sourceName: String,
    settings: SourceSettings,
    onChange: (SourceSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var dialog by remember { mutableStateOf<SourceSettingDialog?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            SourceSettingsHeader(
                sourceName = sourceName,
                onDismiss = onDismiss
            )
            Spacer(Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    SourceSettingRow(
                        title = "Preferred Type For Latest",
                        value = settings.preferredType,
                        onClick = { dialog = SourceSettingDialog.TYPE }
                    )
                    RowDivider()
                    SourceSettingRow(
                        title = "Preferred Quality",
                        value = settings.preferredQuality,
                        onClick = { dialog = SourceSettingDialog.QUALITY }
                    )
                    RowDivider()
                    SourceSettingRow(
                        title = "Preferred Server Type",
                        value = settings.preferredServerType,
                        onClick = { dialog = SourceSettingDialog.SERVER_TYPE }
                    )
                    RowDivider()
                    SourceSettingRow(
                        title = "Preferred Audio Type",
                        value = settings.preferredAudioType,
                        onClick = { dialog = SourceSettingDialog.AUDIO_TYPE }
                    )
                    RowDivider()
                    SourceSettingRow(
                        title = "Exclude Audio Types",
                        value = if (settings.excludedAudioTypes.isEmpty()) {
                            "None excluded"
                        } else {
                            settings.excludedAudioTypes.sorted().joinToString(", ")
                        },
                        subtitle = "Hide hosters of the selected audio types.",
                        onClick = { dialog = SourceSettingDialog.EXCLUDE_AUDIO }
                    )
                    RowDivider()
                    SwitchSettingRow(
                        title = "Show Download Hosters",
                        subtitle = "Include download links in the hoster list alongside streams.",
                        checked = settings.showDownloadHosters,
                        onCheckedChange = { enabled ->
                            onChange(settings.copy(showDownloadHosters = enabled))
                        }
                    )
                    RowDivider()
                    SwitchSettingRow(
                        title = "Hide NSFW Entries",
                        subtitle = "Filter out NSFW titles from browse and search results.",
                        checked = settings.hideNsfw,
                        onCheckedChange = { hidden ->
                            onChange(settings.copy(hideNsfw = hidden))
                        }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        AppVectorIcons.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Quality preference selects the closest file the source reports, " +
                            "and Hide NSFW filters the source list. The built-in Archive " +
                            "source reports only a file and a quality, so the preferred " +
                            "type, server-type, audio and download-hoster preferences are " +
                            "stored for the sandboxed extension runtimes rather than " +
                            "applied to this list.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    when (dialog) {
        SourceSettingDialog.TYPE -> SingleChoiceDialog(
            title = "Preferred Type For Latest",
            options = SourceSettings.TYPES,
            selected = settings.preferredType,
            onSelect = {
                onChange(settings.copy(preferredType = it))
                dialog = null
            },
            onDismiss = { dialog = null }
        )
        SourceSettingDialog.QUALITY -> SingleChoiceDialog(
            title = "Preferred Quality",
            options = SourceSettings.QUALITIES,
            selected = settings.preferredQuality,
            onSelect = {
                onChange(settings.copy(preferredQuality = it))
                dialog = null
            },
            onDismiss = { dialog = null }
        )
        SourceSettingDialog.SERVER_TYPE -> SingleChoiceDialog(
            title = "Preferred Server Type",
            options = SourceSettings.SERVER_TYPES,
            selected = settings.preferredServerType,
            onSelect = {
                onChange(settings.copy(preferredServerType = it))
                dialog = null
            },
            onDismiss = { dialog = null }
        )
        SourceSettingDialog.AUDIO_TYPE -> SingleChoiceDialog(
            title = "Preferred Audio Type",
            options = SourceSettings.AUDIO_TYPES,
            selected = settings.preferredAudioType,
            onSelect = {
                onChange(settings.copy(preferredAudioType = it))
                dialog = null
            },
            onDismiss = { dialog = null }
        )
        SourceSettingDialog.EXCLUDE_AUDIO -> ExcludeAudioDialog(
            excluded = settings.excludedAudioTypes,
            onChange = { excluded ->
                onChange(settings.copy(excludedAudioTypes = excluded))
            },
            onDismiss = { dialog = null }
        )
        null -> Unit
    }
}

@Composable
private fun SourceSettingsHeader(sourceName: String, onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            IconButton(onClick = onDismiss) {
                Icon(AppVectorIcons.Back, contentDescription = "Close source settings")
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (sourceName.isBlank()) "Source Settings" else "$sourceName Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "Filters and playback defaults for this source",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SourceSettingRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .bouncyPress()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle ?: value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(10.dp))
        Icon(
            AppVectorIcons.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SwitchSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .bouncyPress()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
    )
}

@Composable
private fun SingleChoiceDialog(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == selected,
                            onClick = { onSelect(option) }
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(option, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun ExcludeAudioDialog(
    excluded: Set<String>,
    onChange: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val options = SourceSettings.AUDIO_TYPES.filterNot {
        it.equals("Auto", ignoreCase = true)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exclude Audio Types") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                options.forEach { option ->
                    val checked = option in excluded
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onChange(
                                    if (checked) excluded - option else excluded + option
                                )
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { now ->
                                onChange(if (now) excluded + option else excluded - option)
                            }
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(option, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}
