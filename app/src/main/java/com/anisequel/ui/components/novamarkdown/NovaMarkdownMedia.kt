package com.anisequel.ui.components.novamarkdown

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
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
    // No background behind the picture. AniList's social links are transparent
    // PNGs, and painting a container behind every image drew a dark rounded card
    // around each icon - five boxes where the author uploaded five logos.
    val mediaModifier = novaImageSizeModifier(width, height, fillWidth)
        .clip(MaterialTheme.shapes.medium)

    // A declared size is also a decode budget. Without this, five 50dp icons are
    // decoded at whatever the source file happens to be - the social art on the
    // profiles that exposed this bug is several times the 50x60 it is drawn at.
    // SVGs go through the WebView below, so there is no Coil request to size.
    val model = remember(context, url, width, height, density, screenWidthDp) {
        if (isNovaSvgUrl(url)) return@remember url

        val (decodeWidthDp, decodeHeightDp) = novaImageDecodeDp(width, height, screenWidthDp)
        ImageRequest.Builder(context)
            .data(url)
            // A slow CDN should fade the artwork in rather than pop it over the
            // placeholder; this adds no extra request.
            .crossfade(true)
            .apply {
                if (decodeWidthDp != null || decodeHeightDp != null) {
                    size(
                        Size(
                            decodeWidthDp.toDecodeDimension(density),
                            decodeHeightDp.toDecodeDimension(density)
                        )
                    )
                }
            }
            .build()
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
            SubcomposeAsyncImage(
                model = model,
                contentDescription = alt ?: "Markdown image",
                modifier = contentModifier,
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.Medium,
                // A picture that has not arrived, and one that never will, are
                // drawn small. `placeholder`/`error` painters were stretched to
                // the box by `contentScale = Fit`, so a wide block image that
                // failed drew a full-bleed warning triangle the size of the
                // artwork it was standing in for - and a bio's whole page became
                // a warning sign because one GIF was missing.
                loading = { NovaImagePending(modifier = Modifier.size(28.dp)) },
                error = { NovaImageUnavailable() }
            )
        }
    }
}

/**
 * Something small where the picture will be, while it is on its way.
 *
 * A spinner rather than a copy of the artwork's box: the box is already reserved
 * by [novaImageSizeModifier], so this only has to say "not yet" in a size that
 * does not become the page's most prominent thing when a network is slow.
 */
@Composable
private fun NovaImagePending(modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        modifier = modifier,
        strokeWidth = 2.dp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    )
}

/**
 * The honest state for an image that will not load: a sentence, not a triangle.
 *
 * It is deliberately the size of a line of text. The block's box already reserved
 * the artwork's dimensions, so an error that filled it drew the biggest thing on
 * the page over the smallest amount of information - a warning sign about a
 * missing decoration. The tap still opens the picture's own URL, which is the one
 * thing a reader can still do with it.
 */
@Composable
private fun NovaImageUnavailable(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("nova_markdown_image_unavailable")
    ) {
        Icon(
            imageVector = AppVectorIcons.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = "Couldn't load this image",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** An undefined budget tells Coil to fit whichever dimension the view leaves free. */
private fun Int?.toDecodeDimension(density: Density): Dimension =
    this?.let { with(density) { it.dp.roundToPx() } }?.let(::Dimension)
        ?: Size.ORIGINAL.width

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

/**
 * The video embed: a poster frame with a play badge, not a line of text about a video.
 *
 * ## Why this is a card and not the row it was
 *
 * `youtube(id)` and `webm(url)` used to draw a small outlined row - a play glyph, the
 * words "Watch on YouTube" and the URL - for what is the most visual thing a bio can
 * contain. A reader scrolling a profile saw a link where the author had posted a
 * video, and the poster frame was never shown at all.
 *
 * Dantotsu's AniMarkdown draws this as a 16:9 `.yt-embed` (its WebView widget: the
 * `hqdefault` frame, a red play badge, `object-fit: cover`) and that shape is the part
 * worth having here: the frame is the video's own artwork, so the card is recognisable
 * before it is read. The interactive part of Dantotsu's widget - swapping an `<iframe>`
 * in on tap - has no counterpart in a Compose bio that opens links, so a tap opens the
 * video the way every other embed in this renderer does.
 *
 * A `webm`/`mp4` clip has no poster frame to ask a service for, so it gets the same
 * card without the artwork: a dark 16:9 plate, the same badge, and the clip's own file
 * name under it.
 */
@Composable
internal fun NovaVideo(video: NovaBlock.Video) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val thumbnail = novaYouTubeThumbnail(video.url)
    val caption = if (video.youtube) "Watch on YouTube" else "Watch this clip"
    val detail = if (video.youtube) {
        novaVideoHost(video.url)
    } else {
        video.url.substringAfterLast('/').substringBefore('?').ifBlank { video.url }
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            // The clip is for the press indication, not for the card: `clickable` is on
            // this node, outside the `Surface`, so its ripple would paint over square
            // corners - the shape the card is not.
            .clip(MaterialTheme.shapes.medium)
            .bouncyPress(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                // The whole card is one target, and a screen reader has to be told
                // what it does: the play badge and the caption are drawn, not spoken.
                onClickLabel = "Open the video"
            ) {
                openExternalUrlOrCopy(context, video.url)
            }
            .testTag("nova_video_embed")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(MEDIA_EMBED_ASPECT)
                    .background(
                        if (thumbnail != null) Color.Black
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    AsyncImage(
                        model = remember(context, thumbnail) {
                            ImageRequest.Builder(context)
                                .data(thumbnail)
                                // A poster is a placeholder for a link, not artwork the
                                // reader is looking at; a slow CDN should not pop it in.
                                .crossfade(true)
                                .build()
                        },
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        // `Crop` is what makes `hqdefault` usable: the frame is 4:3 with
                        // black bars above and below the picture, and the bars are what
                        // the card is not allowed to show.
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.Medium
                    )
                }

                // No second glyph on a poster-less plate: the badge already says
                // "video", and a file-type icon behind it drew two shapes in the same
                // centre. The clip's file name is in the caption under the plate.
                MediaEmbedPlayBadge()
            }

            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = AppVectorIcons.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/** A 16:9 plate for a video embed, the shape every player and Dantotsu's widget use. */
private const val MEDIA_EMBED_ASPECT = 16f / 9f

/**
 * The play badge over a poster: a red rounded plate with a white triangle.
 *
 * Red because that is the cue that makes the surface read as a video rather than as a
 * picture with a link under it, and drawn as its own composable so the poster and the
 * poster-less clip card wear the identical badge.
 */
@Composable
private fun MediaEmbedPlayBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xD9F22B2B))
            .size(width = 56.dp, height = 38.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = AppVectorIcons.Play,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}
