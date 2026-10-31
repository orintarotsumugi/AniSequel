package com.anisequel.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.domain.jokes.JokeDwell
import com.anisequel.domain.jokes.JokeMoment
import com.anisequel.domain.jokes.JokePosition
import com.anisequel.domain.jokes.JokeSettings
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.bouncyPress
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlin.random.Random

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
 * [settings] decides every question the Settings screen asks: whether jokes run,
 * how long the waits are, which edge the pill uses, which pages it may appear
 * over, how long a line stays up, whether it hums on arrival, which tones it may
 * draw from, and which extra moments invite one. The host used to hard-code all
 * of those - a bottom edge, a first wait of 45 seconds, a repeat wait of four to
 * fifteen minutes and a five-second dwell - so Settings would have been
 * describing a feature it did not own. The rules live in
 * [com.anisequel.domain.jokes.JokeSettings] and are pinned by a JVM test.
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
 * [JokeMomentSchedule], [JokePreviewSchedule], [JokeArrivalHaptic] and
 * [JokeToastPill] each own one reason it changes. Keeping the loops, the tick and
 * the surface in one body made a composable with several effects and nested
 * branches, which is hard to read and trips the project's complexity check for no
 * benefit.
 */
@Composable
fun JokeToastHost(
    settings: JokeSettings,
    active: Boolean,
    modifier: Modifier = Modifier,
    favouriteLines: Set<String> = emptySet(),
    onToggleFavourite: (String) -> Unit = {}
) {
    // The joke is remembered separately from visibility: the exit animation keeps
    // drawing the card after `visible` flips, so the text cannot be derived from
    // that flag without going blank halfway through the slide.
    var joke by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    // The shuffle bag: every line in the current tone filter once before any is
    // repeated. A plain `mutableSetOf` rather than snapshot state because nothing
    // reads it during composition - the only writers are the effects' coroutines.
    val seen = remember { mutableSetOf<String>() }
    // Wall-clock time of the last line shown, by any trigger. Moment gaps are
    // measured from here rather than from the previous moment, so a timer joke
    // three seconds ago also keeps a tab-change joke quiet.
    var lastShownAt by remember { mutableStateOf(0L) }
    // Which line owns the screen, as a counter rather than a boolean. Three
    // effects can put a line up, and each one ends its turn by hiding "the pill" -
    // which used to mean whichever pill was up when its own timer expired. So a
    // preview tapped two seconds into a timer joke was cut off when that joke's
    // dwell ended, and a preview that had already replaced a line could hide a
    // *newer* timer joke the same way. Each show increments this and hands the
    // value to its hide; a hide whose value is stale is a pill that is no longer
    // its business.
    var lineShown by remember { mutableIntStateOf(0) }
    // Read inside the lambdas below rather than captured per recomposition, so a
    // settings change reaches an effect that is already waiting without restarting
    // the wait.
    val settingsNow by rememberUpdatedState(settings)

    val showLine: (String) -> Int = { line ->
        lineShown += 1
        seen += line
        joke = line
        visible = true
        lastShownAt = System.currentTimeMillis()
        lineShown
    }

    // One place for the bag's one subtle rule - the seam - and it is
    // `AppJokes.draw` rather than this lambda, because a pure function of the bag
    // is the only version of that rule a JVM test can break.
    val showNext: () -> Int = {
        showLine(AppJokes.draw(seen, joke, settingsNow.activeTones))
    }

    val hide: () -> Unit = { visible = false }

    // The guard above, in one line: only the effect that put the current line up
    // may take it down. Cancellation goes through this too, with the token that
    // effect was holding, so leaving a page hides the line the timer put there and
    // not the preview the reader asked for a second later.
    val hideIfCurrent: (Int) -> Unit = { shown ->
        if (shown == lineShown) visible = false
    }

    // How an effect waits for a line to be done with the screen: a timer in every
    // arm except "until tapped", where it waits for the reader. `snapshotFlow`
    // rather than a channel because the state is already the truth and a channel
    // would need draining between lines.
    val waitForDwell: suspend () -> Unit = {
        val millis = settingsNow.dwell.visibleMillis
        if (millis != null) delay(millis) else snapshotFlow { visible }.first { !it }
    }

    // A moment is an *invitation*: it still has to respect the switch and the
    // gap the reader's chosen moment declares.
    val mayShowMoment: (JokeMoment) -> Boolean = { moment ->
        !visible && System.currentTimeMillis() - lastShownAt >= moment.minGapMillis
    }

    JokeAutoSchedule(
        settings = settings,
        active = active,
        visible = { visible },
        onShow = showNext,
        onSettle = waitForDwell,
        onHide = hideIfCurrent
    )
    JokeMomentSchedule(
        settings = settings,
        active = active,
        mayShow = mayShowMoment,
        onShow = showNext,
        onSettle = waitForDwell,
        onHide = hideIfCurrent
    )
    JokePreviewSchedule(
        onShowNext = showNext,
        onShowLine = showLine,
        onSettle = waitForDwell,
        onHide = hideIfCurrent
    )
    JokeArrivalHaptic(joke = joke, visible = visible, enabled = settings.haptics)
    JokeToastPill(
        joke = joke,
        visible = visible,
        fromBottom = settings.position == JokePosition.BOTTOM,
        dwell = settings.dwell,
        speakerName = settings.displayName,
        stamp = settings.displayStamp,
        textEffect = settings.textEffect,
        isFavourite = joke in favouriteLines,
        onToggleFavourite = { onToggleFavourite(joke) },
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
    visible: () -> Boolean,
    onShow: () -> Int,
    onSettle: suspend () -> Unit,
    onHide: (Int) -> Unit
) {
    val resumed = rememberIsResumed()
    // Survives the effect's restarts, so coming back from the background does
    // not shorten the next wait to the first-run one. A local `var` inside the
    // effect reset with it, which is exactly the bug this hoisting fixes.
    var seenOne by remember { mutableStateOf(false) }
    val currentVisible by rememberUpdatedState(visible)
    val currentShow by rememberUpdatedState(onShow)
    val currentSettle by rememberUpdatedState(onSettle)
    val currentHide by rememberUpdatedState(onHide)

    LaunchedEffect(active, resumed, settings.autoJokesEnabled, settings.frequency) {
        if (!active || !resumed || !settings.autoJokesEnabled) return@LaunchedEffect
        // The line this effect is currently showing, so cancellation can take
        // back its own pill without taking down one that replaced it.
        var owned: Int? = null
        try {
            while (true) {
                val wait = if (seenOne) {
                    settings.frequency.repeatWaitMillis(Random.Default)
                } else {
                    settings.frequency.firstWaitMillis(Random.Default)
                }
                delay(wait)
                seenOne = true
                // A moment or a preview got there first. Skip this turn instead of
                // stacking a second pill on top of the one already up.
                if (currentVisible()) continue
                val shown = currentShow()
                owned = shown
                currentSettle()
                currentHide(shown)
                owned = null
            }
        } finally {
            // Cancellation is the background case: whatever this effect put up
            // goes away with the loop rather than staying on a screen nobody is
            // looking at. The token is what makes that *its* pill and not the
            // preview the reader asked for two seconds ago.
            owned?.let { currentHide(it) }
        }
    }
}

/**
 * The three moments that are not the timer.
 *
 * The screen that knows something happened announces it on [JokeMomentBus];
 * this effect is the one place that decides whether the reader asked for that
 * moment and whether a line is due. The switch is part of the key because a
 * reader who turns jokes off should not leave a collector running that will
 * spring to life if they turn them back on in another window.
 *
 * "When you open the app" is once per host lifetime rather than once per resume:
 * `openShown` is composable state, so backgrounding and returning does not earn
 * a second greeting. A greeting on every return is not a greeting.
 */
@Composable
private fun JokeMomentSchedule(
    settings: JokeSettings,
    active: Boolean,
    mayShow: (JokeMoment) -> Boolean,
    onShow: () -> Int,
    onSettle: suspend () -> Unit,
    onHide: (Int) -> Unit
) {
    val resumed = rememberIsResumed()
    var openShown by remember { mutableStateOf(false) }
    val settingsNow by rememberUpdatedState(settings)
    val currentMayShow by rememberUpdatedState(mayShow)
    val currentShow by rememberUpdatedState(onShow)
    val currentSettle by rememberUpdatedState(onSettle)
    val currentHide by rememberUpdatedState(onHide)

    LaunchedEffect(active, resumed, settings.autoJokesEnabled, settings.moments) {
        if (!active || !resumed || !settings.autoJokesEnabled) return@LaunchedEffect

        // `owned` is the line this effect is showing. It is not decoration: a
        // moment line cancelled mid-dwell (navigating away, or switching a moment
        // off) used to stay up with nothing left to hide it, because the hide
        // lived after the suspending wait and cancellation skipped it. The token
        // means the cleanup hides this effect's line and not a preview's.
        var owned: Int? = null
        try {
            if (JokeMoment.OPEN in settings.moments && !openShown) {
                openShown = true
                if (currentMayShow(JokeMoment.OPEN)) {
                    val shown = currentShow()
                    owned = shown
                    currentSettle()
                    currentHide(shown)
                    owned = null
                }
            }

            JokeMomentBus.moments.collect { moment ->
                if (moment == JokeMoment.OPEN) return@collect
                if (moment !in settingsNow.moments) return@collect
                if (!currentMayShow(moment)) return@collect
                val shown = currentShow()
                owned = shown
                currentSettle()
                currentHide(shown)
                owned = null
            }
        } finally {
            owned?.let { currentHide(it) }
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
 *
 * A request may name a line - a saved favourite - in which case it is shown as
 * itself rather than as another random draw. The reader tapped that line because
 * they wanted that line.
 */
@Composable
private fun JokePreviewSchedule(
    onShowNext: () -> Int,
    onShowLine: (String) -> Int,
    onSettle: suspend () -> Unit,
    onHide: (Int) -> Unit
) {
    val request by JokePreviewBus.requests.collectAsState()
    var handledTick by remember { mutableIntStateOf(request.tick) }
    val currentShowNext by rememberUpdatedState(onShowNext)
    val currentShowLine by rememberUpdatedState(onShowLine)
    val currentSettle by rememberUpdatedState(onSettle)
    val currentHide by rememberUpdatedState(onHide)

    LaunchedEffect(request) {
        if (request.tick <= 0 || request.tick == handledTick) return@LaunchedEffect
        handledTick = request.tick
        val line = request.line
        val shown = if (line.isNullOrBlank()) currentShowNext() else currentShowLine(line)
        currentSettle()
        // A preview replaces whatever was up, including a timer joke whose own
        // dwell is still counting. Without the token that joke's timer would hide
        // this line early - which is the one case the preview button is used in.
        currentHide(shown)
    }
}

/**
 * One light tick when a line arrives.
 *
 * It is not a notification and nothing is expected of the reader, so it does not
 * use the long-press or error buzz. [enabled] is the reader's own switch: a pill
 * that is already unrequested speech is allowed to be silent in the hand too.
 */
@Composable
private fun JokeArrivalHaptic(joke: String, visible: Boolean, enabled: Boolean) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(joke, visible, enabled) {
        if (enabled && visible && joke.isNotEmpty()) {
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
 *
 * [dwell] is passed only so the draw's semantics can describe the arm; the timing
 * itself belongs to the schedule that owns the wait.
 */
@Composable
private fun JokeToastPill(
    joke: String,
    visible: Boolean,
    fromBottom: Boolean,
    dwell: JokeDwell,
    speakerName: String,
    stamp: String,
    textEffect: com.anisequel.domain.jokes.JokeTextEffect,
    isFavourite: Boolean,
    onToggleFavourite: () -> Unit,
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
            JokeToastBody(
                joke = joke,
                speakerName = speakerName,
                stamp = stamp,
                textEffect = textEffect,
                fromBottom = fromBottom,
                isFavourite = isFavourite,
                onToggleFavourite = onToggleFavourite,
                staysUntilDismissed = dwell.staysUntilDismissed
            )
        }
    }
}

/**
 * The named line: Ani-chan's sparkle, her name, the joke and a way to keep it.
 *
 * Named, not anonymous. A line that arrives by itself with no attribution reads
 * like a notification from nobody; Ani-chan is the app's own voice and gives the
 * toast a reason to be there. `AnimeSparkle` and not `NewReleases`: the update
 * glyph means "go and install something", and this line is not that.
 *
 * The heart is the one control in the pill. It sits inside the Surface's tap
 * target, so its own clickable has to consume the tap - which is what stops
 * "save this line" from also meaning "dismiss the pill I am reading".
 */
@Composable
private fun JokeToastBody(
    joke: String,
    speakerName: String,
    stamp: String,
    textEffect: com.anisequel.domain.jokes.JokeTextEffect,
    fromBottom: Boolean,
    isFavourite: Boolean,
    onToggleFavourite: () -> Unit,
    staysUntilDismissed: Boolean
) {
    Row(
        modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The reader's stamp, in place of the fixed sparkle. A pill rather than a
        // circle because a stamp can be more than one glyph: a 30dp circle clipped
        // anything past the second character, and the field legitimately accepts a
        // multi-part emoji.
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            modifier = Modifier.heightIn(min = 30.dp)
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stamp,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
        }
        Spacer(modifier = Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            // These two used to be `onSecondaryContainer` at 0.65 and 0.55 alpha.
            // That is not a style choice, it is a contrast bug: blended over the
            // container the name measured ~4.0:1 in the default dark scheme and
            // "Tap to dismiss" ~3.3:1, both under 4.5:1 for 11sp text. The label
            // and the caption are the two pieces of the pill a reader needs in
            // order to know who is talking and how to get rid of her.
            Text(
                text = speakerName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.86f)
            )
            // Every new line *starts over*: the text re-enters from the pill's own
            // edge on the same spring the pill itself used, instead of mutating in
            // place under the reader's eyes. A timer that draws a second joke while
            // the first is up used to swap the sentence with no motion at all, which
            // read as the app editing itself mid-sentence.
            AnimatedContent(
                targetState = joke,
                transitionSpec = {
                    val direction = if (fromBottom) 1 else -1
                    (slideInVertically(
                        animationSpec = ExpressiveMotion.FastSpatialOffset
                    ) { height -> direction * height / 2 } +
                        fadeIn(animationSpec = ExpressiveMotion.FastEffects)) togetherWith
                        fadeOut(animationSpec = ExpressiveMotion.FastEffects)
                },
                label = "joke_line"
            ) { line ->
                // The reader's text effect, resolved here so the toast and its
                // Settings preview share one implementation - and so INSTANT still
                // means instant.
                JokeLineText(
                    line = line,
                    effect = textEffect
                )
            }
            // "Until tapped" is the one arm where the pill will not leave on its
            // own, so it says so. Every other arm has a timer, and a caption
            // explaining that would be noise on every line.
            if (staysUntilDismissed) {
                Text(
                    text = "Tap to dismiss",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f)
                )
            }
        }
        Spacer(modifier = Modifier.size(6.dp))
        JokeFavouriteButton(isFavourite = isFavourite, onToggle = onToggleFavourite)
    }
}

/**
 * Save or unsave the line currently on screen.
 *
 * Capsule-shaped and drawn from the app's own vectors, like every other icon.
 * The colour rides the effects spring and the press the bouncy one - colour is
 * not geometry and must not overshoot, which is the same division the Settings
 * chips use.
 */
@Composable
private fun JokeFavouriteButton(isFavourite: Boolean, onToggle: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val tint by animateColorAsState(
        targetValue = if (isFavourite) {
            MaterialTheme.colorScheme.primary
        } else {
            // 0.6 was under the 3:1 non-text minimum against the container in the
            // darker palettes, which made the unsaved heart look disabled rather
            // than available.
            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.74f)
        },
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "joke_favourite_tint"
    )
    val label = if (isFavourite) "Remove from favourites" else "Save this joke"

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(percent = 50))
            .bouncyPress(pressedScale = 0.86f)
            .clickable(onClickLabel = label, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggle()
            }
            .semantics { contentDescription = label }
            .testTag("joke_favourite"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isFavourite) {
                AppVectorIcons.ActivityLiked
            } else {
                AppVectorIcons.ActivityNotLiked
            },
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}
