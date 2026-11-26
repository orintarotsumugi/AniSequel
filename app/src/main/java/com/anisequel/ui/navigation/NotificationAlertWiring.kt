package com.anisequel.ui.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.anisequel.data.notifications.NotificationAlertStore
import com.anisequel.data.notifications.NotificationAlerts
import com.anisequel.data.notifications.NotificationAlertsScheduler
import com.anisequel.data.repository.NotificationPreferencesStore
import com.anisequel.data.repository.NotificationReadStore
import com.anisequel.domain.notifications.NotificationAlertPreferences
import com.anisequel.domain.notifications.NotificationDestinations
import com.anisequel.ui.viewmodel.AuthUiState
import kotlinx.coroutines.flow.first

/**
 * Keeps the background schedule in step with the session and the switches.
 *
 * Three things need to happen outside the Settings page, because the reader can
 * sign out, switch account or have a stored session expire without ever opening
 * that page:
 *
 *  - a signed-in session with the master switch on has exactly one periodic
 *    request, updated when the interval changes;
 *  - signing out cancels it and clears the tray, so the next person to pick up
 *    the phone cannot tap an alert addressed to the previous account;
 *  - switching account clears the previous account's tray. The cursor is keyed
 *    by account, so the new account starts from its own baseline rather than the
 *    previous one's watermark.
 *
 * Nothing is scheduled until both halves are true. Scheduling on the switch
 * alone would make WorkManager wake a device with no token every half hour and
 * fail its way into backoff.
 */
@Composable
internal fun NotificationAlertSchedulingEffect(
    context: Context,
    store: NotificationAlertStore,
    authState: AuthUiState,
    viewerId: Int?
) {
    val settings by store.preferences.collectAsStateWithLifecycle(
        initialValue = NotificationAlertPreferences()
    )
    // Remembered across recompositions so the effect can tell a first sign-in
    // (nothing to clear) from a switch (the old tray must go).
    var lastAccount by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(authState, viewerId, settings.enabled, settings.intervalMinutes) {
        // Only an actual sign-out cancels and clears. `Authenticated` with a null
        // viewer is the dashboard still loading on a cold start, and treating
        // that as signed-out wiped the tray every time the app was opened.
        when (authState) {
            is AuthUiState.Unauthenticated -> {
                lastAccount = null
                NotificationAlertsScheduler.cancel(context)
                NotificationAlerts.cancelAll(context)
                return@LaunchedEffect
            }

            is AuthUiState.Authenticated -> Unit

            else -> return@LaunchedEffect
        }
        val account = viewerId ?: return@LaunchedEffect
        store.recordActiveAccount(account)
        if (lastAccount != null && lastAccount != account) {
            NotificationAlerts.cancelAll(context)
        }
        lastAccount = account
        if (settings.enabled) {
            NotificationAlertsScheduler.ensureScheduled(context, settings.intervalMinutes)
        } else {
            NotificationAlertsScheduler.cancel(context)
        }
    }
}

/**
 * Opens a destination carried by a system-notification tap.
 *
 * Deferred until the session and the viewer are known for the same reason the
 * widget tap is: the `PendingIntent` can start the Activity before the stored
 * token has been read, and a media or thread destination fetched without a
 * viewer would render a demo fallback or a 401 for a row the reader owns.
 *
 * The route is validated first. `MainActivity` is exported, so the extra can
 * arrive from any app; [NotificationDestinations.isSafe] is the allow-list, and
 * an unrecognised value is consumed silently rather than pushed onto the back
 * stack.
 *
 * Marking the group read happens here rather than in the Activity because this
 * is where the read store and the preference that gates it already live. It
 * respects `markReadOnOpen`: a reader who asked notification taps not to clear
 * their inbox keeps the dot.
 */
internal suspend fun deliverNotificationRouteOnce(
    navController: NavHostController,
    route: String,
    notificationIds: IntArray?,
    viewerId: Int?,
    readStore: NotificationReadStore,
    preferencesStore: NotificationPreferencesStore
) {
    if (!NotificationDestinations.isSafe(route)) return
    navController.navigateSingle(route)
    val ids = notificationIds?.filter { it > 0 }.orEmpty()
    val account = viewerId ?: return
    if (ids.isEmpty()) return
    val preferences = preferencesStore.preferences.first()
    if (!preferences.trackingEnabled || !preferences.markReadOnOpen) return
    readStore.markRead(account, ids)
}
