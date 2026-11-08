package com.anisequel.ui.screens.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisequel.data.extensions.PlaybackContext
import com.anisequel.data.extensions.PlaybackQueue
import com.anisequel.data.extensions.SourceEpisode
import com.anisequel.data.extensions.SourceServer
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.rememberFullHeightSheetState
import com.anisequel.ui.viewmodel.WatchViewModel

/**
 * The anime Watch page.
 *
 * It is the full-page counterpart to the detail sheet: the AniList title at the
 * top, the chosen source and its matched title under it, then the episodes and a
 * pinned Watch bar. The layout follows the reference screenshots, but every
 * control does something in this beta - the source sheet lists the sources the
 * app can load, "Wrong Title?" opens a real search against the selected source,
 * the server sheet lists the files the source reported, and the Watch bar writes
 * a resume point the player reads.
 *
 * The third-party APK runtime is Phase 2. Installed Aniyomi/CloudStream sources
 * are still listed in the source sheet with their state, so the install is visible
 * rather than hidden; only the native source is selectable until the runtime
 * lands.
 */
@Composable
fun WatchScreen(
    viewModel: WatchViewModel,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onOpenAniList: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val media = viewModel.media
    val progressMap by viewModel.watchPreferences.progress.collectAsStateWithLifecycle()
    val watchlist by viewModel.watchPreferences.watchlist.collectAsStateWithLifecycle()
    val layout by viewModel.watchPreferences.episodeLayout.collectAsStateWithLifecycle()
    val artwork by viewModel.watchPreferences.showArtwork.collectAsStateWithLifecycle()
    val preferredServer by viewModel.watchPreferences.preferredServer.collectAsStateWithLifecycle()
    val resume = progressMap[media.id]

    val sheetState = rememberFullHeightSheetState()
    var showSources by rememberSaveable { mutableStateOf(false) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showInfo by rememberSaveable { mutableStateOf(false) }
    var serverEpisode by remember { mutableStateOf<SourceEpisode?>(null) }
    var serverStartMs by remember { mutableStateOf(0L) }
    var rangeIndex by rememberSaveable { mutableIntStateOf(0) }
    var downloaded by rememberSaveable { mutableStateOf(false) }

    val episodes = state.episodes
    val ranges = remember(episodes) { episodeRanges(episodes) }
    LaunchedEffect(state.details?.anime?.id) {
        rangeIndex = 0
        // A range is an index into a list that is rebuilt when the source changes;
        // keeping the old index would leave the reader on "26-50" of a five-episode
        // show, with no rows under it.
    }
    val selectedRange = ranges.getOrNull(rangeIndex)?.range
    val visibleEpisodes = if (selectedRange == null) {
        episodes
    } else {
        episodes.filter { it.number in selectedRange }
    }

    val startPlayback: (SourceEpisode, SourceServer?, Long) -> Unit = { episode, server, startMs ->
        val mapping = state.mapping
        if (mapping != null) {
            PlaybackQueue.prepare(
                context = PlaybackContext(
                    mediaId = media.id,
                    mediaTitle = media.title,
                    coverUrl = state.details?.anime?.coverUrl ?: media.coverUrl,
                    sourceId = mapping.sourceId,
                    animeId = mapping.animeId,
                    episodes = episodes
                ),
                index = episodes.indexOf(episode).coerceAtLeast(0),
                server = server,
                startPositionMs = startMs
            )
            onPlay()
        }
    }

    val requestEpisode: (SourceEpisode, Long) -> Unit = { episode, startMs ->
        if (episode.servers.isEmpty()) {
            showInfo = true
        } else {
            val chosen = episode.servers.firstOrNull {
                it.name.equals(preferredServer, ignoreCase = true)
            }
            if (chosen == null && episode.servers.size > 1 && preferredServer == null) {
                serverEpisode = episode
                serverStartMs = startMs
            } else {
                startPlayback(episode, chosen ?: episode.servers.first(), startMs)
            }
        }
    }

    val resumeEpisode = resume?.let { point -> episodes.firstOrNull { it.id == point.episodeId } }
    val targetEpisode = resumeEpisode ?: episodes.firstOrNull()
    val targetStart = if (resumeEpisode != null && resume != null && !resume.isNearlyFinished) {
        resume.positionMs
    } else {
        0L
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("watch_screen")
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val inList = media.id in watchlist
                    ListAction(
                        label = if (inList) "CURRENT" else "MY LIST",
                        icon = if (inList) AppVectorIcons.CheckCircle else AppVectorIcons.BookmarkAdd,
                        selected = inList,
                        onClick = { viewModel.watchPreferences.toggleWatchlist(media.id) },
                        modifier = Modifier.weight(1f)
                    )
                    ListAction(
                        label = "CUSTOM LIST",
                        icon = AppVectorIcons.Playlist,
                        selected = false,
                        onClick = { onOpenAniList(media.anilistUrl) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item(key = "source") {
                SourceCard(
                    source = state.selectedSource,
                    onOpenSources = { showSources = true },
                    onOpenSettings = { showSettings = true },
                    onOpenTrust = { showInfo = true }
                )
            }

            item(key = "found") {
                FoundCard(
                    mapping = state.mapping,
                    matched = state.matchedAnime,
                    isSearching = state.isBusy,
                    onWrongTitle = {
                        viewModel.setSearchQuery(state.mapping?.animeTitle ?: media.title)
                        showSearch = true
                        viewModel.search()
                    }
                )
            }

            item(key = "tabs") {
                WatchTabs(
                    downloaded = downloaded,
                    onOnline = { downloaded = false },
                    onDownloaded = { downloaded = true },
                    onSettings = { showSettings = true }
                )
            }

            if (downloaded) {
                item(key = "downloads_empty") {
                    DownloadsEmpty(onOnline = { downloaded = false })
                }
            } else if (episodes.isNotEmpty()) {
                item(key = "ranges") {
                    RangeChips(
                        options = ranges,
                        selectedIndex = rangeIndex,
                        onSelect = { rangeIndex = it }
                    )
                }
                items(visibleEpisodes, key = { "${it.id}:${it.name}" }) { episode ->
                    WatchEpisodeRow(
                        episode = episode,
                        layout = layout,
                        showArtwork = artwork,
                        coverUrl = state.details?.anime?.coverUrl ?: media.coverUrl,
                        progress = resume?.takeIf { it.episodeId == episode.id },
                        serverCount = episode.servers.size,
                        onClick = { requestEpisode(episode, 0L) }
                    )
                }
            } else if (state.isBusy) {
                item(key = "loading") { WatchLoadingCard() }
            } else {
                item(key = "no_episodes") {
                    NoEpisodesCard(
                        error = state.error,
                        onRetry = viewModel::retry,
                        onSearch = { showSearch = true }
                    )
                }
            }

            if (state.error != null && episodes.isNotEmpty()) {
                item(key = "error") {
                    InlineError(message = state.error.orEmpty(), onRetry = viewModel::retry)
                }
            }

            item(key = "footer_space") { Spacer(Modifier.height(4.dp)) }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 12.dp)
                .padding(bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            resume?.let { point ->
                ContinueWatching(
                    progress = point,
                    onClick = {
                        val episode = episodes.firstOrNull { it.id == point.episodeId }
                        if (episode != null) {
                            requestEpisode(episode, point.positionMs)
                        } else {
                            showSearch = true
                        }
                    }
                )
            }
            WatchActionBar(
                enabled = targetEpisode != null && !state.isBusy,
                label = if (resumeEpisode != null && !resume?.isNearlyFinished.orFalse()) {
                    "Resume"
                } else {
                    "Watch"
                },
                onWatch = {
                    targetEpisode?.let { requestEpisode(it, targetStart) }
                },
                onInfo = { showInfo = true },
                onComment = { onOpenAniList(media.anilistUrl) }
            )
        }
    }

    if (showSources) {
        WatchSourceSheet(
            sheetState = sheetState,
            state = state,
            onDismiss = { showSources = false },
            onSelect = { source ->
                showSources = false
                viewModel.selectSource(source)
            }
        )
    }

    if (showSearch) {
        WatchSearchSheet(
            sheetState = sheetState,
            state = state,
            onDismiss = { showSearch = false },
            onQueryChange = viewModel::setSearchQuery,
            onSearch = { viewModel.search() },
            onChoose = { anime ->
                showSearch = false
                viewModel.chooseResult(anime)
            }
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
            onLayout = viewModel.watchPreferences::setEpisodeLayout,
            onArtwork = viewModel.watchPreferences::setShowArtwork,
            onPreferredServer = viewModel.watchPreferences::setPreferredServer,
            onDismiss = { showSettings = false }
        )
    }

    serverEpisode?.let { episode ->
        WatchServerSheet(
            sheetState = sheetState,
            episode = episode,
            preferredServer = preferredServer,
            onDismiss = { serverEpisode = null },
            onChoose = { server ->
                serverEpisode = null
                startPlayback(episode, server, serverStartMs)
            }
        )
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            icon = { Icon(AppVectorIcons.Info, contentDescription = null) },
            title = { Text("About this source") },
            text = {
                Text(
                    "AniSequel does not host video. The built-in Internet Archive source " +
                        "searches public-domain and Creative Commons animation and plays the " +
                        "files Archive.org serves. Aniyomi and CloudStream extensions are " +
                        "third-party code; this beta installs and records them, but does " +
                        "not execute APK code in this release.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text("Got it") }
            }
        )
    }
}

private fun Boolean?.orFalse(): Boolean = this == true
