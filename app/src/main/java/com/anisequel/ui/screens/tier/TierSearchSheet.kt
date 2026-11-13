package com.anisequel.ui.screens.tier

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.domain.tier.TIER_SEARCH_MAX_LENGTH
import com.anisequel.domain.tier.TierCandidate
import com.anisequel.domain.tier.TierMediaType
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.rememberFullHeightSheetState

/**
 * The board's own search: a name search across every title the open list can
 * rank, laid out as pages of a 3x3 poster grid.
 *
 * ## Why this is not the filter's name field
 *
 * The filter already has a name query, and that query is a *filter*: it hides
 * everything that does not match, which is the right tool for narrowing a wall of
 * posters already on the board. This is the opposite question - "show me only the
 * matches, with the artwork large enough to recognise" - and it has an action on
 * each result, so it is a picker rather than a sieve. Sharing the field would mean
 * one control whose effect depends on which sheet it is in.
 *
 * ## Why pages of nine
 *
 * A vertical list of 159 rows is a list to scroll, not a search to scan. Nine
 * posters across three rows is the largest set that still reads in one glance, and
 * swiping sideways keeps the rest one gesture away without turning the result into
 * an endless column. The pager is finite on purpose: an "infinite" pager over a
 * finite result set scrolls forever past the same posters, which looks like more
 * results than there are.
 *
 * ## The plus is the action
 *
 * A poster is a thing to look at; the plus is what files it. Tapping either is the
 * same choice, but the plus is the control that says what will happen, and it is
 * drawn on the artwork the reader just recognised. The picker hands the candidate
 * back to the host, which opens the tier chooser on top of the board - the same
 * placement sheet every other add uses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TierSearchSheet(
    candidates: List<TierCandidate>,
    mediaType: TierMediaType,
    onPick: (TierCandidate) -> Unit,
    onDismiss: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query, candidates) {
        val tokens = query.trim()
            .lowercase(java.util.Locale.ROOT)
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) {
            candidates
        } else {
            candidates.filter { candidate -> tokens.all { it in candidate.searchKey } }
        }
    }
    val pages = remember(results) { results.chunked(SEARCH_PAGE_SIZE) }
    val pager = rememberPagerState(pageCount = { pages.size })
    val sheetState = rememberFullHeightSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 14.dp)
                .testTag("tier_search_sheet")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 10.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Find a title",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (query.isBlank()) {
                            "Every ${mediaType.libraryLabel} title you can rank."
                        } else {
                            "${results.size} ${if (results.size == 1) "match" else "matches"}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                CloseSearchButton(onDismiss = onDismiss)
            }

            OutlinedTextField(
                value = query,
                onValueChange = { if (it.length <= TIER_SEARCH_MAX_LENGTH) query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .testTag("tier_search_field"),
                placeholder = { Text("Title, romaji or native name") },
                leadingIcon = {
                    Icon(
                        imageVector = AppVectorIcons.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = AppVectorIcons.Close,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                shape = ExpressiveShapes.pill
            )

            when {
                pages.isEmpty() -> Text(
                    // The copy names what was searched rather than saying "no
                    // results": the pool is the viewer's own list, and a title
                    // they have not added to AniList cannot be found here. A blank
                    // query on an unloaded pool is its own sentence: quoting an
                    // empty string read as "nothing matches ””".
                    text = if (query.isBlank() && candidates.isEmpty()) {
                        "Your ${mediaType.libraryLabel} list has not loaded yet, so " +
                            "there is nothing to find."
                    } else {
                        "Nothing in your ${mediaType.libraryLabel} list matches “$query”."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp)
                )

                else -> {
                    HorizontalPager(
                        state = pager,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                        pageSpacing = 12.dp,
                        modifier = Modifier.testTag("tier_search_pager")
                    ) { page ->
                        SearchPage(
                            page = pages[page],
                            onPick = onPick
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${pager.currentPage + 1} / ${pages.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (pages.size > 1) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "·  swipe for more",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CloseSearchButton(onDismiss: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    IconButton(
        onClick = onDismiss,
        modifier = Modifier.bouncyPress(
            pressedScale = 0.9f,
            interactionSource = interactionSource
        ),
        interactionSource = interactionSource
    ) {
        Icon(imageVector = AppVectorIcons.Close, contentDescription = "Close search")
    }
}

/** Nine candidates, three across and three down, every cell the same size. */
@Composable
private fun SearchPage(page: List<TierCandidate>, onPick: (TierCandidate) -> Unit) {
    // Centred rather than pinned to the leading edge. A 3x3 grid of 92dp cells is
    // 292dp wide while a 360dp phone leaves 320dp after the pager's 20dp gutters,
    // so the old left-aligned grid carried a 28dp hole on one side and read as a
    // column that had failed to fill. Each row gets the same treatment, including
    // the padded last row, so the columns stay aligned.
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        page.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                row.forEach { candidate ->
                    SearchPoster(candidate = candidate, onPick = onPick)
                }
                // A short last row keeps the grid's columns aligned instead of
                // letting the two remaining posters stretch.
                repeat(3 - row.size) { Spacer(modifier = Modifier.width(SEARCH_CELL_WIDTH)) }
            }
        }
    }
}

@Composable
private fun SearchPoster(candidate: TierCandidate, onPick: (TierCandidate) -> Unit) {
    val placeholder = rememberVectorPainter(AppVectorIcons.List)
    Column(
        modifier = Modifier
            .width(SEARCH_CELL_WIDTH)
            .bouncyPress(pressedScale = 0.96f)
            .clickable(role = Role.Button) { onPick(candidate) }
            .testTag("tier_search_result_${candidate.mediaId}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(ExpressiveShapes.SmallCorner))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            AsyncImage(
                model = candidate.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
                placeholder = placeholder,
                error = placeholder,
                fallback = placeholder
            )

            // The big plus sits on the artwork rather than under it: the poster is
            // the thing being chosen, and a row of buttons below the grid would
            // make the reader match a title to a control.
            Surface(
                onClick = { onPick(candidate) },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shadowElevation = 3.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add ${candidate.title} to a tier",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = candidate.title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Nine cells fit a 360dp phone with the sheet's 20dp gutters and 8dp gaps. */
private val SEARCH_CELL_WIDTH = 92.dp
private const val SEARCH_PAGE_SIZE = 9
