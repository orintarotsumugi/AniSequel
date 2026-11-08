package com.anisequel.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.anisequel.data.network.GifResult
import com.anisequel.domain.gif.GifProvider
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.ShimmerBox
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import com.anisequel.ui.components.rememberShimmerPhase
import com.anisequel.ui.viewmodel.GifSearchStore

/**
 * The GIF browser, as the composer's third pane.
 *
 * ## What it is for
 *
 * A comment can carry an animated GIF, and AniList shows one as the animation -
 * this app's own parser already turns a bare `.gif` line and an `img(url)` into a
 * moving image, and Coil decodes it. What has been missing is a way to *get* one on
 * a phone: the composer could only paste an address, which meant a second app, a
 * copy and a walk out of the conversation. This pane is the search and the tap, and
 * nothing else - choosing a GIF hands its URL to the draft, which the reader still
 * sees in Preview before posting.
 *
 * ## Why it is a pane rather than a sheet over the sheet
 *
 * A second `ModalBottomSheet` stacked on the comments sheet has its own scrim, its
 * own back handling and its own half-expanded state, and all three fight the sheet
 * underneath it. The composer already swaps a full-height body between Write and
 * Preview; a third value in that swap costs nothing and cannot be dismissed into a
 * state nobody can get back from.
 *
 * ## The grid
 *
 * Adaptive square cells, animated by Coil's decoder. A tile draws
 * [GifResult.gridUrl] - the provider's small copy - and the comment gets the full file
 * at [GifResult.embedUrl]. Which copy is "small" is decided by the provider rather
 * than assumed: on Safebooru most animated posts have no smaller file at all - the
 * response hands back `sample_url` equal to `file_url` - so the tile falls back to the
 * thumbnail still rather than the original, and the grid never holds more animation
 * than a 104dp cell can decode. See `booruTileUrl`.
 */
@Composable
internal fun ColumnScope.GifBrowser(
    store: GifSearchStore,
    /** The resolved index the store is searching - the source of the pane's chips. */
    provider: GifProvider,
    onUse: (String) -> Unit
) {
    val state by store.state.collectAsStateWithLifecycle()
    // A local, because a smart cast onto `state.error` is not possible: `state` is
    // a delegated property, so the compiler will not promise it stays non-null
    // between the check and the use.
    val errorMessage = state.error
    val recent by store.recent.collectAsStateWithLifecycle()
    val recentQueries by store.recentQueries.collectAsStateWithLifecycle()

    GifSearchField(state = state, store = store)
    if (state.query.isBlank()) {
        GifBlankQueryChips(provider = provider, recentQueries = recentQueries) { tag ->
            store.setQuery(tag)
            store.searchNow()
        }
    }
    GifBrowserBody(state = state, errorMessage = errorMessage, recent = recent, store = store, onUse = onUse)
}

