package com.anisequel.ui.screens.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anisequel.data.extensions.EpisodeLayout
import com.anisequel.data.extensions.PlayableSource
import com.anisequel.data.extensions.SourceAnime
import com.anisequel.data.extensions.SourceEpisode
import com.anisequel.data.extensions.SourceServer
import com.anisequel.data.extensions.SourceSettings
import com.anisequel.data.extensions.WatchProgress
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.navigation.WatchAnime
import com.anisequel.ui.viewmodel.WatchUiState

/**
 * The scrolling half of the Watch page.
 *
 * Extracted from the screen because the page's state machine (source fetch,
 * mapping, resume, four sheets) and its layout are separate reasons to change.
 * The extraction also keeps the top-level composable below the complexity
 * threshold CI checks: every one of these item lambdas used to count there.
 */
@Composable
internal fun WatchBody(
    state: WatchUiState,
    media: WatchAnime,
    resume: WatchProgress?,
    watchlist: Set<Int>,
    layout: EpisodeLayout,
    artwork: Boolean,
    downloaded: Boolean,
    rangeIndex: Int,
    episodes: List<SourceEpisode>,
    visibleEpisodes: List<SourceEpisode>,
    ranges: List<EpisodeRangeOption>,
    onBack: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onOpenAniList: (String) -> Unit,
    onOpenSources: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSourceSettings: () -> Unit,
    onOpenInfo: () -> Unit,
    onOpenSearch: () -> Unit,
    onDownloaded: (Boolean) -> Unit,
    onRange: (Int) -> Unit,
    onRequestEpisode: (SourceEpisode, Long) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val resumeEpisode = resume?.let { point -> episodes.firstOrNull { it.id == point.episodeId } }
    val targetEpisode = resumeEpisode ?: episodes.firstOrNull()
    val targetStart = if (resumeEpisode != null && resume != null && !resume.isNearlyFinished) {
        resume.positionMs
    } else {
        0L
    }
    val resumeLabel = if (resumeEpisode != null && resume?.isNearlyFinished == false) {
        "Resume"
    } else {
        "Watch"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = if (resume != null) 224.dp else 148.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "hero") {
                WatchHero(
                    title = state.details?.anime?.title ?: media.title,
                    coverUrl = state.details?.anime?.coverUrl ?: media.coverUrl,
                    bannerUrl = media.bannerUrl,
                    studio = media.studio,
                    status = media.status,
                    format = media.format,
                    year = media.year,
                    score = media.score,
                    onBack = onBack
                )
            }

            item(key = "list_actions") {
                WatchListActions(
                    inList = media.id in watchlist,
                    onToggleList = onToggleWatchlist,
                    onCustomList = { onOpenAniList(media.anilistUrl) }
                )
            }

            item(key = "source") {
                SourceCard(
                    source = state.selectedSource,
                    onOpenSources = onOpenSources,
                    onOpenSettings = onOpenSourceSettings,
                    onOpenTrust = onOpenInfo
                )
            }

            item(key = "found") {
                FoundCard(
                    mapping = state.mapping,
                    matched = state.matchedAnime,
                    isSearching = state.isBusy,
                    onWrongTitle = onOpenSearch
                )
            }

            item(key = "tabs") {
                WatchTabs(
                    downloaded = downloaded,
                    episodeCount = episodes.size,
                    onOnline = { onDownloaded(false) },
                    onDownloaded = { onDownloaded(true) },
                    onSettings = onOpenSettings
                )
            }

            watchEpisodeFeed(
                downloaded = downloaded,
                episodes = episodes,
                visibleEpisodes = visibleEpisodes,
                ranges = ranges,
                rangeIndex = rangeIndex,
                layout = layout,
                showArtwork = artwork,
                coverUrl = state.details?.anime?.coverUrl ?: media.coverUrl,
                resume = resume,
                isBusy = state.isBusy,
                resolvingEpisodeId = state.resolvingEpisodeId,
                error = state.error,
                onRange = onRange,
                onEpisode = { onRequestEpisode(it, 0L) },
                onOnline = { onDownloaded(false) },
                onSearch = onOpenSearch,
                onRetry = onRetry
            )

            item(key = "footer_space") { Spacer(Modifier.height(4.dp)) }
        }

        WatchPinnedBar(
            resume = resume,
            enabled = targetEpisode != null && !state.isBusy,
            label = resumeLabel,
            onResume = {
                resumeWatchEpisode(
                    progress = resume,
                    episodes = episodes,
                    onEpisode = onRequestEpisode,
                    onMissing = onOpenSearch
                )
            },
            onWatch = { targetEpisode?.let { onRequestEpisode(it, targetStart) } },
            onInfo = onOpenInfo,
            onComment = { onOpenAniList(media.anilistUrl) },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun WatchListActions(
    inList: Boolean,
    onToggleList: () -> Unit,
    onCustomList: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ListAction(
            label = if (inList) "CURRENT" else "MY LIST",
            icon = if (inList) AppVectorIcons.CheckCircle else AppVectorIcons.BookmarkAdd,
            selected = inList,
            onClick = onToggleList,
            modifier = Modifier.weight(1f)
        )
        ListAction(
            label = "CUSTOM LIST",
            icon = AppVectorIcons.Playlist,
            selected = false,
            onClick = onCustomList,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Every sheet and dialog the Watch page can open.
 *
 * One host owns the `if`s so the page's main composable is wiring only. The
 * sheets share one full-height state on purpose: only one can be open at a time,
 * and a second state object would let two sheets animate over each other.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WatchOverlayHost(
    state: WatchUiState,
    sheetState: SheetState,
    showSources: Boolean,
    showSearch: Boolean,
    showSettings: Boolean,
    showSourceSettings: Boolean,
    showInfo: Boolean,
    serverEpisode: SourceEpisode?,
    sourceName: String,
    sourceSettings: SourceSettings,
    onSourceSettingsChange: (SourceSettings) -> Unit,
    preferredServer: String?,
    episodes: List<SourceEpisode>,
    layout: EpisodeLayout,
    artwork: Boolean,
    onDismissSources: () -> Unit,
    onSelectSource: (PlayableSource) -> Unit,
    onDismissSourceSettings: () -> Unit,
    onDismissSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onChoose: (SourceAnime) -> Unit,
    onDismissSettings: () -> Unit,
    onLayout: (EpisodeLayout) -> Unit,
    onArtwork: (Boolean) -> Unit,
    onPreferredServer: (String?) -> Unit,
    onDismissServer: () -> Unit,
    onChooseServer: (SourceServer) -> Unit,
    onDismissInfo: () -> Unit
) {
    if (showSources) {
        WatchSourceSheet(
            sheetState = sheetState,
            state = state,
            hideNsfw = sourceSettings.hideNsfw,
            onDismiss = onDismissSources,
            onSelect = onSelectSource
        )
    }

    if (showSourceSettings) {
        SourceSettingsSheet(
            sheetState = sheetState,
            sourceName = sourceName,
            settings = sourceSettings,
            onChange = onSourceSettingsChange,
            onDismiss = onDismissSourceSettings
        )
    }

    if (showSearch) {
        WatchSearchSheet(
            sheetState = sheetState,
            state = state,
            onDismiss = onDismissSearch,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            onChoose = onChoose
        )
    }

    if (showSettings) {
        WatchSettingsSheet(
            sheetState = sheetState,
            layout = layout,
            showArtwork = artwork,
            preferredServer = preferredServer,
            serverNames = episodes.flatMap { episode -> episode.servers.map { it.name } }
                .distinct()
                .sorted(),
            onLayout = onLayout,
            onArtwork = onArtwork,
            onPreferredServer = onPreferredServer,
            onDismiss = onDismissSettings
        )
    }

    serverEpisode?.let { episode ->
        WatchServerSheet(
            sheetState = sheetState,
            episode = episode,
            preferredServer = preferredServer,
            onDismiss = onDismissServer,
            onChoose = onChooseServer
        )
    }

    if (showInfo) {
        WatchSourceInfoDialog(onDismiss = onDismissInfo)
    }
}

@Composable
private fun WatchSourceInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(AppVectorIcons.Info, contentDescription = null) },
        title = { Text("About this source") },
        text = {
            Text(
                "AniSequel does not host video. The built-in Internet Archive source " +
                    "searches public-domain and Creative Commons animation and plays the " +
                    "files Archive.org serves. Aniyomi anime extensions are third-party " +
                    "code and run in a sandboxed process with no access to your data; " +
                    "CloudStream and JS modules are installed and recorded but not run yet.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        }
    )
}
