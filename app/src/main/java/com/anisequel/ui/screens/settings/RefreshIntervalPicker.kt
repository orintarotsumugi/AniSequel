package com.anisequel.ui.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.data.repository.RefreshInterval
import com.anisequel.ui.components.rememberBoundSliderState
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.WavyTrackSlider
import kotlin.math.roundToInt

/**
 * The refresh-interval picker, shared by Settings and first-run setup.
 *
 * ## Why a discrete slider
 *
 * The chips this replaces were five pills in a wrapping row, which said
 * "Always", the durations and "Manual" all had the same weight. They do not: the
 * two extremes switch the timer off at either end of one ordered scale. A
 * discrete slider draws that scale as one control, and the two long intervals
 * this offers - a day and a week - would have wrapped a seventh pill onto a line
 * of its own. The chosen value is printed large above the track instead of being
 * a filled pill the reader has to count to.
 *
 * ## Why it is shared
 *
 * The first-run walk and Settings ask the same question, and the answer has the
 * same stops. Two spellings of it - chips on one screen, a slider on the other -
 * is how one screen ends up missing the cadence the other gained. Both callers
 * pass the stored value in and receive the chosen one on release; the draft
 * between those two moments lives here.
 *
 * The release is also where the write belongs. A slider crosses several stops in
 * one drag, and writing each of them would make one gesture several preference
 * writes and could leave the stored value on a stop the thumb did not stop at.
 */
@Composable
internal fun RefreshIntervalPicker(
    selected: RefreshInterval,
    onSelect: (RefreshInterval) -> Unit,
    modifier: Modifier = Modifier,
    tag: String = "refresh_interval",
    detail: (RefreshInterval) -> String = { it.intervalDetail() }
) {
    val options = RefreshInterval.entries
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    // The draft, not the stored value, moves the thumb: the write lands on
    // release, and waiting for DataStore to echo it back would make every choice
    // feel a beat late. Remembering on the stored value is still what pulls the
    // draft back when something else writes the preference.
    var draftIndex by remember(selected) { mutableIntStateOf(selectedIndex) }
    val preview = options[draftIndex.coerceIn(options.indices)]
    val sliderState = rememberBoundSliderState(
        value = draftIndex.toFloat(),
        steps = options.size - 2,
        trackRange = 0f..options.lastIndex.toFloat()
    )
    val haptic = LocalHapticFeedback.current

    Column(modifier = modifier.testTag("${tag}_row")) {
        Text(
            text = "Refresh interval",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))

        AnimatedContent(
            targetState = preview,
            transitionSpec = {
                (fadeIn(animationSpec = ExpressiveMotion.FastEffects) +
                    scaleIn(
                        initialScale = 0.92f,
                        animationSpec = ExpressiveMotion.FastSpatial
                    )).togetherWith(
                    fadeOut(animationSpec = ExpressiveMotion.FastEffects) +
                        scaleOut(
                            targetScale = 0.92f,
                            animationSpec = ExpressiveMotion.FastSpatial
                        )
                )
            },
            label = "${tag}_value_animation"
        ) { shown ->
            Text(
                text = shown.displayName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("${tag}_value")
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        WavyTrackSlider(
            state = sliderState,
            onValueChange = { raw ->
                val next = raw.roundToInt().coerceIn(options.indices)
                if (next != draftIndex) {
                    draftIndex = next
                    // Keep Material 3's own copy in step as well. Its drag code
                    // compares the next position against `state.value` before
                    // reporting, and a state left a frame behind can swallow a drag
                    // that turns back to the stop it started on.
                    sliderState.value = next.toFloat()
                    // The same tick the sliders elsewhere in Settings give on a stop:
                    // a discrete control that does not answer is indistinguishable
                    // from one that did not move.
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            },
            onValueChangeFinished = {
                // From the draft, not from `sliderState.value`: Material 3 hands
                // the drag to `onValueChange` instead of writing the state itself,
                // so the state still holds the stop the gesture started on until
                // the next frame pushes the draft in. Reading it here would commit
                // the previous stop on every drag.
                //
                // Always report, even for the stop that was already chosen: the
                // caller's stored value can still be the previous stop while its
                // write is in flight, and skipping the "unchanged" release there
                // would drop the reader's second choice and leave them on the
                // first.
                onSelect(options[draftIndex.coerceIn(options.indices)])
            },
            // The platform would read the raw stop out of 0..6, and "4.0" is not
            // an interval. Same reason the text-size slider writes its own.
            modifier = Modifier
                .fillMaxWidth()
                .semantics { stateDescription = preview.displayName }
                .testTag("${tag}_slider"),
            activeColor = MaterialTheme.colorScheme.primary,
            inactiveColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = RefreshInterval.ALWAYS.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = RefreshInterval.MANUAL_ONLY.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        AnimatedContent(
            targetState = preview,
            transitionSpec = {
                fadeIn(animationSpec = ExpressiveMotion.FastEffects)
                    .togetherWith(fadeOut(animationSpec = ExpressiveMotion.FastEffects))
            },
            label = "${tag}_detail_animation"
        ) { shown ->
            Text(
                text = detail(shown),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * What the chosen stop actually does, in the reader's terms rather than the
 * enum's. Kept beside the picker so a new [RefreshInterval] fails the `else`
 * review here instead of silently reusing another interval's sentence.
 */
private fun RefreshInterval.intervalDetail(): String = when (this) {
    RefreshInterval.ALWAYS ->
        "Asks AniList again every time you return to the app, and every time the " +
            "list comes back on screen."
    RefreshInterval.MANUAL_ONLY ->
        "Nothing is fetched on its own. Pull down or use the refresh button to check."
    else ->
        "A saved copy older than $displayName is re-read when you come back, and the " +
            "list ticks over on its own after $displayName while the app stays open."
}
