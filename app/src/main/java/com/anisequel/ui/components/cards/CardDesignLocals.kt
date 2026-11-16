package com.anisequel.ui.components.cards

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.anisequel.domain.card.CardDesign
import com.anisequel.ui.components.LocalTitleMotion
import com.anisequel.ui.components.TitleMotion

/**
 * The reader's card design, handed down from the app root.
 *
 * A composition local rather than a parameter for the same reason
 * [LocalMediaCardStyle] is one: the Library grid, a profile's poster list, the
 * dashboard's sequel rows and the Settings preview all draw the same card, and a
 * parameter has to be threaded through every layer between the setting and the
 * `Text` that uses it - which is exactly how the preview and the grid end up
 * describing different cards. Provided once in `AppNavigation`, beside the style.
 *
 * The value is already clamped by the repository. It is not clamped again here:
 * the helpers below are also called by editors that clamp their own drafts, and a
 * second clamp would hide a bug rather than fix one.
 */
val LocalCardDesign = staticCompositionLocalOf { CardDesign.DEFAULT }

/**
 * Heavier than the shipped weight only when the reader asked for it.
 *
 * Semibold is the baseline rather than the typography's own weight because a card's
 * name is read at 11-13sp against artwork, where Medium is a caption and Semibold is
 * a title. Bold is the next step up and not a different face, so the two settings
 * describe one axis - how loud the name is - instead of two.
 */
fun cardTitleWeight(design: CardDesign): FontWeight =
    if (design.boldTitles) FontWeight.Bold else FontWeight.SemiBold

/**
 * A card's name, at the reader's size and weight.
 *
 * Both the size and the line height are scaled together: growing the glyphs inside a
 * line box that stayed the same is how a two-line caption ends up with its second
 * line clipped by the card's own boundary. [base] is a parameter so the preview and
 * the wider card modes can start from their own step of the type scale while
 * inheriting the reader's factor.
 */
@Composable
fun cardTitleStyle(
    design: CardDesign = LocalCardDesign.current,
    base: TextStyle = MaterialTheme.typography.labelMedium
): TextStyle = remember(base, design.titleScale, design.boldTitles) {
    base.scaledBy(design.titleScale).copy(fontWeight = cardTitleWeight(design))
}

/**
 * A card's rating - the percentage on the poster, the pill under it, the line in the
 * caption - at the reader's size.
 *
 * Separate from the title's factor because the two answer different questions: a
 * score is scanned, a name is read, and a reader who wants a bigger score almost
 * never wants a bigger name as well.
 */
@Composable
fun cardRatingStyle(
    design: CardDesign = LocalCardDesign.current,
    base: TextStyle = MaterialTheme.typography.labelSmall
): TextStyle = remember(base, design.ratingScale) {
    base.scaledBy(design.ratingScale)
}

/**
 * A title too long for its line scrolls only if the reader left the switch on.
 *
 * The app-wide [LocalTitleMotion] still has the last word: under an Instant motion
 * style nothing moves anywhere, and a card is not an exception. This only adds the
 * card-specific answer on top of it.
 */
@Composable
fun cardTitleMotion(design: CardDesign = LocalCardDesign.current): TitleMotion {
    val appWide = LocalTitleMotion.current
    return remember(appWide, design.scrollTitles) {
        if (design.scrollTitles) appWide else appWide.copy(animates = false)
    }
}

/**
 * A poster's width-over-height ratio at the reader's card size.
 *
 * Stretching the same ratio is what makes a two-across card a bigger picture rather
 * than the same cover with more empty space beside it. The bias is small by design:
 * the aspect ratio is what the artwork was cropped for, and a large change would show
 * the letterboxing a crop is supposed to hide.
 */
fun cardCoverAspect(base: Float, design: CardDesign): Float = base * design.size.coverBias

/** Scales the parts of a style that describe size, leaving colour and family alone. */
private fun TextStyle.scaledBy(scale: Float): TextStyle {
    if (scale == 1f) return this
    return copy(
        fontSize = fontSize.scaledBy(scale),
        lineHeight = lineHeight.scaledBy(scale)
    )
}

/**
 * Multiplies a length, or leaves it unspecified.
 *
 * `TextUnit.Unspecified` is a sentinel and throws when arithmetic is applied to it -
 * a style that inherits its size from the caller must keep inheriting it, not crash.
 */
private fun TextUnit.scaledBy(scale: Float) =
    if (this == TextUnit.Unspecified) this else this * scale
