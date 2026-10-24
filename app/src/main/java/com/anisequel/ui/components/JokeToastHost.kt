package com.anisequel.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.domain.jokes.JokePosition
import com.anisequel.domain.jokes.JokeSettings
import com.anisequel.ui.components.expressive.ExpressiveMotion
import kotlinx.coroutines.delay
import kotlin.random.Random

/** How long a joke stays up before it dismisses itself. */
private const val JokeVisibleMillis = 5_000L

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
 * reasoned about; a toast says its one thing and goes.
 *
 * ## What the reader controls
 *
 * [settings] decides all four questions the Settings card asks: whether jokes
 * run at all, how long the waits are, which edge the pill uses, and whether it
 * may appear over pages pushed from the bar. The host used to hard-code every
 * one of those - a bottom edge, a first wait of 45 seconds and a repeat wait of
 * four to fifteen minutes - so the settings screen would have been describing a
 * feature it did not own. The timing rules live in
 * [com.anisequel.domain.jokes.JokeFrequency] and are pinned by a JVM test.
 *
 * ## Why it only runs where [active] allows
 *
 * The host is told [active] by the navigation. It passes true for the bar's
 * three pages, or for every signed-in screen when the reader chose
 * [com.anisequel.domain.jokes.JokeScope.EVERYWHERE]. A joke arriving over the
 * login screen is an interruption at exactly the moment the reader is deciding
 * something; over a page they have settled on, it is a small surprise. The
 * preview button is the one exception: it is an explicit request, so it is shown
 * wherever the host is mounted.
 *
 * ## Why the scheduling lives in helpers
 *
 * The host owns the state a joke is shown from; [JokeAutoSchedule],
 * [JokePreviewSchedule], [JokeArrivalHaptic] and [JokeToastPill] each own one
 * reason it changes. Keeping the loop, the preview bus, the tick and the surface
 * in one body made a composable with several effects and nested branches, which
 * is hard to read and trips the project's complexity check for no benefit.
 */
@Composable
fun JokeToastHost(
    settings: JokeSettings,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    // The joke is remembered separately from visibility: the exit animation keeps
    // drawing the card after `visible` flips, so the text cannot be derived from
    // that flag without going blank halfway through the slide.
    var joke by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    // The shuffle bag: every line once before any line is repeated. A plain
    // `mutableSetOf` rather than snapshot state because nothing reads it during
    // composition - the only writers are the schedules' coroutines.
    val seen = remember { mutableSetOf<String>() }

    // One place for the bag's one subtle rule: the line just read stays in the
    // set across the seam, so the first draw of a new cycle cannot repeat the
    // last line of the old one.
    val showNext: () -> Unit = {
        if (seen.size >= AppJokes.lines.size) {
            seen.clear()
            if (joke.isNotEmpty()) seen += joke
        }
        val next = AppJokes.pickUnseen(seen)
        seen += next
        joke = next
        visible = true
    }
    val hide: () -> Unit = { visible = false }

    JokeAutoSchedule(settings = settings, active = active, onShow = showNext, onHide = hide)
    JokePreviewSchedule(onShow = showNext, onHide = hide)
    JokeArrivalHaptic(joke = joke, visible = visible)
    JokeToastPill(
        joke = joke,
        visible = visible,
        fromBottom = settings.position == JokePosition.BOTTOM,
        onDismiss = hide,
        modifier = modifier
    )
}

/**
 * The ambient wait.
 *
 * The wait runs only while the Activity is resumed, so a joke cannot come due
 * while the reader is in another app and be waiting for them when they return -
 * and the `finally` hides one that was already on screen, so they do not come
 * back to a stale line frozen mid-air. The signal is `rememberIsResumed`, the
 * same one the dashboard's freshness check waits on, and it is deliberately
 * *stopped* rather than merely paused: the loop is cancelled with the effect, so
 * nothing is left ticking on a screen nobody is looking at.
 *
 * The gate is keyed on the lifecycle, not on [active]. Keying it on both made
 * the effect restart every time the reader moved between the bar's pages, which
 * threw away the wait in progress - so a reader who tapped Home and Library a
 * few times never reached the first joke at all.
 */
@Composable
private fun JokeAutoSchedule(
    settings: JokeSettings,
    active: Boolean,
    onShow: () -> Unit,
    onHide: () -> Unit
) {
    val resumed = rememberIsResumed()
    // Survives the effect's restarts, so coming back from the background does
    // not shorten the next wait to the first-run one. A local `var` inside the
    // effect reset with it, which is exactly the bug this hoisting fixes.
    var seenOne by remember { mutableStateOf(false) }
    val currentShow by rememberUpdatedState(onShow)
    val currentHide by rememberUpdatedState(onHide)

    LaunchedEffect(active, resumed, settings.autoJokesEnabled, settings.frequency) {
        if (!active || !resumed || !settings.autoJokesEnabled) return@LaunchedEffect
        try {
            while (true) {
                val wait = if (seenOne) {
                    settings.frequency.repeatWaitMillis(Random.Default)
                } else {
                    settings.frequency.firstWaitMillis(Random.Default)
                }
                delay(wait)
                seenOne = true
                currentShow()
                delay(JokeVisibleMillis)
                currentHide()
            }
        } finally {
            // Cancellation is the background case: whatever is up goes away with
            // the loop rather than staying on a screen nobody is looking at.
            currentHide()
        }
    }
}

