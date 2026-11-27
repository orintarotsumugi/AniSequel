package com.anisequel.ui.screens.tier

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anisequel.domain.tier.TierDefinition

/**
 * How many tier bars a preview shows before it stops being a shape and becomes a
 * wall. Five rows is what the standard list has; longer lists show their first
 * five rather than squeezing twelve into the same 26dp.
 */
internal const val TIER_PREVIEW_MAX_BARS = 5

/**
 * A list's tiers as a small stack of bars, in list order.
 *
 * The library used to identify a saved list by its name alone, so two lists of
 * the same shelf - "Mine", "Mine 2" - were rows of identical text and the only
 * way to tell which was which was to open it. The bars are the tier list's own
 * colours in its own order, which is the one thing about a list that is
 * recognisable before any of its titles are.
 *
 * Drawn in one [Canvas] rather than as a `Column` of boxes: this sits inside a
 * `LazyColumn` row and a preview of five shapes does not need five layout nodes
 * to say "S is red, A is orange". It carries no semantics on purpose - it repeats
 * the list's identity, and a screen reader announcing "five coloured bars" before
 * the name is noise.
 */
@Composable
internal fun TierListPreview(
    tiers: List<TierDefinition>,
    modifier: Modifier = Modifier,
    width: Dp = 26.dp,
    height: Dp = 26.dp,
    maxBars: Int = TIER_PREVIEW_MAX_BARS
) {
    if (tiers.isEmpty() || maxBars <= 0) return
    val colors = remember(tiers, maxBars) {
        tiers.take(maxBars).map { tierRowColor(it.colorArgb) }
    }
    Canvas(modifier = modifier.size(width, height).testTag("tier_list_preview")) {
        // The gap is a share of the height, so one row and five rows both fill
        // the same box instead of a one-tier list drawing a single hairline.
        val gap = size.height * 0.08f
        val barHeight = ((size.height - gap * (colors.size - 1)) / colors.size)
            .coerceAtLeast(1f)
        val radius = CornerRadius(barHeight / 2f, barHeight / 2f)
        colors.forEachIndexed { index, color ->
            drawRoundRect(
                color = color,
                topLeft = Offset(0f, index * (barHeight + gap)),
                size = Size(size.width, barHeight),
                cornerRadius = radius
            )
        }
    }
}
