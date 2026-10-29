package com.anisequel.ui.components.markdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

/**
 * Stands in for an inline spoiler's characters while it is hidden.
 *
 * One unit-width glyph, so the obscured run occupies roughly the width the real
 * text will when it is revealed and the sentence does not reflow. It is *not* a
 * box-drawing character: those are not guaranteed to render at a consistent
 * advance width across the app's font stack, and a spoiler block that is subtly
 * a different width after revealing is a visible jump.
 */
private const val SPOILER_PLACEHOLDER = "▓"

/** The click tag on an inline spoiler, so the reveal can name the run it opens. */
private const val INLINE_SPOILER_TAG = "markdown_inline_spoiler_"

/**
 * Builds an [AnnotatedString] from a list of [InlineToken]s with Material 3 styling
 * and clickable link annotations.
 * ## Links actually go somewhere
 *
 * [onLinkClick] used to be accepted and then ignored, on the assumption that a
 * `LinkAnnotation.Url` is opened by the default `UriHandler` in `Text`. That is
 * true, but it meant the app's own URL handling - the one every other tap in the
 * UI goes through, which is what makes a link open in a Custom Tab rather than
 * whatever the system resolves - was never reached. The annotation now carries a
 * [LinkInteractionListener] that forwards the URL to [onLinkClick] when one is
 * supplied, and falls back to the platform handler when it is not.
 *
 * The listener is held through a [rememberUpdatedState] rather than being read
 * directly, because the `AnnotatedString` is itself remembered: capturing the
 * lambda value would freeze the first composition's reference, whereas reading
 * the state at click time always uses the current one.
 *
 * ## Nested markup renders as markup
 *
 * Emphasis runs and link labels carry parsed children (see [InlineToken.Bold]),
 * so a bold line whose text is a link, or a link whose label is bold, renders as
 * both instead of printing the inner source. A link nested inside another link is
 * not wrapped a second time - it is styled in place - so no two link annotations
 * ever overlap, which is the one shape that makes the framework pick arbitrarily.
 *
 * ## An inline spoiler toggles in place
 *
 * `~!...!~` in the middle of a sentence is the same token the block-level
 * [RenderSpoilerBlock] handles, one paragraph down. The run is the tap target:
 * an `AnnotatedString` cannot hold a chip, and a pill dropped mid-sentence would
 * break the line it lives on, so the placeholder characters carry a
 * [LinkAnnotation.Clickable] and toggle on the tap that lands on them.
 *
 * It used to be unrevealable, and the note here said why: "an `AnnotatedString`
 * cannot be tapped, so it has nowhere to put a reveal". That stopped being true
 * with `LinkAnnotation.Clickable`, and the cost of not noticing was that every
 * plot point written inline in a review stayed covered for good - while a
 * `~!...!~` block a line away, whose chip did reveal fine, sat right next to it.
 *
 * Unlike the block chip, which is a separate control and deliberately leaves the
 * revealed text inert, an inline spoiler has no control but the text itself - so
 * the revealed run stays tappable and keeps its container tint to show it can be
 * closed again. A tap there is a deliberate hit on a marked run, not a thumb
 * resting on a paragraph.
 *
 * The reveal is per paragraph and saved, for the same reason the chip's is: a bio
 * paragraph composed into a scrolling list is disposed when it leaves the
 * viewport, and a reader who opened a spoiler has already chosen to read it.
 */
