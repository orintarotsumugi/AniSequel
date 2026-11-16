package com.anisequel.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.repository.InterfacePreferences
import com.anisequel.data.repository.QuickAddPreferences
import com.anisequel.data.repository.ThemeMode
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.ui.components.AppVectorIcons
import kotlinx.coroutines.CoroutineScope

/** The key the [rememberSaveableStateHolder] files the Interface shelf under. */
private const val INTERFACE_HUB_KEY = "interface_hub"

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
 * ## Why this page is a shelf of sub-screens
 *
 * The page had grown into six full cards in one scroll - layout, sequels, library view,
 * card style, motion and gestures - each of which opens with a preview several hundred
 * pixels tall. The same argument that split Settings into pages applies one level down:
 * a reader who came to turn the bottom bar down was scrolling past a motion sample and a
 * quick-add button to reach it, and the page's own name ("Card style, layout and motion")
 * had already drifted from its contents.
 *
 * So every part gets a row with a sentence under it, and a sub-page of its own. The
 * parts did not change: each body in `InterfaceSections.kt` is the card that used to
 * sit in this file, and every one of them still writes the preference it wrote before.
 * Nothing
 * is drawn twice, so there is no page where two controls over one value can disagree.
 *
 * ## Why a sub-page still says "Interface" above it
 *
 * The header above this content is the page the reader came from, and the sub-header
 * below it is the part they are in - a breadcrumb, not two titles for one screen. Back
 * drops the sub-page before it drops the page: this composable's `BackHandler` is
 * registered after the one in `SettingsScreen`, and the dispatcher gives the most
 * recently added enabled callback the event.
 */
@Composable
fun InterfaceTab(
    themePreferences: ThemePreferences,
    interfacePreferences: InterfacePreferences,
    /**
     * The hold/swipe/confirm gestures.
     *
     * On this page rather than in Device Manager: a gesture is part of how the
     * interface behaves, and the preview on its sub-page is the whole reason the
     * settings are worth a page of their own - the reader can try the real
     * control before deciding whether the tick is worth it.
     */
    quickAddPreferences: QuickAddPreferences,
    modifier: Modifier = Modifier,
    /**
     * Up to three of the reader's own entries, for the previews to draw.
     *
     * Passed in rather than fetched here. The dashboard has already downloaded the
     * list this screen is previewing, so asking the repository again would either
     * be a cache hit that still walks the whole collection or - on a cold Settings
     * visit - an entire list download to draw three thumbnails. Empty is a real
     * answer (demo mode, or Settings opened before Home finishes) and falls back
     * to the sample titles the previews have always used.
     */
    previewEntries: List<MediaListEntryItem> = emptyList()
) {
    var section by rememberSaveable { mutableStateOf<InterfaceSection?>(null) }
    // Collected once for the page rather than once per sub-page: the motion sample
    // and the shelf are two views of the same settings object, and a second reader
    // would only be a second chance to disagree.
    val themeSettings by themePreferences.settings.collectAsState(
        initial = ThemePreferences.ThemeSettings(
            themeMode = ThemeMode.DEFAULT,
            useDynamicColor = false
        )
    )
    val scope = rememberCoroutineScope()

    // The shelf and the sub-pages are separate compositions, and `AnimatedContent`
    // disposes the outgoing one - so a sub-page's scroll position would start at the
    // top every time the reader came back to it, and so would the shelf's. The
    // holder keys each destination so both survive the trip, exactly as the Settings
    // pages do one level up.
    val sectionStateHolder = rememberSaveableStateHolder()

    // Only while a sub-page is open: disabled here, the event falls through to the
    // Settings page's own handler, which is what Back means on the shelf.
    BackHandler(enabled = section != null) { section = null }

    AnimatedContent(
        targetState = section,
        // The same push in and drop away the Settings pages use, so a level deeper
        // reads as a level deeper at both depths - see `settingsLevelTransition`.
        transitionSpec = { settingsLevelTransition() },
        label = "interface_section_transition",
        modifier = modifier
            .fillMaxSize()
            .testTag("interface_tab_content")
    ) { destination ->
        sectionStateHolder.SaveableStateProvider(
            key = destination?.name ?: INTERFACE_HUB_KEY
        ) {
            if (destination == null) {
                InterfaceHub(onOpen = { section = it })
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    SettingsSubHeader(
                        title = destination.title,
                        subtitle = destination.subtitle,
                        onBack = { section = null },
                        // Not "Back to settings": this goes one page out, not two,
                        // and the wrong sentence on the one control every sub-page
                        // is left through is worse than no sentence.
                        backDescription = "Back to Interface",
                        backTestTag = "interface_sub_back"
                    )
                    InterfaceSectionBody(
                        section = destination,
                        themePreferences = themePreferences,
                        themeSettings = themeSettings,
                        interfacePreferences = interfacePreferences,
                        quickAddPreferences = quickAddPreferences,
                        scope = scope,
                        previewEntries = previewEntries
                    )
                }
            }
        }
    }
}

