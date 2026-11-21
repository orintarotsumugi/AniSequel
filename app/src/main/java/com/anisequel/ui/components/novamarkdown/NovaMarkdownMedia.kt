package com.anisequel.ui.components.novamarkdown

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.openExternalUrlOrCopy

@Composable
internal fun NovaImageBlock(image: NovaBlock.Image) {
    NovaImageSurface(
        url = image.url,
        alt = image.alt,
        targetUrl = image.targetUrl,
        width = image.width,
        align = image.align
    )
}

@Composable
internal fun NovaInlinePicture(
    image: NovaInline.Image,
    targetUrl: String?,
    align: NovaAlign = NovaAlign.Start
) {
    NovaImageSurface(
        url = image.url,
        alt = image.alt,
        targetUrl = targetUrl,
        width = image.width,
        align = align
    )
}

/**
 * One image, whichever kind of source it has.
 *
 * Raster and GIF artwork goes through Coil. An SVG goes through
 * [NovaDynamicSvg], because AniList's live profile cards are not the flat SVG
 * `coil-svg` can draw: they nest `data:` images and embed WOFF2 fonts, and
 * AndroidSVG renders those as a blank card. The split is by URL, once, here.
 *
 * Every image is tappable. AniList opens the picture itself when the markup gives
 * it no target, and an image that looks like a picture but behaves like a
 * decoration is a broken link the reader cannot see.
 */
@Composable
private fun NovaImageSurface(
    url: String,
    alt: String?,
    targetUrl: String?,
    width: String?,
    align: NovaAlign
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val tapUrl = targetUrl ?: url
    val mediaModifier = novaImageWidthModifier(width)
        .heightIn(max = NOVA_MAX_IMAGE_HEIGHT_DP.dp)
        .clip(MaterialTheme.shapes.medium)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyPress(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource) {
                openExternalUrlOrCopy(context, tapUrl)
            },
        contentAlignment = align.toContentAlignment()
    ) {
        if (isNovaSvgUrl(url)) {
            NovaDynamicSvg(
                url = url,
                contentDescription = alt,
                modifier = mediaModifier
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = alt ?: "Markdown image",
                modifier = mediaModifier,
                contentScale = ContentScale.Fit,
                placeholder = rememberVectorPainter(AppVectorIcons.AnimeSparkle),
                error = rememberVectorPainter(AppVectorIcons.Warning)
            )
        }
    }
}

/**
 * The width `img220(...)` or `img50%(...)` asked for.
 *
 * A plain number is dp and a percentage is a share of the column. Neither can
 * overflow: `width` reports its size through the incoming constraints, and the
 * percentage is clamped to 1. A malformed size degrades to the full-width default
 * rather than to an invisible zero.
 */
private fun novaImageWidthModifier(width: String?): Modifier {
    novaImageWidthPercent(width)?.let { return Modifier.fillMaxWidth(it) }
    novaImageWidthDp(width)?.let { return Modifier.width(it.dp) }
    return Modifier.fillMaxWidth()
}

@Composable
internal fun NovaVideo(video: NovaBlock.Video) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .bouncyPress(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource) {
                openExternalUrlOrCopy(context, video.url)
            }
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
                    text = if (video.youtube) "Watch on YouTube" else "Watch media clip",
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
