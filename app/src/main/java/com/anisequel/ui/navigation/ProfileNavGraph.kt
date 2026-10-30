package com.anisequel.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.anisequel.data.model.ViewerProfile
import com.anisequel.data.repository.AniListRepository
import com.anisequel.domain.usecase.profileIdentityFromRoute
import com.anisequel.ui.screens.profile.SEGMENT_ACTIVITY
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
            },
            // A **non-null** default, which is what makes the argument omissible so
            // that `OWN_DEFAULT` (`profile?tab=`) and the widget's `OWN_ACTIVITY`
            // still resolve to this destination. `OWN_TAB_ARG` above leaves the
            // default null, and that is exactly why those two callers have to spell
            // `tab=` out: an argument with no default value has to be present in the
            // URI, and a `navigate` that matches nothing does nothing at all. Copying
            // the tab's shape here would therefore have broken the bar's Profile tab
            // the moment the segment was added. `nullable` is set as well so the
            // argument is optional however the matcher reads it; both "absent" and
            // "empty" mean the same thing to `profileSegmentFromRoute`.
            navArgument(ProfileRoutes.OWN_SEGMENT_ARG) {
                type = NavType.StringType
                nullable = true
                defaultValue = ""
            }
        )
    ) { backStackEntry ->
        // Read off the route rather than from a shared flag, so the request
        // travels with the destination. A flag would outlive the visit and
        // re-open the Activity tab on the *next* profile the user opened,
        // from wherever they opened it.
        val requestedTab = backStackEntry.arguments?.getString(ProfileRoutes.OWN_TAB_ARG)
        val requestedSegment =
            backStackEntry.arguments?.getString(ProfileRoutes.OWN_SEGMENT_ARG)

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
            },
            // The same one-way mapping as the tab's: the route's word decides the
            // segment the Activity page is *initialised* to, and nothing else. An
            // unrecognised or absent value means "whatever the reader left it on",
            // which is what keeps every existing caller - `OWN_DEFAULT`, the widget's
            // `OWN_ACTIVITY` - behaving exactly as it did before a route could name a
            // segment.
            initialSegment = profileSegmentFromRoute(requestedSegment) ?: SEGMENT_ACTIVITY
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
        // Read as an identity rather than as two loose arguments, because the two
        // failure modes are different: a name with no id is a profile that can be
        // fetched (by name), and an id that is missing or `0` is nobody at all. The
        // route used to say `getInt("userId") ?: 0`, which made that second case a
        // live request for AniList user 0 - a real account, with a real bio, that
        // nobody asked to see.
        val identity = profileIdentityFromRoute(
            rawUserId = backStackEntry.arguments?.getInt("userId"),
            rawUsername = backStackEntry.arguments?.getString(ProfileRoutes.ID_NAME_ARG)
        )
        ProfileDestination(
            repository = aniListRepository,
            detailHost = detailHost,
            // The tapped follower's own name, which the row carries. It is what the
            // bar prints on the first frame and what the ViewModel key is built
            // from; the query itself still goes by id, which is the cheap half.
            username = identity.username.orEmpty(),
            userId = identity.userId,
            // See the by-name route: the reader and the profile being read
            // are two different accounts here.
            viewerId = signedInViewer?.id,
            isDemo = false,
            navController = navController,
            onSignInAgain = onSignInAgain
        )
    }
}
