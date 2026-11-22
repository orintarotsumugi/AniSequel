package com.anisequel.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.repository.InterfacePreferences
import com.anisequel.data.repository.MainTabPreferences
import com.anisequel.data.repository.QuickAddPreferences
import com.anisequel.data.repository.ThemeMode
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.navigation.MainTab
import com.anisequel.ui.navigation.enabledMainTabs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine

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
 * ## Why this page is a shelf of sub-screens, with one header at a time
 *
 * The page grew into six full cards in one scroll, so every part got a row and a
 * sub-page. Then the sub-pages themselves nested - Interface > Card design > Card
 * shape - and each level drew its own header under the one above: three rows of
 * title and subtitle, a third of the screen, before the first control. The fix is
 * not cosmetic: **the screen that is open draws the header, and the screens under it
 * are gone**. Card design's three parts now sit beside "Card design" in this page's
 * own stack, and `SettingsScreen` lets this composable draw the page header too, so
 * "Interface" is a level of this stack rather than a frame drawn around it.
 *
 * Back still walks the stack one level at a time: a card part returns to the Card
 * design shelf, a shelf leaves for the Interface hub, and the hub returns to
 * Settings. Every handler is registered in the screen that owns the level, so the
 * most recently added enabled callback wins - see `SettingsScreen`.
 *
 * ## Why the sub-pages are not a second navigation graph
 *
 * They are not destinations a link or a back stack should reach on their own: the
 * shelf is the only entrance, and the `BackHandler` chain is the whole navigation
 * contract. Making them routes would add a second way to arrive at a screen whose
 * back arrow promises the shelf.
 */
@Composable
internal fun InterfaceTab(
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
    /**
     * The header this page shows on its own shelf.
     *
     * Supplied by `SettingsScreen` from the same `SettingsPage` enum the shelf row
     * read, so the row and the page it opens still cannot describe different
     * things - they are just drawn by the same composable now instead of one
     * around the other.
     */
    pageTitle: String,
    pageSubtitle: String,
    /** Leaves the Interface page for the Settings shelf. */
    onClosePage: () -> Unit,
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
    previewEntries: List<MediaListEntryItem> = emptyList(),
    /**
     * Which pages the bar offers, for the two previews that draw it.
     *
     * The previews used to draw a fixed bar - Home, Library, Tiers, Profile - so a
     * reader who had just turned a tab off met it again inside the drawing of their
     * own bar. Collected here rather than in each sub-page because the bar preview and
     * the card-design mock-up are two views of one answer.
     */
    mainTabPreferences: MainTabPreferences,
    /**
     * A part a Settings search result asked for.
     *
     * Deliberately one-shot: the page reports it consumed so reopening Interface
     * from the normal shelf row does not jump back to the part that was searched
     * for last time. Null on every ordinary entrance.
     */
    initialSection: InterfaceSection? = null,
    onInitialSectionConsumed: () -> Unit = {}
) {
    // The bar previews and the card-design mock-up are two views of one answer, so
    // they are read once for the page rather than once per part.
    val barTabs by produceState(
        initialValue = enabledMainTabs(
            tierListTab = MainTabPreferences.DEFAULT_TIER_LIST_TAB,
            searchTab = MainTabPreferences.DEFAULT_SEARCH_TAB,
            searchAnimeEnabled = MainTabPreferences.DEFAULT_SEARCH_ANIME,
            searchMangaEnabled = MainTabPreferences.DEFAULT_SEARCH_MANGA
        ),
        mainTabPreferences
    ) {
        combine(
            mainTabPreferences.tierListTab,
            mainTabPreferences.searchTab,
            mainTabPreferences.searchAnimeEnabled,
            mainTabPreferences.searchMangaEnabled
        ) { tierList, search, searchAnime, searchManga ->
            enabledMainTabs(tierList, search, searchAnime, searchManga)
        }.collect { value = it }
    }
    // Collected once for the page rather than once per sub-page: the motion sample
    // and the shelf are two views of the same settings object, and a second reader
    // would only be a second chance to disagree.
    val themeSettings by themePreferences.settings.collectAsStateWithLifecycle(
        initialValue = ThemePreferences.ThemeSettings(
            themeMode = ThemeMode.DEFAULT,
            useDynamicColor = true
        )
    )
    val scope = rememberCoroutineScope()

    SettingsShelf<InterfaceSection>(
        hubTitle = pageTitle,
        hubSubtitle = pageSubtitle,
        initialSection = initialSection,
        onInitialSectionConsumed = onInitialSectionConsumed,
        onClosePage = onClosePage,
        contentTag = "interface_tab_content",
        modifier = modifier,
        hub = { onOpen ->
            InterfaceHub(
                interfacePreferences = interfacePreferences,
                previewEntries = previewEntries,
                barTabs = barTabs,
                onOpen = onOpen
            )
        },
        body = { section ->
            InterfaceSectionBody(
                section = section,
                themePreferences = themePreferences,
                themeSettings = themeSettings,
                interfacePreferences = interfacePreferences,
                quickAddPreferences = quickAddPreferences,
                scope = scope,
                previewEntries = previewEntries,
                barTabs = barTabs
            )
        }
    )
}

