package com.anisequel.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisequel.data.repository.ArrivingPreferences
import com.anisequel.data.repository.LibraryPreferences
import com.anisequel.data.repository.MainTabPreferences
import com.anisequel.data.repository.RefreshInterval
import com.anisequel.data.repository.RefreshIntervalPreferences
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveNavigationBar
import com.anisequel.ui.navigation.MainTab
import com.anisequel.ui.navigation.enabledMainTabs
import com.anisequel.ui.navigation.mainTabDestinations
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The one part of the Library & dashboard page that is still a page.
 *
 * ## Why the other two came back out
 *
 * The page held six cards in one scroll, so it was split into three rows:
 * Navigation, Library view and Refresh & arriving. That made the shelf tidy and
 * the *reader's* trip worse - "how often does the list refresh" is one of the most
 * common reasons to open Settings at all, and it became a row, then a page, then a
 * Back. A split earns its extra tap only when the halves are long enough that
 * scrolling past one is the cost; two cards each is not.
 *
 * So Library view and Refresh & arriving are cards on this page again, and only the
 * navigation half - three bar cards behind a preview, and the part a reader arrives
 * at with a question about a missing tab - keeps a page of its own.
 */
internal enum class LibrarySection(
    override val title: String,
    override val subtitle: String,
    override val icon: ImageVector,
    override val testTag: String
) : SettingsShelfSection {
    NAVIGATION(
        title = "Navigation",
        subtitle = "The two bars, their shortcuts and the Search page",
        icon = AppVectorIcons.Tune,
        testTag = "library_row_navigation"
    )
}

/**
 * How the saved list is read, how the dashboard presents it, and which pages are
 * reachable from the top and bottom bars.
 *
 * These cards used to share a tab with the AniList Client ID, the app lock and the
 * sign-out button, under the heading "Edit" - a name that describes the source file
 * rather than anything a reader is trying to do. They share a page because they are
 * one subject: the list and the surfaces it appears on.
 *
 * ## Why the bar is drawn on the shelf
 *
 * "Tier lists shortcut" and "Catalogue search tab" are names for two doors, and a
 * reader setting them is choosing a picture they already have in their head. The
 * preview is the *real* navigation bar with the reader's own tabs - the same
 * composable the app draws at the bottom of every screen - so the row below it
 * cannot describe a bar the app is not showing. It is a picture, not a control:
 * tapping it does nothing, and the rows are where the answer is written.
 */
