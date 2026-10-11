package com.example.ui.components.markdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
 * Builds an [AnnotatedString] from a list of [InlineToken]s with Material 3 styling
 * and clickable link annotations.
 *
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
 */
@Composable
fun buildInlineAnnotatedString(
    inlines: List<InlineToken>,
    onLinkClick: ((String) -> Unit)? = null
): AnnotatedString {
    val primaryColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val spoilerBackground = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f)
    val spoilerColor = MaterialTheme.colorScheme.onSurfaceVariant

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

    val currentOnLinkClick = rememberUpdatedState(onLinkClick)

    return remember(inlines, primaryColor, codeBackground, spoilerBackground, spoilerColor) {
        buildAnnotatedString {
            appendTokens(
                tokens = inlines,
                onLinkClick = currentOnLinkClick,
                linkStyles = linkStyles,
                mentionStyles = mentionStyles,
                codeBackground = codeBackground,
                spoilerBackground = spoilerBackground,
                spoilerColor = spoilerColor,
                allowLinks = true
            )
        }
    }
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
    spoilerBackground: Color,
    spoilerColor: Color,
    allowLinks: Boolean
) {
    tokens.forEach { token ->
        when (token) {
            is InlineToken.Plain -> append(token.text)

            is InlineToken.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                appendTokens(token.childrenOrText(), onLinkClick, linkStyles, mentionStyles, codeBackground, spoilerBackground, spoilerColor, allowLinks)
            }

            is InlineToken.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                appendTokens(token.childrenOrText(), onLinkClick, linkStyles, mentionStyles, codeBackground, spoilerBackground, spoilerColor, allowLinks)
            }

            is InlineToken.BoldItalic -> withStyle(
                SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
            ) {
                appendTokens(token.childrenOrText(), onLinkClick, linkStyles, mentionStyles, codeBackground, spoilerBackground, spoilerColor, allowLinks)
            }

            is InlineToken.Underline -> withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                appendTokens(token.childrenOrText(), onLinkClick, linkStyles, mentionStyles, codeBackground, spoilerBackground, spoilerColor, allowLinks)
            }

            is InlineToken.Strikethrough -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                appendTokens(token.childrenOrText(), onLinkClick, linkStyles, mentionStyles, codeBackground, spoilerBackground, spoilerColor, allowLinks)
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
                spoilerBackground = spoilerBackground,
                spoilerColor = spoilerColor,
                allowLinks = allowLinks
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

            is InlineToken.Spoiler -> withStyle(
                SpanStyle(
                    background = spoilerBackground,
                    color = spoilerColor,
                    fontWeight = FontWeight.Medium
                )
            ) {
                append(" [Spoiler: ${token.text}] ")
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
    spoilerBackground: Color,
    spoilerColor: Color,
    allowLinks: Boolean
) {
    val labelTokens = children.ifEmpty { listOf(InlineToken.Plain(label)) }
    if (allowLinks) {
        withLink(link(url, styles, onLinkClick)) {
            appendTokens(labelTokens, onLinkClick, styles, mentionStyles, codeBackground, spoilerBackground, spoilerColor, allowLinks = false)
        }
    } else {
        // Already inside a link: keeping the styling but not a second annotation
        // means a nested label still reads as a link without two clickable ranges
        // overlapping over the same characters.
        withStyle(styles.style ?: SpanStyle()) {
            appendTokens(labelTokens, onLinkClick, styles, mentionStyles, codeBackground, spoilerBackground, spoilerColor, allowLinks = false)
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
