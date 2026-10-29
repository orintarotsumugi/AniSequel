package com.anisequel.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.anisequel.ui.screens.login.LoginScreen
import com.anisequel.ui.viewmodel.AuthUiState
import com.anisequel.ui.viewmodel.AuthViewModel

/**
 * The sign-in destination.
 *
 * Offers AniList OAuth sign-in, token paste, username scan (read-only), and the
 * offline demo fixture.
 */
fun NavGraphBuilder.authDestinations(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    authState: AuthUiState
) {
    composable(AppRoutes.LOGIN) {
        LoginScreen(
            authViewModel = authViewModel,
            authState = authState,
            onStartDemo = {
                navController.navigate(AppRoutes.DASHBOARD_DEMO)
            },
            onScanUsername = { username ->
                navController.navigateSingle(AppRoutes.userDashboard(username))
            }
        )
    }
}
