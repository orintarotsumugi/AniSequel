package com.anisequel.ui.components.cards

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.MissedSequel
import com.anisequel.ui.components.expressive.bouncyPress

@Composable
fun SequelCard(
    sequel: MissedSequel,
    onClick: () -> Unit,
    onAddToPlanning: () -> Unit,
    /** Stops offering this entry again. Persisted by the ViewModel. */
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
    onAddToWatching: () -> Unit = {},
    holdDurationSeconds: Int = 3,
    swipeEnabled: Boolean = true
) {
    // Shared with `bouncyPress`, so the press state comes from the `Card` rather
    // than from `bouncyPress`'s own pointer-input loop.
    //
    // This is the most consequential of the call sites that were missing one.
    // `bouncyPress`'s own KDoc names this card as the case that matters most,
    // because there is one of these on every row of the dashboard's LazyColumn -
    // and a loop that sets "pressed" on every pointer-down springs each row in
    // and back out as a fling passes over it, and costs a second pointer-input
    // node per row while it does it. The `Card(onClick =)` overload takes an
    // `interactionSource`, and given one, it emits press on down and cancels the
    // moment the gesture is claimed as a drag - which is the thing the loop was
    // approximating badly. With a source shared, the loop is not installed.
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .bouncyPress(pressedScale = 0.98f, interactionSource = interactionSource)
            .testTag("sequel_card_${sequel.sequelId}"),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 6.dp
        ),
        border = CardDefaults.outlinedCardBorder(),
        interactionSource = interactionSource
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Relation banner
            SequelRelationBanner(sequel = sequel)

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                SequelPoster(sequel = sequel)

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 152.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    SequelCardDetails(sequel = sequel)

                    Spacer(modifier = Modifier.height(10.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        SequelPlanningButton(
                            sequel = sequel,
                            onAddToPlanning = onAddToPlanning,
                            onAddToWatching = onAddToWatching,
                            holdDurationSeconds = holdDurationSeconds,
                            swipeEnabled = swipeEnabled
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        TextButton(
                            onClick = onHide,
                            // 34dp overrode the button's own minimum, so the touch
                            // target was 34dp tall on the busiest list in the app. The
                            // framework's 48dp guard cannot help a component whose
                            // outer height is pinned below it.
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("hide_sequel_${sequel.sequelId}"),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.VisibilityOff,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Not interested",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
