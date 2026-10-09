package com.example.ui.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import com.example.MainActivity
import com.example.R

/**
 * Standard AppWidget provider for the profile-activity widget.
 *
 * The widget is small and glanceable: a title, a count of recent list activity,
 * and a tap target that routes the app to the profile activity screen.
 *
 * This uses the stable android.appwidget framework APIs instead of the
 * unreleased Glance widget APIs, so it compiles against the app's current
 * dependency set.
 */
class AniSequelWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onDisabled(context: Context) {
        // Nothing to tear down; the next re-enable will recreate instances.
    }

    companion object {
        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_anisequel_preview).apply {
                setTextViewText(R.id.widget_preview_count, demoActivityCount().toString())
                setOnClickPendingIntent(
                    R.id.widget_preview_root,
                    PendingIntent.getActivity(
                        context,
                        widgetId,
                        Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra(MainActivity.EXTRA_WIDGET_OPEN_ACTIVITY, true)
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }

            appWidgetManager.updateAppWidget(widgetId, views)
        }

        private fun demoActivityCount(): Int =
            com.example.data.repository.DemoProfileProvider.getDemoActivity().size
    }
}