/**
 * The parts of the Interface page, in the order the shelf draws them.
 *
 * An enum rather than lambdas for the reason `SettingsPage` is one: the row's
 * title and the part's header come from the same two strings, so a row cannot
 * promise a different screen than the one it opens.
 *
 * ## Why the card's three parts are here, and not under a "Card design" row
 *
 * They used to sit one level below a "Card design" shelf, which made this the
 * only four-level stack in Settings: Interface -> Card design -> Card shape. That
 * intermediate level existed only to group three rows, so reaching the card's
 * frame cost three taps and put a third title above the other two. The preview
 * the level was worth keeping is now on this page's shelf - see [CardDesignGroup]
 * - and the three parts are rows beside the layout choices.
 */
internal enum class InterfaceSection(
    override val title: String,
    override val subtitle: String,
    override val icon: ImageVector,
    override val testTag: String
) : SettingsShelfSection {
    LAYOUT(
        title = "App layout",
        subtitle = "Shelf or classic, the Info page, bar lift and Home rail",
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
        subtitle = "Poster grid, cover wall, big cards, list and more",
        icon = AppVectorIcons.Library,
        testTag = "interface_row_library"
    ),
    CARD_SHAPE(
        title = "Card shape",
        subtitle = "Style and the cover's own proportions",
        icon = AppVectorIcons.ViewGrid,
        testTag = "card_design_row_shape"
    ),
    CARD_TEXT(
        title = "Name and score",
        subtitle = "Type size, weight, scrolling and which score",
        icon = AppVectorIcons.TextBold,
        testTag = "card_design_row_text"
    ),
    CARD_POSTER(
        title = "Poster art",
        subtitle = "AniList or BetterPosters, and what the card adds to it",
        icon = AppVectorIcons.SectionAppearance,
        testTag = "card_design_row_poster"
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
 * [SettingsScrollColumn] already supplies the gutter and the gap.
 *
 * ## Why the card preview stays on this shelf
 *
 * Choosing a card style means looking at a card, so the mock-up is the answer the
 * "Cards" group is asked for - it is drawn once, above the three rows that change
 * it, instead of being repeated at the top of each of them.
 */
@Composable
private fun InterfaceHub(
    interfacePreferences: InterfacePreferences,
    previewEntries: List<MediaListEntryItem>,
    barTabs: List<MainTab>,
    onOpen: (InterfaceSection) -> Unit
) {
    SettingsScrollColumn {
        SettingsShelfGroup(
            label = "Layout",
            sections = listOf(
                InterfaceSection.LAYOUT,
                InterfaceSection.SEQUELS,
                InterfaceSection.LIBRARY
            ),
            tone = SettingsGroupTone.LOOK,
            onOpen = onOpen
        )

        CardDesignGroup(
            interfacePreferences = interfacePreferences,
            previewEntries = previewEntries,
            barTabs = barTabs,
            onOpen = onOpen
        )

        SettingsShelfGroup(
            label = "Behaviour",
            sections = listOf(
                InterfaceSection.MOTION,
                InterfaceSection.GESTURES
            ),
            tone = SettingsGroupTone.LOOK,
            onOpen = onOpen
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

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
    previewEntries: List<MediaListEntryItem>,
    barTabs: List<MainTab>
) {
    when (section) {
        InterfaceSection.LAYOUT -> InterfaceLayoutSection(
            interfacePreferences = interfacePreferences,
            previewEntries = previewEntries,
            barTabs = barTabs
        )
        InterfaceSection.SEQUELS -> InterfaceSequelSection(
            interfacePreferences = interfacePreferences,
            previewEntries = previewEntries
        )
        InterfaceSection.LIBRARY -> InterfaceLibrarySection(
            interfacePreferences = interfacePreferences,
            previewEntries = previewEntries
        )
        InterfaceSection.CARD_SHAPE -> CardShapeSection(
            interfacePreferences = interfacePreferences,
            previewEntries = previewEntries,
            barTabs = barTabs
        )
        InterfaceSection.CARD_TEXT -> CardTextSection(
            interfacePreferences = interfacePreferences,
            previewEntries = previewEntries,
            barTabs = barTabs
        )
        InterfaceSection.CARD_POSTER -> CardPosterSection(
            interfacePreferences = interfacePreferences,
            previewEntries = previewEntries,
            barTabs = barTabs
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