@Composable
fun buildInlineAnnotatedString(
    inlines: List<InlineToken>,
    onLinkClick: ((String) -> Unit)? = null
): AnnotatedString {
    val primaryColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val spoilerBackground = MaterialTheme.colorScheme.secondaryContainer
    val spoilerColor = MaterialTheme.colorScheme.onSecondaryContainer

    val linkStyles = remember(primaryColor) {
        TextLinkStyles(
            style = SpanStyle(
                color = primaryColor,
                textDecoration = TextDecoration.Underline,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
    val mentionStyles = remember(primaryColor) {
        TextLinkStyles(
            style = SpanStyle(color = primaryColor, fontWeight = FontWeight.SemiBold)
        )
    }
    // The obscured run wears the same pair of roles as `SpoilerChip`, so "this text
    // is hidden by a spoiler" is one colour language in both places. It was drawn
    // on `surfaceContainerHighest` before, which is also the code-run background:
    // an inline spoiler and a `code` span looked like the same chip.
    val spoilerStyles = remember(spoilerBackground, spoilerColor) {
        TextLinkStyles(
            style = SpanStyle(
                background = spoilerBackground,
                color = spoilerColor,
                fontWeight = FontWeight.Medium
            )
        )
    }
    // A revealed run keeps the same container so the reader can see what used to
    // be covered - and therefore where to tap to cover it again - but switches to
    // ordinary body colour, because the plot point is supposed to be read now.
    val revealedSpoilerStyles = remember(spoilerBackground) {
        TextLinkStyles(
            style = SpanStyle(background = spoilerBackground)
        )
    }

    val currentOnLinkClick = rememberUpdatedState(onLinkClick)

    // Which of this paragraph's inline spoilers the reader has opened, by index.
    // `rememberSaveable`, for the reason `RenderSpoilerBlock` saves its one boolean:
    // the paragraph is disposed with its LazyColumn item, and a reveal the reader
    // chose must not be undone by scrolling past and back.
    var revealedSpoilers by rememberSaveable { mutableStateOf(IntArray(0)) }

    return remember(inlines, linkStyles, mentionStyles, codeBackground, spoilerStyles, revealedSpoilerStyles, revealedSpoilers) {
        buildAnnotatedString {
            appendTokens(
                tokens = inlines,
                onLinkClick = currentOnLinkClick,
                linkStyles = linkStyles,
                mentionStyles = mentionStyles,
                codeBackground = codeBackground,
                allowLinks = true,
                spoilers = InlineSpoilers(
                    styles = spoilerStyles,
                    revealedStyles = revealedSpoilerStyles,
                    revealed = revealedSpoilers,
                    onToggle = { index ->
                        revealedSpoilers = if (revealedSpoilers.contains(index)) {
                            revealedSpoilers.filterNot { it == index }.toIntArray()
                        } else {
                            revealedSpoilers + index
                        }
                    }
                )
            )
        }
    }
}

/**
 * The state one [buildInlineAnnotatedString] call needs to hide and reveal its
 * spoilers.
 *
 * [index] counts spoilers in reading order as [appendTokens] walks them, and the
 * rebuilt string walks the same tokens in the same order, so "index 2" is the
 * same plot twist in the reveal set and in the string that reads it. It is
 * per-call rather than global because [AniListMarkdownParagraph] renders one
 * paragraph's text runs as separate `Text`s, each with its own reveal set.
 */
private class InlineSpoilers(
    val styles: TextLinkStyles,
    val revealedStyles: TextLinkStyles,
    val revealed: IntArray,
    val onToggle: (Int) -> Unit
) {
    var index: Int = 0
}

/**
 * Appends [tokens] under the style they carry, recursing into the nested
 * expression of every emphasis run and link label.
 *
 * [allowLinks] is false while inside a link, so a nested link (or mention, or
 * inline image) degrades to styled text rather than a second, overlapping
 * annotation.
 */
private fun AnnotatedString.Builder.appendTokens(
    tokens: List<InlineToken>,
    onLinkClick: State<((String) -> Unit)?>,
    linkStyles: TextLinkStyles,
    mentionStyles: TextLinkStyles,
    codeBackground: Color,
    allowLinks: Boolean,
    spoilers: InlineSpoilers
) {
    tokens.forEach { token ->
        when (token) {
            is InlineToken.Plain -> append(token.text)

            // Bold is `ExtraBold`, not `Bold`.
            //
            // At `bodyMedium` on a phone the two are close enough to be a coin toss,
            // so `**bold**` in a bio rendered as very slightly heavier body text and a
            // reader could not tell it had been emphasised at all. AniList bios lean
            // on bold hard - the "hall of fame" convention is a whole bold line per
            // entry - so this is not a rare case. `ExtraBold` is the step that reads
            // as a deliberate emphasis against `Normal` while still staying a weight
            // the typeface can render rather than a synthetic embolden.
            is InlineToken.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) {
                appendTokens(token.childrenOrText(), onLinkClick, linkStyles, mentionStyles, codeBackground, allowLinks, spoilers)
            }

            is InlineToken.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                appendTokens(token.childrenOrText(), onLinkClick, linkStyles, mentionStyles, codeBackground, allowLinks, spoilers)
            }

            is InlineToken.BoldItalic -> withStyle(
                SpanStyle(fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic)
            ) {
                appendTokens(token.childrenOrText(), onLinkClick, linkStyles, mentionStyles, codeBackground, allowLinks, spoilers)
            }

            // Underlined *and* coloured, for the same reason as bold above: an
            // underline alone on body text is a hairline at this size, and it is the
            // one decoration that reads as a broken link rather than as emphasis.
            // It is not given the link's own colour - that would make emphasised text
            // indistinguishable from a hyperlink, and the two mean different things.
            is InlineToken.Underline -> withStyle(
                SpanStyle(textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold)
            ) {
                appendTokens(token.childrenOrText(), onLinkClick, linkStyles, mentionStyles, codeBackground, allowLinks, spoilers)
            }

            is InlineToken.Strikethrough -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                appendTokens(token.childrenOrText(), onLinkClick, linkStyles, mentionStyles, codeBackground, allowLinks, spoilers)
            }

            is InlineToken.Code -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)
            ) {
                append(" ${token.code} ")
            }

            is InlineToken.Link -> appendLink(
                url = token.url,
                label = token.label,
                children = token.children,
                styles = linkStyles,
                onLinkClick = onLinkClick,
                mentionStyles = mentionStyles,
                codeBackground = codeBackground,
                allowLinks = allowLinks,
                spoilers = spoilers
            )

            // The fallback for an AniList media link. `AniListMarkdownView`
            // lifts these out of a paragraph into a cover card; everywhere a card
            // cannot be drawn - a list item, a header, a table cell, a truncated
            // bio - the link has to still read as a link, or the title would
            // simply vanish from the sentence.
            is InlineToken.MediaLink -> appendLink(
                url = token.url,
                label = token.label,
                children = emptyList(),
                styles = linkStyles,
                onLinkClick = onLinkClick,
                mentionStyles = mentionStyles,
                codeBackground = codeBackground,
                allowLinks = allowLinks,
                spoilers = spoilers
            )

            is InlineToken.Mention -> {
                val label = "@${token.username}"
                if (allowLinks) {
                    withLink(link("https://anilist.co/user/${token.username}", mentionStyles, onLinkClick)) {
                        append(label)
                    }
                } else {
                    withStyle(mentionStyles.style ?: SpanStyle()) { append(label) }
                }
            }

            // The fallback for an inline image. `RenderParagraph` embeds the real
            // image when it can; everywhere else (a header, a list item, a
            // truncated bio) an annotation cannot hold a bitmap, so the image
            // becomes a link to itself labelled by its alt text.
            is InlineToken.Image -> {
                val label = token.alt ?: "Image"
                if (allowLinks) {
                    withLink(link(token.url, linkStyles, onLinkClick)) { append(label) }
                } else {
                    withStyle(linkStyles.style ?: SpanStyle()) { append(label) }
                }
            }

            // The obscured run is the tap target, which is the whole reveal UX: a
            // chip is not drawable in an `AnnotatedString`, and a pill dropped
            // mid-sentence would break the line it lives on. The placeholders keep
            // the run's shape while it is closed.
            //
            // It used to be unrevealable - drawn obscured, with no path to the text
            // at all - so every spoiler written inline inside a paragraph was
            // permanently hidden, while the block-level `~!...!~` chip a line away
            // opened fine. (Before *that* it printed `[Spoiler: ...]` in full, which
            // is the opposite failure.) The revealed run stays link-annotated so a
            // second tap closes it again, matching the block chip's toggle.
            is InlineToken.Spoiler -> {
                val index = spoilers.index++
                val revealed = spoilers.revealed.contains(index)
                if (allowLinks) {
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = "$INLINE_SPOILER_TAG$index",
                            styles = if (revealed) spoilers.revealedStyles else spoilers.styles,
                            linkInteractionListener = { spoilers.onToggle(index) }
                        )
                    ) {
                        if (revealed) {
                            append(token.text)
                        } else {
                            append(SPOILER_PLACEHOLDER.repeat(token.text.length.coerceAtLeast(1)))
                        }
                    }
                } else if (revealed) {
                    append(token.text)
                } else {
                    // Inside a link label: two annotations over the same characters
                    // is the one shape the framework resolves arbitrarily (see
                    // `appendLink`), so it keeps the obscuring style and no tap.
                    withStyle(spoilers.styles.style ?: SpanStyle()) {
                        append(SPOILER_PLACEHOLDER.repeat(token.text.length.coerceAtLeast(1)))
                    }
                }
            }
        }
    }
}

