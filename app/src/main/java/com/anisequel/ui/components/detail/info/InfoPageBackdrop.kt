package com.anisequel.ui.components.detail.info

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.posterUrl
import com.anisequel.ui.components.cards.cardPosterUrl
import com.anisequel.data.repository.InfoBackdropBlur
import com.anisequel.data.repository.InfoBackdropSource
import com.anisequel.data.repository.InfoBackdropTint
import com.anisequel.ui.components.cards.unseamedCover
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.theme.CoverColors
import com.anisequel.ui.theme.DynamicThemeBuilder
import com.anisequel.ui.theme.LocalAppDarkTheme

/**
 * The artwork behind the whole Info page, under the cards.
 *
 * ## Why this is not the hero's backdrop
 *
 * The hero already draws its own full-bleed artwork and hands it over to the page
 * with a gradient that ends on `surface`. Below that, the page was a flat container
 * colour - the title's art stopped at the first card and the rest of the scroll was
 * grey. This draws the *same* image full-screen behind the list instead, so the
 * colour is still there further down the page; the hand-off below decides where it
 * is allowed to show through.
 *
 * ## Why the hand-off is anchored to the hero, not to the window
 *
 * The fade from `surface` to the artwork used to be a fixed fraction of the
 * viewport (`surface` at 0, transparent at 42%). That is approximately the hero's
 * height only on a phone at rest, and the "approximately" is the bug. The hero is
 * a measured item that scrolls; the fraction was a fixed line across the window.
 * By the hero's bottom edge the fade was already almost transparent, so the 16dp
 * item gap under it - and the top of the first card - showed the artwork at nearly
 * full strength beneath a hero whose own scrim had just landed on opaque
 * `surface`.
 *
 * The scale mismatch made it worse. This layer draws the banner with
 * `ContentScale.Crop` into a full-window box, while the hero crops the same banner
 * into its 340dp box, so the "continuation" below the hero was the same picture at
 * a very different zoom - a second, closer view of the scene. That is the cut
 * between the artwork and the page that readers kept reporting, and matching the
 * two gradients could never fix it because the two images were never the same
 * picture on screen.
 *
 * [heroBottom] is the hero's real laid-out edge, read from the list's own layout in
 * the draw phase. The mask is opaque `surface` at that line and fades the artwork
 * in over [HERO_HANDOFF_FADE] of the content below it, so the page's background is
 * the colour the hero ends on exactly where the two meet, and the artwork arrives
 * under the first card instead of in the edge above it. Both the hero and this
 * line are measured from the list, so the relationship holds at every scroll
 * position and every font scale; there is no viewport fraction or assumed hero
 * height left to drift.
 *
 * ## Why the cards still read
 *
 * The cards are `surfaceContainer` at 78% alpha: they are meant to sit on the app's
 * background, not on a photograph. Two things keep the page legible without turning
 * the backdrop off. The blur (see [InfoBackdropBlur]) removes the high-frequency
 * detail that competes with text, and the scrim below is the scheme's own `surface`
 * at increasing alpha - the same role the page body uses, so a light theme gets a
 * light wash and a dark theme a dark one, instead of one hard-coded black overlay.
 *
 * ## The parallax
 *
 * [collapse] is read inside `graphicsLayer`, so the drift is a draw-phase transform.
 * The backdrop is *behind* the scrolling list rather than part of it; moving it a
 * little against the scroll is what tells the reader there is depth under the page
 * instead of a screenshot pinned to the window.
 */
