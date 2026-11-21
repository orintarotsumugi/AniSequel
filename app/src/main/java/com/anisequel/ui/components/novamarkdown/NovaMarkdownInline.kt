package com.anisequel.ui.components.novamarkdown

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
 * One unit-width glyph used while an inline spoiler is closed.
 *
 * It is not a box-drawing character: those are not guaranteed to share an
 * advance width across the app's font stack, and a run that changes width when
 * it is revealed reflows the sentence above and below it.
 */
private const val SPOILER_PLACEHOLDER = "▓"

private const val SPOILER_TAG = "nova_inline_spoiler_"

/**
 * Builds the styled span for one inline expression.
 *
 * ## Links go through the caller's handler
 *
 * A `LinkAnnotation.Url` is enough for the platform to open a URL, but this app
 * owns its link behaviour (`openExternalUrlOrCopy`) and a bio tapped in the
 * reader must go the same way as every other tap. The listener is read through
 * `rememberUpdatedState`, because the `AnnotatedString` itself is remembered and
 * a captured lambda would keep the first composition's reference on a recycled
 * list item.
 *
 * ## A spoiler inside a sentence is a toggle
 *
 * An `AnnotatedString` cannot hold a chip, so the closed run's characters carry a
 * `LinkAnnotation.Clickable`: the placeholder text is the control, the label
 * stays legible, and the index is the same in the string and in the reveal set.
 * Nested spoiler indices are reserved while a parent is closed, otherwise opening
 * one spoiler would reveal a sibling's payload under the wrong number.
 */
