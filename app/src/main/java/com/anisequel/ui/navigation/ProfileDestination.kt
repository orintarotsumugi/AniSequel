package com.anisequel.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.anisequel.data.model.FollowUser
import com.anisequel.data.repository.AniListRepository
import com.anisequel.ui.screens.profile.UserProfileScreen
import com.anisequel.ui.viewmodel.UserActivityViewModel
import com.anisequel.ui.viewmodel.UserOverviewViewModel
import com.anisequel.ui.viewmodel.UserSocialViewModel

/**
 * The routes a profile can be opened for.
 *
 * Three, and the third is the one the Social tab exists for.
 *
 *  - [OWN] the signed-in viewer's own profile.
 *  - [BY_USERNAME] a scanned public profile.
 *  - [BY_ID] **any** AniList account by id - which is what a tap on a follower or
 *    a followed account navigates to.
 *
 * [BY_ID] could be [BY_USERNAME] with the name, and for a while it was going to
 * be. It is separate because the id is what makes it cheap and correct: the
 * activity feed and both social lists are addressable only by id, so a profile
 * opened from the Social grid would have to look the name up before it could fetch
 * anything else about that person - one extra round trip, on the exact screen
 * where the user is tapping fastest. The follower row carries the id.
 */
object ProfileRoutes {
    const val OWN = "profile"
    const val BY_USERNAME = "profile_user/{username}"
    const val BY_ID = "profile_id/{userId}"

    /**
     * [OWN] with the tab the profile should open on.
     *
     * The tab is an optional query argument rather than a second destination, so
     * `navigate(OWN)` from the dashboard resolves to the same composable and the
     * two cannot drift. It carries the home-screen widget's request: a tap on the
     * tile asks for the Activity tab, and `"profile"` alone leaves the profile on
     * its default tab as before.
     */
    const val OWN_ROUTE = "profile?tab={tab}"
    const val OWN_TAB_ARG = "tab"

    /** The value [OWN_TAB_ARG] takes for the profile's Activity tab. */
    const val TAB_ACTIVITY = "activity"

    /** Index of the Activity tab in `ProfileTabs`. */
    const val ACTIVITY_TAB_INDEX = 1

    /** [OWN] opening directly on the Activity tab. Used by the widget. */
    const val OWN_ACTIVITY = "profile?tab=activity"

    /**
     * The tab a profile opens on when nothing asks for one in particular.
     *
     * [OWN_TAB_ARG] with an **empty** value rather than [OWN] on its own, because a
     * `composable()` route is matched exactly: navigating to `"profile"` finds no
     * match for `"profile?tab={tab}"` and silently does nothing, so the avatar tap
     * from the dashboard - and the demo dashboard - were dead. The empty value is
     * the optional argument left unset, which is the one thing this route *can* be
     * reached by.
     */
    const val OWN_DEFAULT = "profile?tab="

    fun byUsername(username: String) = "profile_user/$username"
    fun byId(userId: Int) = "profile_id/$userId"
}

val ProfileUsernameArguments = listOf(navArgument("username") { type = NavType.StringType })
val ProfileIdArguments = listOf(navArgument("userId") { type = NavType.IntType })

/**
 * Builds the profile screen for whoever it is about, and routes taps on a
 * follower to *that person's* profile.
 *
 * ## The identity comes in rather than being looked up
 *
 * Every route that can land here already knows who: the signed-in dashboard has
 * the viewer's own name and id, the username scan has the name, and a tap on the
 * Social grid has both name and id. Passing them through means this composable
 * never blocks on a lookup to find out who to show, so the screen's first frame is
 * a header rather than a spinner for a name it was always given.
 *
 * It also means the list fetch shares a cache with whatever opened it - the
 * signed-in dashboard has already put the whole anime list under `user:<id>`, and
 * `getUserAnimeList(userId)` is that key. Fetching by name instead would use
 * `userName:` and re-download the single most expensive query in the app to
 * recount what was already in memory.
 *
 * ## Three ViewModels, not one
 *
 * The Activity and Social tabs are a second and third pair of requests that a
 * person who opened their profile to look at their favourites does not need. They
 * are held separately so they can load when their tab is opened rather than in
 * `init`, which is what keeps "open profile" to one request.
 */
@Composable
fun ProfileDestination(
    repository: AniListRepository,
    username: String,
    userId: Int?,
    isDemo: Boolean,
    navController: NavHostController,
    onSignInAgain: () -> Unit,
    /**
     * True only for the signed-in viewer's own profile.
     *
     * Controls whether the tier-list trophy is offered. Defaulted to false so a
     * route that forgets to say lands on the safe side - a followed or scanned
     * profile - rather than offering a control that opens the wrong account's
     * ranking.
     */
    isOwnProfile: Boolean = false,
    /**
     * The Library tab's remembered media type.
     *
     * Passed in rather than built here so there is exactly one DataStore reader
     * per process. Two readers of the same file is how the tab ends up
     * disagreeing with itself about what was last chosen.
     */
    libraryPreferences: com.anisequel.data.repository.LibraryPreferences,
    initialTab: Int = 0
) {
    // Keyed on the username as well as the id: two profiles can be opened one
    // after the other from the same stack, and without the name in the key the
    // second would render the first one's bio and favourites.
    val viewModelKey = remember(username, userId, isDemo) {
        "profile_${if (isDemo) "demo" else username}_${userId ?: "name"}"
    }

    val overviewViewModel: UserOverviewViewModel = viewModel(
        key = "$viewModelKey:overview",
        factory = UserOverviewViewModel.Factory(
            aniListRepository = repository,
            userId = userId,
            username = username,
            isDemo = isDemo
        )
    )

    val activityViewModel: UserActivityViewModel = viewModel(
        key = "$viewModelKey:activity",
        factory = UserActivityViewModel.Factory(
            aniListRepository = repository,
            userId = userId,
            isDemo = isDemo
        )
    )

    val socialViewModel: UserSocialViewModel = viewModel(
        key = "$viewModelKey:social",
        factory = UserSocialViewModel.Factory(
            aniListRepository = repository,
            userId = userId,
            isDemo = isDemo
        )
    )

    UserProfileScreen(
        overviewViewModel = overviewViewModel,
        activityViewModel = activityViewModel,
        socialViewModel = socialViewModel,
        // Carries the tapped person, id and all. Not `username` from screen
        // state and never the signed-in viewer: that is how a grid of fifty faces
        // ends up opening one profile fifty times.
        onOpenUser = { person: FollowUser ->
            navController.navigate(ProfileRoutes.byId(person.id))
        },
        onNavigateBack = { navController.popBackStack() },
        isOwnProfile = isOwnProfile,
        onOpenTierList = { navController.navigate(AppRoutes.TIER_LIST) },
        libraryPreferences = libraryPreferences,
        onSignInAgain = onSignInAgain,
        initialTab = initialTab,
        // The same key the ViewModels are built on, handed to the screen so its saved
        // tab is scoped per profile too - see `UserProfileScreen`.
        viewModelKey = viewModelKey
    )
}