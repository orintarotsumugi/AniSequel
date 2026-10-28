package com.anisequel.ui.screens.profile

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
import com.anisequel.data.model.MediaCover
import com.anisequel.data.model.MediaCoverKey
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.markdown.AniListMarkdownParser
import com.anisequel.ui.components.markdown.AniListMarkdownView
import com.anisequel.ui.components.markdown.MARKDOWN_COLLAPSED_BLOCK_LIMIT

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
     * Media key to cover for the bio's AniList preview cards.
     *
     * Fetched by the ViewModel as one batch and threaded down, because a bio can
     * carry a dozen AniList links and a request per card would spend a dozen of
     * AniList's 30-a-minute budget before the reader had scrolled.
     *
     * ## Why this one surface does not use `AniListMarkdownWithCovers`
     *
     * Every other markdown renderer resolves its covers through
     * `LocalMediaCoverLoader`, provided once at the navigation root. This card
     * keeps its own map because the fetch is deliberately started by the
     * *ViewModel*, the moment the overview arrives, rather than when this card
     * first composes: the bio's posters are already on their way while the
     * profile's list is still downloading, which is what
     * `UserOverviewLoadOrderTest` pins. Rendering it through the local loader
     * would move the request into composition and tie it to whether this card is
     * the one being drawn.
     */
    mediaCovers: Map<MediaCoverKey, MediaCover> = emptyMap()
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

// `stripMarkdown` and its nine regexes used to live here, with a test suite in
// ProfileTextFormattingTest.kt.
//
// Nothing rendered it. `AboutCard` draws the parsed AST through
// `AniListMarkdownView`, so the only thing keeping a Markdown-to-plain-text
// formatter alive was its own test - and that is the worst kind of evidence,
// because a tested-but-uncalled function reads as "some screen must be using
// this" to the next person who opens the file.
//
// It is deleted rather than wired up. A single-line preview of a bio is a real
// thing to want (a search result, a future social row), but when it is wanted it
// should be written against the real requirement, which is that over-stripping
// is worse than under-stripping: a bio that loses its asterisks and also loses
// its apostrophes is a person's own words edited by their app. Those tests are
// worth keeping in that form, and are re-added then.
