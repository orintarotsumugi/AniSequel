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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RectangleShape
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
import com.anisequel.data.model.studioCredits
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.cards.toCoverColorOrNull
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.expressiveHoldGesture

/**
 * The Info page's hero: the artwork, behind its own title.
 *
 * ## Why the backdrop is the key visual rather than a banner
 *
 * The sheet used to lead with the banner, with the title lockup over a dark scrim.
 * A banner is a 16:9 frame of whatever the studio put on the poster edge, and
 * cropped into a hero it competes with the title sitting on top of it. The key
 * visual is the artwork a reader already recognises from the row they tapped, and
 * it also makes the hero honest for the common case: the dashboard's list query
 * does not fetch `bannerImage` at all, so a banner-led hero started as a flat grey
 * slab for every sheet opened from the dashboard.
 *
 * ## Why the artwork is sharp and the scrim does the work
 *
 * It used to be blurred at 26dp, on the reasoning - still recorded on the old
 * constant - that a sharp key visual puts its own lettering and faces behind the
 * title lockup and the two fight. That reasoning was sound and the treatment was
 * still wrong, because 26dp of blur is not a scrim: it is the artwork redrawn as
 * an impression of itself. A hero is the one place a reader decides what a title
 * looks like, and the poster's own artwork is the thing worth showing them.
 *
 * So the blur is gone and the legibility it was buying moved into the gradient
 * below, which is weighted for sharp art and can be read in one place. The
 * `ProfileActivityCard` backdrop keeps its blur, and the difference is deliberate:
 * that is a 64dp wash behind a title on a card, where the art is a colour hint and
 * never the subject.
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
    /**
     * Opens the page for one of this title's studios.
     *
     * The chip on the lockup is tappable for the same reason the ones in the
     * Details card are: the studio a show is by is the one credit people follow,
     * and this is the first place it is drawn.
     */
    onOpenStudio: (Int, String?) -> Unit = { _, _ -> },
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
            )
        }

        // Ends on `surface`, not on black: the hero has to hand over to the page
        // body without a visible seam, and the sheet's own container is `surface`.
        //
        // Weighted for artwork that is **sharp**. This scrim used to be written for
        // a blurred backdrop and was far too light for a sharp one: the top stop was
        // fully transparent, so the poster's own lettering and faces - the very
        // things the blur had been there to remove - sat directly under the title
        // lockup and fought every glyph in it. The blur is gone (see the KDoc on
        // this function) so the legibility work moves here, where it costs nothing
        // and can be read: top-weighted enough to sit under the artwork, strong at
        // the bottom where the lockup and its chips are drawn.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.10f),
                        0.30f to Color.Black.copy(alpha = 0.22f),
                        0.58f to Color.Black.copy(alpha = 0.52f),
                        0.82f to Color.Black.copy(alpha = 0.74f),
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
                // Decorative. The poster itself is the clickable thing and carries
                // the title as its `contentDescription`, so this badge was a second
                // element for a reader using a screen reader to reach - announcing
                // "Hold to expand" for a control whose actual action is a tap, over
                // a poster that already said what it was.
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.62f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    Icon(
                        imageVector = AppVectorIcons.FullscreenExpand,
                        contentDescription = null,
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
                    // The artwork behind is sharp now, so the title carries its own
                    // shadow rather than relying on the gradient alone. One shadow on
                    // one short block of text: it is what keeps a white
                    // `headlineSmall` legible over a pale poster *without*
                    // darkening the whole hero, which is what pushing the gradient
                    // harder would have done.
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.shadow(
                        elevation = 6.dp,
                        shape = RectangleShape,
                        ambientColor = Color.Black,
                        spotColor = Color.Black
                    )
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
                    // The main studio, when AniList flagged one. `studioCredits`
                    // sorts those first and falls back to the flat list for the
                    // queries that carry no edges - so this still headlines the
                    // animation studio on a node whose studios arrived unordered.
                    media.studioCredits.firstOrNull()?.let { studio ->
                        HeroChip(
                            icon = AppVectorIcons.Studio,
                            label = studio.name,
                            // Null id, null tap: a credit AniList gave no id for
                            // cannot be opened, and a chip that looks tappable and
                            // is not is worse than a plain one.
                            onClick = studio.id?.let { id -> { onOpenStudio(id, studio.name) } }
                        )
                    }
                    // The score is the number most people open a title to check, and
                    // it lived half a page down in the Details card between "Source"
                    // and "Popularity". It belongs in the lockup with the rest of the
                    // "what is this" answers. `averageScore` is AniList's 0-100 mean,
                    // which is already a percentage, so it is not rescaled here.
                    media.averageScore?.let { score ->
                        HeroChip(icon = AppVectorIcons.Star, label = "$score%")
                    }
                    HeroChip(
                        icon = AppVectorIcons.Info,
                        label = media.statusLabel.uppercase()
                    )
                    HeroChip(
                        icon = when {
                            media.isManga -> AppVectorIcons.SourceBook
                            // Read off the raw field, not `formatLabel`: that is
                            // now spelled for a reader ("TV Short"), so comparing
                            // it to `"MOVIE"` would silently never match and every
                            // movie would draw the TV icon.
                            media.format.equals("MOVIE", ignoreCase = true) -> AppVectorIcons.Movie
                            else -> AppVectorIcons.Tv
                        },
                        // Small caps, the way the other hero chips are set. This
                        // used to be `.uppercase()` on a raw enum, which printed
                        // `TV_SHORT` on screen; `formatLabel` now spells it.
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
 *
 * ## The title pill is a control, and it carries the two things this page lacked
 *
 * A thirteen-section page has no orientation and no way back. The pill is both: the
 * label is a *button* that returns to the hero, and the same pill carries [progress]
 * as a wash that fills from the left as the reader descends. Both live inside the one
 * control rather than being added as a fourth floating element, because everything
 * floating over this page already competes with the content behind it.
 *
 * The wash is `scaleX` on a full-size box, read inside `graphicsLayer`, so it is a
 * draw-phase transform: the bar does not recompose on the frames the reader is
 * scrolling - the same reason the hero's parallax is written the way it is. [progress]
 * is a function rather than a value for the same reason the hero's `collapse` is one.
 *
 * ## The bug this replaces
 *
 * The title `Surface` took `weight(1f, fill = false)` and a `Spacer` then took
 * `weight(1f)` *after* it, so two weighted children split the free width in half:
 * the title was measured against roughly half the bar and ellipsized there no matter
 * how much room the bar really had. On screen that was a long title cut to
 * `MARRIAGETO...` with a third of the screen sitting empty beside it. With one
 * weighted child the title still hugs its content (`fill = false`, so a short title's
 * pill stays short) but is measured against every dp the controls are not using.
 */
@Composable
fun InfoFloatingBar(
    visible: Boolean,
    title: String,
    progress: () -> Float,
    onScrollToTop: () -> Unit,
    onOpenAniList: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    val onContainer = MaterialTheme.colorScheme.onSurface
    val wash = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    val pill = ExpressiveShapes.pill

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
                shape = pill,
                color = container,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clip(pill)
                    // `onClickLabel` rather than a `contentDescription` on the icon:
                    // the pill's own text is what should be announced, and the action
                    // is what a screen reader needs said after it. Overwriting the
                    // description with "Scroll to top" would replace the title.
                    .clickable(onClickLabel = "Scroll to top", onClick = onScrollToTop)
                    .testTag("info_bar_title")
            ) {
                Box {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                scaleX = progress()
                                transformOrigin = TransformOrigin(0f, 0.5f)
                            }
                            .background(wash)
                    )
                    Row(
                        modifier = Modifier.padding(start = 9.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = AppVectorIcons.ExpandLess,
                            contentDescription = null,
                            tint = onContainer,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = pill,
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
 * ripple clipping as one control, and Material's own button brings its own container
 * and padding that would leave the divider floating inside a gap.
 *
 * 48dp is the platform minimum touch target and this used to be 46dp, which put
 * both the hero's own controls and the docked bar's below it. The two that matter
 * most are the ones a reader reaches for one-handed at the bottom of the screen, so
 * the shortfall was felt rather than merely linted.
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
            .size(48.dp)
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
    accent: Boolean = false,
    /** Non-null makes the chip a button, with a press spring and a brighter rim. */
    onClick: (() -> Unit)? = null
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
        border = BorderStroke(
            width = 1.dp,
            color = if (onClick != null) {
                Color.White.copy(alpha = 0.34f)
            } else {
                Color.White.copy(alpha = 0.14f)
            }
        ),
        modifier = if (onClick != null) {
            // The border is brighter and the whole chip springs: over artwork, a
            // tappable capsule has nothing else to distinguish it from the four
            // facts beside it.
            Modifier
                .bouncyPress(pressedScale = 0.94f)
                .clickable(onClick = onClick)
        } else {
            Modifier
        }
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
 *
 * Not applied. The hero shows its artwork sharp now, so the blur is gone and the
 * legibility it bought is done by the scrim instead - see the note on that gradient.
 * The reasoning is kept here rather than deleted silently, because the blur was a
 * deliberate answer to sharp artwork competing with the title, and the next person
 * to weaken that scrim will be re-solving the same problem.
 */

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
