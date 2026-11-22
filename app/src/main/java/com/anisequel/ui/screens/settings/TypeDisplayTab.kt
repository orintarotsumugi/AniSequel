package com.anisequel.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.anisequel.data.repository.InterfacePreferences
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.ui.components.AppVectorIcons

/**
 * Type and display size, as one page.
 *
 * ## Why these were two pages, and why they are not
 *
 * "Type & reading" and "Display size" sat next to each other on the shelf, and a
 * reader who wanted the app to be bigger had two candidate rows and no way to tell
 * which one did it. The pages knew the problem: the Type page carried a card whose
 * only job was a button to the Display size page. Two names for one intention, and
 * the app's own navigation admitting the ambiguity.
 *
 * They are one page now: a specimen of what the reader is looking at, then one row
 * for the text and one for the scale. Both halves keep their own samples - the text
 * size slider its paragraph, the density section its card-and-gap explanation - so
 * nothing was flattened into a number without a picture.
 *
 * ## Why a shelf rather than one long scroll
 *
 * Seven cards in one scroll is what made the old Type page a wall: the font picker,
 * five saved faces, the size slider, the title-motion sample, the density mode, the
 * dp slider and a paragraph about what a dp is. The two subjects have nothing to do
 * with each other beyond both changing a number that ends in "size", so they get a
 * row each - and the sample on this shelf is what makes the page glanceable.
 */
internal enum class TypeSection(
    override val title: String,
    override val subtitle: String,
    override val icon: ImageVector,
    override val testTag: String
) : SettingsShelfSection {
    TEXT(
        title = "Font & text",
        subtitle = "Typeface, saved fonts, text size and long titles",
        icon = AppVectorIcons.TextBold,
        testTag = "type_row_text"
    ),
    SCALE(
        title = "Display size",
        subtitle = "Automatic, or a density you choose",
        icon = AppVectorIcons.Tune,
        testTag = "type_row_scale"
    )
}

/**
 * Type & display: the two halves of "make it bigger", behind one row.
 *
 * The two bodies are existing pages with their own scroll columns, called from
 * here rather than re-implemented: a second copy of the font picker or the density
 * slider is a second place for the two to disagree about what is stored, and the
 * samples on both are the reason those pages exist at all.
 */
@Composable
internal fun TypeDisplayTab(
    themePreferences: ThemePreferences,
    interfacePreferences: InterfacePreferences,
    pageTitle: String,
    pageSubtitle: String,
    onClosePage: () -> Unit,
    modifier: Modifier = Modifier,
    /** A half a Settings search result asked for; consumed once. */
    initialSection: TypeSection? = null,
    onInitialSectionConsumed: () -> Unit = {}
) {
    SettingsShelf<TypeSection>(
        hubTitle = pageTitle,
        hubSubtitle = pageSubtitle,
        initialSection = initialSection,
        onInitialSectionConsumed = onInitialSectionConsumed,
        onClosePage = onClosePage,
        contentTag = "type_tab_content",
        modifier = modifier,
        hub = { onOpen ->
            SettingsScrollColumn {
                // The hub's own answer, before either half is opened: what the type
                // and the scale currently are, drawn rather than described.
                TypeSpecimenCard(
                    themePreferences = themePreferences,
                    interfacePreferences = interfacePreferences
                )
                SettingsShelfGroup(
                    label = "Type & scale",
                    sections = TypeSection.entries,
                    tone = SettingsGroupTone.LOOK,
                    onOpen = onOpen
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        },
        body = { section ->
            when (section) {
                TypeSection.TEXT -> TypeTextSectionBody(themePreferences = themePreferences)
                TypeSection.SCALE -> DisplaySizeSectionBody(
                    interfacePreferences = interfacePreferences
                )
            }
        }
    )
}