@Composable
internal fun buildNovaAnnotatedString(
    inlines: List<NovaInline>,
    onLinkClick: ((String) -> Unit)? = null,
    italic: Boolean = false
): AnnotatedString {
    val primary = MaterialTheme.colorScheme.primary
    val codeBackground = primary.copy(alpha = 0.14f)
    val spoilerBackground = MaterialTheme.colorScheme.secondaryContainer
    val spoilerColor = MaterialTheme.colorScheme.onSecondaryContainer

    val linkStyles = remember(primary) {
        TextLinkStyles(
            style = SpanStyle(
                color = primary,
                textDecoration = TextDecoration.Underline,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
    val mentionStyles = remember(primary) {
        TextLinkStyles(style = SpanStyle(color = primary, fontWeight = FontWeight.SemiBold))
    }
    val spoilerStyles = remember(spoilerBackground, spoilerColor) {
        TextLinkStyles(
            style = SpanStyle(
                background = spoilerBackground,
                color = spoilerColor,
                fontWeight = FontWeight.Medium
            )
        )
    }
    val revealedSpoilerStyles = remember(spoilerBackground) {
        TextLinkStyles(style = SpanStyle(background = spoilerBackground))
    }
    val accentStyles = remember(primary) {
        SpanStyle(color = primary, fontWeight = FontWeight.SemiBold)
    }

    val currentOnLinkClick = rememberUpdatedState(onLinkClick)
    var revealed by rememberSaveable { mutableStateOf(IntArray(0)) }

    return remember(
        inlines,
        italic,
        linkStyles,
        mentionStyles,
        spoilerStyles,
        revealedSpoilerStyles,
        accentStyles,
        codeBackground,
        revealed
    ) {
        buildAnnotatedString {
            val spoilers = InlineSpoilerState(
                styles = spoilerStyles,
                revealedStyles = revealedSpoilerStyles,
                revealed = revealed,
                onToggle = { index ->
                    revealed = if (revealed.contains(index)) {
                        revealed.filterNot { it == index }.toIntArray()
                    } else {
                        revealed + index
                    }
                }
            )
            val body: AnnotatedString.Builder.() -> Unit = {
                appendNovaTokens(
                    tokens = inlines,
                    onLinkClick = currentOnLinkClick,
                    linkStyles = linkStyles,
                    mentionStyles = mentionStyles,
                    accentStyles = accentStyles,
                    codeBackground = codeBackground,
                    allowLinks = true,
                    spoilers = spoilers
                )
            }
            if (italic) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { body() }
            } else {
                body()
            }
        }
    }
}

private class InlineSpoilerState(
    val styles: TextLinkStyles,
    val revealedStyles: TextLinkStyles,
    val revealed: IntArray,
    val onToggle: (Int) -> Unit
) {
    var index: Int = 0
}

private fun countNovaSpoilers(tokens: List<NovaInline>): Int {
    var count = 0
    fun walk(list: List<NovaInline>) {
        list.forEach { token ->
            if (token is NovaInline.Spoiler) {
                count++
                walk(token.children)
            } else {
                walk(token.nestedInlines())
            }
        }
    }
    walk(tokens)
    return count
}

private fun AnnotatedString.Builder.appendNovaTokens(
    tokens: List<NovaInline>,
    onLinkClick: State<((String) -> Unit)?>,
    linkStyles: TextLinkStyles,
    mentionStyles: TextLinkStyles,
    accentStyles: SpanStyle,
    codeBackground: Color,
    allowLinks: Boolean,
    spoilers: InlineSpoilerState
) {
    tokens.forEach { token ->
        when (token) {
            is NovaInline.Text -> append(token.text)

            is NovaInline.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) {
                appendNovaTokens(
                    token.children, onLinkClick, linkStyles, mentionStyles,
                    accentStyles, codeBackground, allowLinks, spoilers
                )
            }

            is NovaInline.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                appendNovaTokens(
                    token.children, onLinkClick, linkStyles, mentionStyles,
                    accentStyles, codeBackground, allowLinks, spoilers
                )
            }

            is NovaInline.BoldItalic -> withStyle(
                SpanStyle(fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic)
            ) {
                appendNovaTokens(
                    token.children, onLinkClick, linkStyles, mentionStyles,
                    accentStyles, codeBackground, allowLinks, spoilers
                )
            }

            is NovaInline.Underline -> withStyle(
                SpanStyle(
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.SemiBold
                )
            ) {
                appendNovaTokens(
                    token.children, onLinkClick, linkStyles, mentionStyles,
                    accentStyles, codeBackground, allowLinks, spoilers
                )
            }

            is NovaInline.Strike -> withStyle(
                SpanStyle(textDecoration = TextDecoration.LineThrough)
            ) {
                appendNovaTokens(
                    token.children, onLinkClick, linkStyles, mentionStyles,
                    accentStyles, codeBackground, allowLinks, spoilers
                )
            }

            is NovaInline.Code -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)
            ) {
                append(" ${token.code} ")
            }

            is NovaInline.Mono -> withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = codeBackground
                )
            ) {
                appendNovaTokens(
                    token.children, onLinkClick, linkStyles, mentionStyles,
                    accentStyles, codeBackground, allowLinks, spoilers
                )
            }

            is NovaInline.Link -> appendNovaLink(
                url = token.url,
                children = token.children,
                styles = linkStyles,
                onLinkClick = onLinkClick,
                mentionStyles = mentionStyles,
                accentStyles = accentStyles,
                codeBackground = codeBackground,
                allowLinks = allowLinks,
                spoilers = spoilers
            )

            is NovaInline.MediaLink -> appendNovaLink(
                url = token.url,
                children = listOf(NovaInline.Text(token.label)),
                styles = linkStyles,
                onLinkClick = onLinkClick,
                mentionStyles = mentionStyles,
                accentStyles = accentStyles,
                codeBackground = codeBackground,
                allowLinks = allowLinks,
                spoilers = spoilers
            )

            is NovaInline.Mention -> {
                val label = "@${token.username}"
                val url = "https://anilist.co/user/${token.username}"
                if (allowLinks) {
                    withLink(novaLink(url, mentionStyles, onLinkClick)) { append(label) }
                } else {
                    withStyle(mentionStyles.style ?: SpanStyle()) { append(label) }
                }
            }

            is NovaInline.Accent -> withStyle(accentStyles) {
                appendNovaTokens(
                    token.children, onLinkClick, linkStyles, mentionStyles,
                    accentStyles, codeBackground, allowLinks, spoilers
                )
            }

            is NovaInline.Image -> {
                val label = token.alt ?: "Image"
                if (allowLinks) {
                    withLink(novaLink(token.url, linkStyles, onLinkClick)) { append(label) }
                } else {
                    withStyle(linkStyles.style ?: SpanStyle()) { append(label) }
                }
            }

            is NovaInline.Spoiler -> appendNovaSpoiler(
                token = token,
                onLinkClick = onLinkClick,
                linkStyles = linkStyles,
                mentionStyles = mentionStyles,
                accentStyles = accentStyles,
                codeBackground = codeBackground,
                allowLinks = allowLinks,
                spoilers = spoilers
            )
        }
    }
}

