package com.anisequel.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.anisequel.data.repository.InfoBackdropBlur
import com.anisequel.data.repository.InfoBackdropSource
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressivePolygonSegmentedBar
import com.anisequel.ui.components.expressive.SegmentedOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What an opened title's page is drawn on.
 *
 * The page had one backdrop before this: the hero's artwork, which scrolls away
 * with the hero. Below it the sheet was the plain container colour, so a title
 * with a striking key visual became a grey stack of cards halfway down. This
 * section lets the reader keep that artwork under the whole page - the banner or
 * the poster - and choose how much of it is allowed through.
 *
 * Two controls rather than one list of presets: "which picture" and "how soft"
 * are independent questions, and four combined presets would hide that the reader
 * can have a sharp poster if they want one. The blur is dimmed rather than hidden
 * when the backdrop is off, so the section does not change height under a finger.
 */
@Composable
internal fun InfoBackdropSection(
    themePreferences: ThemePreferences,
    settings: ThemePreferences.ThemeSettings,
    scope: CoroutineScope
) {
    val source = settings.infoBackdropSource
    val backdropOn = source != InfoBackdropSource.OFF

    SectionCard(
        title = "Page backdrop",
        icon = AppVectorIcons.SectionBackground,
        subtitle = "What an opened title is drawn on"
    ) {
        SettingsFieldLabel(text = "Backdrop")
        Spacer(modifier = Modifier.height(8.dp))
        ExpressivePolygonSegmentedBar(
            options = InfoBackdropSource.entries.map { SegmentedOption(label = it.displayName) },
            selectedIndex = InfoBackdropSource.entries.indexOf(source),
            onSelect = { index ->
                scope.launch {
                    themePreferences.setInfoBackdropSource(InfoBackdropSource.entries[index])
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("info_backdrop_source_row")
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Banner keeps the wide key visual under the page. Cover uses the " +
                "poster, which is the picture you tapped in the grid. Off returns the " +
                "page to the app's surface.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))
        SettingsFieldLabel(text = "Softness")
        Spacer(modifier = Modifier.height(8.dp))
        ExpressivePolygonSegmentedBar(
            options = InfoBackdropBlur.entries.map { SegmentedOption(label = it.displayName) },
            selectedIndex = InfoBackdropBlur.entries.indexOf(settings.infoBackdropBlur),
            onSelect = { index ->
                if (backdropOn) {
                    scope.launch {
                        themePreferences.setInfoBackdropBlur(InfoBackdropBlur.entries[index])
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (backdropOn) 1f else 0.4f)
                .testTag("info_backdrop_blur_row")
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (backdropOn) {
                "Crisp is the artwork itself; Soft keeps its colour and removes the " +
                    "detail that competes with the cards; Heavy leaves only the palette."
            } else {
                "Turn a backdrop on to choose how soft it is."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
