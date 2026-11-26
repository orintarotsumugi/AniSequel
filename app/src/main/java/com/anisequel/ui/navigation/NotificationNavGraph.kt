package com.anisequel.ui.navigation

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.anisequel.data.model.ViewerProfile
import com.anisequel.data.repository.AniListRepository
import com.anisequel.data.repository.NotificationPreferencesStore
import com.anisequel.data.repository.NotificationReadStore
import com.anisequel.domain.notifications.InboxNotification
import com.anisequel.domain.notifications.NotificationTarget
import com.anisequel.ui.components.detail.RepositoryEntryEditor
import com.anisequel.ui.screens.notifications.NotificationActivityScreen
import com.anisequel.ui.screens.notifications.NotificationCenterScreen
import com.anisequel.ui.screens.notifications.NotificationMediaScreen
import com.anisequel.ui.screens.notifications.NotificationThreadScreen
import com.anisequel.ui.viewmodel.MediaDetailHost
import com.anisequel.ui.viewmodel.NotificationViewModel

/**
 * The inbox and every destination its rows can open.
 *
 * Every target kind stays in the app now. Media, activity and thread used to
 * leave for `anilist.co` because there was no standalone destination for them -
 * details were a sheet drawn over the list that already had the node - and the
 * inbox had only an id. That was a limitation of the route, not of the data: the
 * repository can fetch a title by id, an activity's root, and a thread's post and
 * comments. Fabricating a minimal node is what would have been dishonest, so each
 * destination fetches the real payload before it draws.
 *
 * A media target keeps the same `RepositoryEntryEditor` the Library uses, so the
 * sheet opened from a notification can still edit the viewer's entry. An activity
 * opens the real profile Activity feed at the named post rather than a standalone
 * comment sheet: the reader lands in the list the post belongs to, and only a post
 * older than the loaded pages falls back to that post's thread. A thread is
 * read-only: it has no reply destination of its own yet, and a dead reply box would
 * be worse than none.
 */
fun NavGraphBuilder.notificationDestinations(
    navController: NavHostController,
    aniListRepository: AniListRepository,
    notificationPreferencesStore: NotificationPreferencesStore,
    notificationReadStore: NotificationReadStore,
    signedInViewer: ViewerProfile?,
    entryEditor: RepositoryEntryEditor?,
    threadSeedStore: NotificationThreadSeedStore,
    detailHost: MediaDetailHost,
    onSignInAgain: () -> Unit
) {
    composable(AppRoutes.NOTIFICATIONS) {
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
                openNotification(navController, notification, threadSeedStore)
            }
        )
    }

    composable(
        route = AppRoutes.NOTIFICATION_MEDIA,
        arguments = listOf(
            navArgument(AppRoutes.NOTIFICATION_MEDIA_ID_ARG) { type = NavType.IntType },
            navArgument(AppRoutes.NOTIFICATION_MEDIA_TYPE_ARG) {
                type = NavType.StringType
                defaultValue = ""
            }
        )
    ) { entry ->
        val mediaId = entry.arguments?.getInt(AppRoutes.NOTIFICATION_MEDIA_ID_ARG) ?: 0
        val mediaType = entry.arguments
            ?.getString(AppRoutes.NOTIFICATION_MEDIA_TYPE_ARG)
            ?.takeIf { it.isNotBlank() }
        NotificationMediaScreen(
            mediaId = mediaId,
            mediaType = mediaType,
            repository = aniListRepository,
            entryEditor = entryEditor,
            onBack = { navController.popBackStack() },
            onOpenMedia = { id, type ->
                navController.navigateSingle(AppRoutes.notificationMedia(id, type))
            },
            onOpenStudio = { id, name ->
                navController.navigateSingle(StudioRoutes.studio(id, name))
            }
        )
    }

    composable(
        route = AppRoutes.NOTIFICATION_ACTIVITY,
        arguments = listOf(
            navArgument(AppRoutes.NOTIFICATION_ACTIVITY_ID_ARG) { type = NavType.IntType }
        )
    ) { entry ->
        val activityId = entry.arguments?.getInt(AppRoutes.NOTIFICATION_ACTIVITY_ID_ARG) ?: 0
        val viewer = signedInViewer
        if (viewer == null) {
            // A restored route can be composed for one frame before the session
            // ViewModel has answered. Keep the old standalone reader as that
            // bridge; the profile feed replaces it as soon as the viewer lands.
            NotificationActivityScreen(
                activityId = activityId,
                viewerId = null,
                repository = aniListRepository,
                onBack = { navController.popBackStack() },
                onOpenUser = { id, name ->
                    navController.navigateSingle(ProfileRoutes.byId(id, name))
                }
            )
        } else {
            ProfileDestination(
                repository = aniListRepository,
                detailHost = detailHost,
                username = viewer.name,
                userId = viewer.id,
                isDemo = false,
                navController = navController,
                onSignInAgain = onSignInAgain,
                isOwnProfile = true,
                viewerId = viewer.id,
                initialTab = ProfileRoutes.ACTIVITY_TAB_INDEX,
                initialActivityId = activityId.takeIf { it > 0 }
            )
        }
    }

    composable(
        route = AppRoutes.NOTIFICATION_THREAD,
        arguments = listOf(
            navArgument(AppRoutes.NOTIFICATION_THREAD_ID_ARG) { type = NavType.IntType },
            navArgument(AppRoutes.NOTIFICATION_THREAD_COMMENT_ARG) {
                type = NavType.IntType
                defaultValue = -1
            }
        )
    ) { entry ->
        val threadId = entry.arguments?.getInt(AppRoutes.NOTIFICATION_THREAD_ID_ARG) ?: 0
        val commentId = entry.arguments
            ?.getInt(AppRoutes.NOTIFICATION_THREAD_COMMENT_ARG)
            ?.takeIf { it > 0 }
        NotificationThreadScreen(
            threadId = threadId,
            commentId = commentId,
            repository = aniListRepository,
            seedComment = threadSeedStore.find(threadId, commentId),
            onBack = { navController.popBackStack() }
        )
    }
}

private fun openNotification(
    navController: NavHostController,
    notification: InboxNotification,
    threadSeedStore: NotificationThreadSeedStore
) {
    when (val target = notification.target) {
        is NotificationTarget.Media ->
            navController.navigateSingle(
                AppRoutes.notificationMedia(target.mediaId, target.type)
            )

        is NotificationTarget.User ->
            navController.navigateSingle(ProfileRoutes.byId(target.userId, target.name))

        is NotificationTarget.Message ->
            navController.navigateSingle(
                "${ProfileRoutes.OWN}?tab=${ProfileRoutes.TAB_ACTIVITY}&segment=${ProfileRoutes.SEGMENT_MESSAGES}"
            )

        is NotificationTarget.Thread -> {
            threadSeedStore.record(target)
            navController.navigateSingle(
                AppRoutes.notificationThread(target.threadId, target.commentId)
            )
        }

        is NotificationTarget.Activity ->
            navController.navigateSingle(
                AppRoutes.notificationActivity(target.activityId)
            )

        NotificationTarget.None -> Unit
    }
}
