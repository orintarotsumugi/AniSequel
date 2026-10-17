package com.anisequel.ui.screens.studio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.StudioNode
import com.anisequel.domain.studio.StudioYearShelf
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveEmptyOrb
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.viewmodel.StudioViewModel
import java.text.NumberFormat
import java.util.Locale

/**
 * The studio page's parts: the header, the shelf headings, and the three states the
 * page can end in.
 *
 * Split from `StudioScreen.kt` by responsibility rather than by line count: that file
 * is about *what the page is* - which studio, which shelves, what happens when a
 * title is tapped - and this one is presentation that would look the same on any
 * screen that lists a company's work. `internal` rather than `private` because they
 * are now in two files, which is the cost of the split and the reason it is worth
 * naming: a `private` composable cannot leave its file, so this is the boundary that
 * says these belong together.
 */

/** The name, what kind of company it is, and how much AniList has it doing. */
@Composable
internal fun StudioIdentity(
    studio: StudioNode?,
    fallbackName: String,
    titleCount: Int?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = studio?.name ?: fallbackName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Nothing at all until the answer lands, rather than a row of empty capsules:
        // the facts are all optional, and a header that draws three blanks for the
        // length of a round trip reads as a page with nothing in it.
        if (studio == null) return@Column

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            studio.isAnimationStudio?.let { animationStudio ->
                StudioMetaChip(
                    icon = AppVectorIcons.Studio,
                    // AniList's own flag, not a guess from the name: the same
                    // connection carries publishers and licensors, and calling
                    // Kodansha an animation studio would be wrong about the thing
                    // the page is.
                    label = if (animationStudio) "Animation studio" else "Publisher",
                    accent = true
                )
            }
            studio.favourites?.let { favourites ->
                StudioMetaChip(
                    icon = AppVectorIcons.ActivityLiked,
                    label = "${NumberFormat.getIntegerInstance(Locale.getDefault()).format(favourites)} favourites"
                )
            }
            titleCount?.takeIf { it > 0 }?.let { count ->
                StudioMetaChip(
                    icon = AppVectorIcons.List,
                    label = if (count == 1) "1 title" else "$count titles"
                )
            }
        }
    }
}

/** One fact about the studio, as a small capsule. */
@Composable
private fun StudioMetaChip(
    icon: ImageVector,
    label: String,
    accent: Boolean = false
) {
    Surface(
        shape = ExpressiveShapes.pill,
        color = if (accent) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (accent) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (accent) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/**
 * A year, how many titles are in it, and a rule out to the edge.
 *
 * The count is in the heading rather than left to be counted, because the shelf is
 * horizontal: the reader can see three of nine posters and nothing on screen says
 * that six more are off to the right.
 */
@Composable
internal fun StudioShelfHeader(shelf: StudioYearShelf) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${shelf.label} (${shelf.titles.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(10.dp))
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * The end of the list: what is happening, or why it stopped.
 *
 * Three states and not one, because they mean different things to the reader - a
 * page still arriving, a page that failed, and the end of the filmography. The end
 * state is spelled out with the count so a short list is not mistaken for a list
 * that failed to load.
 */
@Composable
internal fun StudioFooter(
    success: StudioViewModel.StudioUiState.Success,
    name: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            success.loadMoreError != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Couldn't load more of this studio - ${success.loadMoreError}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                TextButton(onClick = onRetry, modifier = Modifier.testTag("studio_load_more_retry")) {
                    Text("Try again")
                }
            }

            success.isLoadingMore -> ExpressiveLoadingIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.primary
            )

            !success.hasMore && success.titleCount > 0 -> Text(
                text = if (success.titleCount == 1) {
                    "That is the one title AniList credits $name with"
                } else {
                    "That is all ${success.titleCount} titles AniList credits $name with"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** The first page failed: say so plainly, with the way back to it. */
@Composable
internal fun StudioFailure(message: String, onRetry: () -> Unit) {
    StudioComplement(
        icon = AppVectorIcons.Warning,
        title = "Couldn't load this studio",
        detail = message,
        action = {
            Button(onClick = onRetry, modifier = Modifier.testTag("studio_retry_button")) {
                Text("Try again")
            }
        }
    )
}

/** The shared shape of an empty or failed page: orb, headline, explanation, action. */
@Composable
private fun StudioComplement(
    icon: ImageVector,
    title: String,
    detail: String,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ExpressiveEmptyOrb(icon = icon, modifier = Modifier.size(84.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp)
        )
        action?.let {
            Spacer(modifier = Modifier.height(16.dp))
            it()
        }
    }
}

/** The empty state, spelled here so [StudioFailure] and it share one shape. */
@Composable
internal fun StudioEmpty() {
    StudioComplement(
        icon = AppVectorIcons.Studio,
        title = "Nothing credited yet",
        detail = "AniList has no titles listed under this studio."
    )
}