@Composable
private fun GifSearchField(state: GifSearchStore.State, store: GifSearchStore) {
    OutlinedTextField(
        // The `String` overload, not a rebuilt `TextFieldValue`. Wrapping the store's
        // query in `TextFieldValue(state.query)` puts the caret at index 0 on every
        // recomposition, so the second keystroke was inserted *before* the first:
        // typing "cat" into the search box produced "tac", and the search the reader
        // watched was not the word they typed. The selection is the field's own
        // business as long as the value it reports is the value it is given.
        value = state.query,
        onValueChange = { store.setQuery(it) },
        placeholder = {
            Text(
                store.substitutionNote
                    ?: if (store.providerName.isBlank()) "Search GIFs"
                    else "Search ${store.providerName} for a GIF"
            )
        },
        singleLine = true,
        leadingIcon = {
            Icon(
                imageVector = AppVectorIcons.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            // A way back to the recent picks without selecting the query and
            // deleting it on a phone keyboard. It resets the store rather than
            // emptying the field alone, so the grid the reader was looking at goes
            // with it and no request for the cleared word can land afterwards.
            if (state.query.isNotBlank()) {
                IconButton(
                    onClick = { store.reset() },
                    modifier = Modifier.testTag("comment_gif_clear")
                ) {
                    Icon(
                        imageVector = AppVectorIcons.Close,
                        contentDescription = "Clear the GIF search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { store.searchNow() }),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .testTag("comment_gif_field")
    )
}

@Composable
private fun GifBlankQueryChips(
    provider: GifProvider,
    recentQueries: List<String>,
    onPick: (String) -> Unit
) {
    // The reader's own answered searches come first: the word they are most
    // likely to want again is one they already typed, and these chips are known
    // to answer - the store records only searches that returned tiles.
    if (recentQueries.isNotEmpty()) {
        GifSuggestionRow(
            caption = "Recent searches",
            suggestions = recentQueries,
            tagTestPrefix = "comment_gif_recent_query",
            onPick = onPick
        )
    }
    GifSuggestionRow(
        caption = "Reactions",
        suggestions = reactionsFor(provider),
        tagTestPrefix = "comment_gif_suggestion",
        onPick = onPick
    )
    // Pairing is gifukai's own filter, so the phrases are offered only there.
    if (provider == GifProvider.GIFUKAI) {
        GifSuggestionRow(
            caption = "Pairings",
            suggestions = GIFUKAI_PAIRINGS,
            tagTestPrefix = "comment_gif_pairing",
            onPick = onPick
        )
    }
    when (provider) {
        // Series tags are booru tags; only Safebooru carries them.
        GifProvider.SAFEBOORU -> GifSuggestionRow(
            caption = "Series",
            suggestions = GIF_SERIES,
            tagTestPrefix = "comment_gif_series",
            onPick = onPick
        )
        // nekos.best carries reaction categories and nothing else, so a name row
        // would be chips that all open an empty grid.
        GifProvider.NEKOS_BEST -> Unit
        // Free-text catalogues answer a name, and Gifukai reaches one through the
        // Klipy hand-off. The note on the answer says which index gave it.
        else -> {
            GifSuggestionRow(
                caption = "Anime",
                suggestions = GIF_ANIME,
                tagTestPrefix = "comment_gif_anime",
                onPick = onPick
            )
            GifSuggestionRow(
                caption = "Characters",
                suggestions = GIF_CHARACTERS,
                tagTestPrefix = "comment_gif_character",
                onPick = onPick
            )
        }
    }
}

@Composable
private fun ColumnScope.GifBrowserBody(
    state: GifSearchStore.State,
    errorMessage: String?,
    recent: List<GifResult>,
    store: GifSearchStore,
    onUse: (String) -> Unit
) {
    when {
        errorMessage != null && state.results.isEmpty() -> GifMessage(
            icon = AppVectorIcons.Warning,
            text = errorMessage,
            action = if (state.query.isNotBlank()) store::searchNow else null
        )

        state.isSearching && state.results.isEmpty() -> GifSkeleton(Modifier.weight(1f))

        state.query.isBlank() && recent.isNotEmpty() -> Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Picked this session",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp)
            )
            RecentGifGrid(
                gifs = recent,
                onPick = { gif ->
                    store.rememberUsed(gif)
                    onUse(gif.embedUrl)
                }
            )
        }

        state.results.isEmpty() -> GifMessage(
            icon = if (state.query.isBlank()) AppVectorIcons.AnimatedGif else AppVectorIcons.Search,
            text = emptyStateNote(state, errorMessage),
            action = if (errorMessage != null) store::searchNow else null,
            modifier = Modifier.weight(1f)
        )

        else -> {
            // A hand-off answer says which index gave it. The placeholder still
            // names the chosen one, and a substitution that is not said out loud
            // is the reader searching one catalogue and getting another's tiles.
            state.note?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                )
            }
            // Shuffle is for random-draw answers. A hand-off grid carries a note
            // and is ranked, not drawn - re-asking would return the same page.
            if (store.rerollable && state.next == null && state.note == null) {
                GifShuffleRow(onShuffle = store::reroll)
            }
            GifResultsGrid(store = store, state = state, onUse = onUse)
        }
    }
}

/**
 * The results grid, its own scroll state, and the paging that belongs to it.
 *
 * Split out of [GifBrowser] because the pane's job is choosing which of five
 * states to draw - an error, a waiting skeleton, the recent strip, an empty index,
 * or results - and none of that is the grid's business. Everything here only
 * matters once there are tiles: the state the reader is scrolling, the end-of-list
 * test that asks for the next page, the spinner that says one is coming, and the
 * line that says it failed.
 */
@Composable
private fun ColumnScope.GifResultsGrid(
    store: GifSearchStore,
    state: GifSearchStore.State,
    onUse: (String) -> Unit
) {
    val gridState = rememberLazyGridState()
    // A new word starts at the top of its own results. The grid state outlives the
    // query - the pane keeps it while the reader refines the word - and keeping the
    // scroll *offset* too left a fresh search parked past its first rows, so the
    // grid looked empty above the reader. Paging does not touch this: only a new
    // query resets it, or loading the next page would jump back to the top.
    LaunchedEffect(state.query) {
        gridState.scrollToItem(0)
    }
    val nearEnd by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= gridState.layoutInfo.totalItemsCount - 6
        }
    }
    // Asked for when the last rows come into view, not when they are drawn: a page
    // that is fetched the moment it is *needed* is the difference between a picker
    // and a rate-limit notice, and `next` is the provider's own "there is more".
    LaunchedEffect(nearEnd, state.next, state.isLoadingMore) {
        if (nearEnd && state.next != null && !state.isLoadingMore) store.loadMore()
    }

    val staleAlpha by animateFloatAsState(
        targetValue = if (state.isSearching) 0.45f else 1f,
        animationSpec = ExpressiveMotion.FastEffects,
        label = "gifGridStale"
    )
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        state = gridState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            // A new query over an old grid kept the old GIFs at full strength until
            // the answer replaced them, so the grid looked like the answer. Dimmed
            // in the draw phase: alpha is not read by layout.
            .graphicsLayer { alpha = staleAlpha }
            .testTag("comment_gif_grid")
    ) {
        items(state.results, key = { it.id }) { gif ->
            GifTile(
                modifier = Modifier.animateItem(
                    placementSpec = ExpressiveMotion.DefaultSpatialOffset,
                    fadeInSpec = ExpressiveMotion.ListItemFadeIn,
                    fadeOutSpec = ExpressiveMotion.ListItemFadeOut
                ),
                gif = gif,
                onUse = {
                    // Recorded here rather than by whoever receives the URL, because
                    // this is the last place the tile's dimensions are known: the
                    // strip that shows the pick back has to draw the same small copy
                    // the grid drew, not the full animation.
                    store.rememberUsed(gif)
                    onUse(gif.embedUrl)
                }
            )
        }
        if (state.isLoadingMore) {
            // Its own item, so the spinner that says "more coming" does not shift
            // the tiles that are already on screen.
            item(key = "gif_loading_more") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ExpressiveLoadingIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
    // A page that failed to arrive leaves the ones already drawn readable, and the
    // line says so rather than replacing the grid with an error state. The retry is
    // a tap on purpose: the store refuses to re-ask a failed page on its own, because
    // the end-of-list trigger above would otherwise re-fire it the instant it failed
    // for as long as the reader stays parked at the bottom.
    state.error?.let {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = store::retryPage,
                modifier = Modifier.testTag("comment_gif_page_retry")
            ) {
                Text("Try again")
            }
        }
    }
}

