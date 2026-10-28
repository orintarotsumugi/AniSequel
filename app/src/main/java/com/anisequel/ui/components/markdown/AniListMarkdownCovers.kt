package com.anisequel.ui.components.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.anisequel.data.model.MediaCover
import com.anisequel.data.model.MediaCoverKey
import com.anisequel.data.repository.AniListRepository

/**
 * Resolves the cover art for the AniList links in one markdown document.
 *
 * A `fun interface` rather than a function type so a host can name it in a
 * `CompositionLocal` without every provider having to agree on the exact spelling
 * of a suspend lambda. `load` takes the whole document's keys at once, because
 * that is the shape of the request the repository already batches - one call for
 * a bio, not one per card.
 */
fun interface MediaCoverLoader {
    suspend fun load(keys: List<MediaCoverKey>): Map<MediaCoverKey, MediaCover>
}

/**
 * How markdown resolves the posters beside its AniList link cards.
 *
 * ## Why this is a `CompositionLocal` and not a parameter
 *
 * Every renderer of AniList markdown can contain a link card, and the same
 * document reaches the screen through four different hosts: a person's bio, the
 * profile's About card, a comment thread and a title's synopsis. Passing a loader
 * down each of those chains meant the same lambda threaded through a dozen
 * composables that do not otherwise care about covers - and the last time it was
 * not threaded, the person sheet's bio drew every card with the badge and no
 * poster while the profile's About card beside it drew them correctly.
 *
 * Provided once, beside the repository, so a new markdown surface gets covers by
 * rendering its document with [AniListMarkdownWithCovers] and nothing else. A
 * document with no AniList links never asks.
 *
 * Null is a real answer: a demo or a screen with no AniList connection draws the
 * badge alone, exactly as it did before covers existed.
 */
val LocalMediaCoverLoader = staticCompositionLocalOf<MediaCoverLoader?> { null }

/**
 * Provides [LocalMediaCoverLoader] for everything drawn inside [content].
 *
 * The one line a host needs for its markdown surfaces to resolve their AniList
 * link posters. The repository is asked for the whole document's keys in one call
 * (it batches and caches them), and a failure is not an error state: a card with
 * no poster is exactly what these cards looked like before covers existed.
 *
 * Null - a demo, or a host with no connection - provides no loader, which leaves
 * every card at the badge rather than pretending the request succeeded.
 */
@Composable
fun ProvideMediaCovers(
    repository: AniListRepository?,
    content: @Composable () -> Unit
) {
    val loader = remember(repository) {
        repository?.let { source ->
            MediaCoverLoader { keys -> source.getMediaCovers(keys).getOrNull().orEmpty() }
        }
    }
    CompositionLocalProvider(LocalMediaCoverLoader provides loader, content = content)
}

/**
 * AniList markdown whose link cards resolve their own covers.
 *
 * This is the one call a caller needs. It collects the media keys out of the
 * parsed document, asks the loader for them **once**, and hands the result to
 * [AniListMarkdownView] - so a bio with twelve linked titles spends one request
 * rather than twelve, and a screen that renders the same document twice reuses
 * the repository's cache instead of asking again.
 *
 * [loader] defaults to [LocalMediaCoverLoader]; the parameter exists so a test
 * or a preview can answer without a composition local.
 *
 * The fetch is keyed on the keys and the loader, not on the nodes: an edit that
 * only changes the prose around the same links must not re-request posters the
 * app already has - and, just as importantly, must not *clear* the posters that
 * are already drawn. Keying the state on `nodes` while keying the effect on the
 * keys did exactly that: the map reset to empty and the effect did not re-run,
 * because the keys were equal. A bio edited in place lost its artwork.
 *
 * A failed fetch is not an error state - a card with no poster is the
 * pre-cover rendering, and that is what a missing cover looks like. The loader
 * maps a failed request to an empty map for that reason; this does not catch
 * exceptions on its own, because a `runCatching` here would also swallow the
 * cancellation of a fetch the reader scrolled away from.
 */
@Composable
fun AniListMarkdownWithCovers(
    nodes: List<MarkdownNode>,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    onOverflowChange: ((Boolean) -> Unit)? = null,
    loader: MediaCoverLoader? = LocalMediaCoverLoader.current
) {
    val keys = remember(nodes) { mediaCoverKeysIn(nodes) }
    var covers by remember(keys, loader) {
        mutableStateOf<Map<MediaCoverKey, MediaCover>>(emptyMap())
    }
    LaunchedEffect(keys, loader) {
        if (keys.isEmpty() || loader == null) return@LaunchedEffect
        covers = loader.load(keys)
    }

    AniListMarkdownView(
        nodes = nodes,
        modifier = modifier,
        maxLines = maxLines,
        mediaCovers = covers,
        onOverflowChange = onOverflowChange
    )
}

/**
 * The same call for a document that has not been parsed yet.
 *
 * Parses once per string and delegates, so a caller holding only the bio text -
 * which is every caller that is not already walking the AST for its own reasons -
 * does not have to remember to parse before rendering.
 */
@Composable
fun AniListMarkdownWithCovers(
    markdown: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    onOverflowChange: ((Boolean) -> Unit)? = null,
    loader: MediaCoverLoader? = LocalMediaCoverLoader.current
) {
    val nodes = remember(markdown) { AniListMarkdownParser.parse(markdown) }
    AniListMarkdownWithCovers(
        nodes = nodes,
        modifier = modifier,
        maxLines = maxLines,
        onOverflowChange = onOverflowChange,
        loader = loader
    )
}
