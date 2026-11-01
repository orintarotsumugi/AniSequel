package com.anisequel.ui.components.detail.info

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.ui.components.cards.unseamedCover
import com.anisequel.ui.components.expressive.ExpressiveMotion

/**
 * The hero's full-width artwork, under the scrim and the lockup.
 *
 * ## Two treatments, not one stretched cover fit
 *
 * It used to be one `Crop` fit for both images. For the key visual that is right:
 * a 2:3 poster covers a 340dp hero by cropping its top and bottom, and what is
 * left is still the artwork the reader recognised from the row they tapped. For
 * the banner it was wrong in two ways at once. An AniList banner is about
 * 1900x400; covering the hero's box scales it up over 2x and throws away the
 * left and right thirds - the artwork arrives blurry and "stretched", which is
 * exactly what a key art *strip* is not meant to be. And the rounded pixel
 * destination of `Crop` could come out a row short of the box, so the hero's
 * own accent-tinted backdrop showed as a coloured line across the top.
 *
 * So the banner now draws at its own ratio, full width, flush with the hero's
 * top edge: whole, sharp, and nothing of it stretched. The space it leaves
 * below is what the scrim's own gradient was always ending on - the sheet's
 * surface - and the lockup sits on that fade rather than on art it would fight.
 * The key visual keeps the full-bleed treatment.
 *
 * ## Why both branches still cover as they collapse
 *
 * The parallax is a draw-phase transform (see [collapse]'s contract on
 * `InfoHero`): the backdrop lags the page by [PARALLAX_TRAVEL] and the full
 * bleed grows by [PARALLAX_ZOOM] so it keeps covering the box it is pulled out
 * of. The lag can only ever expose the top of the box above the viewport's cut
 * line - the page scrolls the whole collapse distance for every unit of
 * collapse, and `PARALLAX_TRAVEL` is far less - so the banner strip lags without
 * a floor. The full bleed additionally starts 1% over-covered
 * ([FULL_BLEED_OVERDRAW]): no sub-pixel rounding can then reach the accent
 * behind the image.
 */
@Composable
internal fun BoxScope.InfoHeroBackdrop(
    banner: String?,
    posterUrl: String?,
    collapse: () -> Float
) {
    // The banner's own ratio, once it reports one. The fallback only sizes the
    // frames before the decode lands, and a frame that is wrong by a hair is
    // re-measured on arrival - `Crop` guarantees no gap at either point.
    var bannerAspect by remember(banner) { mutableFloatStateOf(0f) }

    Crossfade(
        targetState = banner ?: posterUrl,
        animationSpec = ExpressiveMotion.FastEffects,
        label = "hero_backdrop"
    ) { backdrop ->
        when {
            backdrop == null -> Unit
            backdrop == banner -> Box(
                modifier = Modifier.matchParentSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                AsyncImage(
                    model = backdrop,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    onSuccess = { state ->
                        val size = state.painter.intrinsicSize
                        if (size.width > 0f && size.height > 0f) {
                            bannerAspect = size.width / size.height
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(
                            if (bannerAspect > 0f) bannerAspect else DEFAULT_BANNER_ASPECT
                        )
                        .graphicsLayer {
                            val scroll = collapse()
                            translationY = scroll * PARALLAX_TRAVEL.toPx()
                            alpha = 1f - scroll * BACKDROP_HANDOFF
                        }
                        .unseamedCover()
                )
            }
            else -> AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val scroll = collapse()
                        translationY = scroll * PARALLAX_TRAVEL.toPx()
                        val zoom = FULL_BLEED_OVERDRAW + scroll * PARALLAX_ZOOM
                        scaleX = zoom
                        scaleY = zoom
                        alpha = 1f - scroll * BACKDROP_HANDOFF
                    }
            )
        }
    }
}

/** AniList's banner shape (about 1900x400), used until the image reports its own. */
private const val DEFAULT_BANNER_ASPECT = 1900f / 400f

/** How far the backdrop lags the page, at full collapse. */
private val PARALLAX_TRAVEL = 64.dp

/** How much the backdrop grows by the time the hero is out of the way. */
private const val PARALLAX_ZOOM = 0.16f

/**
 * The full bleed's over-cover at rest, as a scale rather than an addition.
 *
 * `Crop`'s destination is rounded to whole pixels and the rounding can come out
 * a row short; the centring offset then lands the missing row at the top, where
 * the hero's accent-tinted backdrop shows through as a coloured line. One
 * percent cannot be out-caled by any rounding, and a cover fit was cropping
 * that much off anyway.
 */
private const val FULL_BLEED_OVERDRAW = 1.01f

/**
 * How much of the backdrop is gone once the hero has fully collapsed.
 *
 * Half, not all: at the end of the collapse the hero is still 140dp tall on screen,
 * and a backdrop that had faded out completely would expose the container colour
 * underneath for the rest of the artwork's travel. Half of it also leaves the show's
 * own palette on screen for the handover into the page.
 */
private const val BACKDROP_HANDOFF = 0.5f
