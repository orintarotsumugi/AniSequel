package com.anisequel.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.anisequel.ui.components.expressive.ExpressiveMotion
import kotlinx.coroutines.delay
import kotlin.random.Random

/** How long a joke stays up before it dismisses itself. */
private const val JokeVisibleMillis = 5_000L

/**
 * The first wait, which is shorter than the rest on purpose.
 *
 * A fifteen-minute first delay means most sessions never see the feature at all,
 * and a reader cannot tell a bug from a long wait. Once it has appeared, the
 * gaps are long enough to stay a surprise.
 */
private const val FirstJokeDelayMinMillis = 90_000L
private const val FirstJokeDelayMaxMillis = 240_000L

/** The gaps between later jokes: quiet enough to be a treat, not a ticker. */
private const val RepeatJokeDelayMinMillis = 4 * 60_000L
private const val RepeatJokeDelayMaxMillis = 15 * 60_000L

/**
 * A joke that turns up on its own, at a random time, and leaves.
 *
 * ## Why a toast and not another card
 *
 * The app is a lot of pages with opinions on it. This is the one surface with no
 * job: a line about backlogs and recaps that costs the reader nothing and takes
 * no space when it is not there. A card would have to be dismissed, placed, and
 * reasoned about; a toast arrives above the bar, says its one thing and goes.
 *
 * ## Why it only runs on the bar's pages
 *
 * The host is told [enabled] by the navigation, which passes true only for the
 * three pages the main bar owns. A joke arriving over the login screen or a
 * title sheet is an interruption at exactly the moment the reader is doing
 * something; over a page they have settled on, it is a small surprise.
 *
 * ## Why the wait is lifecycle-aware
 *
 * The wait runs only while the Activity is resumed, so a joke cannot come due
 * while the reader is in another app and be waiting for them when they return -
 * and the `finally` hides one that was already on screen, so they do not come
 * back to a stale line frozen mid-air. The signal is `LifecycleResumeEffect`'s,
 * the same one the dashboard uses for its freshness check, and it is deliberately
 * *stopped* rather than merely paused: the loop is cancelled with the effect, so
 * nothing is left ticking on a screen nobody is looking at.
 */
@Composable
fun JokeToastHost(
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    // The joke is remembered separately from visibility: the exit animation keeps
    // drawing the card after `visible` flips, so the text cannot be derived from
    // that flag without going blank halfway through the slide.
    var joke by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var resumed by remember { mutableStateOf(false) }
    // Survives the effect's restarts, so coming back from the background does
    // not shorten the next wait to the first-run one. A local `var` inside the
    // effect reset with it, which is exactly the bug this hoisting fixes.
    var seenOne by remember { mutableStateOf(false) }

    LifecycleResumeEffect(enabled) {
        resumed = true
        onPauseOrDispose { resumed = false }
    }

    LaunchedEffect(enabled, resumed) {
        if (!enabled || !resumed) return@LaunchedEffect
        try {
            while (true) {
                val wait = if (seenOne) {
                    Random.nextLong(RepeatJokeDelayMinMillis, RepeatJokeDelayMaxMillis)
                } else {
                    Random.nextLong(FirstJokeDelayMinMillis, FirstJokeDelayMaxMillis)
                }
                delay(wait)
                seenOne = true
                joke = AppJokes.pickDifferent(previous = joke.takeIf { it.isNotEmpty() })
                visible = true
                delay(JokeVisibleMillis)
                visible = false
            }
        } finally {
            // Cancellation is the background case: whatever is up goes away with
            // the loop rather than staying on a screen nobody is looking at.
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        // Spring tokens rather than a tween, like every other entrance in the
        // app. The slide's spec is an `IntOffset` one - `slideInVertically` takes
        // offsets, not floats - which is why this uses the offset token rather
        // than `FastSpatialInt`; the two are not interchangeable.
        enter = fadeIn(animationSpec = ExpressiveMotion.FastEffects) +
            slideInVertically(animationSpec = ExpressiveMotion.DefaultSpatialOffset) { height -> -height / 2 },
        exit = fadeOut(animationSpec = ExpressiveMotion.FastEffects) +
            slideOutVertically(animationSpec = ExpressiveMotion.DefaultSpatialOffset) { height -> -height / 2 },
        modifier = modifier
    ) {
        Surface(
            // Tapping dismisses it early. No scrim and no modal: it is a remark,
            // not a decision, and the page underneath must stay usable.
            onClick = { visible = false },
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            tonalElevation = 3.dp,
            modifier = Modifier
                .testTag("joke_toast")
                .widthIn(max = 420.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = AppVectorIcons.NewReleases,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = joke,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
