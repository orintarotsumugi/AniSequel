package com.example.ui.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionUpdateAppWidget
import androidx.glance.appwidget.glanceAppWidget
import androidx.glance.appwidget.state.UpdateFailureException
import androidx.glance.appwidget.updateAll
import androidx.glance.appwidget.updateAppWidget
import com.example.ui.widget.glance.WidgetView

/**
 * Glance app widget that shows a compact snapshot of the viewer's recent list
 * activity and opens the app on the activity screen when tapped.
 *
 * The widget is updated by `AniSequelWidgetUpdater.updateAll` and by the system
 * on the periodic interval declared in `res/xml/widget_anisequel.xml`.
 */
object AniSequelWidgetProvider : GlanceAppWidget() {
    override val glanceRequest = glanceAppWidget {
        val widgetId = appWidgetId
        val state = loadState(widgetId)

        WidgetView(
            activityCount = state.activityCount,
            subtitle = state.subtitle,
            onClick = actionStartActivity(
                intent = Intent().apply {
                    // The hosting activity is expected to route this to the
                    // profile activity screen on resume.
                    setClass(context, com.example.MainActivity::class.java)
                    putExtra(EXTRA_WIDGET_OPEN_ACTIVITY, true)
                }
            )
        )
    }

    private fun loadState(widgetId: Int): WidgetState {
        val context = context
        val glanceContext = androidx.glance.context.GlanceContext.getGlanceContext(context)
        val stateStore = glanceContext.appWidgetState[widgetId]
        val countRaw = stateStore?.getString(AndroidWidgetKeys.ACTIVITY_COUNT_KEY)
        val subtitleRaw = stateStore?.getString(AndroidWidgetKeys.SUBTITLE_KEY)
        return WidgetState(
            activityCount = countRaw?.toIntOrNull() ?: 0,
            subtitle = subtitleRaw ?: "Activity"
        )
    }

    fun updateWidget(
        context: Context,
        widgetId: Int,
        activityCount: Int,
        subtitle: String
    ) {
        try {
            updateAppWidgetState(context, widgetId) { state ->
                state.setString(AndroidWidgetKeys.ACTIVITY_COUNT_KEY, activityCount.toString())
                state.setString(AndroidWidgetKeys.SUBTITLE_KEY, subtitle)
            }
            updateAppWidget(context, widgetId, this)
        } catch (_: UpdateFailureException) {
            // Widget may have been deleted; the next update will recreate it.
        }
    }

    fun updateAllWidgets(context: Context) {
        updateAll(context, this)
    }

    /**
     * Convenience launcher used by the manifest receiver and by tests that need
     * to refresh every installed instance without touching the updater service.
     */
    fun updateOnReceive(context: Context, intent: Intent) {
        updateAllWidgets(context)
    }
}

/**
 * Keys used to store the per-instance widget state.
 *
 * Kept in a separate object so update and view code stay consistent.
 */
internal object AndroidWidgetKeys {
    const val ACTIVITY_COUNT_KEY = "activity_count"
    const val SUBTITLE_KEY = "subtitle"
}
