package com.anisequel.ui.components.detail.info

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.anisequel.data.model.MediaNode
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.openExternalUrlOrCopy
import com.anisequel.ui.components.expressive.bouncyPress
import java.util.Locale

/**
 * The title's official links: the site, the social accounts, the reference pages.
 *
 * ## Why the streaming links are not here
 *
 * They already have a row of their own - "Where to Watch" - and it is ordered
 * differently on purpose: that card answers "where can I watch this", this one
 * answers "where else does this exist". Listing Crunchyroll in both made the page
 * look like it had two copies of the same section, which is exactly the duplicate
 * the reference avoids.
 *
 * A link with no site name or no URL is dropped rather than drawn as a blank chip:
 * AniList's `externalLinks` is community-editable, and a chip that opens nothing is
 * worse than one fewer chip.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InfoExternalLinksSection(
    media: MediaNode,
    modifier: Modifier = Modifier
) {
    val links = media.externalLinks.orEmpty()
        .filter { !it.site.isNullOrBlank() && !it.url.isNullOrBlank() }
        .filterNot { it.type.equals(STREAMING, ignoreCase = true) }
        .distinctBy { (it.site + it.url).lowercase(Locale.ROOT) }
    if (links.isEmpty()) return

    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("info_external_links_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        InfoAccentHeading(title = "External Links")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            links.forEach { link ->
                val site = link.site.orEmpty()
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .bouncyPress(pressedScale = 0.96f)
                        .clickable(role = Role.Button) {
                            openExternalUrlOrCopy(context, link.url.orEmpty())
                        }
                        .testTag("external_link_$site")
                ) {
                    Row(
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = AppVectorIcons.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = site,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/** AniList's `ExternalLinkType` value that means "a place to watch this". */
private const val STREAMING = "STREAMING"
