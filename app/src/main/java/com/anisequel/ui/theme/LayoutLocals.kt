package com.anisequel.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.anisequel.domain.ui.AppLayout
import com.anisequel.domain.ui.HomeSequelStyle
import com.anisequel.domain.ui.LibraryViewMode
import com.anisequel.domain.ui.NavBarLift

/**
 * The layout the app is drawing.
 *
 * A `CompositionLocal` rather than a parameter on every screen: the dashboard,
 * the Library, the bar and the Settings preview all have to agree about which
 * look is live, and threading one enum through four nav graphs is how one of
 * them ends up drawing last week's answer. Provided once at the app root from
 * [com.anisequel.data.repository.InterfacePreferences].
 */
val LocalAppLayout = staticCompositionLocalOf { AppLayout.DEFAULT }

/**
 * How dense the new Library draws itself.
 *
 * Read by the library grid and by the Settings preview, written only by the
 * preference, so a tap in Settings repaints the real grid on the same trip back.
 */
val LocalLibraryViewMode = staticCompositionLocalOf { LibraryViewMode.DEFAULT }

/**
 * Whether the new Home starts with the Continue Watching rail.
 *
 * A rail is a whole request's worth of covers above the list; the reader who
 * wants Home to be lists and sequels can take it off without losing the feature,
 * which still lives in the Library.
 */
val LocalShowContinueRail = staticCompositionLocalOf { true }

/**
 * How the new Home draws its missed sequels: compact rows or poster cards.
 *
 * Read by the Home list and by the Interface page's preview, written only by the
 * preference - so the sample the reader is choosing between is drawn by the same
 * enum the real rows are, and a card style added later cannot appear in one and
 * not the other.
 */
val LocalHomeSequelStyle = staticCompositionLocalOf { HomeSequelStyle.DEFAULT }

/**
 * How far the main bar floats above the bottom edge, in dp.
 *
 * Read by the bar, by every list's bottom clearance and by the Interface page's
 * live preview, so dragging the slider in Settings and leaving the page cannot
 * leave one of the three describing a different position. Provided once at the
 * app root from [com.anisequel.data.repository.InterfacePreferences]; see
 * [com.anisequel.domain.ui.NavBarLift] for why the range is small.
 *
 * A *dynamic* local, unlike its neighbours here. The root animates this value on
 * every frame of the spring between two lift settings, and a `staticCompositionLocalOf`
 * recomposes the provider's entire content when it changes - the whole app, once
 * per frame of an animation whose whole point is to look smooth. A dynamic local
 * invalidates only the composables that read the lift, which is the bar and the
 * handful of containers that reserve clearance for it.
 */
val LocalMainBarLift = compositionLocalOf { NavBarLift.DEFAULT_DP }

/**
 * Whether tapping "Add to Planning" asks for a tick or a cross first.
 *
 * Defaults to **true** - the confirmation is the shipped behaviour. The option
 * exists because the previous one-tap add was genuinely quick, and a reader who
 * has decided they trust it should not have to press twice forever; turning this
 * off is the way back to it. Read by
 * [com.anisequel.ui.components.cards.QuickAddPlanningButton], so every card, row
 * and detail sheet honours one switch.
 */
val LocalConfirmPlanningAdd = staticCompositionLocalOf { true }

/** Optional direct catalogue shortcut shown beside the Nova/classic top-bar actions. */
val LocalSearchTopBarShortcut = staticCompositionLocalOf { false }

/**
 * Which namespaces the catalogue page may search.
 *
 * Read by the Search page and written only by Settings, so the chips and the
 * actual request cannot disagree. Both default on: the page keeps the combined
 * search a fresh install has always had until the reader narrows it.
 */
val LocalSearchAnimeEnabled = staticCompositionLocalOf { true }
val LocalSearchMangaEnabled = staticCompositionLocalOf { true }

/** Optional direct tier-list shortcut shown beside the Nova/classic top-bar actions. */
val LocalTierTopBarShortcut = staticCompositionLocalOf { false }
