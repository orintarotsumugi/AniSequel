package com.anisequel.ui.screens.watch

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisequel.data.extensions.PlayableSource
import com.anisequel.data.extensions.SourceEpisode
import com.anisequel.data.extensions.SourceServer
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
 * Third-party Aniyomi anime extensions run in a sandboxed process and appear as
 * ordinary sources; CloudStream/JS modules are still listed with their state but
 * not executed. The source sheet shows what the app can load rather than hiding
 * an install that did something.
 *
 * The body and the overlay host live in `WatchScreenContent.kt`. This function
 * owns only the state, the two playback decisions and the wiring between them.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val sourceSettings by viewModel.watchPreferences.sourceSettings.collectAsStateWithLifecycle()
    val resume = progressMap[media.id]

    val sheetState = rememberFullHeightSheetState()
    var showSources by rememberSaveable { mutableStateOf(false) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showSourceSettings by rememberSaveable { mutableStateOf(false) }
    var showInfo by rememberSaveable { mutableStateOf(false) }
    var serverEpisode by remember { mutableStateOf<SourceEpisode?>(null) }
    var serverStartMs by remember { mutableStateOf(0L) }
    var rangeIndex by rememberSaveable { mutableIntStateOf(0) }
    var downloaded by rememberSaveable { mutableStateOf(false) }

    val episodes = state.episodes
    val ranges = remember(episodes) { episodeRanges(episodes) }
    LaunchedEffect(state.details?.anime?.id) {
        rangeIndex = 0
        // A range is an index into a list that is rebuilt when the source
        // changes; keeping the old index would leave the reader on "26-50" of a
        // five-episode show, with no rows under it.
    }
    val selectedRange = ranges.getOrNull(rangeIndex)?.range
    val visibleEpisodes = remember(episodes, rangeIndex, selectedRange) {
        if (selectedRange == null) episodes else episodes.filter { it.number in selectedRange }
    }

    val startPlayback: (SourceEpisode, SourceServer?, Long) -> Unit = { episode, server, startMs ->
        launchWatchPlayback(
            mapping = state.mapping,
            media = media,
            coverUrl = state.details?.anime?.coverUrl ?: media.coverUrl,
            episodes = episodes,
            episode = episode,
            server = server,
            startMs = startMs,
            onPlay = onPlay
        )
    }

    val requestEpisode: (SourceEpisode, Long) -> Unit = { episode, startMs ->
        viewModel.resolveEpisode(episode) { resolved ->
            requestWatchEpisode(
                episode = resolved,
                startMs = startMs,
                preferredServer = preferredServer,
                preferredQuality = sourceSettings.preferredQuality,
                onNoServers = { showInfo = true },
                onNeedsServer = { needsServer, position ->
                    serverEpisode = needsServer
                    serverStartMs = position
                },
                onPlay = { server -> startPlayback(resolved, server, startMs) }
            )
        }
    }

    val openSearch: () -> Unit = {
        viewModel.setSearchQuery(state.mapping?.animeTitle ?: media.title)
        showSearch = true
        viewModel.search()
    }

    WatchBody(
        state = state,
        media = media,
        resume = resume,
        watchlist = watchlist,
        layout = layout,
        artwork = artwork,
        downloaded = downloaded,
        rangeIndex = rangeIndex,
        episodes = episodes,
        visibleEpisodes = visibleEpisodes,
        ranges = ranges,
        onBack = onBack,
        onToggleWatchlist = { viewModel.watchPreferences.toggleWatchlist(media.id) },
        onOpenAniList = onOpenAniList,
        onOpenSources = { showSources = true },
        onOpenSettings = { showSettings = true },
        onOpenSourceSettings = { showSourceSettings = true },
        onOpenInfo = { showInfo = true },
        onOpenSearch = openSearch,
        onDownloaded = { downloaded = it },
        onRange = { rangeIndex = it },
        onRequestEpisode = requestEpisode,
        onRetry = viewModel::retry,
        modifier = modifier.testTag("watch_screen")
    )

    WatchOverlayHost(
        state = state,
        sheetState = sheetState,
        showSources = showSources,
        showSearch = showSearch,
        showSettings = showSettings,
        showSourceSettings = showSourceSettings,
        showInfo = showInfo,
        serverEpisode = serverEpisode,
        sourceName = state.selectedSource?.name.orEmpty(),
        sourceSettings = sourceSettings,
        onSourceSettingsChange = viewModel.watchPreferences::setSourceSettings,
        preferredServer = preferredServer,
        episodes = episodes,
        layout = layout,
        artwork = artwork,
        onDismissSources = { showSources = false },
        onSelectSource = { source: PlayableSource ->
            showSources = false
            viewModel.selectSource(source)
        },
        onDismissSourceSettings = { showSourceSettings = false },
        onDismissSearch = { showSearch = false },
        onQueryChange = viewModel::setSearchQuery,
        onSearch = { viewModel.search() },
        onChoose = { anime ->
            showSearch = false
            viewModel.chooseResult(anime)
        },
        onDismissSettings = { showSettings = false },
        onLayout = viewModel.watchPreferences::setEpisodeLayout,
        onArtwork = viewModel.watchPreferences::setShowArtwork,
        onPreferredServer = viewModel.watchPreferences::setPreferredServer,
        onDismissServer = { serverEpisode = null },
        onChooseServer = { server ->
            val episode = serverEpisode
            serverEpisode = null
            if (episode != null) startPlayback(episode, server, serverStartMs)
        },
        onDismissInfo = { showInfo = false }
    )
}
