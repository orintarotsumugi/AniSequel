package com.example.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R

/**
 * Widget displaying the count of missed franchise sequels.
 *
 * Tapping it opens AniSequel directly onto the main dashboard.
 */
class MissedSequelsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    companion object {

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, MissedSequelsWidgetProvider::class.java)
            )
            updateWidgets(context, manager, ids)
        }

        private fun updateWidgets(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray
        ) {
            if (ids.isEmpty()) return
            manager.updateAppWidget(ids, renderWidget(context))
        }

        private fun renderWidget(context: Context): RemoteViews {
            val count = AniSequelWidgetData.missedSequelsCount(context)
            val topTitle = AniSequelWidgetData.topMissedTitle(context)

            val caption = context.getString(
                if (count == 0) R.string.widget_missed_caption_empty else R.string.widget_missed_caption
            )
            val subcaption = if (!topTitle.isNullOrBlank()) {
                "Top: $topTitle"
            } else {
                context.getString(R.string.app_name)
            }

            return RemoteViews(context.packageName, R.layout.widget_missed_sequels).apply {
                setTextViewText(R.id.widget_missed_count, count.toString())
                setTextViewText(R.id.widget_missed_caption, caption)
                setTextViewText(R.id.widget_missed_subcaption, subcaption)
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
