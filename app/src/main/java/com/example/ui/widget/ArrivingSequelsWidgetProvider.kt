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
 * Widget displaying the count of arriving soon and currently airing sequels.
 *
 * Tapping it opens AniSequel on the dashboard, where the arriving section sits
 * at the top.
 */
class ArrivingSequelsWidgetProvider : AppWidgetProvider() {

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
                ComponentName(context, ArrivingSequelsWidgetProvider::class.java)
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
            val count = AniSequelWidgetData.arrivingCount(context)

            val caption = context.getString(
                if (count == 0) R.string.widget_arriving_caption_empty else R.string.widget_arriving_caption
            )
            val subcaption = "Airing & upcoming"

            return RemoteViews(context.packageName, R.layout.widget_arriving_sequels).apply {
                setTextViewText(R.id.widget_arriving_count, count.toString())
                setTextViewText(R.id.widget_arriving_caption, caption)
                setTextViewText(R.id.widget_arriving_subcaption, subcaption)
                setOnClickPendingIntent(R.id.widget_arriving_root, openArrivingPendingIntent(context))
            }
        }

        private fun openArrivingPendingIntent(context: Context): PendingIntent {
            // No extra. This used to carry an `EXTRA_WIDGET_OPEN_ARRIVING` that
            // `MainActivity` never read, documented as opening "the arriving
            // section" - a deep link that does not exist. The dashboard is the
            // start destination and shows the arriving section at the top, so
            // opening the app is the behaviour the extra only pretended to add.
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            return PendingIntent.getActivity(
                context,
                2,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
