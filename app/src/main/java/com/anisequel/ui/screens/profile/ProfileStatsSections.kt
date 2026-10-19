package com.anisequel.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.anisequel.ui.components.expressive.ExpressiveStateChip

/**
 * The three groups the Stats tab is read in.
 *
 * ## Why this exists rather than one long scroll
 *
 * The tab stacked ten cards into a single column, and two of them are not like the
 * others: a genre chart runs to twenty-odd rows and a tag chart to sixty, because
 * AniList tags every entry with everything it is. So the answer to "what genres do
 * I actually watch" - the most interesting thing on the tab - sat below three
 * phone screens of year, country and episode bands, and the tag table sat below
 * that. Nothing on the page said either was there.
 *
 * Splitting them is not only tidier: the Overview keeps the distributions a reader
 * scans in one pass (score, status, format, country, years, episodes), and the two
 * long tail charts get a screen each, where their length is the whole point rather
 * than something to scroll past. It is also the arrangement the client this was
 * built from uses, and the counts on the chips say whether a section is worth
 * opening before it costs a tap - "Tags (0)" is an answer.
 *
 * The selection is held by the screen rather than by [ProfileStatsTab], because
 * the profile disposes the tab it switches away from: a section remembered here
 * would reset to Overview every time somebody looked at their Activity and came
 * back, which is exactly the bug the Library page's remembered filter was fixed for.
 */
enum class StatsSection(val label: String) {
    OVERVIEW("Overview"),
    GENRES("Genres"),
    TAGS("Tags")
}

/**
 * The section selector.
 *
 * A scrolling row rather than a `Row`, for the reason the Library's filter chips
 * are one: the three labels carry counts, and "Overview · Genres (24) · Tags (61)"
 * needs about 330dp of a 360dp phone before the user turns their font size up.
 * Laid out edge to edge, the chip that overflowed was simply unreachable - which is
 * the one failure a two-word control must not have, because there is no other way
 * to reach what is behind it.
 */
@Composable
fun StatsSectionRow(
    selected: StatsSection,
    genreCount: Int,
    tagCount: Int,
    onSelect: (StatsSection) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .widthIn(max = ProfileMaxContentWidth)
            .fillMaxWidth()
            .testTag("stats_sections"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        // Mirrors the tab's own list padding, so the first chip lines up with the
        // Summary card beneath it rather than sitting 16dp off it.
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(StatsSection.entries, key = { it.name }) { option ->
            ExpressiveStateChip(
                // The count is only on the two sections that can be empty. "Overview
                // (8)" would be a count of cards, which is a fact about the layout
                // rather than about the person.
                label = when (option) {
                    StatsSection.OVERVIEW -> option.label
                    StatsSection.GENRES -> "${option.label} ($genreCount)"
                    StatsSection.TAGS -> "${option.label} ($tagCount)"
                },
                selected = option == selected,
                onClick = { onSelect(option) },
                // 44dp, not the chip's own ~30dp: this row is the only way into the
                // two sections, and a mis-tap on a phone held in one hand leaves the
                // reader on a screen they believe they asked to leave.
                minTouchTargetHeight = 44.dp,
                modifier = Modifier.testTag("stats_section_${option.name.lowercase()}")
            )
        }
    }
}
