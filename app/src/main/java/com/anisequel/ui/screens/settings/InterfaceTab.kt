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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.data.repository.InterfacePreferences
import com.anisequel.data.repository.ThemeMode
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.domain.card.MediaCardStyle
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.cards.MediaPosterCell
import com.anisequel.ui.components.expressive.ExpressiveStateChip
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
    modifier: Modifier = Modifier
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
            CardStylePreview(style = style)

            Spacer(modifier = Modifier.height(14.dp))
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
 * Three sample cards drawn by [MediaPosterCell] - the same composable the Library
 * grid and the dashboard's rows use, with the same style value.
 *
 * ## Why it shares the renderer rather than drawing its own idea of the style
 *
 * A preview written separately is an advert. It can be tuned to look good, it can be
 * left behind when the real card changes, and nothing fails when it does, because
 * nobody taps a preview. This one cannot drift: it calls the cell with a made-up
 * title and score, and the cell decides where the title and the rating go from the
 * style - so a style that is wrong in the preview is wrong in the grid, visibly,
 * while the reader is picking it.
 *
 * ## Why the artwork is a flat tint
 *
 * No cover URL. The preview's job is to show where the name and the number land, and
 * a real cover would take a request per style change, arrive late, and be the loudest
 * thing on a card whose artwork is not what is being judged. The tint stands in for
 * art, and the cell's own placeholder path is what draws it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardStylePreview(style: MediaCardStyle, modifier: Modifier = Modifier) {
    val samples = listOf(
        "Dandadan" to 84,
        "Frieren: Beyond Journey's End S2" to 91,
        "Kaiju No. 8" to 76
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Preview",
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
            samples.forEachIndexed { index, (title, score) ->
                MediaPosterCell(
                    coverUrl = null,
                    coverColor = previewTint(index),
                    title = title,
                    score = score,
                    style = style,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * The three placeholder covers, taken from the scheme so a preview on a light theme
 * is not three dark slabs and vice versa.
 */
@Composable
private fun previewTint(index: Int): Color = when (index) {
    0 -> MaterialTheme.colorScheme.primaryContainer
    1 -> MaterialTheme.colorScheme.tertiaryContainer
    else -> MaterialTheme.colorScheme.secondaryContainer
}
