package com.anisequel.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anisequel.ui.theme.AniSequelTheme
import kotlin.math.absoluteValue

/**
 * What a poster draws before its cover is there, and what it keeps when the cover
 * never comes.
 *
 * ## Why a tile and not a spinner, an icon or a blank box
 *
 * A cold start is the one moment when *every* cover on the screen is missing at once.
 * Memory cache is empty, so each poster is a disk read plus a decode away from its
 * artwork, and on a fresh install the disk cache is empty too and it is a network
 * round trip. What used to be drawn in that gap was one of three things, none of them
 * an answer:
 *
 * - **a flat grey box** (`surfaceContainerHigh`), which read as a broken image that
 *   happened to have the right shape;
 * - **`Color.Transparent`**, which on the Home rails was a hole in the card;
 * - **the same vector glyph on every cell** (`AnimeSparkle` in a bio, `Movie` on the
 *   arriving row), so twelve loading posters were twelve copies of one icon and the
 *   grid looked like a list of the same failure.
 *
 * A tile made of the title's own initials on a tint derived from the title gives each
 * cell its own content on the first frame. It costs no request, no asset and no
 * decode, and when the artwork lands Coil's crossfade dissolves it away - so the page
 * appears complete immediately and sharpens, instead of filling in from nothing.
 *
 * ## Why it is also the error state
 *
 * A cover that 404s, or whose host is unreachable, leaves this tile behind rather than
 * a blank rectangle: the initials still say which title the card is about, which is
 * the whole reason the reader is looking at the card. There is no separate "broken
 * image" surface anywhere in the app for the same reason a reader does not care which
 * of the two happened.
 *
 * [tint] is the cover's own dominant colour from AniList when the app already knows
 * it. Passing it keeps the card's colour identity from the first frame; when it is
 * null the tint comes from [posterPlaceholderSlot], which is the title's own hash and
 * is stable across launches, so a title does not change colour when the app restarts.
 */
@Composable
fun PosterPlaceholder(
    title: String,
    modifier: Modifier = Modifier,
    tint: Color? = null
) {
    val slot = posterPlaceholderSlot(title)
    val base = tint ?: posterPlaceholderColor(slot)
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(base)
                .background(
                    // A wash rather than a flat fill: a block of solid colour under a
                    // cover that has not arrived reads as a missing image, and the
                    // gradient reads as a surface the artwork is going to land on.
                    // Both stops keep the slot's hue, so the card's colour identity is
                    // the same one the cover will bring.
                    Brush.verticalGradient(
                        listOf(
                            base.copy(alpha = 0.55f),
                            base.copy(alpha = 0.16f)
                        )
                    )
                )
        )
        Text(
            text = posterInitials(title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
    }
}

/**
 * The slot whose tint stands in for a cover's own colour.
 *
 * Five, because the palette below has five entries and two neighbouring posters in a
 * row should not usually share one. The value is derived from the title rather than
 * from the position in the grid: a card that moves when the list is sorted must not
 * change colour as it moves.
 */
internal const val POSTER_PLACEHOLDER_SLOTS = 5

/**
 * Which palette slot a title's placeholder uses. Stable for a given title, forever.
 *
 * `String.hashCode` is specified by the JDK and therefore identical across processes
 * and launches, which is what makes "the same title is the same colour" true on the
 * next cold start rather than merely within one.
 */
internal fun posterPlaceholderSlot(title: String, slots: Int = POSTER_PLACEHOLDER_SLOTS): Int {
    if (slots <= 1) return 0
    // `Int.MIN_VALUE` has no positive counterpart, so `abs` is guarded rather than
    // trusted: one title in four billion would otherwise index with a negative.
    val hash = title.trim().lowercase().hashCode()
    return (if (hash == Int.MIN_VALUE) 0 else hash.absoluteValue) % slots
}

/** The palette a placeholder draws from, in the theme's own colours. */
@Composable
internal fun posterPlaceholderColor(slot: Int): Color {
    val scheme = MaterialTheme.colorScheme
    return when (slot % POSTER_PLACEHOLDER_SLOTS) {
        0 -> scheme.primary
        1 -> scheme.secondary
        2 -> AniSequelTheme.statusColors.info
        3 -> scheme.tertiary
        else -> AniSequelTheme.statusColors.success
    }
}

/**
 * The initials a placeholder prints.
 *
 * Two shapes, because titles come in two shapes:
 *
 * - **Two or more words** print the first letter of the first two words that have one
 *   ("Frieren: Beyond Journey's End" is `FB`, not `F:`), which is the abbreviation a
 *   reader would make themselves.
 * - **One word** prints its first two characters ("86" stays `86`, "Bleach" is `Bl`),
 *   because a single letter is not enough to tell two titles apart in a grid.
 *
 * Characters are taken by code point, so a title that starts with an emoji or a
 * character outside the BMP yields a whole character rather than half a surrogate pair
 * - the same rule `ContentLimits` clips a draft by.
 */
internal fun posterInitials(title: String): String {
    val words = title.trim()
        .split(' ', '\t', '\n', '-', '_', ':', '/', ',', '.', '(', ')', '[', ']', '~', '|')
        .filter { word -> word.any { it.isLetterOrDigit() } }
    if (words.isEmpty()) return "?"
    if (words.size >= 2) {
        val first = words[0].firstOrNull { it.isLetterOrDigit() }
        val second = words[1].firstOrNull { it.isLetterOrDigit() }
        return listOfNotNull(first, second)
            .joinToString("") { it.toString() }
            .uppercase()
            .take(2)
    }
    val single = words.single()
    val start = single.indexOfFirst { it.isLetterOrDigit() }.coerceAtLeast(0)
    // `codePointCount`/`offsetByCodePoints` rather than `take(2)`: an emoji or a CJK
    // character outside the BMP is two `Char`s, and a card printing half of one is
    // the same broken glyph the draft clip was fixed for.
    val end = single.offsetByCodePoints(
        start,
        minOf(2, single.codePointCount(start, single.length))
    )
    return single.substring(start, end).uppercase()
}
