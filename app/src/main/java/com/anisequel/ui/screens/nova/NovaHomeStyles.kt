package com.anisequel.ui.screens.nova

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.anisequel.ui.components.cards.QuickAddPlanningButton
import com.anisequel.ui.components.cards.toCoverColorOrNull
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.nova.NovaSurface

@Composable
internal fun NovaSequelBanner(
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
            .height(190.dp)
            .bouncyPress(pressedScale = 0.985f, interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onOpen)
            .clip(RoundedCornerShape(22.dp)),
        tonalElevation = true
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(sequel.sequelMedia.posterColor.toCoverColorOrNull() ?: Color.Transparent)
            ) {
                AsyncImage(
                    model = sequel.sequelCoverUrl,
                    contentDescription = sequel.sequelTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Black.copy(alpha = 0.82f), Color.Transparent)
                            )
                        )
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .align(Alignment.CenterStart)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    text = sequel.relationLabel.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primaryContainer
                )
                Text(
                    text = sequel.sequelTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                QuickAddPlanningButton(
                    mediaId = sequel.sequelId,
                    isAddedToPlanning = sequel.isAddedToPlanning,
                    isAddedToWatching = sequel.isAddedToWatching,
                    isBusy = sequel.isSavingToList,
                    onAddToPlanning = onAddToPlanning,
                    onAddToWatching = onAddToWatching,
                    holdDurationSeconds = holdDurationSeconds,
                    swipeEnabled = swipeEnabled,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            // A real IconButton with a 48dp target rather than a 30dp boxed
            // glyph: the close sits on artwork that is itself one big tap, so a
            // fingertip that overshoots by a few dp dismisses a recommendation
            // instead of opening it. The visual circle stays compact; the target
            // around it is the platform minimum.
            IconButton(
                onClick = onHide,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(48.dp)
                    .testTag("nova_sequel_banner_hide_${sequel.sequelId}")
            ) {
                // The target is 48dp; the dark disc stays 32dp, so the picture
                // is not wearing a larger close button than it did before.
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(ExpressiveShapes.pill)
                        .background(Color.Black.copy(alpha = 0.36f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppVectorIcons.Close,
                        contentDescription = "Not interested",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun NovaSequelShelfCard(
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
            .clickable(interactionSource = interactionSource, indication = null, onClick = onOpen),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = sequel.sequelCoverUrl,
                contentDescription = sequel.sequelTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 82.dp, height = 110.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(sequel.sequelMedia.posterColor.toCoverColorOrNull() ?: Color.Transparent)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sequel.relationLabel.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = sequel.sequelTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                QuickAddPlanningButton(
                    mediaId = sequel.sequelId,
                    isAddedToPlanning = sequel.isAddedToPlanning,
                    isAddedToWatching = sequel.isAddedToWatching,
                    isBusy = sequel.isSavingToList,
                    onAddToPlanning = onAddToPlanning,
                    onAddToWatching = onAddToWatching,
                    holdDurationSeconds = holdDurationSeconds,
                    swipeEnabled = swipeEnabled,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            IconButton(
                onClick = onHide,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("nova_sequel_shelf_hide_${sequel.sequelId}")
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
