package com.anisequel.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anisequel.data.repository.DEFAULT_TITLE_SCROLL_DP_PER_SECOND

/**
 * How the app scrolls a title that does not fit its row.
 *
 * A CompositionLocal rather than a parameter on every title: the choice is a
 * Settings preference that applies to the whole app, and threading two values
 * through every card would put the same default at a dozen call sites - the drift
 * this codebase already paid for with motion literals.
 */
data class TitleMotion(
    /** Scroll speed in dp per second; see `ThemePreferences.setTitleScrollSpeed`. */
    val velocity: Dp = DEFAULT_TITLE_SCROLL_DP_PER_SECOND.dp,
    /**
     * Whether anything should move at all.
     *
     * False under `MotionStyle.INSTANT`: a reader who has asked for no motion did
     * not ask for "except the text", and a marquee is the one loop that runs while
     * they are reading.
     */
    val animates: Boolean = true,
)

val LocalTitleMotion = staticCompositionLocalOf { TitleMotion() }

/**
 * One line of text that scrolls when - and only when - it is wider than its row.
 *
 * The library's long titles ("... Part 2: The Movie: Final Chapter") were
 * ellipsized everywhere, so the one fact a title row exists to carry was the part
 * that got cut. `basicMarquee` scrolls exactly when the text overflows and leaves
 * a short title still, which is the behaviour that makes this safe to apply to
 * every title rather than to a measured subset.
 *
 * [TitleMotion.velocity] is the reader's setting; the framework turns it into a
 * per-frame offset, so a longer title simply takes longer at the same reading speed
 * instead of turning every title into the same fixed-duration blink.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    motion: TitleMotion = LocalTitleMotion.current,
) {
    if (!motion.animates) {
        Text(
            text = text,
            modifier = modifier,
            color = color,
            style = style,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
        )
        return
    }

    Text(
        text = text,
        modifier = modifier
            .basicMarquee(
                // Continuous rather than a fixed number of passes: a reader who
                // lingers should still be able to read the end of a title, and a
                // loop that stops mid-name is worse than one that never started.
                iterations = Int.MAX_VALUE,
                // Long enough to read the start before it moves, and to see the
                // whole name before it resets - the reset itself is the part that
                // looks broken if it is too eager.
                initialDelayMillis = 1_200,
                repeatDelayMillis = 2_400,
                velocity = motion.velocity,
            ),
        color = color,
        style = style,
        fontWeight = fontWeight,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Visible,
        textAlign = textAlign,
    )
}
