package com.anisequel.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.MissedSequel
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.bouncyPress

/**
 * The "Sequel to …" line, which is also the way to the *parent*.
 *
 * The card below it is about the sequel; this row names the entry the gap was
 * measured against, so tapping it opens that one. It used to be a plain `Row` -
 * the sentence was there and the title it named was not a target, which is what
 * made a tap on the most informative line on the card do nothing at all.
 *
 * [onOpenParent] is null where there is no parent to open - a preview, or a row
 * drawn inside a screen with no detail sheet - and then the row keeps its old
 * non-interactive shape rather than looking tappable and swallowing the tap.
 */
@Composable
fun SequelRelationBanner(
    sequel: MissedSequel,
    modifier: Modifier = Modifier,
    onOpenParent: (() -> Unit)? = null
) {
    val relationColor = if (sequel.isEarlierInFranchise) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.primary
    }

    // Its own interaction source: the card is one big clickable, and a press that
    // travelled to both would spring the whole row while the reader is aiming at
    // this line.
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(
                if (onOpenParent == null) {
                    Modifier
                } else {
                    Modifier
                        .testTag("sequel_parent_link_" + sequel.parentId)
                        .bouncyPress(pressedScale = 0.98f, interactionSource = interactionSource)
                        // The `interactionSource`/`indication`/`onClickLabel` overload
                        // takes no modifier of its own, so the tag belongs in the chain
                        // above it - that is where the first version of this put nothing
                        // and CI put the whole call out of the build.
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClickLabel = "Open " + sequel.parentTitle
                        ) { onOpenParent() }
                }
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (sequel.parentCoverUrl != null) {
            AsyncImage(
                model = sequel.parentCoverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(22.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(
                        sequel.parentCoverColor.toCoverColorOrNull()
                            ?: MaterialTheme.colorScheme.surfaceContainerHighest
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Icon(
            imageVector = if (sequel.isEarlierInFranchise) {
                AppVectorIcons.FranchiseBranch
            } else {
                AppVectorIcons.SequelJump
            },
            contentDescription = null,
            tint = relationColor,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = sequel.relationLabel,
            style = MaterialTheme.typography.labelMedium,
            color = relationColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = sequel.parentTitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
