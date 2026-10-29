package com.anisequel.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.data.repository.ArrivingPreferences
import com.anisequel.data.repository.LibraryPreferences
import com.anisequel.data.repository.MainTabPreferences
import com.anisequel.data.repository.QuickAddPreferences
import com.anisequel.data.repository.RefreshInterval
import com.anisequel.data.repository.RefreshIntervalPreferences
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * How the saved list is read, how the dashboard presents it, and which pages the
 * bottom bar offers.
 *
 * These cards used to share a tab with the AniList Client ID, the app lock and the
 * sign-out button, under the heading "Edit" - a name that describes the source file
 * rather than anything a reader is trying to do. They share a page now because they
 * are one question: how fresh the list is, what the dashboard does with it, and
 * where it can be reached from.
 */
@Composable
fun LibrarySettingsTab(
    refreshIntervalPreferences: RefreshIntervalPreferences,
    arrivingPreferences: ArrivingPreferences,
    quickAddPreferences: QuickAddPreferences,
    libraryPreferences: LibraryPreferences,
    mainTabPreferences: MainTabPreferences,
    modifier: Modifier = Modifier
) {
    SettingsScrollColumn(modifier) {
        ListRefreshCard(refreshIntervalPreferences)
        LibraryViewCard(libraryPreferences)
        ArrivingSectionCard(arrivingPreferences)
        QuickAddSectionCard(quickAddPreferences)
        BottomBarSectionCard(mainTabPreferences)
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * The tier list's place on the bottom bar.
 *
 * A switch rather than a decision because the two kinds of reader are both real: the
 * tier list is the only page in the app that owns the whole screen, so a reader who
 * never ranks anything pays a tab's width for a page they will not open, while a
 * reader who ranks everything should not have to reach it through the Library every
 * time.
 *
 * The copy says what the switch does *not* do. It takes the tab off the bar; it does
 * not disable tier lists - the trophy in the Library and the row in the profile open
 * the same page either way. A switch called "Tier lists" that left the feature
 * working would be read as a broken switch.
 *
 * Off out of the box, so the switch's subtitle leads with what turning it on buys
 * rather than assuming the reader knows what "Tiers" would be a tab for.
 */
@Composable
private fun BottomBarSectionCard(mainTabPreferences: MainTabPreferences) {
    val scope = rememberCoroutineScope()
    val tierListTab by mainTabPreferences.tierListTab.collectAsState(
        initial = MainTabPreferences.DEFAULT_TIER_LIST_TAB
    )

    SectionCard(
        title = "Bottom bar",
        icon = AppVectorIcons.Trophy,
        subtitle = "Which pages the bar switches between"
    ) {
        SwitchRow(
            title = "Tier lists tab",
            subtitle = "Add Tiers between Library and Profile, so your lists are one " +
                "tap away. The trophy in your library opens the same page either way.",
            checked = tierListTab,
            onCheckedChange = { enabled ->
                scope.launch { mainTabPreferences.setTierListTab(enabled) }
            },
            modifier = Modifier.testTag("tier_list_tab_switch")
        )
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
    }.collectAsState(initial = null)

    SectionCard(
        title = "Library view",
        icon = AppVectorIcons.List,
        subtitle = "What the Library reopens with"
    ) {
        Text(
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
    val interval by refreshIntervalPreferences.interval.collectAsState(
        initial = RefreshInterval.DEFAULT
    )

    SectionCard(
        title = "List refresh",
        icon = AppVectorIcons.Schedule,
        subtitle = "How fresh your scan stays"
    ) {
        Text(
            text = "AniSequel re-reads your list when you come back to the app if it is " +
                "older than this, and on its own while it stays open. Pull down or " +
                "use the refresh button any time to check immediately.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(14.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.testTag("refresh_interval_row")
        ) {
            RefreshInterval.entries.forEach { option ->
                RefreshIntervalChip(
                    interval = option,
                    selected = option == interval,
                    onClick = { scope.launch { refreshIntervalPreferences.setInterval(option) } }
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
            visible = interval != RefreshInterval.DEFAULT,
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

/**
 * How the dashboard's airing and upcoming rows behave.
 *
 * Two switches because they answer different questions: hiding the section
 * removes the rows above search entirely (and with them every poster load on the
 * path to a list), while compact keeps the information in plain rows that fetch
 * no artwork - the entire cost of those cards is the image. Compact is
 * meaningless on a hidden section, so it disables along with it.
 */
@Composable
private fun ArrivingSectionCard(arrivingPreferences: ArrivingPreferences) {
    val scope = rememberCoroutineScope()
    val settings by arrivingPreferences.settings.collectAsState(
        initial = ArrivingPreferences.ArrivingSettings()
    )

    SectionCard(
        // "Dashboard" was the name of the page these rows live on, not of the
        // setting: the card only holds the arriving section, and a title that names
        // the whole screen under a switch called "Currently arriving" reads as if
        // the switch turned the dashboard off.
        title = "Arriving rows",
        icon = AppVectorIcons.Tv,
        subtitle = "The airing and upcoming rows above search"
    ) {
        SwitchRow(
            title = "Currently arriving",
            subtitle = "Show airing and upcoming rows above search.",
            checked = settings.showArrivingSection,
            onCheckedChange = { enabled ->
                scope.launch { arrivingPreferences.setShowArrivingSection(enabled) }
            },
            modifier = Modifier.testTag("arriving_section_switch")
        )
        SwitchRow(
            title = "Compact arriving rows",
            subtitle = "Plain rows that load no artwork - less data, less battery.",
            checked = settings.compactArrivingCards,
            enabled = settings.showArrivingSection,
            onCheckedChange = { compact ->
                scope.launch { arrivingPreferences.setCompactArrivingCards(compact) }
            },
            modifier = Modifier.testTag("compact_arriving_switch")
        )
    }
}

/** One refresh interval, selected or not. */
@Composable
private fun RefreshIntervalChip(
    interval: RefreshInterval,
    selected: Boolean,
    onClick: () -> Unit
) {
    // These were four `if (selected)` reads, which made this the one control in
    // Settings that snapped while everything around it travelled: the container, the
    // label and the border all swapped on the frame of the tap. The colours ride the
    // colour-effects spring and the border width the spatial one, because a width is
    // geometry and geometry gets the spatial curve.
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "refresh_chip_container"
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "refresh_chip_content"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 2.dp else 1.dp,
        animationSpec = ExpressiveMotion.FastSpatialDp,
        label = "refresh_chip_border_width"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "refresh_chip_border_colour"
    )

    Row(
        modifier = Modifier
            .clip(ExpressiveShapes.pill)
            .background(container)
            .border(
                BorderStroke(width = borderWidth, color = borderColor),
                shape = ExpressiveShapes.pill
            )
            .bouncyPress(pressedScale = 0.94f)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics {
                this.selected = selected
                role = Role.RadioButton
            }
            .testTag("refresh_interval_${interval.storageValue}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = interval.displayName,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = content
        )
    }
}
