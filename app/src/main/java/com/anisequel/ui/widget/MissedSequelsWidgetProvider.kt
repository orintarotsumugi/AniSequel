package com.anisequel.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import com.anisequel.MainActivity
import com.anisequel.R

/**
 * Widget displaying the count of missed franchise sequels, the top one's poster
 * and title, and the viewer's handle.
 *
 * Tapping it opens AniSequel directly onto the main dashboard.
 *
 * `onReceive` and the update path are shared with the other three providers via
 * [WidgetUpdater]; see [AniSequelWidgetProvider] for why `goAsync()` matters.
 */
class MissedSequelsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        WidgetUpdater.updateWidgets(context, appWidgetManager, appWidgetIds) { c, cover ->
            renderWidget(c, cover)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != AppWidgetManager.ACTION_APPWIDGET_UPDATE) return

        val manager = AppWidgetManager.getInstance(context)
        val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
            ?: manager.getAppWidgetIds(ComponentName(context, MissedSequelsWidgetProvider::class.java))
        if (ids.isEmpty()) return

        val pendingResult = goAsync()
        WidgetUpdater.refreshCover(
            context = context,
            manager = manager,
            ids = ids,
            coverWidthDp = COVER_WIDTH_DP,
            coverHeightDp = COVER_HEIGHT_DP,
            coverUrl = { AniSequelWidgetData.topMissedCover(context) },
            render = { c, cover -> renderWidget(c, cover) },
            onDone = { pendingResult.finish() }
        )
    }

    companion object {
        private const val COVER_WIDTH_DP = 48
        private const val COVER_HEIGHT_DP = 64

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, MissedSequelsWidgetProvider::class.java)
            )
            WidgetUpdater.updateAll(
                context = context,
                manager = manager,
                ids = ids,
                coverWidthDp = COVER_WIDTH_DP,
                coverHeightDp = COVER_HEIGHT_DP,
                coverUrl = { AniSequelWidgetData.topMissedCover(context) },
                render = { c, cover -> renderWidget(c, cover) }
            )
        }

        internal fun renderWidget(context: Context, cover: Bitmap?): RemoteViews {
            val count = AniSequelWidgetData.missedSequelsCount(context)
            val title = AniSequelWidgetData.topMissedTitle(context)
            val username = AniSequelWidgetData.username(context)

            val caption = context.getString(
                if (count == 0) R.string.widget_missed_caption_empty else R.string.widget_missed_caption
            )

            return RemoteViews(context.packageName, R.layout.widget_missed_sequels).apply {
                setTextViewText(R.id.widget_missed_count, count.toString())
                setTextViewText(R.id.widget_missed_caption, caption)
                // No top entry means the list is empty and there is nothing to
                // name. Showing the app name here read as a headline for a show
                // nobody is missing.
                setTextViewText(R.id.widget_missed_title, title.orEmpty())
                setViewVisibility(
                    R.id.widget_missed_title,
                    if (title.isNullOrBlank()) View.GONE else View.VISIBLE
                )
                setTextViewText(R.id.widget_missed_username, username?.let { "@$it" }.orEmpty())
                setViewVisibility(
                    R.id.widget_missed_username,
                    if (username.isNullOrBlank()) View.GONE else View.VISIBLE
                )
                setImageViewBitmap(
                    R.id.widget_missed_cover,
                    cover ?: WidgetCover.placeholder(
                        context,
                        AniSequelWidgetData.topMissedColor(context),
                        WidgetCover.dp(context, COVER_WIDTH_DP),
                        WidgetCover.dp(context, COVER_HEIGHT_DP)
                    )
                )
                setOnClickPendingIntent(R.id.widget_missed_root, openDashboardPendingIntent(context))
            }
        }

        private fun openDashboardPendingIntent(context: Context): PendingIntent {
            // No extra: the dashboard is already the start destination for a
            // signed-in session. There used to be an `EXTRA_WIDGET_OPEN_DASHBOARD`
            // here that nothing ever read, so it promised a routing decision that
            // did not exist.
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            return PendingIntent.getActivity(
                context,
                1,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
