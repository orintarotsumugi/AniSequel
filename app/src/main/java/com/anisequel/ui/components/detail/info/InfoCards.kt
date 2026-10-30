package com.anisequel.ui.components.detail.info

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MissedSequel
import com.anisequel.data.model.StudioCredit
import com.anisequel.data.model.airingEpisodeNumber
import com.anisequel.data.model.compactCount
import com.anisequel.data.model.countryLabel
import com.anisequel.data.model.descriptionBody
import com.anisequel.data.model.hasNotAired
import com.anisequel.data.model.hasProgressCard
import com.anisequel.data.model.isManga
import com.anisequel.data.model.progressCountLabel
import com.anisequel.data.model.releaseLabel
import com.anisequel.data.model.seasonLabel
import com.anisequel.data.model.sourceLabel
import com.anisequel.data.model.studioCredits
import com.anisequel.data.model.watchFraction
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.cards.toCoverColorOrNull
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.markdown.AniListMarkdownParser
import com.anisequel.ui.components.markdown.AniListMarkdownWithCovers
import com.anisequel.ui.components.markdown.MARKDOWN_COLLAPSED_BLOCK_LIMIT
import com.anisequel.ui.theme.AniSequelTheme

/**
 * The container every section of the Info page sits in.
 *
 * One shell rather than a shape per card: a page of a dozen cards is only readable
 * if they share a corner radius, an inset and a surface tone, and the alternative -
 * each section choosing its own - is how the old Specs tab ended up with a trailer
 * banner, two bordered boxes and a bare grid stacked on top of each other.
 */
@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        // 0.55 was see-through enough that a retained cover tint showed *through*
        // the card and fought the body text. The card is the reading surface; the
        // page's backdrop is what should carry the colour.
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        // A page of a dozen cards is exactly what heading
                        // navigation is for: without this, every card title is
                        // one more stop in a linear read rather than a landmark.
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() }
                    )
                    trailing?.invoke()
                }
            }
            content()
        }
    }
}

/**
 * The viewer's progress through this title, as a wave.
 *
 * Renders nothing at all when there is no progress to show - not on the list, or a
 * total AniList does not know - rather than a 0% bar. An empty bar is a claim that
 * the viewer is at zero, and for a title they are *not* tracking that is a claim
 * about a thing that does not exist.
 *
 * The page asks [MediaNode.hasProgressCard] before adding this as a list item,
 * because an item that draws nothing still costs a gap - see the note on
 * [com.anisequel.data.model.hasRankings].
 */
