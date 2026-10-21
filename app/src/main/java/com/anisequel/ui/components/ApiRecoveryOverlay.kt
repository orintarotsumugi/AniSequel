package com.anisequel.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.theme.AniSequelTheme
import kotlinx.coroutines.delay

/**
 * How long the celebration sits on screen before it dismisses itself.
 *
 * Long enough for the halves to join, the glue to reach the bottom of the seam
 * and the sparkle to land - plus a beat to read the line. Short enough that a
 * reader who just wants their list back is not held hostage by a joke.
 */
private const val RECOVERY_OVERLAY_MILLIS = 2_400L

/**
 * The "Ani-chan is back" moment: a broken heart, glued.
 *
 * ## Why this is an overlay and not part of the success screen
 *
 * The failure it celebrates is a *state transition*, and the screen that shows
 * it is exactly the one that is being replaced. Drawing the animation in the
 * error view means it vanishes the instant the retry succeeds - the reader
 * never sees the glue. Sitting above whatever comes next is the only place the
 * repair can be seen from start to finish, and it costs the success screen
 * nothing: the data is already there underneath, so dismissing early shows the
 * real content immediately.
 *
 * ## Why taps are consumed
 *
 * The overlay covers a live screen with working buttons underneath. A tap that
 * fell through would open a detail sheet for a card the reader cannot see yet,
 * so an empty `detectTapGestures` swallows pointers without adding a click
 * action or a button role to the scrim. Dismissal is the explicit button, or
 * the automatic timer.
 *
 * The haptic fires once, on appearance, on the same [LocalHapticFeedback]
 * channel every other confirm gesture in the app uses.
 */
@Composable
fun ApiRecoveryOverlay(
    visible: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(visible) {
        if (visible) {
            haptics.performHapticFeedback(
                androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
            )
            delay(RECOVERY_OVERLAY_MILLIS)
            onFinished()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = ExpressiveMotion.FastEffects),
        exit = fadeOut(animationSpec = ExpressiveMotion.FastEffects),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.62f))
                .pointerInput(Unit) { detectTapGestures { } }
                .testTag("api_recovery_overlay"),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 380.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    HeartRepairScene()
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Ani-chan is back",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "The API is answering again. The list was never in danger - " +
                            "only the heart was.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Good as new. Slightly sticky.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AniSequelTheme.statusColors.success,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = onFinished,
                        modifier = Modifier.bouncyPress(pressedScale = 0.96f)
                    ) {
                        Text("Let's go")
                    }
                }
            }
        }
    }
}
