package com.anisequel.ui.components.detail.info

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.displayName
import com.anisequel.data.model.formatLabel
import com.anisequel.data.model.isManga
import com.anisequel.data.model.pageUrl
import com.anisequel.data.model.posterColor
import com.anisequel.data.model.posterUrl
import com.anisequel.data.model.statusLabel
import com.anisequel.data.model.studioName
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.cards.toCoverColorOrNull
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.expressiveHoldGesture

/**
 * The Info page's hero: the artwork, blurred behind its own title.
 *
 * ## Why the backdrop is the *blurred* key visual rather than a banner
 *
 * The sheet used to lead with the banner, unblurred, with the title lockup over a
 * dark scrim. A banner is a 16:9 frame of whatever the studio put on the poster
 * edge, and cropped into a hero it competes with the title sitting on top of it.
 * Blurring the same artwork removes the competition and keeps the colour - the
 * page's first impression is still this show, but nothing in it is fighting the
 * text for attention. It also makes the hero honest for the common case: the
 * dashboard's list query does not fetch `bannerImage` at all, so a banner-led hero
 * started as a flat grey slab for every sheet opened from the dashboard.
 *
 * ## The collapse
 *
 * [collapse] is read inside the `graphicsLayer` lambda, so the parallax is a
 * draw-phase transform: the page can scroll at 120Hz over an image the layout has
 * not touched. It is a function rather than a value because a value would
 * recompose the hero - and every sibling in the list - on every scrolled frame.
 *
 * Three things move against each other as it collapses, all of them from that one
 * number:
 *
 * - the backdrop *lags* the page and grows, so it keeps covering the box it is
 *   being pulled out of;
 * - the lockup *leads* it, and the two fades hand the page over to the cards
 *   underneath - the artwork recedes and the docked bar arrives carrying the
 *   title the lockup was showing. They are deliberately different fades: the
 *   backdrop keeps a floor, because it is what is left of the artwork once the
 *   title has gone and a hero that faded to nothing would leave a bare band of
 *   container colour for the last 140dp of its own height; the lockup goes all
 *   the way out, because by then the bar at the top of the screen is
 *   *displaying its title*, and two titles is one too many;
 * - the lockup settles onto the artwork on arrival ([sectionReveal] index zero,
 *   the page's own entrance), because the backdrop is already drawn: this is
 *   content landing on a picture, not a picture appearing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InfoHero(
    media: MediaNode,
    relationLabel: String?,
    collapse: () -> Float,
    onOpenCover: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coverColor = media.posterColor.toCoverColorOrNull()
        ?: MaterialTheme.colorScheme.surfaceContainerHigh
    val surface = MaterialTheme.colorScheme.surface
    val backdrop = media.bannerImage ?: media.posterUrl

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HERO_HEIGHT)
            .background(coverColor)
    ) {
        if (backdrop != null) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val scroll = collapse()
                        // Travels slower than the page, and grows: the image has to
                        // keep covering the box it is being pulled out of, or a
                        // parallax on a `Crop` scale exposes an edge.
                        translationY = scroll * PARALLAX_TRAVEL.toPx()
                        val zoom = 1f + scroll * PARALLAX_ZOOM
                        scaleX = zoom
                        scaleY = zoom
                        alpha = 1f - scroll * BACKDROP_HANDOFF
                    }
                    .blur(BACKDROP_BLUR)
            )
        }

        // Ends on `surface`, not on black: the hero has to hand over to the page
        // body without a visible seam, and the sheet's own container is `surface`.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.42f to Color.Black.copy(alpha = 0.30f),
                        0.76f to Color.Black.copy(alpha = 0.62f),
                        1f to surface
                    )
                )
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        ) {
            Surface(
                shape = ExpressiveShapes.pill,
                color = Color.Black.copy(alpha = 0.42f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                modifier = Modifier.testTag("detail_hero_actions")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeroIconButton(
                        icon = AppVectorIcons.OpenInBrowser,
                        label = "Open on AniList",
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(media.pageUrl))
                                )
                            }
                        }
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(22.dp)
                            .background(Color.White.copy(alpha = 0.18f))
                    )
                    HeroIconButton(
                        icon = AppVectorIcons.Close,
                        label = "Close",
                        onClick = onClose
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
                .sectionReveal(0)
                .graphicsLayer {
                    val scroll = collapse()
                    // Up, while the backdrop goes down: the two halves of the hero
                    // moving opposite ways at different speeds is what makes it
                    // read as two depths rather than one flat picture sliding by.
                    translationY = -scroll * LOCKUP_LIFT.toPx()
                    // No-op until the docked bar is on its way in, then out over the
                    // next 60dp of scroll. Fading it from the first pixel instead
                    // would put the poster at half strength while it is still the
                    // only thing on screen. The window is shared with the bar's own
                    // threshold - see `InfoMotion.kt`.
                    val handoff = ((scroll - LOCKUP_FADE_FROM) / (LOCKUP_FADE_TO - LOCKUP_FADE_FROM))
                        .coerceIn(0f, 1f)
                    alpha = 1f - handoff
                },
            verticalAlignment = Alignment.Bottom
        ) {
            Box(
                modifier = Modifier
                    .width(116.dp)
                    .height(174.dp)
                    .shadow(
                        elevation = 18.dp,
                        shape = MaterialTheme.shapes.medium,
                        ambientColor = coverColor,
                        spotColor = Color.Black
                    )
                    .clip(MaterialTheme.shapes.medium)
                    .border(1.dp, Color.White.copy(alpha = 0.22f), MaterialTheme.shapes.medium)
                    .background(coverColor)
                    .expressiveHoldGesture(onHold = onOpenCover, onClick = onOpenCover)
                    .testTag("detail_hero_poster")
            ) {
                AsyncImage(
                    model = media.posterUrl,
                    contentDescription = media.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.62f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    Icon(
                        imageVector = AppVectorIcons.FullscreenExpand,
                        contentDescription = "Hold to expand",
                        tint = Color.White,
                        modifier = Modifier
                            .padding(4.dp)
                            .size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = media.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (relationLabel != null) {
                        HeroChip(
                            icon = AppVectorIcons.FranchiseBranch,
                            label = relationLabel,
                            accent = true
                        )
                    }
                    media.studioName?.let { studio ->
                        HeroChip(icon = AppVectorIcons.Studio, label = studio)
                    }
                    HeroChip(
                        icon = AppVectorIcons.Info,
                        label = media.statusLabel.uppercase()
                    )
                    HeroChip(
                        icon = when {
                            media.isManga -> AppVectorIcons.SourceBook
                            media.formatLabel == "MOVIE" -> AppVectorIcons.Movie
                            else -> AppVectorIcons.Tv
                        },
                        label = media.formatLabel.uppercase()
                    )
                }
            }
        }
    }
}

/**
 * The controls that take over once the hero has scrolled away.
 *
 * The hero's own pill leaves the screen with the hero - which is what the reference
 * does, and it means that on a twelve-card page the only way out is the sheet's own
 * swipe. This is the same two actions, docked, arriving as the lockup hands its title
 * over: the two are timed against each other by [DOCKED_BAR_AT] and [LOCKUP_FADE_FROM]
 * in `InfoMotion.kt`. `visible` is computed from a `derivedStateOf` at the call site,
 * so the crossing recomposes this bar once rather than on every scrolled frame.
 */
