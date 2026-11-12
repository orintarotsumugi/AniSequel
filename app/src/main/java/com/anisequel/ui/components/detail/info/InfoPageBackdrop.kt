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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.posterUrl
import com.anisequel.data.repository.InfoBackdropBlur
import com.anisequel.data.repository.InfoBackdropSource
import com.anisequel.data.repository.InfoBackdropTint
import com.anisequel.ui.components.cards.unseamedCover
import com.anisequel.ui.components.expressive.ExpressiveMotion

/**
 * The artwork behind the whole Info page, under the cards.
 *
 * ## Why this is not the hero's backdrop
 *
 * The hero already draws its own full-bleed artwork and hands it over to the page
 * with a gradient that ends on `surface`. Below that, the page was a flat container
 * colour - the title's art stopped at the first card and the rest of the scroll was
 * grey. This draws the *same* image full-screen behind the list instead, so the
 * colour continues past the hero, and the hero's own bottom fade becomes the seam
 * between two treatments of one picture rather than the end of it.
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
    modifier: Modifier = Modifier
) {
    val artwork = when (source) {
        InfoBackdropSource.BANNER -> media.bannerImage?.takeIf { it.isNotBlank() }
            ?: media.posterUrl
        InfoBackdropSource.COVER -> media.posterUrl
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
                        .blur(radius)
                        .unseamedCover()
                )
            }

            // The theme's colour, under the legibility scrim. Tinted is a wash of
            // the scheme's primary over the artwork, which is what makes a brown
            // or grey key visual belong to the app rather than to its own poster;
            // natural leaves the pixels alone. It sits *below* the scrim so the
            // cards' text is still read against `surface`, not against pigment.
            if (tint == InfoBackdropTint.TINTED) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                        .testTag("info_page_backdrop_tint")
                )
            }

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