@Composable
internal fun LibrarySettingsTab(
    refreshIntervalPreferences: RefreshIntervalPreferences,
    arrivingPreferences: ArrivingPreferences,
    libraryPreferences: LibraryPreferences,
    mainTabPreferences: MainTabPreferences,
    pageTitle: String,
    pageSubtitle: String,
    onClosePage: () -> Unit,
    modifier: Modifier = Modifier,
    /** A part a Settings search result asked for; consumed once. */
    initialSection: LibrarySection? = null,
    onInitialSectionConsumed: () -> Unit = {}
) {
    // The bar's preview and the two switches below it read one answer, so the tab
    // list is collected once here rather than once per card.
    val tabs by produceState(
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

    SettingsShelf<LibrarySection>(
        hubTitle = pageTitle,
        hubSubtitle = pageSubtitle,
        initialSection = initialSection,
        onInitialSectionConsumed = onInitialSectionConsumed,
        onClosePage = onClosePage,
        contentTag = "library_tab_content",
        modifier = modifier,
        hub = { onOpen ->
            SettingsScrollColumn {
                // The bar first: it is what "Library & dashboard" looks like, and the
                // row beneath it is the only thing here that is a page of its own.
                BottomBarSample(tabs = tabs)
                SettingsHubGroup(
                    entries = LibrarySection.entries.map { it.shelfEntry(onOpen) }
                )
                LibraryViewCard(libraryPreferences)
                ListRefreshCard(refreshIntervalPreferences)
                ArrivingSectionCard(arrivingPreferences)
                Spacer(modifier = Modifier.height(24.dp))
            }
        },
        body = { section ->
            when (section) {
                LibrarySection.NAVIGATION -> SettingsScrollColumn {
                    // Navigation first: it is what the page's own preview shows, and
                    // where a reader arrives with a question about a missing tab.
                    TopBarSectionCard(mainTabPreferences)
                    BottomBarSectionCard(mainTabPreferences)
                    SearchPageSectionCard(mainTabPreferences)
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    )
}

/**
 * The app's own bar, with the reader's own tabs.
 *
 * Not a mock: `ExpressiveNavigationBar` with `mainTabDestinations` is the exact pair
 * the app draws at the bottom of every page, so a tab the reader turned off is a tab
 * missing here for the same reason it is missing there. `selectedIndex` is the
 * Library because that is the page whose settings these are, and `onSelect` is a
 * no-op: this is a picture of the answer, and a bar that navigated away from the
 * settings that shaped it would be a second control over the same preference.
 */
@Composable
private fun BottomBarSample(tabs: List<MainTab>) {
    // The Library is always enabled, so its index is present by construction - but
    // `indexOf` on a list the reader can change is not worth trusting to `coerceAtLeast`
    // alone. It falls back to zero, which is Home in every bar this app can draw.
    val selected = tabs.indexOf(MainTab.LIBRARY).coerceAtLeast(0)
    SectionCard(
        title = "Your bar",
        icon = AppVectorIcons.Tune,
        subtitle = "The tabs you have on, as the app draws them"
    ) {
        ExpressiveNavigationBar(
            destinations = mainTabDestinations(tabs = tabs, profileAvatarUrl = null),
            selectedIndex = selected,
            onSelect = {},
            // The real bar, drawn without the hold: the gesture lifts the bar and
            // previews a tab it will never switch to here, which is a picture that
            // answers a finger it cannot serve.
            gesturesEnabled = false,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("library_bar_sample")
        )
    }
}

/**
 * The shortcuts beside Settings and Profile.
 *
 * This and [BottomBarSectionCard] used to be one card called "Bottom bar" that
 * held both answers. That card was wrong twice: its title named one surface while
 * half its switches changed the other, and each shortcut forced its matching tab
 * on so a reader could not have one without the other. They are two cards now,
 * and each switch writes only the surface it names.
 */
@Composable
private fun TopBarSectionCard(mainTabPreferences: MainTabPreferences) {
    val scope = rememberCoroutineScope()
    val searchTopBar by mainTabPreferences.searchTopBar.collectAsStateWithLifecycle(initialValue = null)
    val tierTopBar by mainTabPreferences.tierTopBar.collectAsStateWithLifecycle(initialValue = null)

    SectionCard(
        title = "Top bar",
        icon = AppVectorIcons.Tune,
        subtitle = "Shortcuts beside Settings and Profile"
    ) {
        if (searchTopBar == null || tierTopBar == null) {
            Text(
                text = "Reading your top bar shortcuts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            SwitchRow(
                title = "Tier lists shortcut",
                subtitle = "Show the trophy in the top bar in both layouts. This does " +
                    "not add a Tiers tab; the two switches are separate.",
                checked = tierTopBar == true,
                onCheckedChange = { enabled ->
                    scope.launch { mainTabPreferences.setTierTopBar(enabled) }
                },
                modifier = Modifier.testTag("tier_list_top_bar_switch")
            )

            Spacer(modifier = Modifier.height(8.dp))

            SwitchRow(
                title = "Catalogue search shortcut",
                subtitle = "Show the magnifier in the top bar in both layouts. This does " +
                    "not add a Search tab; the two switches are separate.",
                checked = searchTopBar == true,
                onCheckedChange = { enabled ->
                    scope.launch { mainTabPreferences.setSearchTopBar(enabled) }
                },
                modifier = Modifier.testTag("search_top_bar_switch")
            )
        }
    }
}

/**
 * The pages the bottom bar switches between.
 *
 * A switch rather than a decision because the two kinds of reader are both real: the
 * tier list is the only page in the app that owns the whole screen, so a reader who
 * never ranks anything pays a tab's width for a page they will not open, while a
 * reader who ranks everything should not have to reach it through the Library every
 * time.
 *
 * The copy says what each switch does *not* do. It takes the tab off the bar; it does
 * not disable the page or its top-bar shortcut. A switch called "Tier lists" that
 * left the feature working would be read as a broken switch.
 *
 * Off out of the box, so the switch's subtitle leads with what turning it on buys
 * rather than assuming the reader knows what "Tiers" would be a tab for.
 */
@Composable
private fun BottomBarSectionCard(mainTabPreferences: MainTabPreferences) {
    val scope = rememberCoroutineScope()
    val tierListTab by mainTabPreferences.tierListTab.collectAsStateWithLifecycle(initialValue = null)
    val searchTab by mainTabPreferences.searchTab.collectAsStateWithLifecycle(initialValue = null)

    SectionCard(
        title = "Bottom bar",
        icon = AppVectorIcons.Trophy,
        subtitle = "Which pages the bar switches between"
    ) {
        if (tierListTab == null || searchTab == null) {
            Text(
                text = "Reading your bottom bar pages.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            SwitchRow(
                title = "Tier lists tab",
                subtitle = "Add Tiers between Library and Profile. Turning this off hides " +
                    "only the tab; the trophy shortcut and your lists remain available.",
                checked = tierListTab == true,
                onCheckedChange = { enabled ->
                    scope.launch { mainTabPreferences.setTierListTab(enabled) }
                },
                modifier = Modifier.testTag("tier_list_tab_switch")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // The search tab is the one optional page with no second door: the tier
            // list is reachable from the Library's trophy and the profile's row, but
            // catalogue search exists here and nowhere else. The subtitle says so,
            // because a switch that reads like a shortcut for something already
            // available would be understood as cosmetic.
            SwitchRow(
                title = "Search tab",
                subtitle = "Add Search to the bar. Turning this off hides only the tab; " +
                    "the top-bar shortcut still opens the page.",
                checked = searchTab == true,
                onCheckedChange = { enabled ->
                    scope.launch { mainTabPreferences.setSearchTab(enabled) }
                },
                modifier = Modifier.testTag("search_tab_switch")
            )
        }
    }
}

/**
 * What the Search page is allowed to search.
 *
 * These two switches used to live under "Bottom bar" beside the tab switches, which
 * made a card about navigation also own the page's contents. They are their own
 * card now because the question is different: the bar switches decide *where* the
 * page is reachable from, these decide what it can answer.
 */
@Composable
private fun SearchPageSectionCard(mainTabPreferences: MainTabPreferences) {
    val scope = rememberCoroutineScope()
    val searchAnimeEnabled by mainTabPreferences.searchAnimeEnabled.collectAsStateWithLifecycle(initialValue = null)
    val searchMangaEnabled by mainTabPreferences.searchMangaEnabled.collectAsStateWithLifecycle(initialValue = null)

    SectionCard(
        title = "Search page",
        icon = AppVectorIcons.Search,
        subtitle = "What the catalogue search can find"
    ) {
        if (searchAnimeEnabled == null || searchMangaEnabled == null) {
            Text(
                text = "Reading your search page options.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            SwitchRow(
                title = "Anime search",
                subtitle = "Offer the anime half of the catalogue on the Search page.",
                checked = searchAnimeEnabled == true,
                onCheckedChange = { enabled ->
                    scope.launch { mainTabPreferences.setSearchAnimeEnabled(enabled) }
                },
                modifier = Modifier.testTag("search_anime_switch")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Separate namespaces, separate switches. The page asks one at a time,
            // and this is where a reader says which halves exist on their device at
            // all. Both off also hides the tab and the shortcut (see `enabledMainTabs`
            // and the host's composition local), because a search page with no
            // catalogue to search is an empty room.
            SwitchRow(
                title = "Manga search",
                subtitle = "Offer the manga half of the catalogue on the Search page. " +
                    "Turning both halves off hides the Search tab and its shortcut.",
                checked = searchMangaEnabled == true,
                onCheckedChange = { enabled ->
                    scope.launch { mainTabPreferences.setSearchMangaEnabled(enabled) }
                },
                modifier = Modifier.testTag("search_manga_switch")
            )
        }
    }
}

/**
 * The Library page's remembered view, and the one thing only Settings can undo.
 *
 * The type, status filter and sort on the Library are remembered on purpose - a
 * page that asks the same one-tap question every visit is a page failing to
 * remember an answer. But `LibraryPreferences.reset()` had no caller: a reader
 * who once narrowed the Library to "Manga, Completed, score high to low" could
 * only get back to the default view by changing three controls in the right
 * order, and had no way to know the page was remembering them at all. The card
 * says what is remembered and offers the one action that forgets it.
 *
 * The read is three-state (`null` until DataStore answers) rather than defaulting
 * to "nothing remembered". That first frame would otherwise hide the reset
 * button from exactly the reader who needs it and replace it with a claim about
 * the default that is about to become false.
 */
@Composable
private fun LibraryViewCard(libraryPreferences: LibraryPreferences) {
    val scope = rememberCoroutineScope()
    val remembered by remember(libraryPreferences) {
        combine(
            libraryPreferences.lastType,
            libraryPreferences.lastFilter,
            libraryPreferences.lastSort,
            libraryPreferences.lastSortAscending
        ) { type, filter, sort, ascending ->
            type != null || filter != null || sort != null || ascending != null
        }
    }.collectAsStateWithLifecycle(initialValue = null)

    SectionCard(
        title = "Library view",
        icon = AppVectorIcons.List,
        subtitle = "What the Library reopens with"
    ) {
        CollapsibleText(
            text = when (remembered) {
                null -> "Reading the view saved on this device."
                true -> "The Library reopens with the type, status filter and sort you " +
                    "last chose there."
                false -> "The Library opens on all titles in list order until you " +
                    "choose something else there."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Mounted only when there is something to forget: a reset button on a page
        // that already opens at the default is a control promising to do nothing.
        AnimatedVisibility(
            visible = remembered == true,
            enter = fadeIn(animationSpec = ExpressiveMotion.FastEffects) +
                expandVertically(animationSpec = ExpressiveMotion.FastSpatialSize),
            exit = fadeOut(animationSpec = ExpressiveMotion.FastEffects) +
                shrinkVertically(animationSpec = ExpressiveMotion.FastSpatialSize)
        ) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsButton(
                    text = "Forget remembered view",
                    onClick = { scope.launch { libraryPreferences.reset() } },
                    icon = AppVectorIcons.Restore,
                    variant = SettingsButtonVariant.Outlined,
                    fillWidth = true,
                    testTag = "reset_library_view_button"
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Only the remembered view is cleared. Nothing is removed " +
                        "from your list.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * How often the saved list is re-read from AniList.
 *
 * The setting exists because of a bug, not a preference. Nothing used to watch
 * for the app returning to the foreground, and the list was cached for a flat
 * hour, so closing and reopening AniSequel - which Android does by resuming the
 * process rather than restarting it - showed the identical dashboard every time.
 * Finishing something on AniList in another app changed nothing until an hour
 * had passed or the refresh button was tapped.
 *
 * ## Why chips and not `ExpressivePolygonSegmentedBar`
 *
 * The segmented bar is how the Theme tab picks one of two or three values. There
 * are five here, and every one of them is short but not equally short - "Always",
 * "15 min", "30 min", "1 hour", "Manual" - squeezed into equal halves of a
 * phone-width row. A wrapping [FlowRow] of pills is the same interaction with a
 * layout that cannot clip a label, which is what `PaletteSwatch` does for the
 * same reason.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListRefreshCard(refreshIntervalPreferences: RefreshIntervalPreferences) {
    val scope = rememberCoroutineScope()
    val interval by refreshIntervalPreferences.interval.collectAsStateWithLifecycle(initialValue = null)

    SectionCard(
        title = "List refresh",
        icon = AppVectorIcons.Schedule,
        subtitle = "How fresh your scan stays"
    ) {
        CollapsibleText(
            text = "AniSequel re-reads your list when you come back to the app if it is " +
                "older than this, and on its own while it stays open. Pull down or " +
                "use the refresh button any time to check immediately.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(14.dp))

        if (interval == null) {
            Text(
                text = "Reading your refresh interval.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val selectedInterval = interval ?: RefreshInterval.DEFAULT
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.testTag("refresh_interval_row")
            ) {
                RefreshInterval.entries.forEach { option ->
                    SettingsChoiceChip(
                        label = option.displayName,
                        selected = option == selectedInterval,
                        onClick = {
                            scope.launch { refreshIntervalPreferences.setInterval(option) }
                        },
                        testTag = "refresh_interval_${option.storageValue}",
                        onClickLabel = "Refresh every ${option.displayName}"
                    )
                }
            }

            // Shown only once the interval is something other than the default, so the
            // card grows a button the moment a chip is chosen. A bare `if` did that by
            // appearing fully drawn on the next frame and shoving the card's bottom edge
            // down instantly; the spring expansion makes the row the thing that moves.
            // `FastSpatialSize` rather than the `Int` token: `expandVertically` is typed
            // on `FiniteAnimationSpec<IntSize>`, because what it animates is the row's
            // measured height rather than a count. The `Int` token looks like the closer
            // match and does not compile.
            AnimatedVisibility(
                visible = selectedInterval != RefreshInterval.DEFAULT,
                enter = fadeIn(animationSpec = ExpressiveMotion.FastEffects) +
                    expandVertically(animationSpec = ExpressiveMotion.FastSpatialSize),
                exit = fadeOut(animationSpec = ExpressiveMotion.FastEffects) +
                    shrinkVertically(animationSpec = ExpressiveMotion.FastSpatialSize)
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    SettingsButton(
                        // Named from the enum rather than typed: "Use AniSequel's
                        // default" made the reader go and find out what the default was,
                        // and it appeared right under the chip that already said it.
                        text = "Reset to ${RefreshInterval.DEFAULT.displayName}",
                        onClick = { scope.launch { refreshIntervalPreferences.reset() } },
                        icon = AppVectorIcons.Restore,
                        variant = SettingsButtonVariant.Text,
                        fillWidth = true,
                        testTag = "reset_refresh_interval_button"
                    )
                }
            }
        }
    }
}

/**
 * How the dashboard's airing and upcoming rows behave.
 *
 * Three switches because they answer different questions: hiding the section
 * removes the rows above search entirely (and with them every poster load on the
 * path to a list), compact keeps the information in plain rows that fetch no
 * artwork - the entire cost of those cards is the image - and separating the
 * groups draws the two headings the combined row deliberately omits. Compact is
 * meaningless on a hidden section, so both dependent switches disable with it.
 */
@Composable
private fun ArrivingSectionCard(arrivingPreferences: ArrivingPreferences) {
    val scope = rememberCoroutineScope()
    val settings by arrivingPreferences.settings.collectAsStateWithLifecycle(initialValue = null)

    SectionCard(
        // "Dashboard" was the name of the page these rows live on, not of the
        // setting: the card only holds the arriving section, and a title that names
        // the whole screen under a switch called "Currently arriving" reads as if
        // the switch turned the dashboard off.
        title = "Arriving rows",
        icon = AppVectorIcons.Tv,
        subtitle = "The airing and upcoming rows above search"
    ) {
        if (settings == null) {
            Text(
                text = "Reading your arriving rows settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val arrivingSettings = settings ?: ArrivingPreferences.ArrivingSettings()
            SwitchRow(
                title = "Currently arriving",
                subtitle = "Show airing and upcoming rows above search.",
                checked = arrivingSettings.showArrivingSection,
                onCheckedChange = { enabled ->
                    scope.launch { arrivingPreferences.setShowArrivingSection(enabled) }
                },
                modifier = Modifier.testTag("arriving_section_switch")
            )
            SwitchRow(
                title = "Compact arriving rows",
                subtitle = "Plain rows that load no artwork - less data, less battery.",
                checked = arrivingSettings.compactArrivingCards,
                enabled = arrivingSettings.showArrivingSection,
                onCheckedChange = { compact ->
                    scope.launch { arrivingPreferences.setCompactArrivingCards(compact) }
                },
                modifier = Modifier.testTag("compact_arriving_switch")
            )
            SwitchRow(
                title = "Separate airing and upcoming",
                subtitle = "Two labelled rows instead of one. Off keeps the section to " +
                    "a single strip; the cards still say which group they belong to.",
                checked = arrivingSettings.separateArrivingGroups,
                enabled = arrivingSettings.showArrivingSection,
                onCheckedChange = { separate ->
                    scope.launch { arrivingPreferences.setSeparateArrivingGroups(separate) }
                },
                modifier = Modifier.testTag("separate_arriving_switch")
            )
        }
    }
}
