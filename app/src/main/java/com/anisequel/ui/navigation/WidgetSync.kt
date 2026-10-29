package com.anisequel.ui.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.anisequel.ui.viewmodel.DashboardUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Keeps the home-screen widgets in sync with the live dashboard counts.
 *
 * ## Why it is a composable of its own
 *
 * It used to be inlined in [AppNavigation]'s body, and that put a read of the
 * dashboard state in the app's root composition. `uiState` is replaced on every
 * recompute - including every character typed into the dashboard's search box -
 * so each keystroke recomposed the `NavHost`, the main bar and the joke toast to
 * write widget counts that had not changed. Nothing here draws anything, so the
 * whole job belongs in a scope of its own.
 *
 * ## Why the effect is keyed on the rendered values
 *
 * Keyed on the values the widgets actually render, not on the whole state
 * object: the state instance changes on every keystroke, so keying on it
 * re-rendered four widgets per character to write numbers that had not changed.
 * The viewer, the two counts, the recent-activity summary and the top entries
 * are the only inputs.
 *
 * The top arriving entry is a key of its own because the tiles render its cover
 * and title, and its size can be unchanged while the entry at the front of the
 * list changes - which would otherwise leave the previous poster on the tile.
 *
 * The avatar is the activity tile's artwork, so the tile repaints when it changes
 * even if nothing else about the viewer did.
 *
 * The update itself runs on [Dispatchers.Default]. It allocates placeholder
 * bitmaps and inflates four `RemoteViews`, and it used to do both on the main
 * thread after every dashboard publish - work for a launcher tile, in front of
 * the frame the reader was waiting for.
 */
@Composable
internal fun WidgetSync(
    dashboardFlow: StateFlow<DashboardUiState?>,
    context: Context
) {
    val dashboardState by dashboardFlow.collectAsState()
    val success = dashboardState as? DashboardUiState.Success
    val topMissedId = success?.missedSequels?.firstOrNull()?.sequelId
    val topArrivingId = success?.arriving?.firstOrNull()?.mediaId
    val avatarUrl = success?.viewer?.avatar?.large ?: success?.viewer?.avatar?.medium

    LaunchedEffect(
        success?.viewer?.id,
        success?.viewer?.name,
        success?.totalMissedCount,
        success?.arriving?.size,
        success?.recentActivity,
        topMissedId,
        topArrivingId,
        avatarUrl
    ) {
        val current = success ?: return@LaunchedEffect
        withContext(Dispatchers.Default) {
            val top = current.missedSequels.firstOrNull()
            val arriving = current.arriving.firstOrNull()
            com.anisequel.ui.widget.AniSequelWidgetData.updateData(
                context = context,
                missedCount = current.totalMissedCount,
                activityCount = current.recentActivity.count,
                arrivingCount = current.arriving.size,
                username = current.viewer.name,
                topMissedTitle = top?.sequelTitle,
                topMissedParent = top?.parentTitle,
                topMissedCover = top?.sequelCoverUrl,
                topMissedColor = top?.coverColor,
                arrivingTitle = arriving?.title,
                arrivingCover = arriving?.coverUrl,
                arrivingColor = arriving?.coverColor,
                activityTitle = current.recentActivity.latestTitle,
                activityArt = avatarUrl
            )
            com.anisequel.ui.widget.AniSequelWidgetProvider.updateAll(context)
            com.anisequel.ui.widget.MissedSequelsWidgetProvider.updateAll(context)
            com.anisequel.ui.widget.ArrivingSequelsWidgetProvider.updateAll(context)
            com.anisequel.ui.widget.FeaturedSequelWidgetProvider.updateAll(context)
        }
    }
}
