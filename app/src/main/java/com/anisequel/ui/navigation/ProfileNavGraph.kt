package com.anisequel.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.anisequel.data.model.ViewerProfile
import com.anisequel.data.repository.AniListRepository
import com.anisequel.ui.viewmodel.MediaDetailHost

/**
 * Destinations for user profiles:
 *
 *  - [ProfileRoutes.OWN_ROUTE]: the signed-in viewer's own profile (or demo).
 *  - [ProfileRoutes.BY_USERNAME]: a scanned public profile by username.
 *  - [ProfileRoutes.BY_ID]: any AniList user by id (e.g. followers/following).
 */
fun NavGraphBuilder.profileDestinations(
    navController: NavHostController,
    aniListRepository: AniListRepository,
    detailHost: MediaDetailHost,
    signedInViewer: ViewerProfile?,
    onSignInAgain: () -> Unit
) {
    // The base route and the widget's `profile?tab=activity` are one
    // destination: the query argument is optional, so every way in lands here
    // and only the widget names a tab.
    //
    // `OWN_DEFAULT` (`profile?tab=`) rather than `OWN` is what the callers use,
    // because a `composable()` route is matched *exactly* - `navigate("profile")`
    // matches no route at all and silently does nothing. See `ProfileRoutes.OWN_DEFAULT`.
    composable(
        route = ProfileRoutes.OWN_ROUTE,
        arguments = listOf(
            navArgument(ProfileRoutes.OWN_TAB_ARG) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        )
    ) { backStackEntry ->
        // Read off the route rather than from a shared flag, so the request
        // travels with the destination. A flag would outlive the visit and
        // re-open the Activity tab on the *next* profile the user opened,
        // from wherever they opened it.
        val requestedTab = backStackEntry.arguments?.getString(ProfileRoutes.OWN_TAB_ARG)

        // `signedInViewer` is null until the dashboard has loaded, and a demo
        // dashboard has no session ViewModel at all - hence the two sources
        // rather than one. The button that reaches this route is on a loaded
        // dashboard, so in practice this is never an empty string.
        val username = signedInViewer?.name ?: DEMO_USERNAME
        ProfileDestination(
            repository = aniListRepository,
            detailHost = detailHost,
            username = username,
            userId = signedInViewer?.id,
            // The viewer reading, which on this route is also the profile in
            // view - keyed off the same source as `userId` so the two can
            // never describe different people.
            viewerId = signedInViewer?.id,
            isDemo = signedInViewer == null,
            navController = navController,
            onSignInAgain = onSignInAgain,
            // This is the viewer's own profile, so the tier list - a ranking
            // of their own saved work - belongs on its bar. The scanned and
            // followed routes leave this defaulted off.
            isOwnProfile = true,
            initialTab = if (requestedTab == ProfileRoutes.TAB_ACTIVITY) {
                ProfileRoutes.ACTIVITY_TAB_INDEX
            } else {
                0
            }
        )
    }

    composable(
        route = ProfileRoutes.BY_USERNAME,
        arguments = ProfileUsernameArguments
    ) { backStackEntry ->
        val username = backStackEntry.arguments?.getString("username").orEmpty()
        ProfileDestination(
            repository = aniListRepository,
            detailHost = detailHost,
            username = username,
            // No id for a scanned profile: `getUserByName` has already been
            // asked for it by the dashboard that led here, but its id is not
            // carried in the route. `null` puts the list fetch on the by-name
            // cache key, which is the same key that dashboard used - so it is
            // still a cache hit rather than a second download.
            userId = null,
            // The reader's own id, which is a different person from the
            // profile in view on this route: it is what the comment thread
            // compares each author against to decide whose comments carry
            // their manage control.
            viewerId = signedInViewer?.id,
            isDemo = false,
            navController = navController,
            onSignInAgain = onSignInAgain
        )
    }

    composable(
        route = ProfileRoutes.BY_ID,
        arguments = ProfileIdArguments
    ) { backStackEntry ->
        val userId = backStackEntry.arguments?.getInt("userId") ?: 0
        ProfileDestination(
            repository = aniListRepository,
            detailHost = detailHost,
            // The name is only a label at this point; the query is by id. The
            // follower row carries both, so an empty name would be a bug in
            // the caller rather than a real case, and it still fetches.
            username = "",
            userId = userId,
            // See the by-name route: the reader and the profile being read
            // are two different accounts here.
            viewerId = signedInViewer?.id,
            isDemo = false,
            navController = navController,
            onSignInAgain = onSignInAgain
        )
    }
}