@Composable
internal fun InfoPageBackdrop(
    media: MediaNode,
    source: InfoBackdropSource,
    blur: InfoBackdropBlur,
    tint: InfoBackdropTint = InfoBackdropTint.DEFAULT,
    collapse: () -> Float,
    /**
     * The hero's bottom edge in viewport pixels; `POSITIVE_INFINITY` before the
     * list has measured anything, and `NEGATIVE_INFINITY` once item zero has
     * scrolled out of it. Read inside the draw phase, like [collapse]: it changes
     * on every scrolled frame, and a value read in composition would recompose
     * the backdrop for the whole scroll.
     */
    heroBottom: () -> Float,
    modifier: Modifier = Modifier
) {
    val chosenPoster = cardPosterUrl(media)
    val artwork = when (source) {
        InfoBackdropSource.BANNER -> media.bannerImage?.takeIf { it.isNotBlank() }
            ?: chosenPoster
        InfoBackdropSource.COVER -> chosenPoster
        InfoBackdropSource.OFF -> null
    }
    val surface = MaterialTheme.colorScheme.surface
    // Animated rather than switched: the three radii are far enough apart that a
    // snap reads as the image being replaced, and the blur is one of the settings
    // a reader tries both ways. `FastSpatialDp` is the shared Dp spring, so the
    // motion style chosen in Interface reaches this too.
    val radius by animateDpAsState(
        targetValue = blur.radiusDp.dp,
        animationSpec = ExpressiveMotion.FastSpatialDp,
        label = "info_backdrop_blur"
    )
    // Crisp is radius 0, and `Modifier.blur(0.dp)` still asks Compose for an
    // offscreen render layer. Skipping the modifier is what makes "off" actually
    // cost nothing; the same rule the new Home's backdrop follows.
    val artworkBlur = if (radius > 0.dp) Modifier.blur(radius) else Modifier

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(surface)
            .testTag("info_page_backdrop")
    ) {
        if (artwork != null) {
            Crossfade(
                targetState = artwork,
                animationSpec = ExpressiveMotion.FastEffects,
                label = "info_page_backdrop_artwork",
                modifier = Modifier.fillMaxSize()
            ) { active ->
                AsyncImage(
                    model = active,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    // Top-anchored for the same reason as the hero: a wide banner's
                    // useful pixels are its top edge, and a centred crop cuts the
                    // same faces twice.
                    alignment = Alignment.TopCenter,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val scroll = collapse()
                            // A gentle drift, not the hero's full travel: this is
                            // already behind the whole page, and moving it as far as
                            // the hero does would expose the surface at its bottom
                            // edge during a long scroll.
                            translationY = scroll * BACKDROP_PARALLAX.toPx()
                            scaleX = BACKDROP_SCALE
                            scaleY = BACKDROP_SCALE
                        }
                        .then(artworkBlur)
                        .unseamedCover()
                )
            }

            // The theme's colour, under the legibility scrim. Tinted is a wash of
            // the scheme's primary over the artwork, which is what makes a brown
            // or grey key visual belong to the app rather than to its own poster;
            // natural leaves the pixels alone. It sits *below* the scrim so the
            // cards' text is still read against `surface`, not against pigment.
            if (tint == InfoBackdropTint.TINTED) {
                // The poster's own colour when the session has read it, not only
                // the brand's primary: this is the wash that makes the page belong
                // to the artwork, and it now follows BetterPosters art too.
                val dark = LocalAppDarkTheme.current
                val wash = CoverColors.color(chosenPoster)?.let {
                    DynamicThemeBuilder.accentColor(it, dark = dark)
                } ?: MaterialTheme.colorScheme.primary
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(wash.copy(alpha = 0.18f))
                        .testTag("info_page_backdrop_tint")
                )
            }

            // The hand-off from the hero - see the KDoc. Drawn in the draw phase
            // rather than composed as a brush because [heroBottom] changes on
            // every scrolled frame; that invalidates this draw without
            // recomposing the backdrop, the same bargain the parallax makes.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val top = heroBottom()
                        val fade = HERO_HANDOFF_FADE.toPx()
                        // First frame: the hero is drawn but the list has not
                        // measured its items yet, so there is no edge to anchor
                        // to. Stay on surface rather than showing the artwork
                        // through the gap for the frame before it arrives.
                        if (top == Float.POSITIVE_INFINITY) {
                            drawRect(surface)
                            return@drawBehind
                        }
                        // The list stops reporting item zero the moment its last
                        // pixel leaves the viewport, so the measured edge would
                        // jump from 0 to "gone" and the artwork would pop on at
                        // the top of the page. Fading the hand-off out across the
                        // hero's last fade-length of travel - the same distance it
                        // covers below itself - gets it to zero before the item is
                        // disposed, so the artwork arrives as the header leaves
                        // instead of appearing all at once once it is gone.
                        val strength = if (top == Float.NEGATIVE_INFINITY) {
                            0f
                        } else {
                            (top / fade).coerceIn(0f, 1f)
                        }
                        if (strength <= 0f) return@drawBehind
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to surface.copy(alpha = strength),
                                1f to surface.copy(alpha = 0f),
                                startY = top,
                                endY = top + fade
                            )
                        )
                    }
            )

            // The legibility layer. Weighted from light at the top - where the hero
            // has already painted its own scrim - to heavier where the page's dense
            // cards are. `surface`, not black: see the KDoc.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to surface.copy(alpha = 0.30f),
                            0.35f to surface.copy(alpha = 0.62f),
                            1f to surface.copy(alpha = 0.88f)
                        )
                    )
            )
        }
    }
}

/**
 * How far the backdrop travels against a fully collapsed hero.
 *
 * Read as a fraction of the hero's collapse rather than of the whole page: the
 * page has no single length to normalise against (its items differ in height by an
 * order of magnitude), and the first 200dp are the stretch where the artwork is
 * still the subject rather than a wash.
 */
private val BACKDROP_PARALLAX = 38.dp

/**
 * One percent of over-cover, for the same rounding reason as the hero.
 *
 * `Crop` rounds its destination to whole pixels and can land a row short; the
 * missing row would show the plain surface along the top edge of a page that is
 * otherwise full of artwork.
 */
private const val BACKDROP_SCALE = 1.01f

/**
 * How far below the hero's edge the page's artwork takes over.
 *
 * Long enough that the artwork arrives under the content rather than at the seam
 * (the bug was a hard band in the 16dp item gap), and short enough that the page
 * is visibly the title's artwork by the time the first card has been read.
 */
private val HERO_HANDOFF_FADE = 200.dp
