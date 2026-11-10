package com.anisequel.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.anisequel.ui.screens.nova.NovaOtherScreen

/**
 * The new look's feature hub.
 *
 * Its own route rather than a sheet because it is a page in the reference this
 * layout follows, and because every card on it is a real destination the reader
 * should be able to come back from by the system gesture. It is deliberately not
 * a main-bar tab: the bar has four contended slots, and this is reached from the
 * one card on Home that names it.
 *
 * No data is loaded here. The cards are doors to pages that already own their
 * queries, which is what keeps opening the hub free even on AniList's rate limit.
 */
fun NavGraphBuilder.otherDestinations(
    navController: NavHostController,
    signedIn: Boolean
) {
    composable(AppRoutes.OTHER) {
        NovaOtherScreen(
            onBack = { navController.popBackStack() },
            onOpenRecommendations = { navController.popBackStack() },
            onOpenActivity = {
                navController.navigateSingle(ProfileRoutes.OWN_ACTIVITY)
            },
            onOpenProfile = {
                navController.navigateToMainTab(MainTab.PROFILE, signedIn = signedIn)
            },
            onOpenLibrary = {
                navController.navigateToMainTab(MainTab.LIBRARY, signedIn = signedIn)
            },
            onOpenTiers = {
                navController.navigateToMainTab(MainTab.TIERS, signedIn = signedIn)
            },
            onOpenSettings = { navController.navigateSingle(AppRoutes.SETTINGS) }
        )
    }
}
