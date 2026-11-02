package com.anisequel.ui.components.markdown

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anisequel.ui.components.openExternalUrlOrCopy

/**
 * One paragraph of inline tokens, with any inline image or GIF embedded in the
 * flow.
 *
 * ## Why this is not one `Text`
 *
 * A `Text` can style a character but cannot draw a bitmap, so an inline image
 * cannot live inside an `AnnotatedString`. This paragraph is therefore split into
 * segments - runs of text rendered as a wrapped `Text`, and each image rendered
 * as a real `AsyncImage` - and laid out in a `Column`. That is what makes a GIF
 * posted between two sentences animate instead of printing as a link.
 *
 * ## Why the plain path is still here
 *
 * A *truncated* paragraph (a collapsed bio, a synopsis preview) asks for
 * `maxLines`, and an embedded GIF has no line to clamp. Truncation and embedding
 * are mutually exclusive, so when a limit is set this falls back to the single
 * annotated `Text` it always used, and the image degrades to a link through
 * [buildInlineAnnotatedString]. Only the expanded form, where `maxLines` is
 * unbounded, gets the flow.
 */
@Composable
fun RenderParagraph(
    paragraph: MarkdownNode.Paragraph,
    maxLines: Int,
    /**
     * Called from layout with whether this paragraph's text was clipped.
     *
     * The clamp is `maxLines`, and until this existed the only way a caller could
     * tell that anything had been cut was to guess from the character count - which
     * is wrong in both directions: at a 200% font scale a 200-character synopsis
     * overflows five lines and the guess said it did not, and on a tablet a
     * 300-character one fits and the guess offered a "Read more" that revealed
     * nothing. `hasVisualOverflow` is the layout answering the question directly.
     *
     * Only the clamped path reports. An unbounded paragraph cannot overflow, and
     * an `onTextLayout` that never fires is what keeps a caller's sticky flag from
     * being reset by the expanded render.
     */
    onOverflow: ((Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val hasImage = paragraph.inlines.containsEmbeddedImage()

    if (!hasImage || maxLines < Int.MAX_VALUE) {
        val annotated = buildInlineAnnotatedString(
            paragraph.inlines,
            onLinkClick = { openExternalUrlOrCopy(context, it) }
        )
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = if (paragraph.centered) TextAlign.Center else TextAlign.Start,
            maxLines = maxLines,
            overflow = if (maxLines < Int.MAX_VALUE) TextOverflow.Ellipsis else TextOverflow.Clip,
            // A non-null lambda even when nothing is listening. The overload for an
            // `AnnotatedString` takes `onTextLayout` as a non-null parameter, so
            // handing it a conditional that can be `null` makes Kotlin reject the
            // call outright - it resolves to the `String` overload instead and
            // reports "actual type is AnnotatedString, but String was expected".
            // The guard lives inside the lambda.
            onTextLayout = { result ->
                if (onOverflow != null && maxLines < Int.MAX_VALUE) {
                    onOverflow(result.hasVisualOverflow)
                }
            },
            modifier = if (paragraph.centered) Modifier.fillMaxWidth() else Modifier
        )
        return
    }

    val segments = remember(paragraph.inlines) { paragraph.inlines.toInlineSegments() }
    Column(
        modifier = if (paragraph.centered) Modifier.fillMaxWidth() else Modifier,
        horizontalAlignment = if (paragraph.centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        segments.forEach { segment ->
            when (segment) {
                is InlineSegment.TextRun -> if (segment.tokens.isNotEmpty()) {
                    Text(
                        text = buildInlineAnnotatedString(
                            segment.tokens,
                            onLinkClick = { openExternalUrlOrCopy(context, it) }
                        ),
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = if (paragraph.centered) TextAlign.Center else TextAlign.Start
                    )
                }

                // Reuses the block image renderer, so an inline GIF and a
                // full-line one are the same size, shape and tap target - there is
                // one image treatment in this markdown stack, not two. The width the
                // author gave *this* file travels with it, so a bio of one 100% banner
                // and two 220 strips draws one banner and two strips.
                is InlineSegment.Image -> RenderImage(
                    MarkdownNode.ImageBlock(
                        url = segment.token.url,
                        alt = segment.token.alt,
                        targetUrl = segment.targetUrl,
                        width = segment.width
                    )
                )
            }
        }
    }
}

/** A run of consecutive text tokens, or a single inline image. */
private sealed interface InlineSegment {
    data class TextRun(val tokens: List<InlineToken>) : InlineSegment

    /**
     * [targetUrl] is set when the image was written inside a link - the site's
     * `[![cover](img)](target)` - so the drawn image keeps the tap the author gave
     * it. `RenderImage` already supports it; the paragraph used to drop the link and
     * show the image's alt text instead.
     *
     * [width] is the size the author asked for, carried through to `RenderImage` so
     * an inline `<img width='220'>` is a 220 strip and not a full-column slab.
     */
    data class Image(
        val token: InlineToken.Image,
        val targetUrl: String? = null,
        val width: String? = null
    ) : InlineSegment
}

/**
 * Groups text tokens into runs so each run is one wrapped `Text` rather than a
 * `Text` per token, which would break a line at every bold word, and lifts every
 * image out of whatever it is nested in.
 *
 * ## Why the lift has to recurse
 *
 * AniList's editor writes a banner three ways: `![alt](x.gif)`,
 * `[![cover](x.gif)](https://…)`, and `**![banner](x.gif)**` - the last is what a
 * bold heading line of a bio produces. Only the first two were lifted, so a GIF
 * inside an emphasis run stayed inside the text token list and the paragraph printed
 * its alt text as a link instead of the picture.
 *
 * ## What it costs
 *
 * A container is only opened when it actually holds an image, so the ordinary
 * `**✨ [#1] [Title](url)**` bio line keeps its bold wrapper untouched. An emphasis
 * run that *does* wrap a bitmap loses the wrapper for its own words, because a
 * `Text` cannot be split around a composable in the middle of a line: the text is
 * drawn unstyled and the image is drawn at its own size, directly after it. Before
 * this, that same sentence drew no image at all.
 */
private fun List<InlineToken>.toInlineSegments(): List<InlineSegment> {
    val segments = mutableListOf<InlineSegment>()
    val run = mutableListOf<InlineToken>()

    fun flushRun() {
        if (run.isNotEmpty()) {
            segments.add(InlineSegment.TextRun(run.toList()))
            run.clear()
        }
    }

    fun emitImage(image: InlineToken.Image, targetUrl: String?) {
        // The flush happens inside, so two images in a row are two segments rather
        // than one segment each appended to a run that never emptied.
        flushRun()
        segments.add(InlineSegment.Image(image, targetUrl, image.width))
    }

    fun walk(tokens: List<InlineToken>, targetUrl: String?) {
        tokens.forEach { token ->
            when {
                token is InlineToken.Image -> emitImage(token, targetUrl)
                token.nestedTokens().containsEmbeddedImage() ->
                    // A link's target travels down with its label, which is the whole
                    // point of `[![cover](img)](target)`: the picture keeps the tap.
                    walk(
                        token.nestedTokens(),
                        (token as? InlineToken.Link)?.url ?: targetUrl
                    )
                else -> run.add(token)
            }
        }
    }

    walk(this, null)
    flushRun()
    return segments
}
