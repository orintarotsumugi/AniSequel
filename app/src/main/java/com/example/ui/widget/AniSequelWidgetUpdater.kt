package com.example.ui.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.UpdateFailureException
import androidx.glance.appwidget.updateAll
import com.example.ui.widget.glance.widgetActivityCount

/**
 * Background updater for the AniSequel profile-activity widget.
 *
 * The widget shows a compact summary of the viewer's recent list activity.
 * Because the widget process cannot use the app's repository or auth layer
 * directly, the widget updater prefers the last known activity snapshot that
 * the app already provides via `DemoProfileProvider.getDemoActivity` for the demo
 * build, and otherwise falls back to zero.
 *
 * In a real release this would reach into the same `AniListRepository` state the
 * app already holds; for now it keeps the widget non-empty on the demo APK so the
 * widget is visible without an auth session.
 */
object AniSequelWidgetUpdater {
    fun updateAll(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        val instanceIds = manager.getGlanceIds(AniSequelWidgetProvider::class.java)

        val activityCount = widgetActivityCount()
        val subtitle = "list activity"

        instanceIds.forEach { widgetId ->
            AniSequelWidgetProvider.updateWidget(
                context = context,
                widgetId = widgetId,
                activityCount = activityCount,
                subtitle = subtitle
            )
        }
    }
}
