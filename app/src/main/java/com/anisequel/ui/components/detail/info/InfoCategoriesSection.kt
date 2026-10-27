package com.anisequel.ui.components.detail.info

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MediaTag
import com.anisequel.data.model.genreList
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.bouncyPress

/**
 * The reference's one "Categories" block: genres, weighted tags, and the tags
 * AniList hides behind a spoiler warning.
 *
 * ## Why the two old cards became one
 *
 * Genres and tags used to be two `InfoChipCard`s with an icon each, so a title with
 * four genres and twenty tags spent two card shells and two headers on what the
 * reference presents as two labelled groups under one heading. Merging them also
 * puts the two facts a reader actually needs next to each other: a genre is what
 * the show *is*, a tag percentage is how much of the audience agrees - "Kaiju 93%"
 * only makes sense beside "Genres".
 *
 * ## Why there is an Expand control at all
 *
 * AniList's tag list is long and mostly noise for a decision - twenty chips is a
 * third of a phone screen before the synopsis. The collapsed view shows the
 * strongest [TAGS_COLLAPSED] by rank and says how many are left, which is the
 * reference's "+14 more"; expanding is remembered per title because a reader who
 * opened it once is going to want it again.
 *
 * ## The spoiler tags are not simply filtered out
 *
 * The obvious version of this section filters them away, which is right for a row
 * that is always on screen - but it also made the *count* on the page wrong: the
 * card said "Tags 20" over fourteen chips. They are a real part of the category
 * list and AniList has already marked them, so they get their own collapsible
 * strip: a spoiler pill, a count, and nothing revealed until the reader asks.
 * Nothing is hidden from someone who wants it, and nothing is spoiled by accident.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InfoCategoriesSection(
    media: MediaNode,
    modifier: Modifier = Modifier
) {
    val genres = media.genreList
    val tags = remember(media) {
        media.tags.orEmpty()
            .filter { !it.name.isNullOrBlank() }
            .sortedByDescending { it.rank ?: 0 }
    }
    val visible = remember(tags) { tags.filter { it.isMediaSpoiler != true } }
    val spoilers = remember(tags) { tags.filter { it.isMediaSpoiler == true } }

    if (genres.isEmpty() && visible.isEmpty() && spoilers.isEmpty()) return

    // `rememberSaveable` keyed on the title: this section is a `LazyColumn` item,
    // so scrolling past it disposes it, and a plain `remember` would reopen the
    // list collapsed on the way back up. The **type** is part of the key as well
    // as the id - a relation can cross from `anime/` to `manga/`, and the two
    // namespaces number independently.
    var expanded by rememberSaveable(media.id, media.type) { mutableStateOf(false) }
    var spoilersShown by rememberSaveable(media.id, media.type) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("info_categories_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        InfoAccentHeading(title = "Categories")

        if (genres.isNotEmpty()) {
            GroupLabel("Genres")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                genres.forEach { genre -> CategoryChip(text = genre) }
            }
        }

        if (visible.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GroupLabel("Tags")
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = tags.size.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                if (visible.size > TAGS_COLLAPSED) {
                    ExpandToggle(
                        expanded = expanded,
                        onToggle = { expanded = !expanded },
                        collapsedLabel = "Expand",
                        expandedLabel = "Show less",
                        testTag = "info_tags_expand"
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val shown = if (expanded) visible else visible.take(TAGS_COLLAPSED)
                shown.forEach { tag -> CategoryChip(text = tag.tagLabel(), highlighted = true) }
                val hidden = visible.size - shown.size
                if (hidden > 0) {
                    CategoryChip(
                        text = "+$hidden more",
                        onClick = { expanded = true },
                        testTag = "info_tags_more"
                    )
                }
            }
        }

        if (spoilers.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { spoilersShown = !spoilersShown }
                    .testTag("info_spoiler_tags_toggle")
            ) {
                Row(
                    // A full 48dp target: this row is the only way to the tags and
                    // it is next to nothing else, so a half-height row would be a
                    // control that looks bigger than it is tappable.
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = "Spoiler",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (spoilersShown) {
                            "${spoilers.size} tags shown"
                        } else {
                            "${spoilers.size} tags, tap to view"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (spoilersShown) {
                            AppVectorIcons.ExpandLess
                        } else {
                            AppVectorIcons.ExpandMore
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (spoilersShown) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    spoilers.forEach { tag ->
                        CategoryChip(
                            text = tag.tagLabel(),
                            highlighted = true,
                            spoiler = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * One category chip.
 *
 * Not clickable unless [onClick] is given: a genre is a fact, and a chip that looks
 * tappable but does nothing is worse than a static label. The "+N more" chip is the
 * one that acts.
 */
@Composable
private fun CategoryChip(
    text: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    spoiler: Boolean = false,
    onClick: (() -> Unit)? = null,
    testTag: String? = null
) {
    val border = when {
        spoiler -> MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
        highlighted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    }
    val contentColor = when {
        spoiler -> MaterialTheme.colorScheme.error
        highlighted -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val clickable = if (onClick != null) {
        Modifier
            .bouncyPress(pressedScale = 0.96f)
            .clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }

    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, border),
        modifier = modifier
            .then(clickable)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun ExpandToggle(
    expanded: Boolean,
    onToggle: () -> Unit,
    collapsedLabel: String,
    expandedLabel: String,
    testTag: String
) {
    Row(
        modifier = Modifier
            .bouncyPress(pressedScale = 0.94f)
            .clickable(role = Role.Button, onClick = onToggle)
            .heightIn(min = 48.dp)
            .padding(horizontal = 4.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (expanded) expandedLabel else collapsedLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = if (expanded) {
                AppVectorIcons.ExpandLess
            } else {
                AppVectorIcons.ExpandMore
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** Tags shown before the list is expanded. */
private const val TAGS_COLLAPSED = 6

/**
 * "Kaiju 93%", or the bare name when AniList sent no rank.
 *
 * The percentage is the share of viewers who applied the tag, not a score - which
 * is why it reads as a share rather than out of ten.
 */
private fun MediaTag.tagLabel(): String =
    listOfNotNull(name, rank?.let { "$it%" }).joinToString(" ")
