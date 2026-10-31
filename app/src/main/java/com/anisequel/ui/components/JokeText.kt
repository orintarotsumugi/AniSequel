package com.anisequel.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.anisequel.data.repository.MotionStyle
import com.anisequel.domain.jokes.JOKE_WRITER_BUDGET_MILLIS
import com.anisequel.domain.jokes.JOKE_WRITER_CHAR_MILLIS
import com.anisequel.domain.jokes.JOKE_WRITER_WORD_MILLIS
import com.anisequel.domain.jokes.JokeTextEffect
import com.anisequel.ui.components.expressive.ExpressiveMotion
import kotlinx.coroutines.delay

/**
 * One joke line, revealed by the reader's chosen text effect.
 *
 * Shared by the toast and its Settings preview on purpose: a preview that types at
 * a different speed from the product is a preview of nothing. The cadence and its
 * budget live in the domain beside the setting, so the two call sites cannot drift.
 *
 * ## Accessibility
 *
 * The two writer arms clear the text node's semantics and set the full line as its
 * description. Without that, a screen reader following a live region would hear
 * one character at a time - or re-announce the line on every frame - which is
 * worse than not animating at all.
 *
 * ## The app-wide switch still wins
 *
 * The effect is opt-in, but `MotionStyle.INSTANT` means the reader asked for no
 * motion *anywhere*. A typewriter that argues with that is the one exception the
 * setting exists to prevent, so the effect resolves to [JokeTextEffect.NONE].
 */
@Composable
fun JokeLineText(
    line: String,
    effect: JokeTextEffect,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    animate: Boolean = true
) {
    val instant = ExpressiveMotion.speed == MotionStyle.INSTANT
    when (val resolved = if (animate && !instant) effect else JokeTextEffect.NONE) {
        JokeTextEffect.NONE -> Text(text = line, modifier = modifier, style = style)

        JokeTextEffect.TYPEWRITER -> TypewriterLine(
            line = line,
            stepMillis = stepFor(
                units = line.codePointCount(0, line.length),
                preferred = JOKE_WRITER_CHAR_MILLIS
            ),
            modifier = modifier,
            style = style
        )

        JokeTextEffect.WORD_WRITER -> WordWriterLine(
            line = line,
            stepMillis = stepFor(
                units = line.count { it == ' ' } + 1,
                preferred = JOKE_WRITER_WORD_MILLIS
            ),
            modifier = modifier,
            style = style
        )

        JokeTextEffect.FADE_UP -> FadeUpLine(
            line = line,
            modifier = modifier,
            style = style
        )
    }
}

/**
 * The per-step delay, shortened when a long line would otherwise outrun the dwell.
 *
 * Never returns zero: the loop's own budget is a reading aid, not a licence to
 * spin a frame per character with no time to draw.
 */
private fun stepFor(units: Int, preferred: Long): Long {
    val safeUnits = units.coerceAtLeast(1)
    return minOf(preferred, JOKE_WRITER_BUDGET_MILLIS / safeUnits).coerceAtLeast(8L)
}

@Composable
private fun TypewriterLine(
    line: String,
    stepMillis: Long,
    modifier: Modifier,
    style: TextStyle
) {
    val codePoints = remember(line) { line.codePointCount(0, line.length) }
    // Counted in code points rather than chars: half a surrogate pair renders as a
    // replacement glyph, and a saved line or a stamp can legitimately contain one.
    var shown by remember(line) { mutableIntStateOf(0) }

    LaunchedEffect(line, stepMillis) {
        shown = 0
        while (shown < codePoints) {
            delay(stepMillis)
            shown += 1
        }
    }

    val end = line.offsetByCodePoints(0, shown.coerceAtMost(codePoints))
    RevealedProse(reserved = line, revealed = line.substring(0, end), modifier, style)
}

@Composable
private fun WordWriterLine(
    line: String,
    stepMillis: Long,
    modifier: Modifier,
    style: TextStyle
) {
    val words = remember(line) { line.split(' ') }
    var shown by remember(line) { mutableIntStateOf(0) }

    LaunchedEffect(line, stepMillis) {
        shown = 0
        while (shown < words.size) {
            delay(stepMillis)
            shown += 1
        }
    }

    RevealedProse(
        reserved = line,
        revealed = words.take(shown).joinToString(" "),
        modifier = modifier,
        style = style
    )
}

/**
 * The revealed text drawn over an invisible copy of the whole line.
 *
 * The copy is what keeps the pill's height stable while a line types: without it
 * the joke grows a wrapped line at a time and the pill visibly jumps under the
 * reader's thumb. It is invisible and silent - the visible text carries the
 * description - so it costs one layout and no semantics.
 */
@Composable
private fun RevealedProse(
    reserved: String,
    revealed: String,
    modifier: Modifier,
    style: TextStyle
) {
    Box(modifier = modifier) {
        Text(
            text = reserved,
            modifier = Modifier
                .alpha(0f)
                .clearAndSetSemantics { },
            style = style
        )
        Text(
            text = revealed,
            modifier = Modifier.clearAndSetSemantics { contentDescription = reserved },
            style = style
        )
    }
}

@Composable
private fun FadeUpLine(
    line: String,
    modifier: Modifier,
    style: TextStyle
) {
    val progress = remember(line) { Animatable(0f) }
    val lift = with(LocalDensity.current) { 8.dp.toPx() }

    LaunchedEffect(line) {
        progress.snapTo(0f)
        progress.animateTo(1f, ExpressiveMotion.FastSpatial)
    }

    Text(
        text = line,
        modifier = modifier.graphicsLayer {
            // Draw-phase reads so a frame of the fade does not recompose the pill.
            val settled = progress.value.coerceIn(0f, 1f)
            alpha = settled
            translationY = (1f - settled) * lift
        },
        style = style
    )
}
