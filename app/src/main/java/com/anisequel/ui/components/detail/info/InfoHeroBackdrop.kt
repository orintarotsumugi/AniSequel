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
 * ## Why the poster is underneath and the banner is on top
 *
 * The summary node arrives without a banner, so the first frame draws the poster;
 * the detail response then crossfades the banner in over it. That used to be one
 * `Crossfade` whose target was `banner ?: posterUrl`, which meant the poster was
 * *replaced* rather than covered: a banner whose image failed to decode, or one
 * still in flight, left the hero on its placeholder tint even though the artwork
 * was already in memory from the card behind the page. Two layers make the banner
 * an enhancement - the poster always paints, and a null banner simply fades back
 * to it.
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
    // One transform for both layers, so the poster and the banner cannot drift
    // apart during the parallax.
    val fullBleed = Modifier
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

    // The key visual is the base layer, always. It is the artwork the reader just
    // tapped, it is already in Coil's memory from the card behind this page, and
    // it paints on the first frame. The banner used to be the only layer once the
    // detail landed, so a banner URL that failed to decode - or one still on the
    // wire - emptied the top of the page back to its placeholder tint. The wide
    // image is an enhancement over the poster now, not a replacement for it.
    if (posterUrl != null) {
        AsyncImage(
            model = posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            // Top-anchored for the same reason as the banner: a cover's faces and
            // title sit near the top, and a centre crop makes a portrait poster into
            // a band of clothing.
            alignment = Alignment.TopCenter,
            modifier = fullBleed
        )
    }

    // The banner dissolves over the poster when it arrives, and dissolves away
    // again if the detail answer has none - which is the same crossfade in both
    // directions, and the reason the target is the nullable banner rather than
    // `banner ?: posterUrl`.
    Crossfade(
        targetState = banner,
        animationSpec = ExpressiveMotion.FastEffects,
        label = "hero_banner",
        // Without this the Crossfade is measured from children that all use
        // `matchParentSize`, which contributes no size of its own.
        modifier = Modifier.matchParentSize()
    ) { backdrop ->
        if (backdrop != null) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = fullBleed
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
