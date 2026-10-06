package com.example.ui.components.cards

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.MissedSequel
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.expressive.ExpressiveContainedLoadingIndicator
import com.example.ui.components.expressive.ExpressiveMotion
import com.example.ui.components.expressive.bouncyPress
import com.example.ui.theme.AniSequelTheme

@Composable
fun SequelPlanningButton(
    sequel: MissedSequel,
    onAddToPlanning: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = if (sequel.isAddedToPlanning) "On Planning List" else "Add to Planning"
    val statusColors = AniSequelTheme.statusColors
    val haptic = LocalHapticFeedback.current

    AnimatedContent(
        targetState = sequel.isAddedToPlanning,
        transitionSpec = {
            (fadeIn(ExpressiveMotion.FastEffects) +
                scaleIn(initialScale = 0.9f, animationSpec = ExpressiveMotion.BouncySpatial)) togetherWith
                (fadeOut(ExpressiveMotion.FastEffects) +
                    scaleOut(targetScale = 0.9f))
        },
        label = "planning_button_anim",
        modifier = modifier
    ) { isPlanned ->
        if (isPlanned) {
            OutlinedButton(
                onClick = {},
                enabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clearAndSetSemantics { contentDescription = "Already on your Planning list" }
                    .testTag("planned_button_${sequel.sequelId}"),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.outlinedButtonColors(
                    disabledContentColor = statusColors.success,
                    disabledContainerColor = statusColors.successContainer
                )
            ) {
                Icon(
                    imageVector = AppVectorIcons.BookmarkDone,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Button(
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onAddToPlanning()
                },
                enabled = !sequel.isAddingToPlanning,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .bouncyPress(pressedScale = 0.96f)
                    .testTag("add_planning_button_${sequel.sequelId}"),
                shape = MaterialTheme.shapes.small
            ) {
                if (sequel.isAddingToPlanning) {
                    ExpressiveContainedLoadingIndicator(
                        modifier = Modifier.size(18.dp),
                        containerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
                        indicatorColor = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = AppVectorIcons.BookmarkAdd,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