/**
 * The sentence under the "nothing here" icon.
 *
 * A blank box is not a search that found nothing - it is a box nobody has typed in
 * yet - and drawing "Nothing found." on the pane's first frame read as a provider
 * failure. The note the store already carries wins over both.
 */
private fun emptyStateNote(state: GifSearchStore.State, error: String?): String =
    state.note ?: error ?: if (state.query.isBlank()) {
        GifSearchStore.BLANK_QUERY_NOTE
    } else {
        "Nothing found."
    }

/**
 * One labelled row of searchable tags.
 *
 * The caption is not decoration: it tells the reader which of the two ways to use
 * a booru they are being handed - a feeling (`crying`) or a title (`sousou no frieren`) -
 * and a chip's own label is the tag the index is asked for, shown with its
 * underscores kept. A reader who learns the spelling here will type it next time.
 */
@Composable
private fun GifSuggestionRow(
    caption: String,
    suggestions: List<String>,
    tagTestPrefix: String,
    onPick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp)
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            suggestions.forEach { tag ->
                // One interaction source for the spring and the click. `bouncyPress`
                // without one installs its own fallback pointer loop on the same
                // element `clickable` is driving, and the two compete for the tap -
                // the failure `SpoilerChip` documents, and the reason the picker's
                // own tiles pass a source while these chips used to not.
                val interactionSource = remember(tag) { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .bouncyPress(interactionSource = interactionSource)
                        .clip(ExpressiveShapes.pill)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClickLabel = "Search for $tag"
                        ) { onPick(tag) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("${tagTestPrefix}_$tag")
                ) {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * The random-draw provider's second sentence: "shuffle for a fresh set".
 *
 * A filled text button rather than a bare icon so the row reads as a sentence
 * about the grid above it; the icon is the affordance, the words are the meaning.
 * Named with a test tag so the store's reroll contract (cache evicted, fresh ask)
 * has a UI pin as well as a store-side one.
 */
@Composable
private fun GifShuffleRow(onShuffle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(
            onClick = onShuffle,
            modifier = Modifier.testTag("comment_gif_shuffle")
        ) {
            Icon(
                imageVector = AppVectorIcons.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Shuffle for a fresh set")
        }
    }
}

/** One animated result, tapped to put it in the comment. */
@Composable
private fun GifTile(
    gif: GifResult,
    onUse: () -> Unit,
    /**
     * The grid's item modifier, so a page of results arriving slides rather than
     * replaces the page before it. `animateItem` has to sit on the item root.
     */
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            // The tile had `indication = null` and nothing in its place, so a tap
            // gave no feedback at all until the sheet closed under it.
            .bouncyPress(pressedScale = 0.92f, interactionSource = interactionSource)
            // The theme's small slot. This was `percent = 12`, which scales with the
            // tile and so disagreed with the skeleton drawn in its place; both are
            // on the same corner token now.
            .clip(ExpressiveShapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = "Add this GIF to the comment",
                onClick = onUse
            )
            .testTag("comment_gif_tile")
    ) {
        AsyncImage(
            model = gif.gridUrl,
            contentDescription = gif.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** The waiting shape: the grid the results will fill, at the sizes they will have. */
@Composable
private fun GifSkeleton(modifier: Modifier = Modifier) {
    // One driver for the whole grid, and a `ShimmerBox` per tile. The phase used to be
    // read here while building a `Color`, which is a *composition* read - a full
    // recomposition of the grid every frame of the shimmer, exactly what
    // [com.anisequel.ui.components.ShimmerBox] exists to avoid. The tiles are therefore
    // phase-locked now rather than offset from each other: one sweep across the waiting
    // grid instead of nine unrelated ones, which is what the shared clock is for.
    val phase = rememberShimmerPhase()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        userScrollEnabled = false,
        modifier = modifier.fillMaxWidth()
    ) {
        items(SKELETON_TILES) {
            ShimmerBox(
                modifier = Modifier.aspectRatio(1f),
                // The tile's own corner, so a waiting cell and the picture that
                // replaces it are the same shape.
                shape = ExpressiveShapes.small,
                phase = phase
            )
        }
    }
}

/** A sentence the browser says instead of a grid, with a retry when one helps. */
@Composable
private fun GifMessage(icon: ImageVector, text: String, action: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (action != null) {
            TextButton(onClick = action, modifier = Modifier.testTag("comment_gif_retry")) {
                Text("Try again")
            }
        }
    }
}

private const val SKELETON_TILES = 9

/**
 * The picks this sheet has already used, in a grid.
 *
 * The reference picker does this and it is the single most useful thing in a GIF
 * picker: the reaction you want is usually one you used earlier in the same
 * conversation. It is session-scoped - see [GifSearchStore.rememberUsed] - so
 * nothing here remembers an image into another session without being told to.
 *
 * Each row is the [GifResult] that was picked, so the tile draws the same small copy
 * the search grid drew. Rebuilding it from the embed URL alone - which is what this
 * did - handed [GifTile] a full-resolution animation with no dimensions to judge it
 * by, and twelve of those in one grid is the crash the provider's own tile budget
 * exists to prevent.
 */
@Composable
private fun ColumnScope.RecentGifGrid(
    gifs: List<GifResult>,
    onPick: (GifResult) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .testTag("comment_gif_recent")
    ) {
        // Keyed on the embed: the strip is deduplicated by it as it is recorded, so
        // one key cannot appear twice, and a repeated key in a lazy grid is the
        // exception that closes the sheet.
        items(gifs, key = { it.embedUrl }) { gif ->
            GifTile(
                modifier = Modifier.animateItem(
                    placementSpec = ExpressiveMotion.DefaultSpatialOffset,
                    fadeInSpec = ExpressiveMotion.ListItemFadeIn,
                    fadeOutSpec = ExpressiveMotion.ListItemFadeOut
                ),
                gif = gif,
                onUse = { onPick(gif) }
            )
        }
    }
}
