package com.anisequel.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.anisequel.data.network.GifResult
import com.anisequel.ui.components.AppVectorIcons
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
 * than assumed: on Safebooru the sample is a shrunken animation for most GIFs and a
 * JPEG still for the rest, so the tile moves when it can and shows a still when the
 * only animation on offer is a poster-sized one that would not survive twenty of them
 * on screen at once.
 */
@Composable
internal fun ColumnScope.GifBrowser(
    store: GifSearchStore,
    onUse: (String) -> Unit
) {
    val state by store.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    // A local, because a smart cast onto `state.error` is not possible: `state` is
    // a delegated property, so the compiler will not promise it stays non-null
    // between the check and the use.
    val errorMessage = state.error
    // Read once per recomposition: the strip is what an empty search shows, so a
    // reader who comes back to the picker is looking at their last picks, not at a
    // blinking "type to search".
    val recent = store.usedThisSession()

    val nearEnd by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= gridState.layoutInfo.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, state.next, state.isLoadingMore) {
        if (nearEnd && state.next != null && !state.isLoadingMore) store.loadMore()
    }

    OutlinedTextField(
        value = TextFieldValue(state.query),
        onValueChange = { store.setQuery(it.text) },
        placeholder = { Text("Search GIFs") },
        singleLine = true,
        leadingIcon = {
            Icon(
                imageVector = AppVectorIcons.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { store.searchNow() }),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .testTag("comment_gif_field")
    )

    when {
        errorMessage != null && state.results.isEmpty() -> GifMessage(
            icon = AppVectorIcons.Warning,
            text = errorMessage,
            action = if (state.query.isNotBlank()) {
                { store.searchNow() }
            } else {
                null
            }
        )

        state.isSearching && state.results.isEmpty() -> GifSkeleton(Modifier.weight(1f))

        state.query.isBlank() && recent.isNotEmpty() -> RecentGifGrid(
            urls = recent,
            onUse = onUse
        )

        state.results.isEmpty() -> GifMessage(
            icon = if (state.query.isBlank()) AppVectorIcons.AnimatedGif else AppVectorIcons.Search,
            // A blank box is not a search that found nothing: it is a box nobody
            // has typed in yet, and "Nothing found." under it read as a provider
            // failure on the pane's first frame.
            text = state.note ?: errorMessage ?: if (state.query.isBlank()) {
                GifSearchStore.BLANK_QUERY_NOTE
            } else {
                "Nothing found."
            },
            action = if (errorMessage != null) {
                { store.searchNow() }
            } else {
                null
            },
            modifier = Modifier.weight(1f)
        )

        else -> {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                state = gridState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("comment_gif_grid")
            ) {
                items(state.results, key = { it.id }) { gif ->
                    GifTile(gif = gif, onUse = { onUse(gif.embedUrl) })
                }
                if (state.isLoadingMore) {
                    // Its own item, so the spinner that says "more coming" does not
                    // shift the tiles that are already on screen.
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
            state.error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/** One animated result, tapped to put it in the comment. */
@Composable
private fun GifTile(gif: GifResult, onUse: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(percent = 12))
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
    val phase by rememberShimmerPhase()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        userScrollEnabled = false,
        modifier = modifier.fillMaxWidth()
    ) {
        items(SKELETON_TILES) { index ->
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(percent = 12))
                    // The same shimmer the comment skeleton uses, offset per tile so
                    // a waiting grid is not nine identical grey squares pulsing
                    // together.
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(
                            alpha = 0.35f + 0.35f * ((phase + index * 0.11f) % 1f)
                        )
                    )
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
 */
@Composable
private fun ColumnScope.RecentGifGrid(
    urls: List<String>,
    onUse: (String) -> Unit
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
        items(urls, key = { it }) { url ->
            GifTile(
                gif = GifResult(
                    id = url,
                    embedUrl = url,
                    gridUrl = url,
                    title = null,
                    width = null,
                    height = null
                ),
                onUse = { onUse(url) }
            )
        }
    }
}
