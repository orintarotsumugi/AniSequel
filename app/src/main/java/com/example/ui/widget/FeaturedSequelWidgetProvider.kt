package com.example.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R

/**
 * The "Featured Sequel" widget: the top missed sequel as a large poster.
 *
 * The three original tiles each answer a count at a glance; this one gives the
 * single most relevant recommendation room to breathe - the poster, what it is a
 * sequel *to*, and how many are waiting - so a user can decide whether to open
 * the app from the home screen.
 *
 * `onReceive` is overridden for the same reason as
 * [AniSequelWidgetProvider]'s: the poster is fetched on a background thread kept
 * alive by `goAsync()` while `super.onReceive` draws the colour placeholder
 * first. With no cover stored it stays on that placeholder and the title, which
 * is the intended look rather than a blank tile.
 */
class FeaturedSequelWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != AppWidgetManager.ACTION_APPWIDGET_UPDATE) return

        val manager = AppWidgetManager.getInstance(context)
        val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
            ?: manager.getAppWidgetIds(ComponentName(context, FeaturedSequelWidgetProvider::class.java))
        if (ids.isEmpty()) return

        val pendingResult = goAsync()
        loadCover(context, manager, ids) { pendingResult.finish() }
    }

    companion object {
        private const val COVER_WIDTH_DP = 88
        private const val COVER_HEIGHT_DP = 120

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, FeaturedSequelWidgetProvider::class.java)
            )
            updateWidgets(context, manager, ids)
            loadCover(context, manager, ids) {}
        }

        private fun updateWidgets(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray
        ) {
            if (ids.isEmpty()) return
            manager.updateAppWidget(ids, renderWidget(context, null))
        }

        private fun loadCover(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray,
            onDone: () -> Unit
        ) {
            if (ids.isEmpty()) {
                onDone()
                return
            }

            WidgetCover.loadAsync(
                url = AniSequelWidgetData.topMissedCover(context),
                targetWidthPx = WidgetCover.dp(context, COVER_WIDTH_DP),
                targetHeightPx = WidgetCover.dp(context, COVER_HEIGHT_DP)
            ) { bitmap ->
                try {
                    manager.updateAppWidget(ids, renderWidget(context, bitmap))
                } catch (t: Throwable) {
                    // Never let a widget update take the process down.
                } finally {
                    onDone()
                }
            }
        }

        private fun renderWidget(context: Context, cover: Bitmap?): RemoteViews {
            val count = AniSequelWidgetData.missedSequelsCount(context)
            val title = AniSequelWidgetData.topMissedTitle(context)
            val parent = AniSequelWidgetData.topMissedParent(context)
            val username = AniSequelWidgetData.username(context)

            val caption = context.getString(
                if (count == 0) R.string.widget_featured_caption_empty else R.string.widget_featured_caption
            )
            val parentLine = if (!parent.isNullOrBlank()) {
                context.getString(R.string.widget_featured_sequel_to, parent)
            } else {
                context.getString(R.string.widget_featured_parent_initial)
            }

            return RemoteViews(context.packageName, R.layout.widget_featured_sequel).apply {
                setTextViewText(
                    R.id.widget_featured_title,
                    title?.takeIf { it.isNotBlank() } ?: context.getString(R.string.widget_featured_title_initial)
                )
                setTextViewText(R.id.widget_featured_parent, parentLine)
                setTextViewText(R.id.widget_featured_count, count.toString())
                setTextViewText(R.id.widget_featured_caption, caption)
                setTextViewText(R.id.widget_featured_username, username?.let { "@$it" }.orEmpty())
                setViewVisibility(
                    R.id.widget_featured_username,
                    if (username.isNullOrBlank()) View.GONE else View.VISIBLE
                )
                setImageViewBitmap(
                    R.id.widget_featured_cover,
                    cover ?: WidgetCover.roundedPlaceholder(
                        context,
                        AniSequelWidgetData.topMissedColor(context),
                        WidgetCover.dp(context, COVER_WIDTH_DP),
                        WidgetCover.dp(context, COVER_HEIGHT_DP)
                    )
                )
                setOnClickPendingIntent(R.id.widget_featured_root, openDashboardPendingIntent(context))
            }
        }

        /**
         * Request code 3 - one more than the three older widgets, so this
         * `PendingIntent` is distinct from theirs even though it points at the
         * same dashboard destination.
         */
        private fun openDashboardPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            return PendingIntent.getActivity(
                context,
                3,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
