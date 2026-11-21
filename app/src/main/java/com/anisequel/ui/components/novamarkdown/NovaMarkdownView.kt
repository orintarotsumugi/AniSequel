package com.anisequel.ui.components.novamarkdown

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anisequel.data.model.EntityPreview
import com.anisequel.data.model.EntityPreviewKey
import com.anisequel.data.model.MediaCover
import com.anisequel.data.model.MediaCoverKey
import com.anisequel.ui.components.openExternalUrlOrCopy

/**
 * Everything a nested renderer needs from the document it belongs to.
 *
 * A quote and a spoiler re-enter the block renderer, and they must use the same
 * batched poster maps and the same line clamp as the document around them. A
 * parameter on every renderer would let one of them forget; the scope is
 * provided once by [NovaMarkdownView] and read where it is needed.
 */
internal data class NovaRenderScope(
    val mediaCovers: Map<MediaCoverKey, MediaCover>,
    val entityPreviews: Map<EntityPreviewKey, EntityPreview>,
    val maxLines: Int,
    val onOverflow: ((Boolean) -> Unit)?
)

internal val LocalNovaRenderScope = compositionLocalOf {
    NovaRenderScope(emptyMap(), emptyMap(), Int.MAX_VALUE, null)
}

/** Set while drawing a blockquote, whose text AniList styles italic. */
internal val LocalNovaItalic = compositionLocalOf { false }

/**
 * Renders a parsed NovaMarkdown document.
 *
 * [maxLines] below `Int.MAX_VALUE` is the collapsed signal: whole blocks past
 * [NOVA_MARKDOWN_COLLAPSED_BLOCK_LIMIT] are dropped as well as each paragraph
 * being clamped, because a bio of six one-line paragraphs would otherwise cost a
 * collapsed card the height of an expanded one. Callers that collapse should fade
 * the bottom edge so a dropped block does not look like the end of the bio.
 */
