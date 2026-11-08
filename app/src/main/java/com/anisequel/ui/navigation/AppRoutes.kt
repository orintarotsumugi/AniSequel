package com.anisequel.ui.navigation

import android.net.Uri

/**
 * The routes the navigation graph registers by name.
 *
 * Kept beside the graph rather than inside `AppNavigation` so the route table and
 * the graph's wiring are separate reasons to change. The bar's mapping and the
 * tests read these constants directly; none of them needs the composition root.
 */
object AppRoutes {
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard"
    const val DASHBOARD_DEMO = "dashboard_demo"
    const val DASHBOARD_USER = "dashboard_user/{username}"
    const val LIBRARY = "library"
    const val EXTENSIONS = "extensions"
    const val EXTENSION_MANAGER = "extension_manager"
    const val PLAYER_SETTINGS = "player_settings"
    const val SETTINGS = "settings"
    const val TIER_LIST = "tier_list"

    /**
     * The path is encoded, not interpolated raw.
     *
     * A `composable()` route is matched exactly, so a name carrying `/`, `#`, `?`
     * or `%` produced a URI that matched no destination and the tap did nothing
     * with no error anywhere - the same silent failure [ProfileRoutes.byId]
     * documents for the query form. Navigation decodes the segment back for
     * `NavType.StringType`, so the screen still receives the name as typed.
     */
    fun userDashboard(username: String) = "dashboard_user/${Uri.encode(username)}"
}