@Composable
fun InfoFloatingBar(
    visible: Boolean,
    title: String,
    onOpenAniList: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    val onContainer = MaterialTheme.colorScheme.onSurface

    AnimatedVisibility(
        visible = visible,
        // It docks rather than blinks: it comes down from the edge it belongs to,
        // on the spatial spring, so the bar arrives with the feel of a control
        // settling into place. Under `INSTANT` both the slide and the fade are a
        // `snap`, which is the style saying exactly that.
        enter = slideInVertically(animationSpec = ExpressiveMotion.DefaultSpatialOffset) { height -> -height } +
            fadeIn(animationSpec = ExpressiveMotion.FastEffects),
        exit = slideOutVertically(animationSpec = ExpressiveMotion.DefaultSpatialOffset) { height -> -height } +
            fadeOut(animationSpec = ExpressiveMotion.FastEffects),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = ExpressiveShapes.pill,
                color = container,
                shadowElevation = 6.dp,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Surface(
                shape = ExpressiveShapes.pill,
                color = container,
                shadowElevation = 6.dp
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeroIconButton(
                        icon = AppVectorIcons.OpenInBrowser,
                        label = "Open on AniList",
                        onClick = onOpenAniList,
                        tint = onContainer
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(22.dp)
                            .background(onContainer.copy(alpha = 0.18f))
                    )
                    HeroIconButton(
                        icon = AppVectorIcons.Close,
                        label = "Close",
                        onClick = onClose,
                        tint = onContainer
                    )
                }
            }
        }
    }
}

/**
 * One icon in a hero pill.
 *
 * Deliberately not an `IconButton`: the pill draws the surface, the divider and the
 * ripple clipping as one control, and Material's own button brings a 48dp touch
 * target with its own container and padding that would leave the divider floating
 * inside a gap.
 */
@Composable
private fun HeroIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = Color.White
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(19.dp)
        )
    }
}

/** A translucent chip over artwork, so it stays legible on any still. */
@Composable
private fun HeroChip(
    icon: ImageVector,
    label: String,
    accent: Boolean = false
) {
    val container = if (accent) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
    } else {
        Color.Black.copy(alpha = 0.38f)
    }
    val content = if (accent) MaterialTheme.colorScheme.onPrimary else Color.White

    Surface(
        shape = ExpressiveShapes.pill,
        color = container,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content.copy(alpha = 0.85f),
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = content,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 190.dp)
            )
        }
    }
}

private val HERO_HEIGHT = 340.dp

/** How far the backdrop lags the page, at full collapse. */
private val PARALLAX_TRAVEL = 64.dp

/** How much the backdrop grows by the time the hero is out of the way. */
private const val PARALLAX_ZOOM = 0.16f

/**
 * Heavy enough that no letterform in the artwork competes with the title, light
 * enough that the show's palette still reads. See the contrast note on
 * `ProfileActivityCard.COVER_BLUR_RADIUS` - this is the same trade at hero scale.
 */
private val BACKDROP_BLUR = 26.dp

/**
 * How much of the backdrop is gone once the hero has fully collapsed.
 *
 * Half, not all: at the end of the collapse the hero is still 140dp tall on screen,
 * and a backdrop that had faded out completely would expose the container colour
 * underneath for the rest of the artwork's travel. Half of it also leaves the show's
 * own palette on screen for the handover into the page.
 */
private const val BACKDROP_HANDOFF = 0.5f

/** How far the lockup lifts *against* the page, on top of scrolling with it. */
private val LOCKUP_LIFT = 26.dp