/**
 * The parts of the Interface page, in the order the shelf draws them.
 *
 * An enum rather than six lambdas for the reason `SettingsPage` is one: the row's
 * title and the sub-page's header come from the same two strings, so a row cannot
 * promise a different page than the one it opens.
 */
private enum class InterfaceSection(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val testTag: String
) {
    LAYOUT(
        title = "App layout",
        subtitle = "Shelf or classic, the bar's lift and the Home rail",
        icon = AppVectorIcons.Tune,
        testTag = "interface_row_layout"
    ),
    SEQUELS(
        title = "Home sequels",
        subtitle = "Rows, cards or banners for the sequel feed",
        icon = AppVectorIcons.HomeTab,
        testTag = "interface_row_sequels"
    ),
    LIBRARY(
        title = "Library view",
        subtitle = "Poster grid, compact grid or list",
        icon = AppVectorIcons.Library,
        testTag = "interface_row_library"
    ),
    CARDS(
        title = "Card design",
        subtitle = "Style, title size, rating, size and scrolling",
        icon = AppVectorIcons.ViewGrid,
        testTag = "interface_row_cards"
    ),
    MOTION(
        title = "Motion",
        subtitle = "How much the app animates, with a sample",
        icon = AppVectorIcons.SectionMotion,
        testTag = "interface_row_motion"
    ),
    GESTURES(
        title = "Quick add gestures",
        subtitle = "Hold or swipe to add to Currently Watching",
        icon = AppVectorIcons.Play,
        testTag = "interface_row_gestures"
    )
}

/**
 * The shelf itself: what the page is made of, grouped by the question each part
 * answers.
 *
 * The rows reuse [SettingsHubGroup] and [SettingsSectionLabel] rather than a second
 * row design - a reader who has just come through the Settings shelf should not meet
 * a different-looking list one level in. The labels are `compact` because
 * [SettingsScrollColumn] already supplies the gutter and the gap; the shelf keeps the
 * inset label only because its rows are indented from it.
 */
@Composable
private fun InterfaceHub(onOpen: (InterfaceSection) -> Unit) {
    SettingsScrollColumn {
        SettingsSectionLabel(text = "Layout", compact = true)
        SettingsHubGroup(
            entries = listOf(
                InterfaceSection.LAYOUT.entry(onOpen),
                InterfaceSection.SEQUELS.entry(onOpen),
                InterfaceSection.LIBRARY.entry(onOpen),
                InterfaceSection.CARDS.entry(onOpen)
            )
        )

        SettingsSectionLabel(text = "Behaviour", compact = true)
        SettingsHubGroup(
            entries = listOf(
                InterfaceSection.MOTION.entry(onOpen),
                InterfaceSection.GESTURES.entry(onOpen)
            )
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** One shelf row, named and described by the sub-page it opens. */
private fun InterfaceSection.entry(
    onOpen: (InterfaceSection) -> Unit
): SettingsHubEntry = SettingsHubEntry(
    icon = icon,
    title = title,
    subtitle = subtitle,
    testTag = testTag,
    onClick = { onOpen(this) }
)

/**
 * The body of one part.
 *
 * The dispatch lives here rather than in `InterfaceSections.kt` so this file names
 * every part of the page: the two bodies that are other files' cards (motion and the
 * quick-add gestures) would otherwise be reachable only by following an import.
 */
@Composable
private fun InterfaceSectionBody(
    section: InterfaceSection,
    themePreferences: ThemePreferences,
    themeSettings: ThemePreferences.ThemeSettings,
    interfacePreferences: InterfacePreferences,
    quickAddPreferences: QuickAddPreferences,
    scope: CoroutineScope,
    previewEntries: List<MediaListEntryItem>
) {
    when (section) {
        InterfaceSection.LAYOUT -> InterfaceLayoutSection(
            interfacePreferences = interfacePreferences,
            previewEntries = previewEntries
        )
        InterfaceSection.SEQUELS -> InterfaceSequelSection(
            interfacePreferences = interfacePreferences,
            previewEntries = previewEntries
        )
        InterfaceSection.LIBRARY -> InterfaceLibrarySection(
            interfacePreferences = interfacePreferences,
            previewEntries = previewEntries
        )
        InterfaceSection.CARDS -> InterfaceCardDesignSection(
            interfacePreferences = interfacePreferences,
            previewEntries = previewEntries
        )
        InterfaceSection.MOTION -> SettingsScrollColumn {
            MotionSection(
                themePreferences = themePreferences,
                settings = themeSettings,
                scope = scope
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
        InterfaceSection.GESTURES -> SettingsScrollColumn {
            QuickAddSectionCard(quickAddPreferences = quickAddPreferences)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
