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
import androidx.compose.foundation.lazy.grid.items
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

/** AniList covers are 2:3, the same shape the real grid lays out. */
private const val LIBRARY_POSTER_ASPECT = 2f / 3f

/**
 * The Library's waiting state, drawn in the shape of the selected library view.
 *
 * The skeleton used to assume a 3-column poster grid. That matched only one view
 * mode: list/table/grouped readers saw a grid placeholder, and shelf/cards/covers
 * readers saw the wrong column count and caption layout before the real content
 * arrived. The loading state now follows the configured view mode so the first
 * real layout replaces placeholders in place.
 */
@Composable
fun LibraryLoadingView(
    message: String,
    modifier: Modifier = Modifier
) {
    val phase = rememberShimmerPhase()
    val viewMode = com.anisequel.ui.theme.LocalLibraryViewMode.current

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

        when (viewMode) {
            com.anisequel.domain.ui.LibraryViewMode.LIST,
            com.anisequel.domain.ui.LibraryViewMode.TABLE,
            com.anisequel.domain.ui.LibraryViewMode.GROUPED -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("library_loading_grid"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    repeat(if (viewMode == com.anisequel.domain.ui.LibraryViewMode.TABLE) 7 else 8) { index ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ShimmerBox(
                                modifier = Modifier.size(width = 40.dp, height = 56.dp),
                                shape = MaterialTheme.shapes.small,
                                phase = phase
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                ShimmerBox(
                                    modifier = Modifier
                                        .fillMaxWidth(if (index % 3 == 0) 0.72f else 0.54f)
                                        .height(12.dp),
                                    phase = phase
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                ShimmerBox(
                                    modifier = Modifier
                                        .fillMaxWidth(0.36f)
                                        .height(10.dp),
                                    phase = phase
                                )
                            }
                            if (viewMode == com.anisequel.domain.ui.LibraryViewMode.TABLE) {
                                Spacer(modifier = Modifier.width(10.dp))
                                ShimmerBox(
                                    modifier = Modifier.size(width = 36.dp, height = 10.dp),
                                    phase = phase
                                )
                            }
                        }
                    }
                }
            }
            else -> {
                val columns = viewMode.columns.coerceAtLeast(1)
                val showCaptions = viewMode != com.anisequel.domain.ui.LibraryViewMode.COVERS &&
                    viewMode != com.anisequel.domain.ui.LibraryViewMode.SHELF
                val itemCount = (columns * 4).coerceIn(6, 20)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("library_loading_grid"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    userScrollEnabled = false
                ) {
                    items(itemCount) { index ->
                        Column {
                            ShimmerBox(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(LIBRARY_POSTER_ASPECT),
                                shape = MaterialTheme.shapes.medium,
                                phase = phase
                            )
                            if (showCaptions) {
                                Spacer(modifier = Modifier.height(5.dp))
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
        }
    }
}
