package com.anisequel.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.anisequel.data.model.FollowUser
import com.anisequel.data.repository.AniListRepository
import com.anisequel.domain.usecase.profileViewModelKey
import com.anisequel.ui.components.detail.RepositoryEntryEditor
import com.anisequel.ui.screens.profile.UserProfileScreen
import com.anisequel.ui.viewmodel.MediaDetailHost
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
    const val BY_ID = "profile_id/{userId}?name={name}"

    /** The by-id route's account name, carried so the bar can be filled on frame one. */
    const val ID_NAME_ARG = "name"

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

    /**
     * The by-id route, always with the name query - even when it is empty.
     *
     * The name is what fills the top bar on the first frame: a follower's profile
     * used to read "Profile" for the length of a round trip because the route
     * carried only the number, and the person's name was in the row that was just
     * tapped. The query form is the same one [StudioRoutes.studio] uses, for the
     * same reason - a `composable()` route is matched *exactly*, so omitting
     * `?name=` finds no destination at all and the tap silently does nothing.
     */
    fun byId(userId: Int, name: String? = null) =
        "profile_id/$userId?name=${Uri.encode(name.orEmpty())}"
}

val ProfileUsernameArguments = listOf(navArgument("username") { type = NavType.StringType })
val ProfileIdArguments = listOf(
    navArgument("userId") { type = NavType.IntType },
    navArgument(ProfileRoutes.ID_NAME_ARG) {
        type = NavType.StringType
        defaultValue = ""
    }
)

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
    /**
     * The app's one on-demand media-detail store.
     *
     * Owned by the navigation beside the other app-scoped stores, because a title
     * opened here and the same title opened from the Library have to be one page: one
     * fetch, one cache, one merge rule. Held by the route instead, each profile would
     * build a store of its own and re-fetch a synopsis the Library already had -
     * which is exactly the "different in the profile" behaviour this replaced.
     */
    detailHost: MediaDetailHost,
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
     * The signed-in viewer's id, for gating comment management.
     *
     * Distinct from [userId], which is the profile being *read*: a reader manages
     * their own comments on a stranger's profile, and the two ids are different
     * people there. Null - signed out - removes the manage control from every
     * comment and leaves the thread itself untouched. Defaulted to null for the
     * same reason [isOwnProfile] defaults false: a route that forgets to say lands
     * on the side that cannot offer a write that would fail.
     */
    viewerId: Int? = null,
    initialTab: Int = 0
) {
    // Keyed on the username as well as the id: two profiles can be opened one
    // after the other from the same stack, and without the name in the key the
    // second would render the first one's bio and favourites. `profileViewModelKey`
    // is where that rule lives, because two follower profiles whose names arrive (or
    // do not arrive) the same way must still not share one set of ViewModels.
    val viewModelKey = remember(username, userId, isDemo) {
        profileViewModelKey(username = username, userId = userId, isDemo = isDemo)
    }

    val overviewViewModel: UserOverviewViewModel = viewModel(
        key = "$viewModelKey:overview",
        factory = UserOverviewViewModel.Factory(
            aniListRepository = repository,
            userId = userId,
            username = username,
            isDemo = isDemo,
            // Whose list this is decides whether it comes down with the profile.
            // The reader's own has to (the main bar's Library page has no way to ask
            // for it); a follower's waits for the tab that shows it.
            isOwnProfile = isOwnProfile
        )
    )

    val activityViewModel: UserActivityViewModel = viewModel(
        key = "$viewModelKey:activity",
        factory = UserActivityViewModel.Factory(
            aniListRepository = repository,
            userId = userId,
            // The account reading, not the profile being read. See [viewerId].
            viewerId = viewerId,
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
        // The screen owns the person-profile store, so it needs the repository to
        // fetch with. Passed rather than reached for on a ViewModel, which holds its
        // own privately.
        repository = repository,
        detailHost = detailHost,
        overviewViewModel = overviewViewModel,
        activityViewModel = activityViewModel,
        socialViewModel = socialViewModel,
        // Carries the tapped person, id and all. Not `username` from screen
        // state and never the signed-in viewer: that is how a grid of fifty faces
        // ends up opening one profile fifty times.
        onOpenUser = { person: FollowUser ->
            // Single-top, because a grid of faces is exactly where a thumb lands
            // twice: a repeated tap used to stack the same profile again, and since
            // every copy looks identical, Back appeared to be broken until the
            // duplicates were gone.
            //
            // The name travels with the id. The tapped row is the only place in the
            // app that has both, and without the name the bar of the profile it
            // opens reads "Profile" until the overview answers - and two follower
            // profiles then share a ViewModel key that differs only by a number.
            navController.navigateSingle(ProfileRoutes.byId(person.id, person.name))
        },
        onNavigateBack = { navController.popBackStack() },
        // The name the route was opened with, so the bar is not blank for the length
        // of a round trip for a person whose name the caller was holding. The
        // overview's own answer replaces it as soon as it lands.
        titleHint = username.takeIf { it.isNotBlank() },
        isOwnProfile = isOwnProfile,
        viewerId = viewerId,
        // The same sheet everywhere, so the same "My Entry" tab: the entry on it
        // is the reader's own for that media, never the profile owner's, which is
        // why a scanned profile gets it too and only a signed-out one does not.
        // Built here rather than inside the screen because this is the caller
        // that knows whether a session exists - [viewerId] is null exactly then.
        // Keyed on both inputs and not held in a bare `remember`: the sheet
        // re-loads the entry whenever the editor's identity changes, so a new
        // instance per recomposition would be one entry fetch per frame.
        entryEditor = remember(viewerId, repository) {
            viewerId?.let {
                RepositoryEntryEditor(repository = repository, viewerId = it)
            }
        },
        // The tier list is one of the bar's pages now, so the trophy takes the
        // same hop the bar's tier tab takes: `popUpTo` Home with the state saved,
        // rather than a push. A push left Back pointing at this profile while the
        // bar's own tab left it pointing at Home - the same page, two behaviours,
        // decided by which door the reader used.
        onOpenTierList = {
            navController.navigateToMainTab(MainTab.TIERS, signedIn = !isDemo)
        },
        // The reader is on a title that credits a studio; the studio page is a place
        // like any other, so it is pushed with the same single-top rule as every
        // other hop in the app.
        onOpenStudio = { studioId, name ->
            navController.navigateSingle(StudioRoutes.studio(studioId, name))
        },
        onSignInAgain = onSignInAgain,
        initialTab = initialTab,
        // The same key the ViewModels are built on, handed to the screen so its saved
        // tab is scoped per profile too - see `UserProfileScreen`.
        viewModelKey = viewModelKey
    )
}