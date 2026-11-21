package com.anisequel.ui.components.novamarkdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.anisequel.data.model.EntityPreview
import com.anisequel.data.model.EntityPreviewKey
import com.anisequel.data.model.MediaCover
import com.anisequel.data.model.MediaCoverKey
import com.anisequel.data.repository.AniListRepository

/** Batched poster lookups for the AniList links in one document. */
interface NovaMediaCoverLoader {
    suspend fun load(keys: List<MediaCoverKey>): Map<MediaCoverKey, MediaCover>
}

/** Batched name-and-portrait lookups for character, staff and studio links. */
interface NovaEntityPreviewLoader {
    suspend fun load(keys: List<EntityPreviewKey>): Map<EntityPreviewKey, EntityPreview>
}

val LocalNovaMediaCovers = staticCompositionLocalOf<NovaMediaCoverLoader?> { null }

val LocalNovaEntityPreviews = staticCompositionLocalOf<NovaEntityPreviewLoader?> { null }

/**
 * Provides both preview loaders for every NovaMarkdown surface inside [content].
 *
 * The two are provided together because they decorate the same card list. A host
 * that provided only the media half would draw a title's poster beside a
 * character's raw `Character #127222` heading, which is a worse card than either
 * one alone.
 */
@Composable
fun ProvideNovaCovers(
    repository: AniListRepository?,
    content: @Composable () -> Unit
) {
    val media = remember(repository) {
        repository?.let { source ->
            object : NovaMediaCoverLoader {
                override suspend fun load(keys: List<MediaCoverKey>): Map<MediaCoverKey, MediaCover> =
                    source.getMediaCovers(keys).getOrNull().orEmpty()
            }
        }
    }
    val entities = remember(repository) {
        repository?.let { source ->
            object : NovaEntityPreviewLoader {
                override suspend fun load(
                    keys: List<EntityPreviewKey>
                ): Map<EntityPreviewKey, EntityPreview> =
                    source.getEntityPreviews(keys).getOrNull().orEmpty()
            }
        }
    }
    CompositionLocalProvider(
        LocalNovaMediaCovers provides media,
        LocalNovaEntityPreviews provides entities,
        content = content
    )
}

/**
 * Resolves the faces of one document's character/staff/studio cards.
 *
 * Kept across key sets for the same reason the media map is: an edit to the prose
 * around the same links must not clear the faces already on screen, and an edit
 * that adds one link asks for that link alone.
 */
@Composable
internal fun rememberNovaEntityPreviews(
    blocks: List<NovaBlock>,
    loader: NovaEntityPreviewLoader? = LocalNovaEntityPreviews.current
): Map<EntityPreviewKey, EntityPreview> {
    val keys = remember(blocks) { novaEntityPreviewKeysIn(blocks) }
    var previews by remember(loader) {
        mutableStateOf<Map<EntityPreviewKey, EntityPreview>>(emptyMap())
    }
    LaunchedEffect(keys, loader) {
        if (keys.isEmpty() || loader == null) return@LaunchedEffect
        val missing = keys.filterNot { previews.containsKey(it) }
        if (missing.isEmpty()) return@LaunchedEffect
        previews = previews + loader.load(missing)
    }
    return previews
}

/**
 * A NovaMarkdown document whose AniList link cards resolve their own posters.
 *
 * One call per document, one batched request for all of its links. The state is
 * deliberately not keyed on the key list: keying it that way cleared every poster
 * on an edit that only changed surrounding prose.
 */
@Composable
fun NovaMarkdownWithCovers(
    blocks: List<NovaBlock>,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    onOverflowChange: ((Boolean) -> Unit)? = null,
    loader: NovaMediaCoverLoader? = LocalNovaMediaCovers.current
) {
    val keys = remember(blocks) { novaMediaCoverKeysIn(blocks) }
    var covers by remember(loader) {
        mutableStateOf<Map<MediaCoverKey, MediaCover>>(emptyMap())
    }
    LaunchedEffect(keys, loader) {
        if (keys.isEmpty() || loader == null) return@LaunchedEffect
        val missing = keys.filterNot { covers.containsKey(it) }
        if (missing.isEmpty()) return@LaunchedEffect
        covers = covers + loader.load(missing)
    }

    NovaMarkdownView(
        blocks = blocks,
        modifier = modifier,
        maxLines = maxLines,
        mediaCovers = covers,
        onOverflowChange = onOverflowChange
    )
}

/** The same call for a document that has not been parsed yet. */
@Composable
fun NovaMarkdownWithCovers(
    markdown: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    onOverflowChange: ((Boolean) -> Unit)? = null,
    loader: NovaMediaCoverLoader? = LocalNovaMediaCovers.current
) {
    val blocks = remember(markdown) { NovaMarkdown.parse(markdown) }
    NovaMarkdownWithCovers(
        blocks = blocks,
        modifier = modifier,
        maxLines = maxLines,
        onOverflowChange = onOverflowChange,
        loader = loader
    )
}
