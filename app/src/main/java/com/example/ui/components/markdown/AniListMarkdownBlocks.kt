package com.example.ui.components.markdown

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.expressive.ExpressiveMotion
import com.example.ui.components.expressive.ExpressiveShapes
import com.example.ui.components.expressive.bouncyPress
import com.example.ui.components.openExternalUrl

@Composable
fun RenderHeader(header: MarkdownNode.Header) {
    val style = when (header.level) {
        1 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp)
        2 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp)
        3 -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        else -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
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
fun RenderParagraph(paragraph: MarkdownNode.Paragraph, maxLines: Int) {
    val context = LocalContext.current
    val annotated = buildInlineAnnotatedString(paragraph.inlines, onLinkClick = { openExternalUrl(context, it) })
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = if (paragraph.centered) TextAlign.Center else TextAlign.Start,
        maxLines = maxLines,
        overflow = if (maxLines < Int.MAX_VALUE) TextOverflow.Ellipsis else TextOverflow.Clip,
        modifier = if (paragraph.centered) Modifier.fillMaxWidth() else Modifier
    )
}

@Composable
fun RenderBlockquote(quote: MarkdownNode.Blockquote) {
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
fun RenderCodeBlock(block: MarkdownNode.CodeBlock) {
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
fun RenderList(list: MarkdownNode.ListBlock) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        list.items.forEachIndexed { index, itemInlines ->
            Row(modifier = Modifier.fillMaxWidth()) {
                val bullet = if (list.ordered) "${index + 1}." else "•"
                // 26dp and `softWrap = false`, not 20dp: an ordered list past its
                // ninth item prints "10.", which does not fit the old column and
                // wrapped onto a second line - so the tenth item of every
                // numbered list in every bio was indented by one blank row.
                Text(
                    text = bullet,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    softWrap = false,
                    maxLines = 1,
                    modifier = Modifier.width(26.dp)
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
fun RenderCheckList(checklist: MarkdownNode.CheckListBlock) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        checklist.items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (item.checked) AppVectorIcons.CheckCircle else AppVectorIcons.BookmarkDone,
                    contentDescription = null,
                    tint = if (item.checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                val annotated = buildInlineAnnotatedString(item.inlines, onLinkClick = { openExternalUrl(context, it) })
                Text(
                    text = annotated,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (item.checked) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun RenderImage(image: MarkdownNode.ImageBlock) {
    val context = LocalContext.current
    val clickableModifier = if (image.targetUrl != null) {
        Modifier
            .bouncyPress()
            .clickable { openExternalUrl(context, image.targetUrl) }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickableModifier),
        contentAlignment = if (image.centered) Alignment.Center else Alignment.CenterStart
    ) {
        AsyncImage(
            model = image.url,
            contentDescription = image.alt ?: "Markdown image",
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentScale = ContentScale.FillWidth,
            placeholder = rememberVectorPainter(AppVectorIcons.AnimeSparkle),
            error = rememberVectorPainter(AppVectorIcons.AnimeSparkle)
        )
    }
}

@Composable
fun RenderVideo(video: MarkdownNode.VideoBlock) {
    val context = LocalContext.current
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .bouncyPress()
            .clickable { openExternalUrl(context, video.url) }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = AppVectorIcons.Play,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (video.isYoutube) "Watch on YouTube" else "Watch Media Clip",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = video.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = AppVectorIcons.OpenInNew,
                contentDescription = "Open",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun RenderSpoilerBlock(spoiler: MarkdownNode.SpoilerBlock) {
    // `rememberSaveable`, not `remember`.
    //
    // This renders inside the profile's `LazyColumn`, and a plain `remember` is
    // discarded as soon as the card leaves the viewport - so a revealed spoiler
    // re-hid itself the moment the reader scrolled past it and back. The state is
    // small enough to save (one boolean), and `rememberSaveable` also survives a
    // configuration change, so the reveal is not undone by a rotation either.
    var revealed by rememberSaveable { mutableStateOf(false) }
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
            // Revealing swaps a one-line prompt for the hidden text, so the block
            // changes height. Animating that here means the surrounding list does
            // not jump; the token is the `IntSize` one for exactly this reason.
            .animateContentSize(animationSpec = ExpressiveMotion.FastSpatialSize)
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
            // The icon stays, as a cue that the same tap hides it again. It used
            // to vanish with the prompt, which left a revealed spoiler looking
            // like plain text - nothing said it could be tapped back shut.
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = AppVectorIcons.Done,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = annotated,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
