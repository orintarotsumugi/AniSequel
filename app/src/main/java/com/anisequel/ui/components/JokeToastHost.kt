package com.anisequel.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
 * How far the toast is parked above the bar's top edge, on top of the bar's own
 * clearance.
 *
 * Sized to clear a dashboard snackbar, which is anchored at the bar's clearance
 * and is about 48dp tall plus its margins - so a joke parked immediately above
 * the bar would be drawn through an "Added to Planning" confirmation. It is a
 * named constant because it is a relationship between two other elements rather
 * than a preference, and the next person to move the bar needs to see that this
 * moves with it.
 */
internal val JokeToastLift = 72.dp

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
 * back to a stale line frozen mid-air. The signal is `rememberIsResumed`, the
 * same one the dashboard's freshness check waits on, and it is deliberately
 * *stopped* rather than merely paused: the loop is cancelled with the effect, so
 * nothing is left ticking on a screen nobody is looking at.
 *
 * The gate is keyed on the lifecycle alone now, not on [enabled]. Keying it on
 * both made the effect restart every time the reader moved between the bar's
 * pages, which threw away the wait in progress - so a reader who tapped Home and
 * Library a few times never reached the first joke at all.
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
    val resumed = rememberIsResumed()
    // Survives the effect's restarts, so coming back from the background does
    // not shorten the next wait to the first-run one. A local `var` inside the
    // effect reset with it, which is exactly the bug this hoisting fixes.
    var seenOne by remember { mutableStateOf(false) }

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
        //
        // The direction is **up**: positive offsets start the content below its
        // resting place. It used to be `-height / 2`, which started the toast half
        // its height *above* where it sits and took it further up on the way out -
        // so a surface anchored to the bottom edge of the screen arrived from
        // above it and left the same way, which read as the bar spitting it out
        // rather than as the app raising a remark. It rises into place now, and
        // leaves back down the way it came.
        enter = fadeIn(animationSpec = ExpressiveMotion.FastEffects) +
            slideInVertically(animationSpec = ExpressiveMotion.DefaultSpatialOffset) { height -> height / 2 },
        exit = fadeOut(animationSpec = ExpressiveMotion.FastEffects) +
            slideOutVertically(animationSpec = ExpressiveMotion.DefaultSpatialOffset) { height -> height / 2 },
        modifier = modifier
    ) {
        // The arrival's scale. `FastSpatial` and not `BouncySpatial`: this spring
        // has to settle *inside* the slide's window, because `AnimatedVisibility`
        // disposes the content when its own exit finishes and a slower scale would
        // be cut off mid-collapse. `FastSpatial` is the quick press spring, so the
        // pill arrives with a small overshoot and is at rest before the slide is.
        //
        // Read inside `graphicsLayer` rather than passed to it, so a frame of the
        // scale invalidates the draw pass and not this composable. `transformOrigin`
        // is the bottom edge for the same reason the slide is upward: the toast
        // grows out of the bar it sits above rather than out of its own middle.
        val scale by transition.animateFloat(
            transitionSpec = { ExpressiveMotion.FastSpatial },
            label = "joke_toast_scale"
        // The lambda receives the `EnterExitState`, not a Boolean: this is the
        // `AnimatedVisibility` transition, whose two states are Visible and the
        // pre/post states either side of it. Reading it as a Boolean does not
        // compile, and reading `targetState` alone would snap instead of
        // animating the collapse.
        ) { state -> if (state == EnterExitState.Visible) 1f else 0.92f }

        Surface(
            // Tapping dismisses it early. No scrim and no modal: it is a remark,
            // not a decision, and the page underneath must stay usable.
            onClick = { visible = false },
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            // Higher than the 3dp it shipped with. The toast lands over a
            // scrolling list of poster artwork, and a `secondaryContainer` pill
            // with a faint tint on it is not reliably separable from a busy
            // picture underneath. This is the elevation that reads as "above the
            // page" on both the light and dark schemes.
            tonalElevation = 6.dp,
            modifier = Modifier
                .testTag("joke_toast")
                .widthIn(max = 420.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                // A surface that appears by itself, with no tap and no focus
                // change to announce it, is invisible to a screen reader. Polite
                // rather than Assertive: a joke is not allowed to interrupt a
                // sentence the reader is already hearing.
                .semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    // `AnimeSparkle` and not `NewReleases`. The glyph this used
                    // to wear is the app's "an update is available" mark - it is
                    // on the update dialog and on the What's new card in
                    // Settings - so an ambient joke about backlogs was wearing
                    // the one icon in the set that means "go and install
                    // something". The sparkle is the app's own delight glyph and
                    // is already the empty state's.
                    imageVector = AppVectorIcons.AnimeSparkle,
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
