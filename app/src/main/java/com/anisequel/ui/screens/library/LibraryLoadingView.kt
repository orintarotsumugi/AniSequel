package com.anisequel.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.ui.components.ShimmerBox
import com.anisequel.ui.components.rememberShimmerPhase
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator

/** Covers drawn while the first library request is in flight: four rows of three. */
private const val LIBRARY_SKELETON_COVERS = 12

/** AniList covers are 2:3, the same shape the real grid lays out. */
private const val LIBRARY_POSTER_ASPECT = 2f / 3f

/**
 * The Library's waiting state, drawn as the grid that is coming.
 *
 * This page used to hand its loading state to the profile's full-screen spinner.
 * On the profile that is right - the page is a header and a handful of rows whose
 * shape changes with the person. On the Library it is not: the page is one thing,
 * a three-column wall of 2:3 posters, and it looks the same for every account. A
 * lone spinner on a page that is about to be a grid also meant the first paint
 * jumped from "one dot in the middle" to "twelve posters plus a filter bar" in a
 * single frame.
 *
 * The header card is the expressive part: the Material 3 morphing loader beside a
 * title and the request's own sentence, on a `primaryContainer` surface. It says
 * *what* is happening - "Connecting to AniList" is different from "Loading your
 * AniList profile" - instead of leaving the reader to guess from a dot. Everything
 * underneath it is the exact shape and spacing of `ProfileLibraryTab`'s grid, so
 * the covers replace the shimmer in place rather than rearranging the page.
 *
 * One shimmer clock for the whole grid, not one per cell. See
 * [rememberShimmerPhase]: twelve independent sweeps would read as a flicker, and
 * each would recompose its own cell every frame.
 */
@Composable
fun LibraryLoadingView(
    message: String,
    modifier: Modifier = Modifier
) {
    val phase = rememberShimmerPhase()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("library_loading")
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExpressiveLoadingIndicator(
                    modifier = Modifier.size(30.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Loading your library",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("library_loading_grid"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false
        ) {
            items(LIBRARY_SKELETON_COVERS) { index ->
                Column {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(LIBRARY_POSTER_ASPECT),
                        shape = MaterialTheme.shapes.medium,
                        phase = phase
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    // The caption under a real poster is one or two lines; this
                    // varies the widths so twelve cells do not read as a table.
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(if (index % 3 == 0) 0.88f else 0.64f)
                            .height(10.dp),
                        phase = phase
                    )
                }
            }
        }
    }
}
