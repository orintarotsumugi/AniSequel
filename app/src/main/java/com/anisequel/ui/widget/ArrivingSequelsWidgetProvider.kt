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
 * Widget displaying the count of arriving soon and currently airing sequels,
 * the first one's poster and title, and the viewer's handle.
 *
 * Tapping it opens AniSequel on the dashboard, where the arriving section sits
 * at the top.
 *
 * `onReceive` and the update path are shared with the other three providers via
 * [WidgetUpdater]; see [AniSequelWidgetProvider] for why `goAsync()` matters.
 */
class ArrivingSequelsWidgetProvider : AppWidgetProvider() {

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
            ?: manager.getAppWidgetIds(ComponentName(context, ArrivingSequelsWidgetProvider::class.java))
        if (ids.isEmpty()) return

        val pendingResult = goAsync()
        WidgetUpdater.refreshCover(
            context = context,
            manager = manager,
            ids = ids,
            coverWidthDp = COVER_WIDTH_DP,
            coverHeightDp = COVER_HEIGHT_DP,
            coverUrl = { AniSequelWidgetData.arrivingCover(context) },
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
                ComponentName(context, ArrivingSequelsWidgetProvider::class.java)
            )
            WidgetUpdater.updateAll(
                context = context,
                manager = manager,
                ids = ids,
                coverWidthDp = COVER_WIDTH_DP,
                coverHeightDp = COVER_HEIGHT_DP,
                coverUrl = { AniSequelWidgetData.arrivingCover(context) },
                render = { c, cover -> renderWidget(c, cover) }
            )
        }

        internal fun renderWidget(context: Context, cover: Bitmap?): RemoteViews {
            val count = AniSequelWidgetData.arrivingCount(context)
            val title = AniSequelWidgetData.arrivingTitle(context)
            val username = AniSequelWidgetData.username(context)

            val caption = context.getString(
                if (count == 0) R.string.widget_arriving_caption_empty else R.string.widget_arriving_caption
            )

            return RemoteViews(context.packageName, R.layout.widget_arriving_sequels).apply {
                setTextViewText(R.id.widget_arriving_count, count.toString())
                setTextViewText(R.id.widget_arriving_caption, caption)
                // Nothing arriving means no entry to name. The app name here would
                // read as a headline for a show that is not coming.
                setTextViewText(R.id.widget_arriving_title, title.orEmpty())
                setViewVisibility(
                    R.id.widget_arriving_title,
                    if (title.isNullOrBlank()) View.GONE else View.VISIBLE
                )
                setTextViewText(R.id.widget_arriving_username, username?.let { "@$it" }.orEmpty())
                setViewVisibility(
                    R.id.widget_arriving_username,
                    if (username.isNullOrBlank()) View.GONE else View.VISIBLE
                )
                setImageViewBitmap(
                    R.id.widget_arriving_cover,
                    cover ?: WidgetCover.placeholder(
                        context,
                        AniSequelWidgetData.arrivingColor(context),
                        WidgetCover.dp(context, COVER_WIDTH_DP),
                        WidgetCover.dp(context, COVER_HEIGHT_DP)
                    )
                )
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
