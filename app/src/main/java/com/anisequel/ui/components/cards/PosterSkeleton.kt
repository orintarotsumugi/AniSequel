package com.anisequel.ui.components.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anisequel.ui.components.ShimmerBox
import com.anisequel.ui.components.rememberShimmerPhase

/**
 * One poster-shaped skeleton: the artwork block, then the two lines the card draws
 * under it.
 *
 * A grid that is waiting for its list is waiting for *posters*, and the shape it holds
 * open should be the shape that arrives - otherwise the finished cards land in the
 * footprint of something that was never the same size. The blocks are the app's own
 * [ShimmerBox] rather than a progress indicator, for the reason the search skeleton
 * gives: the shimmer already says "this is coming", and a second animated loop beside
 * it says the same thing twice.
 *
 * [phase] is hoisted by the caller so every cell on screen sweeps together; see
 * [rememberShimmerPhase].
 */
@Composable
fun PosterSkeletonCell(
    phase: State<Float>,
    modifier: Modifier = Modifier,
    aspect: Float = POSTER_ASPECT
) {
    val artworkShape = MaterialTheme.shapes.small
    Column(modifier = modifier) {
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect),
            shape = artworkShape,
            phase = phase
        )
        Spacer(modifier = Modifier.height(5.dp))
        // The name, then the one line of context the card puts under it.
        ShimmerBox(Modifier.fillMaxWidth(0.85f).height(12.dp), phase = phase)
        Spacer(modifier = Modifier.height(4.dp))
        ShimmerBox(Modifier.fillMaxWidth(0.5f).height(9.dp), phase = phase)
    }
}

/**
 * A still mosaic of [PosterSkeletonCell]s, for a grid whose list has not arrived yet.
 *
 * Rows rather than a nested lazy grid: this is drawn as one item inside the grid that
 * is already scrolling, and a second scrollable surface inside the first is how a
 * pull-to-refresh ends up fighting the list it refreshes. [columns] is the column count
 * the real grid is using, and the gaps are the ones it puts between cells, so the
 * placeholders sit where the posters will.
 */
@Composable
fun PosterSkeletonMosaic(
    columns: Int,
    modifier: Modifier = Modifier,
    rows: Int = 3
) {
    val phase = rememberShimmerPhase()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(columns) {
                    PosterSkeletonCell(phase = phase, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
