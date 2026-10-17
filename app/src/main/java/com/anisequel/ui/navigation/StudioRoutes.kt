package com.anisequel.ui.navigation

import android.net.Uri
import androidx.navigation.NavType
import androidx.navigation.navArgument

/**
 * The studio page, and the two things a caller has to hand it.
 *
 * ## Why the id and the name
 *
 * The page is *fetched* by id - `Studio(id:)` - so the id is the address. The name
 * is carried beside it for one reason: `StudioScreen` draws its top bar from the
 * route on the first frame, before the request has answered, and a bar that reads
 * "Studio" for the length of a round trip is a visibly worse thing than a bar that
 * reads "Shaft". Every caller already has the name: it is what they just tapped.
 *
 * The name is optional (`?name=` with an empty value) for the same reason
 * [ProfileRoutes.OWN_DEFAULT] exists: a `composable()` route is matched *exactly*,
 * so `navigate("studio/12")` finds no match for `studio/12?name={name}` and
 * silently does nothing. [studio] always writes the query, even when the name is
 * empty.
 *
 * ## Why the page is reachable signed out
 *
 * A studio is public AniList data - no token, no viewer entry, no write - so this
 * route is deliberately **not** in the list of routes the auth watcher evacuates
 * when a session ends. A 401 that arrives while somebody is reading a filmography
 * should not throw them off the page they are reading.
 */
object StudioRoutes {
    const val ROUTE = "studio/{studioId}?name={name}"

    const val STUDIO_ID_ARG = "studioId"
    const val NAME_ARG = "name"

    /** Builds the route, always with the `name` query so the exact match resolves. */
    fun studio(studioId: Int, name: String?): String =
        "studio/$studioId?name=${Uri.encode(name.orEmpty())}"
}

val StudioArguments = listOf(
    navArgument(StudioRoutes.STUDIO_ID_ARG) { type = NavType.IntType },
    navArgument(StudioRoutes.NAME_ARG) {
        type = NavType.StringType
        defaultValue = ""
    }
)
