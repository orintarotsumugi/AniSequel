package com.anisequel.ui.components.detail.info

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.anisequel.data.repository.MotionStyle
import com.anisequel.ui.components.expressive.ExpressiveMotion
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * The staggered entrance for one section of the Info page.
 *
 * The page opens with about a dozen cards, and a page that fades in all at once
 * reads as a screenshot arriving; the same cards arriving in sequence read as a
 * page being laid out. Two details make that work rather than annoy:
 *
 * - the delay is capped, so the last card starts within a third of a second of the
 *   first. An uncapped `index * stagger` on twelve sections ends half a second
 *   after the page is already scrollable, and the reader sees the tail of the
 *   animation on content they have moved past;
 * - it rises as it fades, in the draw phase. `graphicsLayer` reads the animation,
 *   so the staggering costs no layout pass per frame - which matters here, because
 *   the animation is running while the sheet is still settling.
 *
 * The spring is `DefaultSpatial`, so the motion style the user picked in Settings
 * governs it: `INSTANT` returns the receiver unchanged, adding nothing at all (an
 * entrance delay *is* motion, so skipping the spring but keeping the wait would be
 * the one style whose name is a lie), `SMOOTH` drops the overshoot, and `CHILL`
 * keeps it.
 *
 * ## It runs once per section, not once per composition of it
 *
 * `played` is `rememberSaveable`, and that is the whole subtlety: a `LazyColumn`
 * disposes the items it has scrolled past and keeps their saved state under the
 * item's key, so this flag outlives the card going off screen. With a plain
 * `remember` the entire stagger replayed on the way back up the page - a dozen
 * cards fading in again from nothing, in sequence, as though the page had
 * reloaded. The effect is unconditional and checks the flag inside itself rather
 * than being wrapped in `if (!played)`, because leaving that `LaunchedEffect` in
 * the composition would cancel the spring the moment the flag flipped.
 *
 * [entranceKey] carries *which title* the flag belongs to. The page's item keys
 * are constant strings ("synopsis", "specs"), and following a relation swaps the
 * title under the same keys - so without it the saved flag from the title the
 * reader left said "already played", and the new title's sections appeared all
 * at once instead of being laid out. `WavyProgressBar` had exactly this bug and
 * names the same fix.
 */
@Composable
fun Modifier.sectionReveal(index: Int, entranceKey: Any? = null): Modifier {
    if (ExpressiveMotion.speed == MotionStyle.INSTANT) return this

    var played by rememberSaveable(entranceKey) { mutableStateOf(false) }
    val progress = remember(entranceKey) { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(entranceKey) {
        if (played) return@LaunchedEffect
        delay((index.coerceIn(0, MAX_STAGGERED_SECTIONS) * STAGGER_MILLIS).toLong())
        // Marked before the spring rather than after it: a card can be scrolled out
        // from under a running animation, and one that comes back should know its
        // entrance is over instead of starting it again.
        played = true
        progress.animateTo(1f, ExpressiveMotion.DefaultSpatial)
    }

    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * REVEAL_RISE.toPx()
    }
}

/**
 * How many sections a first open staggers in.
 *
 * Seven, because the hero is section zero and the last staggered section lands at
 * 294ms ([MAX_STAGGERED_SECTIONS] x [STAGGER_MILLIS]). Past that the delay between
 * the first and the last is longer than the animation itself, and a page that
 * trickles reads as slow rather than as sequenced - so the tail arrives together.
 */
private const val MAX_STAGGERED_SECTIONS = 7

/** The gap between one section's entrance and the next. */
private const val STAGGER_MILLIS = 42

/** How far a section rises as it comes in, in the draw phase. */
private val REVEAL_RISE = 20.dp

/**
 * A number that counts up to its value, once per title.
 *
 * The Info page states its score in a chip over the artwork, and a chip that reads
 * "87%" from the first frame is a label rather than a measurement - it appears with
 * the page, before the reader has looked at it, and gives no sign that anything was
 * counted. Counting it in on `DefaultSpatial` is the same motion the sections around
 * it use, and it is what makes the number read as the *result* of the page having
 * loaded.
 *
 * Three constraints, each of which was a way to get this wrong:
 *
 * - the target is a `Float` in the `Animatable` and rounded on the way out, because
 *   an integer spring stops short of its last step and leaves "86%" on a score of
 *   87;
 * - `played` is `rememberSaveable` and keyed on the title, for the same reason
 *   [sectionReveal] is: a recycled list item would otherwise re-count a number the
 *   reader has already watched arrive, and a new title under the same item would
 *   not count at all;
 * - it is read through a `derivedStateOf` so the *value* only changes when the
 *   rounded integer does - the `Animatable` ticks on every frame, and this is the
 *   one place in the page where a frame of motion has to recompose something
 *   (text cannot be drawn in the draw phase).
 */
@Composable
internal fun rememberCountingUp(target: Int, entranceKey: Any?): State<Int> {
    val animate = ExpressiveMotion.speed != MotionStyle.INSTANT && target > 0
    var played by rememberSaveable(entranceKey) { mutableStateOf(false) }
    val counter = remember(entranceKey) {
        Animatable(if (animate && !played) 0f else target.toFloat())
    }
    LaunchedEffect(entranceKey, target, animate) {
        played = true
        if (animate) {
            counter.animateTo(target.toFloat(), ExpressiveMotion.DefaultSpatial)
        } else {
            counter.snapTo(target.toFloat())
        }
    }
    return remember(counter) { derivedStateOf { counter.value.roundToInt() } }
}

/**
 * The handover between the hero's title lockup and the docked bar, as shares of
 * [AnimeInfoPage]'s collapse.
 *
 * These three live together, in one file, because they are one gesture split across
 * two: the lockup fades out across `START..END` and the bar fades in at [DOCKED_BAR_AT],
 * which sits inside that window. Changing one without the others is what would put
 * a half-strength title on screen behind a bar that is already showing the same
 * title - a bug nothing else in the app can catch, since it is a relationship
 * between two numbers in two files rather than a rule either one breaks alone.
 *
 * The window starts at half rather than at zero so the lockup keeps full strength
 * while it is still the only thing on screen, and ends at 0.8 because by then the
 * poster's top edge is reaching the bar's band.
 */
internal const val LOCKUP_FADE_FROM = 0.5f

internal const val LOCKUP_FADE_TO = 0.8f

/** Past this share of the collapse, the docked bar is the way out of the page. */
internal const val DOCKED_BAR_AT = 0.55f
