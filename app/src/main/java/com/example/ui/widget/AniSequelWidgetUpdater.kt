package com.example.ui.widget

import android.content.Context
import android.appwidget.AppWidgetManager
import com.example.ui.widget.AniSequelWidgetProvider

/**
 * Background updater for the profile-activity widget.
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
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            android.content.ComponentName(context, AniSequelWidgetProvider::class.java)
        )
        ids.forEach { widgetId ->
            AniSequelWidgetProvider.updateWidget(context, manager, widgetId)
        }
    }
}
