package com.anisequel.ui.screens.nova

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.MissedSequel
import com.anisequel.data.model.posterColor
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.cards.PosterPlaceholder
import com.anisequel.ui.components.cards.QuickAddPlanningButton
import com.anisequel.ui.components.cards.toCoverColorOrNull
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.nova.NovaSurface

/**
 * A missed sequel drawn as one wide still.
 *
 * The banner puts its reading column over the artwork; this one gives the picture
 * the whole card and moves the controls into a strip *below* it, so nothing is drawn
 * over the image except the title it has to carry. That is the choice between the
 * two: a banner is a poster with a caption, a spotlight is a frame with a caption.
 *
 * The strip is measured rather than overlaid, so a title that wraps to two lines or
 * a larger Text setting grows the card instead of covering the button.
 */
@Composable
internal fun NovaSequelSpotlight(
    sequel: MissedSequel,
    onOpen: () -> Unit,
    onAddToPlanning: () -> Unit,
    onAddToWatching: () -> Unit,
    onHide: () -> Unit,
    holdDurationSeconds: Int,
    swipeEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    NovaSurface(
        modifier = modifier
            .fillMaxWidth()
            .bouncyPress(pressedScale = 0.985f, interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onOpen)
            .clip(RoundedCornerShape(22.dp)),
        tonalElevation = true
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(SpotlightArtAspect)
            ) {
                PosterPlaceholder(
                    title = sequel.sequelTitle,
                    tint = sequel.sequelMedia.posterColor.toCoverColorOrNull(),
                    modifier = Modifier.matchParentSize()
                )
                AsyncImage(
                    model = sequel.sequelCoverUrl,
                    contentDescription = sequel.sequelTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // The title is the only thing over the picture, and the scrim is
                // what makes it readable over artwork of any brightness.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(0.82f)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = sequel.relationLabel.uppercase(java.util.Locale.ROOT),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = sequel.sequelTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickAddPlanningButton(
                    mediaId = sequel.sequelId,
                    isAddedToPlanning = sequel.isAddedToPlanning,
                    isAddedToWatching = sequel.isAddedToWatching,
                    isBusy = sequel.isSavingToList,
                    onAddToPlanning = onAddToPlanning,
                    onAddToWatching = onAddToWatching,
                    holdDurationSeconds = holdDurationSeconds,
                    swipeEnabled = swipeEnabled,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onHide,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("nova_sequel_spotlight_hide_${sequel.sequelId}")
                ) {
                    Icon(
                        imageVector = AppVectorIcons.Close,
                        contentDescription = "Not interested",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/** Width over height of a [NovaSequelSpotlight]'s artwork. */
private const val SpotlightArtAspect = 1.6f
