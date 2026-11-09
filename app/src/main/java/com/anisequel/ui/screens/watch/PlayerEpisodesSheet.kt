package com.anisequel.ui.screens.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.extensions.SourceEpisode
import com.anisequel.data.extensions.WatchProgress
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.bouncyPress

/**
 * The player's episode rail, matching the reference's Episodes sheet.
 *
 * It is a jump list, not a second Watch page: the queue lives in
 * `PlaybackQueue`, and picking a row moves the same player to that index so the
 * previous/next controls and the resume point keep working. The eye button hides
 * every thumbnail at once - the screenshots' privacy/bandwidth control - and is
 * remembered by the same `showArtwork` preference the Watch page uses.
 *
 * Only the current episode's progress is drawn. A row cannot know another
 * episode's resume point without a per-episode progress store, and showing a
 * guessed bar would be worse than showing none.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerEpisodesSheet(
    sheetState: SheetState,
    seriesTitle: String,
    episodes: List<SourceEpisode>,
    currentIndex: Int,
    currentProgress: WatchProgress?,
    showArtwork: Boolean,
    onArtworkChange: (Boolean) -> Unit,
    onChoose: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Episodes",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        seriesTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { onArtworkChange(!showArtwork) }) {
                    Icon(
                        if (showArtwork) AppVectorIcons.Visibility else AppVectorIcons.VisibilityOff,
                        contentDescription = if (showArtwork) {
                            "Hide episode thumbnails"
                        } else {
                            "Show episode thumbnails"
                        }
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(AppVectorIcons.Close, contentDescription = "Close episodes")
                }
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(
                modifier = Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(episodes, key = { _, episode -> episode.id }) { index, episode ->
                    PlayerEpisodeRow(
                        episode = episode,
                        current = index == currentIndex,
                        progress = currentProgress?.takeIf { it.episodeId == episode.id },
                        showArtwork = showArtwork,
                        onClick = { onChoose(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerEpisodeRow(
    episode: SourceEpisode,
    current: Boolean,
    progress: WatchProgress?,
    showArtwork: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (current) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        modifier = Modifier
            .fillMaxWidth()
            .bouncyPress()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showArtwork) {
                EpisodeThumb(episode)
                Spacer(Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    episode.name.ifBlank { "Episode ${episode.number}" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    buildString {
                        append("EP ${episode.number}")
                        episode.durationSeconds?.takeIf { it > 0 }?.let {
                            append(" \u00b7 ")
                            append(formatRuntime(it))
                        }
                        append(" \u00b7 ")
                        append(
                            if (episode.servers.size == 1) {
                                "1 server"
                            } else {
                                "${episode.servers.size} servers"
                            }
                        )
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (current && progress != null && progress.durationMs > 0L) {
                    Spacer(Modifier.height(5.dp))
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(2.dp)
                            .clip(CircleShape)
                    )
                }
            }
            if (current) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        AppVectorIcons.Play,
                        contentDescription = null,
                        modifier = Modifier.padding(7.dp).size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeThumb(episode: SourceEpisode) {
    val image = episode.thumbnailUrl
    Box(
        modifier = Modifier
            .width(108.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        MaterialTheme.colorScheme.surfaceContainerLow
                    )
                )
            )
    ) {
        if (image != null) {
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.62f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
        ) {
            Text(
                "EP ${episode.number}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