@Composable
fun InfoProgressCard(
    media: MediaNode,
    modifier: Modifier = Modifier
) {
    val fraction = media.watchFraction() ?: return
    val count = media.progressCountLabel ?: return
    val isManga = media.isManga
    val nextEpisode = media.airingEpisodeNumber
    val countdown = rememberAiringCountdown(media)

    InfoCard(
        modifier = modifier.testTag("info_progress_card"),
        title = if (isManga) "Reading Progress" else "Watching Progress",
        icon = AppVectorIcons.Trending,
        trailing = {
            Text(
                text = count,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    ) {
        WavyProgressBar(progress = fraction, entranceKey = media.id)

        // A tracked title at zero is a real state AniList records the moment a show
        // is added to Planning, and it is the one state this card has no words for:
        // the wave is at its resting height, the fill is a single dot, and the
        // header already says "0 / 12". Without a caption the reader is left to
        // decide whether the bar is empty or broken.
        if (fraction <= 0f) {
            Text(
                text = if (isManga) "Not read yet" else "Not started yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (nextEpisode != null && countdown != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = AppVectorIcons.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    // Two bugs in one line, both of which the reader saw.
                    //
                    // The noun and verb were the anime half's: `isManga` was
                    // already read above and used for the card's title, so a manga
                    // drew a *Reading Progress* card over a sentence announcing
                    // that its next episode would air.
                    //
                    // And the line read "airs in in 3d", because
                    // [com.anisequel.data.model.AiringCountdown] already returns
                    // the preposition - "in 3d", "in 5h", "in 30m", "now" - and
                    // this string added a second one. The `now` case printed
                    // "airs in now", which is how it survived review: it only
                    // looked wrong for the last minute.
                    text = if (isManga) {
                        "Chapter $nextEpisode publishes $countdown"
                    } else {
                        "Episode $nextEpisode airs $countdown"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * The synopsis, rendered through the app's AniList markdown pipeline.
 *
 * AniList's `description` is markdown-with-HTML-glue: `(Source: Aniplex)` lines,
 * `<br>` runs, `<i>` notes and occasionally a linked image. Stripping it to plain
 * text - which is what this card replaced - throws away the paragraph structure the
 * author wrote and prints the raw `(Source: ...)` line as body copy.
 */
@Composable
fun InfoSynopsisCard(
    media: MediaNode,
    isDetailLoading: Boolean,
    modifier: Modifier = Modifier
) {
    // `descriptionBody`, not `description`: the markdown renderer reads a bare
    // `(Source: Wikipedia)` line as a paragraph and prints it as part of the story.
    // Stripping it here is what stops the credit line being the last thing on the
    // card - see `MediaNode.descriptionBody`.
    val raw = media.descriptionBody
    val nodes = remember(raw) {
        raw?.takeIf { it.isNotBlank() }?.let { AniListMarkdownParser.parse(it) } ?: emptyList()
    }

    // `rememberSaveable`, not `remember`, and that is the whole point: this card is a
    // `LazyColumn` item, so scrolling past it disposes it. A plain `remember` meant
    // that expanding a long synopsis, reading on and coming back up reopened it
    // collapsed - the card silently undid the one control it has. The `LazyColumn`
    // keeps a disposed item's saved state under its key, so the choice survives the
    // round trip the same way the entrance animation's "already played" flag does.
    var isExpanded by rememberSaveable(media.id, media.type) { mutableStateOf(false) }

    // Whether the *collapsed* render cut the text, reported by layout.
    //
    // Sticky rather than mirrored: once the reader expands, every paragraph is
    // unbounded and `hasVisualOverflow` is false - assigning instead of OR-ing
    // would make "Show less" disappear the moment it was pressed, which is exactly
    // the trap this comment exists to stop the next reader from falling into.
    //
    // `rememberSaveable` for the same reason `isExpanded` is: the two have to
    // survive together. A plain `remember` here meant that expanding a synopsis,
    // scrolling past it and coming back up restored `isExpanded = true` beside
    // `clampedByLines = false` - the expanded text with no "Show less" under it,
    // a card that could only be collapsed by leaving the sheet.
    var clampedByLines by rememberSaveable(media.id, media.type) { mutableStateOf(false) }

    InfoCard(
        modifier = modifier
            // The synopsis is the one card on this page whose *height* changes in
            // front of the reader, and it used to change in one frame: the text
            // jumped, the page jumped with it, and the button the reader just pressed
            // moved out from under their finger. Springing the size instead keeps the
            // control under the finger and reads as the card opening. A size-animating
            // modifier takes an `IntSize` spec, so this is the `FastSpatialSize` token
            // - the same physics `AnimatedVisibility`'s expand/shrink uses.
            .animateContentSize(animationSpec = ExpressiveMotion.FastSpatialSize)
            .testTag("info_synopsis_card"),
        title = "Synopsis",
        icon = AppVectorIcons.SourceBook
    ) {
        when {
            nodes.isEmpty() && isDetailLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                ExpressiveLoadingIndicator(modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Loading synopsis from AniList…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            nodes.isEmpty() -> Text(
                text = "AniList has no synopsis for this title.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            else -> {
                AniListMarkdownWithCovers(
                    nodes = nodes,
                    maxLines = if (isExpanded) Int.MAX_VALUE else COLLAPSED_SYNOPSIS_LINES,
                    onOverflowChange = { overflowed -> if (overflowed) clampedByLines = true }
                )

                // The toggle is gated on the same two things the view clamps on, and
                // the view clamps two different ways: `AniListMarkdownView` drops
                // whole blocks past MARKDOWN_COLLAPSED_BLOCK_LIMIT, *and* clamps each
                // remaining paragraph to `maxLines`.
                //
                // The block case is countable here. The line case is not - it was
                // guessed from a character count, which was wrong in both
                // directions: at a 200% font scale 200 characters overflow five
                // lines and the guess said they did not (text the reader could not
                // reach), while on a tablet 300 characters fit and the guess offered
                // a "Read more" that revealed nothing. `clampedByLines` is the layout
                // answering directly, via `RenderParagraph`'s `hasVisualOverflow`.
                val isClamped = nodes.size > MARKDOWN_COLLAPSED_BLOCK_LIMIT || clampedByLines

                if (isClamped) {
                    TextButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.testTag("info_synopsis_toggle")
                    ) {
                        Text(
                            text = if (isExpanded) "Show less" else "Read more",
                            // Drawn in the accent, and at the same weight as the
                            // card's own title rather than as a button label. This
                            // is the one control on the card, so it reads as chrome
                            // at rest: a full `TextButton` slot of tonal padding in
                            // the middle of a paragraph looked like a button the
                            // synopsis needed.
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Text(
                    text = "Source: AniList",
                    style = MaterialTheme.typography.labelSmall,
                    // Was 0.6f, which put this line below the contrast floor on
                    // the card's translucent surface - a credit nobody can read is
                    // the same as no credit. `onSurfaceVariant` is already the
                    // muted role; dimming it further was the mistake.
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("info_synopsis_source")
                )
            }
        }
    }
}

/**
 * The production facts: who made it, and the numbers a reader came for.
 *
 * A two-column grid rather than a row of four. AniList's own data does not fit four
 * columns on a 360dp phone - "WINTER 2012" and "187472" both need the full quarter
 * width and neither fits it - and the cramped grid was the reason the previous Specs
 * tab wrapped half its values onto a second line at random.
 *
 * The `FlowRow` for the studio credits needs the same layout opt-in, and it is
 * annotated here rather than file-wide so that a function which stops using an
 * experimental layout API stops carrying the promise that it does.
 *
 * ## Why this card draws the main credits and nothing else
 *
 * AniList answers `studios` with the publishers, licensors and music labels
 * alongside the studio that made the thing, and the card used to draw every one of
 * them - which was correct until the page grew a Producers row
 * (`InfoMetaRowsSection`), whose whole content *is* that second half. Drawing both
 * printed "Aniplex" twice on one scroll, a dozen lines apart, under two headings
 * that looked like two different facts. The split is now by `isMain`: the studio
 * here, the rest there, and a payload with no `isMain` information at all is
 * marked main by `studioCredits` so this card still shows what it always did while
 * the Producers row stays absent.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InfoSpecsCard(
    media: MediaNode,
    isDetailLoading: Boolean,
    /**
     * Opens the page for one of the credited studios.
     *
     * Every credit this card draws is openable - AniList gave the card a comma-
     * joined string once, which hid the credits it did not name and made the ones
     * it did name un-tappable. Only the main credits are drawn here now; the
     * publishers and licensors live on the Producers row, which is openable the
     * same way (see the note on this card).
     */
    onOpenStudio: (Int, String?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val studios = media.studioCredits
    // The split described above: main credits here, the rest have their own row.
    // `filter`, not `firstOrNull()`: several studios routinely share the main flag.
    val mainStudios = studios.filter { it.isMain }

    // The next episode, for a show the reader has *not* added to their list. The
    // progress card states it for a title they have started; a currently airing
    // show they have not started said nothing at all about when more of it
    // arrives, which is one of the two reasons to open the page of a running show.
    //
    // Gated on `hasProgressCard` rather than always drawn: that card is the one a
    // tracker reads, and two countdowns a scroll apart would tick out of step and
    // disagree by up to a minute.
    val untrackedAiring = if (media.hasProgressCard) null else rememberAiringCountdown(media)

    val facts = buildList {
        if (media.isManga) {
            // A zero from AniList means "not known yet", not "none": an ongoing
            // manga reports 0 chapters until a count is published, and the card
            // used to print "Chapters 0" as though the series were empty. The
            // volume row is omitted rather than shown as 0 for the same reason -
            // "Volumes 0" beside "Chapters TBA" read as a contradiction.
            add("Chapters" to (media.chapters?.takeIf { it > 0 }?.toString() ?: "TBA"))
            media.volumes?.takeIf { it > 0 }?.let { add("Volumes" to it.toString()) }
        } else {
            add("Episodes" to (media.episodes?.takeIf { it > 0 }?.toString() ?: "TBA"))
            // AniList's `duration` is the length of *one episode*, not of the
            // series. The row was labelled "Duration", which read as the whole
            // runtime - a twelve-episode show said "24m" as though it were
            // twenty-four minutes long. Manga carry no episode length, so the
            // row only exists on the anime side of this branch.
            media.duration?.takeIf { it > 0 }?.let { minutes ->
                add("Episode length" to if (minutes == 1) "1 min" else "${minutes}m")
            }
        }
        // The values are printed as their own helpers spell them. This grid used
        // to uppercase some of them ("WINTER 2019", "FINISHED") and not others
        // ("TV", "Original"), so one card mixed two typographic conventions -
        // and `InfoFact` already uppercases the *label*, which is where the
        // distinction belongs.
        media.seasonLabel?.let { add("Season" to it) }
        // The detail query has always carried `countryOfOrigin`; the grid never
        // read it. A raw "JP" is a database leaking, so the model spells it.
        media.countryLabel?.let { add("Country" to it) }
        // Score, status and format are deliberately absent: the hero's chips
        // already state all three, and printing them again eight lines down made
        // the page look like it had two sources that disagreed about the title.
        //
        // Only anime carry `nextAiringEpisode`, so this is "Next episode" and not
        // a manga branch that could never run.
        untrackedAiring?.let { countdown ->
            add(
                "Next episode" to
                    listOfNotNull(media.airingEpisodeNumber?.toString(), countdown)
                        .joinToString(" · ")
            )
        }
        media.sourceLabel?.let { add("Source" to it) }
        media.meanScore?.let { add("Mean score" to "$it%") }
        media.popularity?.takeIf { it > 0 }?.let {
            // A six-digit popularity was printed in full and wrapped the cell onto
            // a second line; "187.5K" is the same fact in the space it has.
            add("Popularity" to compactCount(it))
        }
        media.releaseLabel.takeIf { it != "TBA" }?.let {
            // "Started Apr 5, 2027" is a tense the date does not have. AniList
            // reports a start date for unreleased titles too.
            add((if (media.hasNotAired) "Starts" else "Started") to it)
        }
        // When it finished, which the card could not say at all: the start date
        // was the only date in the response the grid read. The label follows the
        // status rather than the date, because "Ends" on a finished show is as
        // wrong as "Finished" on one still airing.
        media.endDate?.takeIf { it.year != null }?.let { end ->
            val label = when {
                media.status.equals("FINISHED", ignoreCase = true) -> "Finished"
                media.status.equals("RELEASING", ignoreCase = true) -> "Ends"
                else -> "Ended"
            }
            add(label to end.formatted)
        }
    }

    InfoCard(
        modifier = modifier.testTag("info_specs_card"),
        title = "Details",
        icon = AppVectorIcons.AnimeSparkle
    ) {
        // The studio is the one fact the list query never carries, so it is the one
        // that can be known-absent rather than not-yet-arrived - and showing
        // "Loading production details…" *underneath* a grid of real facts read as
        // though the whole card was still filling in.
        if (studios.isEmpty() && isDetailLoading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExpressiveLoadingIndicator(modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Loading production details…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (mainStudios.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Studio",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("info_studio_row")
                ) {
                    mainStudios.forEach { credit ->
                        StudioCreditChip(credit = credit, onOpenStudio = onOpenStudio)
                    }
                }
            }
        }

        // `Arrangement.spacedBy` on the grid, not a `Row` per pair: each pair was its
        // own row with no space above it, so a two-column grid of facts rendered as
        // four columns of text with the pairs visually indistinguishable from each
        // other - and a single label ("Episodes") sitting above a single value
        // ("24") looked like a broken label/value pair rather than one of four.
        //
        // The pairs are also `Arrangement.Top`, so a fact whose value wraps to two
        // lines does not drag its neighbour's value up to meet it.
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            facts.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    pair.forEach { (label, value) ->
                        InfoFact(label = label, value = value, modifier = Modifier.weight(1f))
                    }
                    if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * A label with its value under it - the unit both detail grids are built from.
 *
 * The label is **uppercased and tracked out**. This is the one place on the page
 * where a reader is scanning for a specific number across a two-column grid, and
 * every label was set in the same weight and size as the values above and below
 * them - so a grid of "Episodes / 24 / Duration / 24m / Season / Winter 2012"
 * read as one undifferentiated block. A label that is visibly a *label* is what
 * makes a number findable: the eye goes to the small tracked-out word, and the
 * answer is the line under it.
 *
 * `onSurfaceVariant` at a reduced alpha rather than at full strength: the values
 * are the content and the labels are the scaffolding, and at equal weight the
 * scaffolding was competing.
 */
@Composable
private fun InfoFact(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            // Letter spacing is what makes a run of small capitals legible as a
            // unit; at this size with the default spacing they run together.
            letterSpacing = 0.6.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * The watched entry a recommendation hangs off.
 *
 * Only a franchise recommendation has a parent - an arriving or activity row is a
 * show the viewer already tracks - and this is the card that explains why the sheet
 * is open at all, so it keeps the parent's own artwork rather than a bare title.
 */
@Composable
fun InfoParentCard(
    sequel: MissedSequel,
    modifier: Modifier = Modifier
) {
    val statusColors = AniSequelTheme.statusColors

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = AppVectorIcons.CheckCircle,
                contentDescription = null,
                tint = statusColors.success,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))

            if (sequel.parentCoverUrl != null) {
                AsyncImage(
                    model = sequel.parentCoverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        // A 48dp square cropped a 2:3 poster to its middle, so the
                        // parent's cover lost the faces at the top and the title at
                        // the bottom. The card keeps the app's cover shape and the
                        // row centres it against the two lines of text.
                        .width(48.dp)
                        .aspectRatio(2f / 3f)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(
                            sequel.parentCoverColor.toCoverColorOrNull()
                                ?: MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${sequel.relationLabel} to watched entry",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = sequel.parentTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * One credited studio, as a capsule that opens its page.
 *
 * Two weights rather than two rows: the studios AniList flags `isMain` are the ones
 * that made the title and are drawn filled, and the publishers, licensors and
 * producers in the same connection are drawn quieter. Splitting them into sections
 * would need names for the halves - AniList does not say which is which - and the
 * page would be inventing a taxonomy to avoid a colour.
 *
 * A credit with no id is drawn as a plain capsule rather than removed: the name is
 * still part of the answer to "who made this", it just cannot be followed.
 */
@Composable
private fun StudioCreditChip(
    credit: StudioCredit,
    onOpenStudio: (Int, String?) -> Unit
) {
    val openable = credit.id != null
    val container = if (credit.isMain) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = if (credit.isMain) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = ExpressiveShapes.pill,
        color = container,
        modifier = Modifier
            // The `clickable` was applied unconditionally with `enabled =
            // openable`, which still publishes button semantics - so a credit
            // AniList gave no id for announced itself as a *disabled button*. The
            // comment above promises a plain capsule for that case, and this is
            // what makes it one.
            .then(
                if (openable) {
                    Modifier
                        .bouncyPress(pressedScale = 0.94f)
                        .clickable(role = Role.Button) {
                            credit.id?.let { id -> onOpenStudio(id, credit.name) }
                        }
                } else {
                    Modifier
                }
            )
            .testTag("info_studio_${credit.name}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = AppVectorIcons.Studio,
                contentDescription = null,
                tint = content.copy(alpha = 0.8f),
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = credit.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (credit.isMain) FontWeight.Bold else FontWeight.Medium,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** How many lines of synopsis show before "Read more". */
private const val COLLAPSED_SYNOPSIS_LINES = 5

/**
 * The page's left and right inset, shared by the cards and by the three sections
 * that bleed to the edge.
 *
 * It lives here, beside the card shell, rather than in the page: the three artwork
 * rows draw their own heading inset and their own `LazyRow` padding because they
 * are *not* cards, and that was a second literal in each file. The two drifted
 * without anything noticing, because the only way to see the drift is to hold the
 * two numbers side by side.
 */
internal val PAGE_INSET = 20.dp
