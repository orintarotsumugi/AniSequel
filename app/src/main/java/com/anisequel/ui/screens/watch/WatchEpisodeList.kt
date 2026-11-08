package com.anisequel.ui.screens.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.extensions.EpisodeLayout
import com.anisequel.data.extensions.SourceEpisode
import com.anisequel.data.extensions.WatchProgress
import com.anisequel.ui.components.AppVectorIcons
import java.util.Locale

/** One chip in the screenshot's All / 1-5 / 6-10 episode range row. */
internal data class EpisodeRangeOption(val label: String, val range: IntRange?)

/**
 * Splits the episode list into the 25-episode chunks the reference uses.
 *
 * "All" is always first even when it covers the same rows as the first chunk,
 * because it is the reset control: after tapping "26-50" on a 30-episode show,
 * the reader needs one tap back to the whole list, not an invisible second tap.
 */
internal fun episodeRanges(episodes: List<SourceEpisode>): List<EpisodeRangeOption> {
    if (episodes.isEmpty()) return emptyList()
    val options = mutableListOf(EpisodeRangeOption("All", null))
    val ordered = episodes.map { it.number }.distinct().sorted()
    if (ordered.isEmpty()) return options
    var start = ordered.first()
    while (start <= ordered.last()) {
        val end = start + 24
        options += EpisodeRangeOption("$start - $end", start..end)
        start = end + 1
    }
    return options
}

@Composable
internal fun RangeChips(
    options: List<EpisodeRangeOption>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(options) { index, option ->
            val selected = index == selectedIndex
            Surface(
                onClick = { onSelect(index) },
                shape = CircleShape,
                color = if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        option.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) {
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
internal fun WatchEpisodeRow(
    episode: SourceEpisode,
    layout: EpisodeLayout,
    showArtwork: Boolean,
    coverUrl: String?,
    progress: WatchProgress?,
    serverCount: Int,
    onClick: () -> Unit
) {
    when (layout) {
        EpisodeLayout.MINIMAL -> MinimalEpisodeRow(episode, progress, serverCount, onClick)
        EpisodeLayout.COMPACT -> CompactEpisodeRow(
            episode, showArtwork, coverUrl, progress, serverCount, onClick
        )
        EpisodeLayout.DETAILED -> DetailedEpisodeRow(
            episode, showArtwork, coverUrl, progress, serverCount, onClick
        )
    }
}

@Composable
private fun MinimalEpisodeRow(
    episode: SourceEpisode,
    progress: WatchProgress?,
    serverCount: Int,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EpisodeNumberBadge(episode.number)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    episode.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                EpisodeMeta(episode, serverCount)
                if (progress != null) {
                    Spacer(Modifier.height(5.dp))
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(2.dp)
                            .clip(CircleShape)
                    )
                }
            }
            Icon(
                AppVectorIcons.Play,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun CompactEpisodeRow(
    episode: SourceEpisode,
    showArtwork: Boolean,
    coverUrl: String?,
    progress: WatchProgress?,
    serverCount: Int,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EpisodeThumbnail(
                episode = episode,
                showArtwork = showArtwork,
                fallbackUrl = coverUrl,
                modifier = Modifier
                    .width(118.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    episode.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                EpisodeMeta(episode, serverCount)
                if (progress != null) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .clip(CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailedEpisodeRow(
    episode: SourceEpisode,
    showArtwork: Boolean,
    coverUrl: String?,
    progress: WatchProgress?,
    serverCount: Int,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 8.4f)
            ) {
                EpisodeThumbnail(
                    episode = episode,
                    showArtwork = showArtwork,
                    fallbackUrl = coverUrl,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.58f))
                            )
                        )
                )
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.58f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(4.dp)
                ) {
                    Icon(
                        AppVectorIcons.Play,
                        contentDescription = "Play episode ${episode.number}",
                        tint = Color.White,
                        modifier = Modifier.padding(12.dp).size(24.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.62f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                ) {
                    Text(
                        "EP ${episode.number}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                if (serverCount > 0) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.62f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                    ) {
                        Text(
                            if (serverCount == 1) "1 server" else "$serverCount servers",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    episode.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                EpisodeMeta(episode, serverCount)
                if (progress != null) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeThumbnail(
    episode: SourceEpisode,
    showArtwork: Boolean,
    fallbackUrl: String?,
    modifier: Modifier = Modifier
) {
    val image = if (showArtwork) episode.thumbnailUrl ?: fallbackUrl else null
    if (image != null) {
        AsyncImage(
            model = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        MaterialTheme.colorScheme.surfaceContainerLow
                    )
                )
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                AppVectorIcons.Movie,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun EpisodeNumberBadge(number: Int) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.size(38.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun EpisodeMeta(episode: SourceEpisode, serverCount: Int) {
    val parts = mutableListOf<String>()
    episode.durationSeconds?.takeIf { it > 0 }?.let { parts += formatRuntime(it) }
    if (serverCount > 0) parts += if (serverCount == 1) "1 server" else "$serverCount servers"
    if (parts.isEmpty()) return
    Text(
        parts.joinToString(" \u00b7 "),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

internal fun formatRuntime(seconds: Long): String {
    val minutes = seconds / 60
    val hours = minutes / 60
    return when {
        hours > 0 -> String.format(Locale.ENGLISH, "%dh %02dm", hours, minutes % 60)
        minutes > 0 -> "${minutes}m"
        else -> "${seconds}s"
    }
}

/**
 * The episode half of the Watch page's single scroll.
 *
 * A `LazyListScope` extension rather than a nested `LazyColumn`: the hero, the
 * source cards and the episodes are one scroll, and a second list inside the
 * first would give the reader two scrollbars and a nested-height bug. Moving
 * this out of `WatchScreen` keeps the screen's own body to wiring while the
 * range/empty/loading choice stays next to the rows it draws.
 */
internal fun LazyListScope.watchEpisodeFeed(
    downloaded: Boolean,
    episodes: List<SourceEpisode>,
    visibleEpisodes: List<SourceEpisode>,
    ranges: List<EpisodeRangeOption>,
    rangeIndex: Int,
    layout: EpisodeLayout,
    showArtwork: Boolean,
    coverUrl: String?,
    resume: WatchProgress?,
    isBusy: Boolean,
    error: String?,
    onRange: (Int) -> Unit,
    onEpisode: (SourceEpisode) -> Unit,
    onOnline: () -> Unit,
    onSearch: () -> Unit,
    onRetry: () -> Unit
) {
    if (downloaded) {
        item(key = "downloads_empty") { DownloadsEmpty(onOnline = onOnline) }
        return
    }

    if (episodes.isEmpty()) {
        if (isBusy) {
            item(key = "loading") { WatchLoadingCard() }
        } else {
            item(key = "no_episodes") {
                NoEpisodesCard(error = error, onRetry = onRetry, onSearch = onSearch)
            }
        }
        return
    }

    item(key = "ranges") {
        RangeChips(
            options = ranges,
            selectedIndex = rangeIndex,
            onSelect = onRange
        )
    }
    items(visibleEpisodes, key = { "${it.id}:${it.name}" }) { episode ->
        WatchEpisodeRow(
            episode = episode,
            layout = layout,
            showArtwork = showArtwork,
            coverUrl = coverUrl,
            progress = resume?.takeIf { it.episodeId == episode.id },
            serverCount = episode.servers.size,
            onClick = { onEpisode(episode) }
        )
    }
    if (error != null) {
        item(key = "error") {
            InlineError(message = error, onRetry = onRetry)
        }
    }
}
