package com.example.ui.screens.profile

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.expressive.ExpressiveMotion
import com.example.ui.components.expressive.bouncyPress
import com.example.ui.components.markdown.AniListMarkdownParser
import com.example.ui.components.markdown.AniListMarkdownView
import com.example.ui.components.markdown.MARKDOWN_COLLAPSED_BLOCK_LIMIT

/**
 * How many lines a collapsed bio shows before the fade takes over.
 *
 * Six is roughly three lines of text at a phone's content width after the card's
 * own insets, which reads as "there is more" without pushing the favourites rows
 * a full screen down.
 */
private const val MARKDOWN_COLLAPSED_LINES = 6

/**
 * The character count past which a bio is assumed to have more to show.
 *
 * A heuristic alongside the block count, not instead of it: a single long
 * paragraph is one block however many words it holds, so the block rule alone
 * would leave a very long paragraph with no toggle at all.
 */
private const val COLLAPSED_CHAR_HINT = 280

/**
 * Whether a bio is long enough to be worth collapsing behind a toggle.
 *
 * Extracted, and taking the parsed block count, because the two halves have to
 * agree and the card is the only place that knows both. Keying off
 * [COLLAPSED_CHAR_HINT] alone was the bug: it disagreed with what
 * [AniListMarkdownView] actually clips, in both directions. A 400-character bio
 * in two paragraphs clipped nothing, so "Show more" offered to reveal a change
 * that was not there; and a 200-character bio in six paragraphs had two of them
 * dropped by the renderer with no cue at all, while the toggle that would have
 * explained it stayed hidden.
 *
 * [blockCount] is the parser's node count, not a count of newlines: a bio with
 * six one-line paragraphs is six blocks and does need collapsing, and a bio that
 * is one paragraph of 400 characters is one block and does not.
 */
internal fun isBioCollapsible(about: String, blockCount: Int): Boolean =
    about.length > COLLAPSED_CHAR_HINT || blockCount > MARKDOWN_COLLAPSED_BLOCK_LIMIT

/**
 * The person's own bio, rendered with expressive AniList Markdown support and an optional
 * expand toggle for long bios to maintain comfortable scrolling.
 */
@Composable
fun AboutCard(
    about: String,
    modifier: Modifier = Modifier,
    maxWidth: Dp = ProfileMaxContentWidth,
    /**
     * Media id to cover URL for the bio's AniList preview cards.
     *
     * Fetched by the ViewModel as one batch and threaded down, because a bio can
     * carry a dozen AniList links and a request per card would spend a dozen of
     * AniList's 30-a-minute budget before the reader had scrolled.
     */
    mediaCovers: Map<Int, String> = emptyMap()
) {
    val nodes = remember(about) { AniListMarkdownParser.parse(about) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val isCollapsed = isBioCollapsible(about, nodes.size) && !expanded

    Card(
        modifier = modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("profile_about_card"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .animateContentSize(animationSpec = ExpressiveMotion.FastSpatialSize)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = AppVectorIcons.ProfileAbout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (isBioCollapsible(about, nodes.size)) {
                    Text(
                        text = if (expanded) "Show less" else "Show more",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .bouncyPress()
                            .clickable { expanded = !expanded }
                            // The toggle is a bare `Text`: at `labelMedium` it is
                            // about 24dp tall, which is half the 48dp minimum and
                            // makes the one control that reveals the rest of a
                            // person's bio the hardest thing on the card to hit.
                            .defaultMinSize(minHeight = 48.dp)
                            .padding(horizontal = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box {
                AniListMarkdownView(
                    nodes = nodes,
                    maxLines = if (isCollapsed) MARKDOWN_COLLAPSED_LINES else Int.MAX_VALUE,
                    mediaCovers = mediaCovers
                )
                // The fade is the point of this block.
                //
                // [AniListMarkdownView] clips a collapsed bio to a few blocks and
                // a few lines, and a clip with nothing at its edge is a bio that
                // simply stops - the reader has no way to tell a person's
                // truncated sentence from the whole of what they wrote, and no
                // reason to look for the toggle above. Fading the last lines into
                // the card is what makes the cut legible.
                if (isCollapsed) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(28.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    )
                                )
                            )
                    )
                }
            }
        }
    }
}

@Composable
internal fun NoBioNote() {
    Text(
        text = "No bio on AniList",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .widthIn(max = ProfileMaxContentWidth)
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .testTag("profile_no_bio")
    )
}

private val STRIP_LINK_REGEX = Regex("""\[([^\]]+)]\([^)]*\)""")
private val STRIP_IMAGE_REGEX = Regex("""!\[[^\]]*]\([^)]*\)""")
private val STRIP_HEADER_REGEX = Regex("""^[ \t]{0,3}#{1,6}[ \t]*""", RegexOption.MULTILINE)
private val STRIP_BLOCKQUOTE_REGEX = Regex("""^[ \t]{0,3}>[ \t]?""")
private val STRIP_BOLD_REGEX = Regex("""(\*\*|__)(.+?)\1""")
private val STRIP_ITALIC_REGEX = Regex("""(\*|_)(.+?)\1""")
private val STRIP_LIST_REGEX = Regex("""^[ \t]{0,3}[-*+][ \t]+""", RegexOption.MULTILINE)
private val STRIP_CODE_REGEX = Regex("""`([^`]+)`""")
private val STRIP_NEWLINES_REGEX = Regex("""\n{3,}""")

/**
 * Reduce Markdown to plain text.
 */
internal fun stripMarkdown(source: String): String = source
    .replace(STRIP_LINK_REGEX, "$1")
    .replace(STRIP_IMAGE_REGEX, "")
    .replace(STRIP_HEADER_REGEX, "")
    .replace(STRIP_BLOCKQUOTE_REGEX, "")
    .replace(STRIP_BOLD_REGEX, "$2")
    .replace(STRIP_ITALIC_REGEX, "$2")
    .replace(STRIP_LIST_REGEX, "")
    .replace(STRIP_CODE_REGEX, "$1")
    .replace(STRIP_NEWLINES_REGEX, "\n\n")
    .trim()
