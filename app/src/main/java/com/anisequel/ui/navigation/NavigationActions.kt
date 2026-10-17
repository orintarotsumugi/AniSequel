package com.anisequel.ui.navigation

import androidx.navigation.NavHostController

/**
 * Go to [route], but only once, however many times the button is pressed.
 *
 * Every screen-to-screen hop in this app is a plain `navigate(route)`, and
 * `NavController` happily pushes the *same* destination again for each call. A
 * finger that lands three times on the dashboard's avatar, on the gear, or on a
 * face in a profile's Social tab therefore left three profiles on the back stack:
 * three copies of the same screen to swipe away one at a time, three sets of
 * ViewModels held alive behind them, and - on a profile - three independent scroll
 * positions that all look identical, so "Back" appears to do nothing until the
 * stack runs out. It is not a crash, which is why it survived: it just feels
 * broken.
 *
 * `launchSingleTop` is the whole fix: if the destination is already on top, the
 * call is a no-op instead of a push. Destinations that need a *different* stack
 * shape (the demo dashboard, sign-in) pass their own `popUpTo` and are left alone -
 * this helper is for the plain "open a screen" cases.
 *
 * A double tap that arrived *while* the first transition is running is the same
 * bug one frame later, so callers must not route around this with a raw
 * `navigate`.
 */
internal fun NavHostController.navigateSingle(route: String) {
    navigate(route) { launchSingleTop = true }
}
