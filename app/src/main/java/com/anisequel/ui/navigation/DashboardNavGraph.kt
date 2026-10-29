package com.anisequel.ui.navigation

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.anisequel.data.repository.AniListRepository
import com.anisequel.data.repository.ArrivingPreferences
import com.anisequel.data.repository.HiddenSequelsPreferences
import com.anisequel.data.repository.QuickAddPreferences
import com.anisequel.data.repository.RefreshIntervalPreferences
import com.anisequel.ui.screens.dashboard.DashboardScreen
import com.anisequel.ui.viewmodel.DashboardViewModel

/**
 * Destinations for the three dashboard flavours:
 *
 *  - [AppRoutes.DASHBOARD]: the signed-in viewer's live sequel recommendations.
 *  - [AppRoutes.DASHBOARD_DEMO]: the offline demo fixture with bundled anime data.
 *  - [AppRoutes.DASHBOARD_USER]: a public profile's sequel recommendations by username scan.
 */
fun NavGraphBuilder.dashboardDestinations(
    navController: NavHostController,
    aniListRepository: AniListRepository,
    mainDashboardViewModel: DashboardViewModel?,
    quickAddPreferences: QuickAddPreferences,
    hiddenSequelsPreferences: HiddenSequelsPreferences,
    refreshIntervalPreferences: RefreshIntervalPreferences,
    arrivingPreferences: ArrivingPreferences,
    onSignInAgain: () -> Unit
) {
    composable(AppRoutes.DASHBOARD) {
        // Null until a session exists; the route is only reachable once one
        // does, and a brief hold beats rendering a dashboard that cannot
        // possibly have a token behind it.
        mainDashboardViewModel?.let { dashboardViewModel ->
            DashboardScreen(
                dashboardViewModel = dashboardViewModel,
                quickAddPreferences = quickAddPreferences,
                // The cast and crew rows open a profile sheet in-app. Null on the
                // demo route below, which is why this is passed rather than read
                // off a ViewModel: the demo deliberately has none.
                repository = aniListRepository,
                onSignInAgain = onSignInAgain,
                onOpenSettings = {
                    navController.navigateSingle(AppRoutes.SETTINGS)
                },
                // Guarded on the viewer existing rather than on the route:
                // the button only appears on a loaded dashboard, and an
                // avatar that can be tapped before there is a name to open
                // is a route that would have to look one up - which is the
                // request this wiring exists to avoid.
                //
                // The same hop the main bar's Profile tab makes: both ways
                // into the viewer's own profile go through
                // `navigateToMainTab`, so the two cannot disagree about
                // the route - or about what Back does from it.
                onOpenProfile = { viewer ->
                    if (viewer != null) {
                        navController.navigateToMainTab(MainTab.PROFILE, signedIn = true)
                    }
                },
                onOpenStudio = { studioId, studioName ->
                    navController.navigateSingle(StudioRoutes.studio(studioId, studioName))
                }
            )
        }
    }

    composable(AppRoutes.DASHBOARD_DEMO) {
        val demoViewModel: DashboardViewModel = viewModel(
            factory = DashboardViewModel.Factory(aniListRepository, isDemo = true)
        )

        DashboardScreen(
            dashboardViewModel = demoViewModel,
            quickAddPreferences = quickAddPreferences,
            onSignInAgain = {
                navController.navigate(AppRoutes.LOGIN) {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
            },
            onOpenSettings = { navController.navigateSingle(AppRoutes.SETTINGS) },
            onOpenStudio = { studioId, studioName ->
                navController.navigateSingle(StudioRoutes.studio(studioId, studioName))
            },
            // The profile is a tab in the demo too: it stacks on the
            // demo dashboard so its back arrow returns Home. See
            // `navigateToMainTab`.
            onOpenProfile = {
                navController.navigateToMainTab(MainTab.PROFILE, signedIn = false)
            }
        )
    }

    composable(
        route = AppRoutes.DASHBOARD_USER,
        arguments = listOf(navArgument("username") { type = NavType.StringType })
    ) { backStackEntry ->
        val username = backStackEntry.arguments?.getString("username").orEmpty()

        // Keyed on the username so switching profiles cannot reuse the
        // ViewModel that is still holding the previous user's list.
        val userViewModel: DashboardViewModel = viewModel(
            key = "dashboard_user_$username",
            factory = DashboardViewModel.Factory(
                aniListRepository = aniListRepository,
                targetUsername = username,
                hiddenSequelsPreferences = hiddenSequelsPreferences,
                refreshIntervalPreferences = refreshIntervalPreferences,
                arrivingPreferences = arrivingPreferences
            )
        )

        DashboardScreen(
            dashboardViewModel = userViewModel,
            quickAddPreferences = quickAddPreferences,
            repository = aniListRepository,
            onSignInAgain = {
                navController.navigate(AppRoutes.LOGIN) {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
            },
            onOpenSettings = { navController.navigateSingle(AppRoutes.SETTINGS) },
            // The scanned person, not the viewer. Passing the viewer's name
            // here would have opened the user's own profile from a dashboard
            // that is showing somebody else's list - which looks exactly
            // right on the top bar and is completely wrong.
            onOpenProfile = {
                if (username.isNotBlank()) {
                    navController.navigateSingle(ProfileRoutes.byUsername(username))
                }
            },
            onOpenStudio = { studioId, studioName ->
                navController.navigateSingle(StudioRoutes.studio(studioId, studioName))
            }
        )
    }
}
