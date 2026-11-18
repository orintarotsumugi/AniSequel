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
import androidx.compose.runtime.collectAsState
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
    var section by rememberSaveable { mutableStateOf<InterfaceSection?>(null) }

    LaunchedEffect(initialSection) {
        if (initialSection != null) {
            section = initialSection
            onInitialSectionConsumed()
        }
    }
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
    // Settings page's own handler, which is what Back means on the shelf. A card
    // part steps back to the Card design shelf, not all the way out - see
    // [InterfaceSection.parent].
    BackHandler(enabled = section != null) { section = section?.parent() }

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
            Column(modifier = Modifier.fillMaxSize()) {
                // One header for what is actually on screen. The title comes from
                // the same enum the row that opened it read, and the page's own
                // title when the shelf is showing.
                SettingsSubHeader(
                    title = destination?.title ?: pageTitle,
                    subtitle = destination?.subtitle ?: pageSubtitle,
                    onBack = {
                        if (destination == null) onClosePage() else section = destination.parent()
                    },
                    backDescription = when {
                        destination == null -> "Back to settings"
                        // A card part goes back to the Card design shelf, which is
                        // one step out - not two, which is what "Back to Interface"
                        // would promise from there.
                        destination?.parent() != null -> "Back to Card design"
                        else -> "Back to Interface"
                    },
                    backTestTag = if (destination == null) {
                        "settings_sub_back"
                    } else {
                        "interface_sub_back"
                    }
                )
                // The body takes the rest of the screen. Without the weight a
                // `fillMaxSize` body inside the Column measured against the whole
                // page and pushed its own last row under the bar.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (destination == null) {
                        InterfaceHub(onOpen = { section = it })
                    } else {
                        InterfaceSectionBody(
                            section = destination,
                            themePreferences = themePreferences,
                            themeSettings = themeSettings,
                            interfacePreferences = interfacePreferences,
                            quickAddPreferences = quickAddPreferences,
                            scope = scope,
                            previewEntries = previewEntries,
                            barTabs = barTabs,
                            onOpenCardPart = { part -> section = part.section }
                        )
                    }
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
 *
 * The three card-design parts are in this list rather than in a nested enum of their
 * own so the page has one stack and one header. Their titles and subtitles are taken
 * from [CardDesignPart], which the Card design shelf's rows also use - the row and the
 * screen stay one description.
 */
internal enum class InterfaceSection(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val testTag: String
) {
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
    CARDS(
        title = "Card design",
        subtitle = "Shape, name and score, and poster art",
        icon = AppVectorIcons.ViewGrid,
        testTag = "interface_row_cards"
    ),
    CARD_SHAPE(
        title = CardDesignPart.SHAPE.title,
        subtitle = CardDesignPart.SHAPE.subtitle,
        icon = CardDesignPart.SHAPE.icon,
        testTag = CardDesignPart.SHAPE.testTag
    ),
    CARD_TEXT(
        title = CardDesignPart.TEXT.title,
        subtitle = CardDesignPart.TEXT.subtitle,
        icon = CardDesignPart.TEXT.icon,
        testTag = CardDesignPart.TEXT.testTag
    ),
    CARD_POSTER(
        title = CardDesignPart.POSTER.title,
        subtitle = CardDesignPart.POSTER.subtitle,
        icon = CardDesignPart.POSTER.icon,
        testTag = CardDesignPart.POSTER.testTag
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
    );

    /**
     * The screen Back returns to, or null when Back leaves this page.
     *
     * The card parts are one level below the Card design shelf; everything else is
     * one level below the Interface shelf. Spelling it here rather than at the
     * `BackHandler` keeps the two callers - Back and the header's arrow - answering
     * the same way.
     */
    fun parent(): InterfaceSection? = when (this) {
        CARD_SHAPE, CARD_TEXT, CARD_POSTER -> CARDS
        else -> null
    }
}

/** The entry this part has in [InterfaceSection]'s stack. */
private val CardDesignPart.section: InterfaceSection
    get() = when (this) {
        CardDesignPart.SHAPE -> InterfaceSection.CARD_SHAPE
        CardDesignPart.TEXT -> InterfaceSection.CARD_TEXT
        CardDesignPart.POSTER -> InterfaceSection.CARD_POSTER
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
    previewEntries: List<MediaListEntryItem>,
    barTabs: List<MainTab>,
    onOpenCardPart: (CardDesignPart) -> Unit
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
        InterfaceSection.CARDS -> CardDesignShelf(
            interfacePreferences = interfacePreferences,
            previewEntries = previewEntries,
            barTabs = barTabs,
            onOpen = onOpenCardPart
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
