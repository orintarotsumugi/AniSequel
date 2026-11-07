package com.anisequel.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.model.displayName
import com.anisequel.data.model.posterColor
import com.anisequel.data.model.posterUrl
import com.anisequel.data.repository.InterfacePreferences
import com.anisequel.data.repository.ThemeMode
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.domain.card.MediaCardStyle
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.cards.MediaPosterCell
import com.anisequel.ui.components.expressive.ExpressiveStateChip
import com.anisequel.ui.theme.DynamicThemeBuilder
import kotlinx.coroutines.launch

/**
 * How the app is laid out, as distinct from what colour it is.
 *
 * ## Why the card style is here and not on the Appearance page
 *
 * The Appearance page answers "what should this look like" in the sense of palette,
 * and it is already long. A card style is a different decision - it changes the
 * shape of every grid in the app - and choosing it means looking at a card, not at a
 * swatch. Putting the two on one page meant scrolling past five colour controls to
 * reach a layout control, and the reference the readers pointed at splits them the
 * same way: a UI page for the interface, a Theme page for the colour.
 *
 * Motion moved here too, for the same reason: it is about how the app behaves on
 * this screen rather than about what it is painted in.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InterfaceTab(
    themePreferences: ThemePreferences,
    interfacePreferences: InterfacePreferences,
    modifier: Modifier = Modifier,
    /**
     * Up to three of the reader's own entries, for the preview to draw.
     *
     * Passed in rather than fetched here. The dashboard has already downloaded the
     * list this screen is previewing, so asking the repository again would either
     * be a cache hit that still walks the whole collection or - on a cold Settings
     * visit - an entire list download to draw three thumbnails. Empty is a real
     * answer (demo mode, or Settings opened before Home finishes) and falls back
     * to the sample titles the preview has always used.
     */
    previewEntries: List<MediaListEntryItem> = emptyList()
) {
    val scope = rememberCoroutineScope()
    val themeSettings by themePreferences.settings.collectAsState(
        initial = ThemePreferences.ThemeSettings(
            themeMode = ThemeMode.DEFAULT,
            useDynamicColor = false
        )
    )
    val style by interfacePreferences.cardStyle.collectAsState(
        initial = MediaCardStyle.DEFAULT
    )

    SettingsScrollColumn(modifier) {
        SectionCard(
            title = "Card style",
            icon = AppVectorIcons.ViewGrid,
            subtitle = style.blurb
        ) {
            CardStylePreview(style = style, libraryEntries = previewEntries)

            Spacer(modifier = Modifier.height(12.dp))
            SettingsFieldLabel(text = "Style")
            Spacer(modifier = Modifier.height(8.dp))

            // A wrapping row, not a scrolling one: the same reason `MotionSection`
            // gives - a picker that hides two of its five options off the edge reads
            // as those options having been removed, because nothing about the row
            // says it scrolls.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_style_row")
            ) {
                MediaCardStyle.entries.forEach { option ->
                    ExpressiveStateChip(
                        label = option.displayName,
                        selected = option == style,
                        onClick = {
                            if (option != style) {
                                scope.launch { interfacePreferences.setCardStyle(option) }
                            }
                        },
                        // This picker is the primary control of the page, not a
                        // filter beside a grid: a 30dp chip is a fingertip target
                        // found by aiming, and the five options are the whole
                        // decision. Give it the platform's 48dp minimum.
                        minTouchTargetHeight = 48.dp,
                        // One style is active at a time, so this is a radio
                        // choice, not a filter. The chip defaults to Checkbox
                        // because that is what the filter rows elsewhere are.
                        role = Role.RadioButton,
                        onClickLabel = "Use the ${option.displayName} card style",
                        modifier = Modifier.testTag("card_style_${option.storageValue}")
                    )
                }
            }
        }

        MotionSection(
            themePreferences = themePreferences,
            settings = themeSettings,
            scope = scope
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Three cards drawn by [MediaPosterCell] - the same composable, and the same
 * three-column width, the Library grid uses.
 *
 * ## Why the cards come from the reader's library
 *
 * A preview of a card style should show the cards being styled. The old sample was
 * three fixed titles on flat tints, so the one thing the reader was actually
 * choosing - whether *their* posters read well under this layout - was the one thing
 * the preview could not answer. Real titles also expose the layout honestly: a long
 * name ellipsizes where the grid will ellipsize it, and a title with no community
 * score shows the empty-rating card the reader will get.
 *
 * The covers are only drawn from entries the dashboard already holds, so this costs
 * no request of its own; Coil serves them from the cache the Library grid filled.
 * When there is no library yet (demo mode, or Settings opened before Home has
 * answered) the original three sample titles are used, so the page never shows an
 * empty preview.
 *
 * ## Why it shares the renderer rather than drawing its own idea of the style
 *
 * A preview written separately is an advert. It can be tuned to look good, it can be
 * left behind when the real card changes, and nothing fails when it does, because
 * nobody taps a preview. This one cannot drift: it calls the cell, and the cell
 * decides where the title and the rating go from the style - so a style that is
 * wrong in the preview is wrong in the grid, visibly, while the reader is picking
 * it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardStylePreview(
    style: MediaCardStyle,
    libraryEntries: List<MediaListEntryItem>,
    modifier: Modifier = Modifier
) {
    // Parsed once per list, not once per frame: the preview repaints on every
    // style tap and every colour crossfade in the card above it.
    val fromLibrary = remember(libraryEntries) {
        libraryEntries.take(PreviewCardCount).map { entry ->
            CardStyleSample(
                title = entry.media.displayName,
                coverUrl = entry.media.posterUrl,
                coverColor = DynamicThemeBuilder.parseHexColor(entry.media.posterColor),
                // The same threshold the Library grid uses: a zero average is an
                // unrated title, and the grid draws no badge for it.
                score = entry.media.averageScore?.takeIf { it > 0 }
            )
        }
    }
    // The scheme's containers, so the fallback on a light theme is not three dark
    // slabs and vice versa.
    val placeholderTints = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.secondaryContainer
    )
    val placeholderTitles = listOf(
        "Dandadan" to 84,
        "Frieren: Beyond Journey's End S2" to 91,
        "Kaiju No. 8" to 76
    )
    val samples = fromLibrary + placeholderTitles
        .drop(fromLibrary.size)
        .mapIndexed { index, (title, score) ->
            CardStyleSample(
                title = title,
                coverUrl = null,
                coverColor = placeholderTints[(fromLibrary.size + index) % placeholderTints.size],
                score = score
            )
        }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = if (fromLibrary.isNotEmpty()) "Preview \u00b7 from your library" else "Preview",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_style_preview")
        ) {
            samples.forEach { sample ->
                MediaPosterCell(
                    coverUrl = sample.coverUrl,
                    coverColor = sample.coverColor,
                    title = sample.title,
                    // Null draws no badge in any style, which is the honest card
                    // for a title the community has not rated yet.
                    score = sample.score,
                    style = style,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** How many cards the preview draws; the Library grid is three columns wide. */
private const val PreviewCardCount = 3

/** One card the preview can draw, already flattened out of a list entry. */
private data class CardStyleSample(
    val title: String,
    val coverUrl: String?,
    val coverColor: Color?,
    val score: Int?
)