/**
 * The explicit "show me one" request from Settings.
 *
 * Not gated on [JokeAutoSchedule]'s switch or page rule: a reader who has jokes
 * off must still be able to see what the placement they are choosing looks like.
 * The tick starts at whatever was already requested so opening Settings does not
 * replay an old preview.
 */
@Composable
private fun JokePreviewSchedule(
    onShow: () -> Unit,
    onHide: () -> Unit
) {
    val previewTick by JokePreviewBus.requests.collectAsState()
    var handledPreview by remember { mutableIntStateOf(previewTick) }
    val currentShow by rememberUpdatedState(onShow)
    val currentHide by rememberUpdatedState(onHide)

    LaunchedEffect(previewTick) {
        if (previewTick <= 0 || previewTick == handledPreview) return@LaunchedEffect
        handledPreview = previewTick
        currentShow()
        delay(JokeVisibleMillis)
        currentHide()
    }
}

/**
 * One light tick when a line arrives.
 *
 * It is not a notification and nothing is expected of the reader, so it does not
 * use the long-press or error buzz.
 */
@Composable
private fun JokeArrivalHaptic(joke: String, visible: Boolean) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(joke, visible) {
        if (visible && joke.isNotEmpty()) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
}

/**
 * The pill itself.
 *
 * Spring tokens rather than a tween, like every other entrance in the app. The
 * direction follows the chosen edge: a bottom toast starts below its resting
 * place and rises, a top toast starts above and drops in, and both leave back
 * the way they came (positive offsets are downward).
 */
@Composable
private fun JokeToastPill(
    joke: String,
    visible: Boolean,
    fromBottom: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        // The slide's spec is an `IntOffset` one - `slideInVertically` takes
        // offsets, not floats - which is why this uses the offset token rather
        // than `FastSpatialInt`; the two are not interchangeable.
        enter = fadeIn(animationSpec = ExpressiveMotion.FastEffects) +
            slideInVertically(animationSpec = ExpressiveMotion.DefaultSpatialOffset) { height ->
                if (fromBottom) height / 2 else -height / 2
            },
        exit = fadeOut(animationSpec = ExpressiveMotion.FastEffects) +
            slideOutVertically(animationSpec = ExpressiveMotion.DefaultSpatialOffset) { height ->
                if (fromBottom) height / 2 else -height / 2
            },
        modifier = modifier
    ) {
        // The arrival's scale. `FastSpatial` and not `BouncySpatial`: this spring
        // has to settle *inside* the slide's window, because `AnimatedVisibility`
        // disposes the content when its own exit finishes and a slower scale would
        // be cut off mid-collapse. The origin is the edge the toast is anchored to,
        // so it grows out of that edge rather than out of its own middle.
        //
        // Read inside `graphicsLayer` rather than passed to it, so a frame of the
        // scale invalidates the draw pass and not this composable.
        val scale by transition.animateFloat(
            transitionSpec = { ExpressiveMotion.FastSpatial },
            label = "joke_toast_scale"
        ) { state -> if (state == EnterExitState.Visible) 1f else 0.92f }

        Surface(
            // Tapping dismisses it early. No scrim and no modal: it is a remark,
            // not a decision, and the page underneath must stay usable.
            onClick = onDismiss,
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            // Higher than the 3dp it shipped with. The toast lands over a
            // scrolling list of poster artwork, and a `secondaryContainer` pill
            // with a faint tint on it is not reliably separable from a busy
            // picture underneath.
            tonalElevation = 6.dp,
            // A hairline edge so the pill reads as a surface over artwork rather
            // than a patch of the poster done in a different colour.
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .testTag("joke_toast")
                .widthIn(max = 420.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0.5f, if (fromBottom) 1f else 0f)
                }
                // A surface that appears by itself, with no tap and no focus
                // change to announce it, is invisible to a screen reader. Polite
                // rather than Assertive: a joke is not allowed to interrupt a
                // sentence the reader is already hearing.
                .semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            JokeToastBody(joke = joke)
        }
    }
}

/**
 * The named line: Ani-chan's sparkle, her name, and the joke.
 *
 * Named, not anonymous. A line that arrives by itself with no attribution reads
 * like a notification from nobody; Ani-chan is the app's own voice and gives the
 * toast a reason to be there. `AnimeSparkle` and not `NewReleases`: the update
 * glyph means "go and install something", and this line is not that.
 */
@Composable
private fun JokeToastBody(joke: String) {
    Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            modifier = Modifier.size(30.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = AppVectorIcons.AnimeSparkle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
        Spacer(modifier = Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Ani-chan",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.65f)
            )
            Text(
                text = joke,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
