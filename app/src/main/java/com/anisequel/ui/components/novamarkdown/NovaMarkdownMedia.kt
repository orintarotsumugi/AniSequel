package com.anisequel.ui.components.novamarkdown

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Dimension
import coil.size.Size
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.openExternalUrlOrCopy

@Composable
internal fun NovaImageBlock(image: NovaBlock.Image) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = image.align.toContentAlignment()
    ) {
        NovaImageContent(
            url = image.url,
            alt = image.alt,
            targetUrl = image.targetUrl,
            width = image.width,
            height = image.height,
            fillWidth = true
        )
    }
}

/**
 * One picture inside a sentence or an icon row.
 *
 * It deliberately does not fill the width: a 50dp icon in a row of five has to
 * stay 50dp and sit beside the next one, not take a column of its own. The old
 * renderer wrapped every inline image in `fillMaxWidth`, which is why a social
 * row stacked vertically.
 */
@Composable
internal fun NovaInlinePicture(
    image: NovaInline.Image,
    targetUrl: String?
) {
    NovaImageContent(
        url = image.url,
        alt = image.alt,
        targetUrl = targetUrl,
        width = image.width,
        height = image.height,
        fillWidth = false
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
private fun NovaImageContent(
    url: String,
    alt: String?,
    targetUrl: String?,
    width: String?,
    height: String?,
    fillWidth: Boolean
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val interactionSource = remember { MutableInteractionSource() }
    val tapUrl = targetUrl ?: url
    val mediaModifier = novaImageSizeModifier(width, height, fillWidth)
        .clip(MaterialTheme.shapes.medium)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)

    // A declared size is also a decode budget. Without this, five 50dp icons are
    // decoded at whatever the source file happens to be - the social art on the
    // profiles that exposed this bug is several times the 50x60 it is drawn at.
    val model = remember(context, url, width, height, density, screenWidthDp) {
        // A declared 2300dp banner must not become a 2300dp decode. The screen
        // width is a safe upper bound for every surface that draws this image;
        // the actual card may be narrower still, and AsyncImage's own constraint
        // sizing handles that when no request size is supplied.
        val widthPx = novaImageWidthDp(width)
            ?.coerceAtMost(screenWidthDp)
            ?.let { with(density) { it.dp.roundToPx() } }
        val heightPx = novaImageHeightDp(height)?.let { with(density) { it.dp.roundToPx() } }
        if (widthPx == null && heightPx == null) {
            url
        } else {
            ImageRequest.Builder(context)
                .data(url)
                .size(
                    Size(
                        widthPx?.let { Dimension(it) } ?: Size.ORIGINAL.width,
                        heightPx?.let { Dimension(it) } ?: Size.ORIGINAL.height
                    )
                )
                .build()
        }
    }

    val fillsBox = fillWidth || novaImageWidthPercent(width) != null
    val contentModifier = if (fillsBox) Modifier.fillMaxWidth() else Modifier

    Box(
        modifier = mediaModifier
            .bouncyPress(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource) {
                openExternalUrlOrCopy(context, tapUrl)
            },
        contentAlignment = Alignment.Center
    ) {
        if (isNovaSvgUrl(url)) {
            NovaDynamicSvg(
                url = url,
                contentDescription = alt,
                modifier = contentModifier
            )
        } else {
            AsyncImage(
                model = model,
                contentDescription = alt ?: "Markdown image",
                modifier = contentModifier,
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.Medium,
                placeholder = rememberVectorPainter(AppVectorIcons.AnimeSparkle),
                error = rememberVectorPainter(AppVectorIcons.Warning)
            )
        }
    }
}

/**
 * The box an image occupies, before its own content is drawn.
 *
 * A block image defaults to the column width. An inline one defaults to the
 * file's intrinsic size, which is what lets an icon row measure several pictures
 * on one line; a declared width or height still wins in both cases.
 */
private fun novaImageSizeModifier(
    width: String?,
    height: String?,
    fillWidth: Boolean
): Modifier {
    var modifier = Modifier.heightIn(max = NOVA_MAX_IMAGE_HEIGHT_DP.dp)
    val fraction = novaImageWidthPercent(width)
    val widthDp = novaImageWidthDp(width)
    when {
        fraction != null -> modifier = modifier.fillMaxWidth(fraction)
        widthDp != null -> modifier = modifier.width(widthDp.dp)
        fillWidth -> modifier = modifier.fillMaxWidth()
    }
    novaImageHeightDp(height)?.let { modifier = modifier.height(it.dp) }
    return modifier
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
