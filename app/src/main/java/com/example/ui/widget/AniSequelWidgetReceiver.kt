package com.example.ui.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.appwidget.AppWidgetManager
import com.example.ui.widget.AniSequelWidgetProvider.Companion.updateWidget

/**
 * Broadcast receiver for the profile-activity widget.
 *
 * The appwidget-provider XML registers this as the widget's lifecycle entry point.
 * On the system update broadcast, this seeds each instance with the demo activity
 * count until the app's updater runs again.
 */
class AniSequelWidgetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                updateWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }
}
