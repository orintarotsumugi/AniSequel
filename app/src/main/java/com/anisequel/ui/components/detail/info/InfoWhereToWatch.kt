package com.anisequel.ui.components.detail.info

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.MediaExternalLink
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.openExternalUrlOrCopy

/**
 * The services AniList says carry this title.
 *
 * ## Why this is its own card rather than a row in Extras
 *
 * Extras is the things the page can *do* with a title - open the artwork, share
 * the link. This is a fact about where the title is legally available, and it
 * belongs above the synopsis where a reader deciding what to watch next will see
 * it. Extras sits at the bottom of the page and is read after the decision.
 *
 * ## Why the chips are plain buttons, not service logos
 *
 * A logo per service would mean bundling or fetching a dozen brand marks, and the
 * app has a vectors-only rule. The site name is the part that carries the
 * meaning; the outbound arrow says the tap leaves the app. The tap uses the
 * shared [openExternalUrlOrCopy], so a service with no browser to answer it still
 * leaves the URL on the clipboard instead of doing nothing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InfoWhereToWatchCard(
    links: List<MediaExternalLink>,
    modifier: Modifier = Modifier
) {
    val available = links.filter { !it.site.isNullOrBlank() && !it.url.isNullOrBlank() }
    if (available.isEmpty()) return

    val context = LocalContext.current

    InfoCard(
        modifier = modifier.testTag("info_streaming_card"),
        title = "Where to Watch",
        icon = AppVectorIcons.Play
    ) {
        Text(
            text = "AniList's streaming links for this title. Availability depends on your region.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            available.forEach { link ->
                val site = link.site.orEmpty()
                val interactionSource = remember { MutableInteractionSource() }
                Surface(
                    shape = ExpressiveShapes.pill,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .clip(ExpressiveShapes.pill)
                        // Every other chip on this page that opens something
                        // springs under the finger; these were the only ones that
                        // gave no press feedback at all.
                        .bouncyPress(pressedScale = 0.95f, interactionSource = interactionSource)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Button
                        ) {
                            openExternalUrlOrCopy(context, link.url.orEmpty())
                        }
                        .testTag("streaming_link_$site")
                ) {
                    Row(
                        // 48dp, not the ~36dp the text and padding add up to: these
                        // chips are taps that leave the app, and they sit beside
                        // each other in a wrapping row where a miss lands on the
                        // neighbouring service.
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = AppVectorIcons.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = site,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
