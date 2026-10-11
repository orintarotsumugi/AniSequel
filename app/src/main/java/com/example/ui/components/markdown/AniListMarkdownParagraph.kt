package com.example.ui.components.markdown

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
import com.example.ui.components.openExternalUrl

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
fun RenderParagraph(paragraph: MarkdownNode.Paragraph, maxLines: Int) {
    val context = LocalContext.current
    val hasImage = paragraph.inlines.any { it is InlineToken.Image }

    if (!hasImage || maxLines < Int.MAX_VALUE) {
        val annotated = buildInlineAnnotatedString(
            paragraph.inlines,
            onLinkClick = { openExternalUrl(context, it) }
        )
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = if (paragraph.centered) TextAlign.Center else TextAlign.Start,
            maxLines = maxLines,
            overflow = if (maxLines < Int.MAX_VALUE) TextOverflow.Ellipsis else TextOverflow.Clip,
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
                            onLinkClick = { openExternalUrl(context, it) }
                        ),
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = if (paragraph.centered) TextAlign.Center else TextAlign.Start
                    )
                }

                // Reuses the block image renderer, so an inline GIF and a
                // full-line one are the same size, shape and tap target - there is
                // one image treatment in the markdown stack, not two.
                is InlineSegment.Image -> RenderImage(
                    MarkdownNode.ImageBlock(url = segment.token.url, alt = segment.token.alt)
                )
            }
        }
    }
}

/** A run of consecutive text tokens, or a single inline image. */
private sealed interface InlineSegment {
    data class TextRun(val tokens: List<InlineToken>) : InlineSegment
    data class Image(val token: InlineToken.Image) : InlineSegment
}

/**
 * Groups text tokens into runs so each run is one wrapped `Text` rather than a
 * `Text` per token, which would break a line at every bold word.
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

    forEach { token ->
        if (token is InlineToken.Image) {
            flushRun()
            segments.add(InlineSegment.Image(token))
        } else {
            run.add(token)
        }
    }
    flushRun()
    return segments
}
