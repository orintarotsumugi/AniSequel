package com.example.ui.components.markdown

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.expressive.ExpressiveMotion
import com.example.ui.components.expressive.ExpressiveShapes
import com.example.ui.components.expressive.bouncyPress
import com.example.ui.components.openExternalUrl

/**
 * Pure Compose renderer for parsed AniList markdown AST.
 */
@Composable
fun AniListMarkdownView(
    nodes: List<MarkdownNode>,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE
) {
    Column(
        modifier = modifier.testTag("anilist_markdown_view"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        nodes.take(if (maxLines < Int.MAX_VALUE) 4 else nodes.size).forEach { node ->
            when (node) {
                is MarkdownNode.Header -> RenderHeader(node)
                is MarkdownNode.Paragraph -> RenderParagraph(node, maxLines)
                is MarkdownNode.Blockquote -> RenderBlockquote(node)
                is MarkdownNode.CodeBlock -> RenderCodeBlock(node)
                is MarkdownNode.ListBlock -> RenderList(node)
                is MarkdownNode.ImageBlock -> RenderImage(node)
                is MarkdownNode.SpoilerBlock -> RenderSpoilerBlock(node)
                is MarkdownNode.Divider -> HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun RenderHeader(header: MarkdownNode.Header) {
    val style = when (header.level) {
        1 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        2 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        else -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
    }
    val context = LocalContext.current
    val annotated = buildInlineAnnotatedString(header.inlines, onLinkClick = { openExternalUrl(context, it) })
    Text(
        text = annotated,
        style = style,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun RenderParagraph(paragraph: MarkdownNode.Paragraph, maxLines: Int) {
    val context = LocalContext.current
    val annotated = buildInlineAnnotatedString(paragraph.inlines, onLinkClick = { openExternalUrl(context, it) })
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines,
        overflow = if (maxLines < Int.MAX_VALUE) TextOverflow.Ellipsis else TextOverflow.Clip
    )
}

@Composable
private fun RenderBlockquote(quote: MarkdownNode.Blockquote) {
    val context = LocalContext.current
    val annotated = buildInlineAnnotatedString(quote.inlines, onLinkClick = { openExternalUrl(context, it) })
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(20.dp)
                .clip(ExpressiveShapes.pill)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RenderCodeBlock(block: MarkdownNode.CodeBlock) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = block.code,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun RenderList(list: MarkdownNode.ListBlock) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        list.items.forEachIndexed { index, itemInlines ->
            Row(modifier = Modifier.fillMaxWidth()) {
                val bullet = if (list.ordered) "${index + 1}." else "•"
                Text(
                    text = bullet,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(20.dp)
                )
                val annotated = buildInlineAnnotatedString(itemInlines, onLinkClick = { openExternalUrl(context, it) })
                Text(
                    text = annotated,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun RenderImage(image: MarkdownNode.ImageBlock) {
    AsyncImage(
        model = image.url,
        contentDescription = image.alt ?: "Profile image",
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentScale = ContentScale.FillWidth,
        placeholder = rememberVectorPainter(AppVectorIcons.AnimeSparkle),
        error = rememberVectorPainter(AppVectorIcons.AnimeSparkle)
    )
}

@Composable
private fun RenderSpoilerBlock(spoiler: MarkdownNode.SpoilerBlock) {
    var revealed by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (revealed) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        modifier = Modifier
            .fillMaxWidth()
            .bouncyPress()
            .clickable { revealed = !revealed }
    ) {
        if (!revealed) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = AppVectorIcons.Visibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Spoiler (tap to reveal)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            val annotated = buildInlineAnnotatedString(spoiler.inlines, onLinkClick = { openExternalUrl(context, it) })
            Text(
                text = annotated,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(10.dp)
            )
        }
    }
}

@Composable
private fun buildInlineAnnotatedString(
    inlines: List<InlineToken>,
    onLinkClick: (String) -> Unit
): AnnotatedString {
    val primaryColor = MaterialTheme.colorScheme.primary

    return remember(inlines, primaryColor) {
        buildAnnotatedString {
            inlines.forEach { token ->
                when (token) {
                    is InlineToken.Plain -> append(token.text)
                    is InlineToken.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(token.text)
                    }
                    is InlineToken.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(token.text)
                    }
                    is InlineToken.BoldItalic -> withStyle(
                        SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                    ) {
                        append(token.text)
                    }
                    is InlineToken.Strikethrough -> withStyle(
                        SpanStyle(textDecoration = TextDecoration.LineThrough)
                    ) {
                        append(token.text)
                    }
                    is InlineToken.Code -> withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color.Black.copy(alpha = 0.08f)
                        )
                    ) {
                        append(" ${token.code} ")
                    }
                    is InlineToken.Link -> {
                        val linkStyle = TextLinkStyles(
                            style = SpanStyle(
                                color = primaryColor,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        withLink(LinkAnnotation.Url(token.url, styles = linkStyle)) {
                            append(token.label)
                        }
                    }
                    is InlineToken.Spoiler -> withStyle(
                        SpanStyle(
                            background = Color.Gray.copy(alpha = 0.25f),
                            fontWeight = FontWeight.Medium
                        )
                    ) {
                        append(" [Spoiler: ${token.text}] ")
                    }
                }
            }
        }
    }
}
