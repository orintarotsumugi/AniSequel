package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.data.model.FollowUser
import com.example.data.repository.AniListRepository
import com.example.ui.screens.profile.UserProfileScreen
import com.example.ui.viewmodel.UserActivityViewModel
import com.example.ui.viewmodel.UserOverviewViewModel
import com.example.ui.viewmodel.UserSocialViewModel

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
    onSignInAgain: () -> Unit
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
        onSignInAgain = onSignInAgain
    )
}