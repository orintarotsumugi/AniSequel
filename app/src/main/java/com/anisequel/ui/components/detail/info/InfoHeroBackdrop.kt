package com.anisequel.ui.components.detail.info

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
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
 * ## Why this draws nothing if the Crossfade is not given the box
 *
 * The hero is a `Box`, and this is the first thing in it. It used to pass no
 * modifier to `Crossfade` while both of its branches sized themselves with
 * `matchParentSize()`. `matchParentSize` is the one modifier that takes the
 * parent's size *without contributing to it*, and `Crossfade` measures its own
 * box from its largest child - so the pair measured to zero and neither the
 * banner nor the key visual was ever laid out. The page showed only the hero's
 * placeholder tint, which is exactly the report: AniList stores a wide
 * `bannerImage` for every title and the Info page never appeared to use it.
 *
 * Handing the Crossfade `Modifier.matchParentSize()` fixes the measurement: it
 * fills the hero, and each branch then fills the Crossfade. That is the only
 * structural thing this file needs from its caller.
 *
 * ## Why the banner fills the hero rather than sitting in a native-ratio strip
 *
 * A second, more visible bug hid behind the first. The banner branch drew at
 * AniList's own ~1900x400 ratio, top-aligned - about 86dp on a phone - so even
 * once it measured, it read as a tinted band above a mostly empty hero. The
 * banner is the title's artwork, and the hero is the one place the page can show
 * it: it is now full-bleed, cropped, and anchored at the top, with the scrim's
 * bottom fade handing the artwork over to the page body. The key visual keeps the
 * same full-bleed treatment, so the two images the sheet can receive look like
 * two versions of the same backdrop rather than two different components.
 *
 * ## Why both images share a branch
 *
 * The dissolve is what matters, not where each image came from: the summary node
 * arrives without a banner, so the first frame draws the poster and the detail
 * response crossfades the banner in over it. A `Crossfade` whose content is the
 * same drawing on both states is exactly that dissolve, and it is one
 * `AsyncImage` instead of two that could drift.
 *
 * ## The parallax
 *
 * [collapse] is read inside `graphicsLayer`, so the travel is a draw-phase
 * transform: the page can scroll at 120Hz over an image the layout has not
 * touched. The backdrop lags the page by [PARALLAX_TRAVEL] and grows by
 * [PARALLAX_ZOOM], so it keeps covering the box it is pulled out of, and starts
 * [FULL_BLEED_OVERDRAW] over-covered so no sub-pixel rounding can expose the
 * accent behind it as a coloured line.
 */
@Composable
internal fun BoxScope.InfoHeroBackdrop(
    banner: String?,
    posterUrl: String?,
    collapse: () -> Float
) {
    Crossfade(
        targetState = banner ?: posterUrl,
        animationSpec = ExpressiveMotion.FastEffects,
        label = "hero_backdrop",
        // The whole fix: without this the Crossfade is measured from children
        // that all use `matchParentSize`, which contributes no size of its own.
        modifier = Modifier.matchParentSize()
    ) { backdrop ->
        if (backdrop != null) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // Top-anchored, because a wide banner's most useful pixels are its
                // top edge and a centre crop would cut the same faces twice.
                alignment = Alignment.TopCenter,
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
                    .unseamedCover()
            )
        }
    }
}

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