private fun AnnotatedString.Builder.appendNovaLink(
    url: String,
    children: List<NovaInline>,
    styles: TextLinkStyles,
    onLinkClick: State<((String) -> Unit)?>,
    mentionStyles: TextLinkStyles,
    accentStyles: SpanStyle,
    codeBackground: Color,
    allowLinks: Boolean,
    spoilers: InlineSpoilerState
) {
    val labelTokens = children.ifEmpty { listOf(NovaInline.Text(url)) }
    if (allowLinks) {
        withLink(novaLink(url, styles, onLinkClick)) {
            appendNovaTokens(
                labelTokens, onLinkClick, styles, mentionStyles,
                accentStyles, codeBackground, allowLinks = false, spoilers
            )
        }
    } else {
        withStyle(styles.style ?: SpanStyle()) {
            appendNovaTokens(
                labelTokens, onLinkClick, styles, mentionStyles,
                accentStyles, codeBackground, allowLinks = false, spoilers
            )
        }
    }
}

private fun AnnotatedString.Builder.appendNovaSpoiler(
    token: NovaInline.Spoiler,
    onLinkClick: State<((String) -> Unit)?>,
    linkStyles: TextLinkStyles,
    mentionStyles: TextLinkStyles,
    accentStyles: SpanStyle,
    codeBackground: Color,
    allowLinks: Boolean,
    spoilers: InlineSpoilerState
) {
    val index = spoilers.index++
    val revealed = spoilers.revealed.contains(index)

    if (!allowLinks) {
        // Already inside a link or another spoiler annotation. The run keeps the
        // obscuring style, but a second annotation over it would be resolved
        // arbitrarily by the text framework, so it gets none.
        if (revealed) {
            appendNovaTokens(
                token.children, onLinkClick, linkStyles, mentionStyles,
                accentStyles, codeBackground, false, spoilers
            )
        } else {
            withStyle(spoilers.styles.style ?: SpanStyle()) {
                append(SPOILER_PLACEHOLDER.repeat(token.text.length.coerceAtLeast(1)))
            }
            spoilers.index += countNovaSpoilers(token.children)
        }
        return
    }

    withLink(
        LinkAnnotation.Clickable(
            tag = "$SPOILER_TAG$index",
            styles = if (revealed) spoilers.revealedStyles else spoilers.styles,
            linkInteractionListener = { spoilers.onToggle(index) }
        )
    ) {
        if (revealed) {
            appendNovaTokens(
                token.children, onLinkClick, linkStyles, mentionStyles,
                accentStyles, codeBackground, allowLinks = false, spoilers
            )
        } else {
            append(SPOILER_PLACEHOLDER.repeat(token.text.length.coerceAtLeast(1)))
            token.label?.let { label ->
                append(" ")
                append(label)
            }
            // The children's spoilers are not drawn while the parent is closed,
            // but their numbers still have to be spent so the next sibling is
            // numbered the same before and after the reveal.
            spoilers.index += countNovaSpoilers(token.children)
        }
    }
}

private fun novaLink(
    url: String,
    styles: TextLinkStyles,
    onLinkClick: State<((String) -> Unit)?>
): LinkAnnotation.Url {
    val listener = if (onLinkClick.value == null) {
        null
    } else {
        LinkInteractionListener { annotation ->
            val target = (annotation as? LinkAnnotation.Url)?.url
            if (target != null) onLinkClick.value?.invoke(target)
        }
    }
    return LinkAnnotation.Url(url = url, styles = styles, linkInteractionListener = listener)
}
