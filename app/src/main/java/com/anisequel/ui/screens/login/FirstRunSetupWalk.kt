package com.anisequel.ui.screens.login

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressivePrimaryButton
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress

/**
 * The frame of the first-run walk: the header, the progress track, the animated
 * page and the footer.
 *
 * Split from `FirstRunSetupDialog` by responsibility - that file owns the draft,
 * the batch write and the Back/Skip/Finish decisions; this one draws the walk
 * around them. It is also what keeps the wizard under AGENTS section 1's line
 * ceiling once the complexity split gave each piece its own function.
 *
 * Every entry point is `internal` because the two files are one screen.
 */

/**
 * The header: the app mark, the title and Skip.
 *
 * A composable of its own because the wizard is counted as one method, and the
 * header's Skip branch was part of what the complexity gate counted. It has a
 * second benefit that matters at runtime: a chip tapped on the page below changes
 * the draft, and the header neither reads nor draws the draft - so it does not
 * have to be rebuilt for that change.
 */
@Composable
internal fun SetupHeader(onSkip: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppVectorIcons.AnimeSparkle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "AniSequel setup",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = onSkip,
            modifier = Modifier.testTag("onboarding_defaults")
        ) {
            Text("Skip")
        }
    }
}

/**
 * The capsule progress track, one step fuller per page.
 *
 * The animated value is read inside `graphicsLayer`, so a frame of the spring
 * invalidates the draw pass only. It was a `fillMaxWidth(progress)`, which is a
 * layout read: every frame re-measured the track and the page under it, which is
 * the one thing AGENTS section 3 forbids and the wizard's only violation of it.
 */
@Composable
internal fun SetupProgressBar(step: Int) {
    val progress by animateFloatAsState(
        targetValue = (step + 1f) / SETUP_STEPS.size,
        animationSpec = ExpressiveMotion.FastSpatial,
        label = "onboarding_progress"
    )

    // Capsule track rather than a `LinearProgressIndicator`: this is a six-page
    // walk, not a download, and the shape is the app's own.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(4.dp)
            .clip(ExpressiveShapes.pill)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    scaleX = progress.coerceIn(0f, 1f)
                }
                .clip(ExpressiveShapes.pill)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

/**
 * One page of the walk: its heading, its copy, and the question it asks.
 *
 * The page transition lives here rather than in the wizard because its direction
 * is a fact about the page changing, not about the setup state changing - every
 * chip tap on a page would otherwise rebuild the transition spec too.
 */
@Composable
internal fun SetupPage(
    stepIndex: Int,
    draft: SetupDraft,
    onDraftChange: (SetupDraft) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = stepIndex.coerceIn(0, SETUP_STEPS.lastIndex),
        transitionSpec = {
            val forward = targetState > initialState
            val enter = slideInHorizontally(
                animationSpec = ExpressiveMotion.DefaultSpatialOffset
            ) { width -> if (forward) width / 4 else -width / 4 } +
                fadeIn(animationSpec = ExpressiveMotion.FastEffects)
            val exit = slideOutHorizontally(
                animationSpec = ExpressiveMotion.DefaultSpatialOffset
            ) { width -> if (forward) -width / 4 else width / 4 } +
                fadeOut(animationSpec = ExpressiveMotion.FastEffects)
            enter togetherWith exit using null
        },
        modifier = modifier,
        label = "onboarding_step"
    ) { index ->
        val page = SETUP_STEPS[index]
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = "${index + 1} of ${SETUP_STEPS.size} · ${page.label}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = page.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = page.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))

            SetupStepChoices(
                index = index,
                draft = draft,
                onDraftChange = onDraftChange
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/**
 * Back on the left, Next or Finish on the right.
 *
 * The first page has no Back - the only previous step is leaving setup - so its
 * slot is an equal-width spacer rather than a shorter button. That keeps the
 * primary action in the same place on every page, which matters most where Next
 * is the one that finishes.
 */
@Composable
internal fun SetupFooter(
    isFirst: Boolean,
    isLast: Boolean,
    backSource: InteractionSource,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isFirst) {
            Spacer(modifier = Modifier.weight(1f))
        } else {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .bouncyPress(pressedScale = 0.96f, interactionSource = backSource)
                    .testTag("onboarding_back"),
                interactionSource = backSource
            ) {
                Text("Back")
            }
        }
        ExpressivePrimaryButton(
            text = if (isLast) "Finish" else "Next",
            onClick = onContinue,
            modifier = Modifier.weight(1.4f),
            icon = if (isLast) AppVectorIcons.CheckCircle else AppVectorIcons.SequelArrow,
            testTag = if (isLast) "onboarding_save" else "onboarding_next"
        )
    }
}
