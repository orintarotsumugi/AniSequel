package com.example.ui.components.markdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
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

    return remember(inlines, primaryColor, codeBackground, spoilerBackground, spoilerColor) {
        buildAnnotatedString {
            inlines.forEach { token ->
                when (token) {
                    is InlineToken.Plain -> append(token.text)
                    is InlineToken.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(token.text)
                    }
                    is InlineToken.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(token.text)
                    }
                    is InlineToken.BoldItalic -> withStyle(
                        SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                    ) {
                        append(token.text)
                    }
                    is InlineToken.Underline -> withStyle(
                        SpanStyle(textDecoration = TextDecoration.Underline)
                    ) {
                        append(token.text)
                    }
                    is InlineToken.Strikethrough -> withStyle(
                        SpanStyle(textDecoration = TextDecoration.LineThrough)
                    ) {
                        append(token.text)
                    }
                    is InlineToken.Code -> withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBackground
                        )
                    ) {
                        append(" ${token.code} ")
                    }
                    is InlineToken.Link -> {
                        val linkStyle = TextLinkStyles(
                            style = SpanStyle(
                                color = primaryColor,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        withLink(LinkAnnotation.Url(token.url, styles = linkStyle)) {
                            append(token.label)
                        }
                    }
                    is InlineToken.Mention -> {
                        val mentionStyle = TextLinkStyles(
                            style = SpanStyle(
                                color = primaryColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        withLink(
                            LinkAnnotation.Url(
                                "https://anilist.co/user/${token.username}",
                                styles = mentionStyle
                            )
                        ) {
                            append("@${token.username}")
                        }
                    }
                    // The fallback for an inline image. `RenderParagraph` embeds the
                    // real image when it can; everywhere else (a header, a list
                    // item, a truncated bio) an annotation cannot hold a bitmap, so
                    // the image becomes a link to itself labelled by its alt text.
                    is InlineToken.Image -> {
                        val imageStyle = TextLinkStyles(
                            style = SpanStyle(
                                color = primaryColor,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        withLink(LinkAnnotation.Url(token.url, styles = imageStyle)) {
                            append(token.alt ?: "Image")
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
    }
}