private fun AnnotatedString.Builder.appendLink(
    url: String,
    label: String,
    children: List<InlineToken>,
    styles: TextLinkStyles,
    onLinkClick: State<((String) -> Unit)?>,
    mentionStyles: TextLinkStyles,
    codeBackground: Color,
    allowLinks: Boolean,
    spoilers: InlineSpoilers
) {
    val labelTokens = children.ifEmpty { listOf(InlineToken.Plain(label)) }
    if (allowLinks) {
        withLink(link(url, styles, onLinkClick)) {
            appendTokens(labelTokens, onLinkClick, styles, mentionStyles, codeBackground, allowLinks = false, spoilers)
        }
    } else {
        // Already inside a link: keeping the styling but not a second annotation
        // means a nested label still reads as a link without two clickable ranges
        // overlapping over the same characters.
        withStyle(styles.style ?: SpanStyle()) {
            appendTokens(labelTokens, onLinkClick, styles, mentionStyles, codeBackground, allowLinks = false, spoilers)
        }
    }
}

/**
 * The URL annotation, routed through the caller's handler when there is one.
 *
 * The listener reads its state on click rather than capturing the callback's
 * value, so a rebuilt `AnnotatedString` is not required for the handler to stay
 * current.
 */
private fun link(
    url: String,
    styles: TextLinkStyles,
    onLinkClick: State<((String) -> Unit)?>
): LinkAnnotation.Url {
    val handler = onLinkClick.value
    val listener = if (handler == null) {
        null
    } else {
        LinkInteractionListener { annotation ->
            val target = (annotation as? LinkAnnotation.Url)?.url
            if (target != null) handler(target)
        }
    }
    return LinkAnnotation.Url(url = url, styles = styles, linkInteractionListener = listener)
}

/** The parsed inner expression, or the flat text this token has always carried. */
private fun InlineToken.childrenOrText(): List<InlineToken> = when (this) {
    is InlineToken.Bold -> children.ifEmpty { listOf(InlineToken.Plain(text)) }
    is InlineToken.Italic -> children.ifEmpty { listOf(InlineToken.Plain(text)) }
    is InlineToken.BoldItalic -> children.ifEmpty { listOf(InlineToken.Plain(text)) }
    is InlineToken.Underline -> children.ifEmpty { listOf(InlineToken.Plain(text)) }
    is InlineToken.Strikethrough -> children.ifEmpty { listOf(InlineToken.Plain(text)) }
    else -> listOf(InlineToken.Plain(""))
}