@Composable
fun NovaMarkdownView(
    blocks: List<NovaBlock>,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    mediaCovers: Map<MediaCoverKey, MediaCover> = emptyMap(),
    onOverflowChange: ((Boolean) -> Unit)? = null
) {
    // Resolved here rather than through a default argument: a default that
    // launches a request is easy to lose when this composable is called through
    // another overload, and then every character card silently draws its fallback.
    val entityPreviews = rememberNovaEntityPreviews(blocks)
    val display = if (maxLines < Int.MAX_VALUE && blocks.size > NOVA_MARKDOWN_COLLAPSED_BLOCK_LIMIT) {
        blocks.take(NOVA_MARKDOWN_COLLAPSED_BLOCK_LIMIT)
    } else {
        blocks
    }

    CompositionLocalProvider(
        LocalNovaRenderScope provides NovaRenderScope(
            mediaCovers = mediaCovers,
            entityPreviews = entityPreviews,
            maxLines = maxLines,
            onOverflow = onOverflowChange
        )
    ) {
        Column(
            modifier = modifier.testTag("nova_markdown_view"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            display.forEach { block -> NovaBlockContent(block) }
        }
    }
}

@Composable
internal fun NovaBlockContent(block: NovaBlock) {
    when (block) {
        is NovaBlock.Heading -> NovaHeading(block)
        is NovaBlock.Paragraph -> NovaParagraph(block)
        is NovaBlock.Quote -> NovaQuote(block)
        is NovaBlock.Code -> NovaCode(block)
        is NovaBlock.BulletList -> NovaList(block)
        is NovaBlock.Table -> NovaTable(block)
        is NovaBlock.Image -> NovaImageBlock(block)
        is NovaBlock.Video -> NovaVideo(block)
        is NovaBlock.Spoiler -> NovaSpoiler(block)
        is NovaBlock.Preview -> NovaPreview(block)
        NovaBlock.Rule -> NovaRule()
    }
}

@Composable
private fun NovaHeading(heading: NovaBlock.Heading) {
    val style = when (heading.level) {
        1 -> MaterialTheme.typography.titleLarge
        2 -> MaterialTheme.typography.titleMedium
        3 -> MaterialTheme.typography.titleSmall
        else -> MaterialTheme.typography.bodyLarge
    }.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    val context = LocalContext.current
    Text(
        text = buildNovaAnnotatedString(
            heading.inlines,
            onLinkClick = { openExternalUrlOrCopy(context, it) }
        ),
        style = style,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = heading.align.toTextAlign(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun NovaParagraph(paragraph: NovaBlock.Paragraph) {
    val scope = LocalNovaRenderScope.current
    val italic = LocalNovaItalic.current
    val context = LocalContext.current
    val textAlign = paragraph.align.toTextAlign()

    // A bitmap cannot live inside an AnnotatedString, so an inline GIF uses the
    // segmented flow. A *clamped* paragraph cannot use the flow (an image has no
    // line to clip), and falls back to the single Text, where the image becomes
    // its alt text link. That is the one case the collapse rule deliberately
    // avoids: a bio with an image anywhere is never collapsed.
    if (paragraph.inlines.containsNovaImage() && scope.maxLines == Int.MAX_VALUE) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = paragraph.align.toHorizontalAlignment(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            paragraph.inlines.toNovaSegments().forEach { segment ->
                when (segment) {
                    is NovaSegment.Run -> if (segment.tokens.isNotEmpty()) {
                        Text(
                            text = buildNovaAnnotatedString(
                                segment.tokens,
                                onLinkClick = { openExternalUrlOrCopy(context, it) },
                                italic = italic
                            ),
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = textAlign,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    is NovaSegment.Picture -> NovaInlinePicture(
                        segment.image,
                        segment.targetUrl,
                        paragraph.align
                    )
                }
            }
        }
        return
    }

    Text(
        text = buildNovaAnnotatedString(
            paragraph.inlines,
            onLinkClick = { openExternalUrlOrCopy(context, it) },
            italic = italic
        ),
        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = textAlign,
        maxLines = scope.maxLines,
        overflow = if (scope.maxLines < Int.MAX_VALUE) {
            TextOverflow.Ellipsis
        } else {
            TextOverflow.Clip
        },
        onTextLayout = { result ->
            if (scope.maxLines < Int.MAX_VALUE) {
                scope.onOverflow?.invoke(result.hasVisualOverflow)
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

private sealed interface NovaSegment {
    data class Run(val tokens: List<NovaInline>) : NovaSegment
    data class Picture(
        val image: NovaInline.Image,
        val targetUrl: String?
    ) : NovaSegment
}

/**
 * Splits a paragraph into text runs and the images they contain.
 *
 * The walk descends into every container so a GIF wrapped in bold
 * (`**![banner](…gif)**`) or in a link (`[![cover](…gif)](target)`) is drawn as a
 * picture rather than printed as its alt text. A container that held only an
 * image contributes nothing else, which is why the empty run is dropped.
 */
private fun List<NovaInline>.toNovaSegments(): List<NovaSegment> {
    val segments = mutableListOf<NovaSegment>()
    val run = mutableListOf<NovaInline>()

    fun flush() {
        if (run.isNotEmpty()) {
            segments += NovaSegment.Run(run.toList())
            run.clear()
        }
    }

    fun walk(tokens: List<NovaInline>, target: String?) {
        tokens.forEach { token ->
            when {
                token is NovaInline.Image -> {
                    flush()
                    segments += NovaSegment.Picture(token, target)
                }

                // A spoiler is an inline control, not a container the segmenter may
                // open. Descending here would draw its bitmap in the clear and reveal
                // what the author hid; the annotated run keeps it behind the lid.
                token is NovaInline.Spoiler -> run += token

                token.nestedInlines().containsNovaImage() -> {
                    walk(
                        token.nestedInlines(),
                        (token as? NovaInline.Link)?.url ?: target
                    )
                }

                else -> run += token
            }
        }
    }

    walk(this, null)
    flush()
    return segments
}

internal fun NovaAlign.toTextAlign(): TextAlign = when (this) {
    NovaAlign.Start -> TextAlign.Start
    NovaAlign.Center -> TextAlign.Center
    NovaAlign.End -> TextAlign.End
    NovaAlign.Justify -> TextAlign.Justify
}

internal fun NovaAlign.toHorizontalAlignment(): Alignment.Horizontal = when (this) {
    NovaAlign.Center -> Alignment.CenterHorizontally
    NovaAlign.End -> Alignment.End
    else -> Alignment.Start
}

internal fun NovaAlign.toContentAlignment(): Alignment = when (this) {
    NovaAlign.Center -> Alignment.Center
    NovaAlign.End -> Alignment.CenterEnd
    else -> Alignment.CenterStart
}
