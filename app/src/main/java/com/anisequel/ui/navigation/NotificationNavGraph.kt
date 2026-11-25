package com.anisequel.ui.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.anisequel.data.repository.AniListRepository
import com.anisequel.data.repository.NotificationPreferencesStore
import com.anisequel.data.repository.NotificationReadStore
import com.anisequel.domain.notifications.InboxNotification
import com.anisequel.domain.notifications.NotificationTarget
import com.anisequel.ui.components.openExternalUrl
import com.anisequel.ui.screens.notifications.NotificationCenterScreen
import com.anisequel.ui.viewmodel.NotificationViewModel

/**
 * The inbox route and the taps it can produce.
 *
 * Media, thread and activity targets leave for AniList: this app has no
 * standalone detail destination - details are a sheet opened from the list that
 * already holds the media node - and fabricating a minimal node to open one would
 * draw a sheet with fields the inbox never fetched. People and messages stay
 * in-app because those destinations take an id, not a payload.
 */
fun NavGraphBuilder.notificationDestinations(
    navController: NavHostController,
    aniListRepository: AniListRepository,
    notificationPreferencesStore: NotificationPreferencesStore,
    notificationReadStore: NotificationReadStore
) {
    composable(AppRoutes.NOTIFICATIONS) {
        val context = LocalContext.current
        val viewModel: NotificationViewModel = viewModel(
            factory = NotificationViewModel.factory(
                aniListRepository = aniListRepository,
                preferencesStore = notificationPreferencesStore,
                readStore = notificationReadStore
            )
        )
        NotificationCenterScreen(
            viewModel = viewModel,
            onNavigateBack = { navController.popBackStack() },
            onOpenSettings = { navController.navigateSingle(AppRoutes.SETTINGS_NOTIFICATIONS) },
            onOpenNotification = { notification ->
                openNotification(navController, context, notification)
            }
        )
    }
}

private fun openNotification(
    navController: NavHostController,
    context: Context,
    notification: InboxNotification
) {
    when (val target = notification.target) {
        is NotificationTarget.Media -> {
            val kind = if (target.type.equals("MANGA", ignoreCase = true)) "manga" else "anime"
            openExternalUrl(context, "https://anilist.co/$kind/${target.mediaId}")
        }

        is NotificationTarget.User ->
            navController.navigateSingle(ProfileRoutes.byId(target.userId, target.name))

        is NotificationTarget.Message ->
            navController.navigateSingle(
                "${ProfileRoutes.OWN}?tab=${ProfileRoutes.TAB_ACTIVITY}&segment=${ProfileRoutes.SEGMENT_MESSAGES}"
            )

        is NotificationTarget.Thread -> {
            val anchor = target.commentId?.let { "#comment-$it" }.orEmpty()
            openExternalUrl(context, "https://anilist.co/forum/thread/${target.threadId}$anchor")
        }

        is NotificationTarget.Activity ->
            openExternalUrl(context, "https://anilist.co/activity/${target.activityId}")

        NotificationTarget.None -> Unit
    }
}
