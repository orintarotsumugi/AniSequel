package com.anisequel.ui.screens.watch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anisequel.data.extensions.SourceEpisode
import com.anisequel.data.extensions.SourceServer
import com.anisequel.ui.components.AppVectorIcons

/**
 * The server chooser from the reference player.
 *
 * It keeps the two decisions separate: which server to play now, and whether
 * that choice should become the title's remembered server. The old sheet did
 * the first and the Watch page's Settings sheet did the second, so picking a
 * server in the player never survived the next episode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerServerSheet(
    sheetState: SheetState,
    seriesTitle: String,
    episode: SourceEpisode,
    current: SourceServer?,
    onDismiss: () -> Unit,
    onChoose: (SourceServer, Boolean) -> Unit
) {
    var remember by rememberSaveable(episode.id) { mutableStateOf(true) }
    var inbuilt by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        AppVectorIcons.Play,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(10.dp).size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Quality Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Episode ${episode.number} \u00b7 $seriesTitle",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(AppVectorIcons.Close, contentDescription = "Close quality settings")
                }
            }

            Spacer(Modifier.height(12.dp))
            QualityTabs(
                inbuilt = inbuilt,
                onServers = { inbuilt = false },
                onInbuilt = { inbuilt = true }
            )
            Spacer(Modifier.height(12.dp))

            if (inbuilt) {
                InbuiltQualityCard(
                    current = current,
                    onUse = {
                        episode.servers.firstOrNull()?.let { onChoose(it, false) }
                    }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "The built-in player uses Media3 with the decoder chosen in Player Settings. " +
                        "An external hoster still supplies the file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
                Spacer(Modifier.height(10.dp))
                return@Column
            }

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { remember = !remember }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Icon(
                                AppVectorIcons.BookmarkAdd,
                                contentDescription = null,
                                modifier = Modifier.padding(9.dp).size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Remember Server",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Auto-select this quality for this anime",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Checkbox(checked = remember, onCheckedChange = { remember = it })
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 430.dp)
                    ) {
                        items(episode.servers, key = { it.id }) { server ->
                            val playing = server.id == current?.id
                            ServerRow(
                                server = server,
                                playing = playing,
                                onClick = { onChoose(server, remember) }
                            )
                            if (server != episode.servers.last()) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${episode.servers.size} " +
                    if (episode.servers.size == 1) "server" else "servers",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

@Composable
private fun QualityTabs(
    inbuilt: Boolean,
    onServers: () -> Unit,
    onInbuilt: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            QualityTab(
                label = "Servers",
                icon = AppVectorIcons.Server,
                selected = !inbuilt,
                onClick = onServers,
                modifier = Modifier.weight(1f)
            )
            QualityTab(
                label = "Inbuilt",
                icon = AppVectorIcons.HighQuality,
                selected = inbuilt,
                onClick = onInbuilt,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun QualityTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.surface
        } else {
            androidx.compose.ui.graphics.Color.Transparent
        },
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(vertical = 9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun InbuiltQualityCard(current: SourceServer?, onUse: () -> Unit) {
    Surface(
        onClick = onUse,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                AppVectorIcons.Play,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Built-in player",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    current?.let { serverDetail(it) } ?: "Uses the first file the source reports",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
            RadioButton(selected = current != null, onClick = onUse)
        }
    }
}

@Composable
private fun ServerRow(
    server: SourceServer,
    playing: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(13.dp),
            color = if (playing) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            }
        ) {
            Icon(
                AppVectorIcons.Play,
                contentDescription = null,
                modifier = Modifier.padding(9.dp).size(18.dp),
                tint = if (playing) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                serverTitle(server),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                serverDetail(server),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        RadioButton(selected = playing, onClick = onClick)
        if (playing) {
            Spacer(Modifier.width(4.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    "PLAYING",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

/** The source's own label first: it already carries subtitle, quality and size. */
internal fun serverTitle(server: SourceServer): String {
    val name = server.name.trim()
    if (name.isNotBlank() && !name.equals("server", true) && !name.equals("default", true)) {
        return name
    }
    return server.quality?.takeIf { it.isNotBlank() } ?: "Server"
}

internal fun serverDetail(server: SourceServer): String {
    val quality = server.quality?.takeIf { it.isNotBlank() }
    return when {
        quality != null -> "$quality \u00b7 Direct file"
        server.name.contains("mp4", ignoreCase = true) ||
            server.name.contains("webm", ignoreCase = true) -> "Direct video file"
        else -> "Opens in the player"
    }
}
