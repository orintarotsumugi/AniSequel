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
 * The profile-activity widget.
 *
 * A 2x2 tile that answers one question - how much has been logged recently -
 * with the viewer's avatar, their handle and the latest entry's title, and,
 * when tapped, opens the app straight on the profile's Activity tab.
 *
 * ## It is the receiver
 *
 * This class is what the manifest registers. There used to be a second
 * `AniSequelWidgetReceiver` in front of it that read `EXTRA_APPWIDGET_ID` off the
 * update broadcast; the system sends the list as `EXTRA_APPWIDGET_IDS`, so the
 * extra was missing and no update was ever applied to a freshly added widget.
 * `AppWidgetProvider` already unpacks both forms, so extending it is the whole
 * fix - there is no forwarding class left to get it wrong.
 *
 * ## Updates are batched
 *
 * Every instance shows the same thing, so one `RemoteViews` is built per update
 * pass and handed to `updateAppWidget(int[], RemoteViews)` for all of them:
 * one layout inflation and one binder transaction, however many copies of the
 * widget are on the home screen, instead of a full rebuild per instance. Reading
 * the data source once per pass is the other half of that - see
 * [AniSequelWidgetData].
 *
 * ## Why `onReceive` is overridden
 *
 * The cover is a network fetch. A broadcast receiver's process can be killed the
 * moment `onReceive` returns, so `goAsync()` keeps it alive until [WidgetCover]
 * has an answer. `super.onReceive` runs first so the existing `onUpdate` draws
 * the synchronous placeholder (counts, caption, the entry's dominant colour)
 * before the art arrives; the background thread then replaces it. Missing that
 * first paint is what used to leave a freshly placed tile blank.
 */
class AniSequelWidgetProvider : AppWidgetProvider() {

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
            ?: manager.getAppWidgetIds(ComponentName(context, AniSequelWidgetProvider::class.java))
        if (ids.isEmpty()) return

        val pendingResult = goAsync()
        WidgetUpdater.refreshCover(
            context = context,
            manager = manager,
            ids = ids,
            coverWidthDp = COVER_WIDTH_DP,
            coverHeightDp = COVER_HEIGHT_DP,
            coverUrl = { AniSequelWidgetData.activityArt(context) },
            render = { c, cover -> renderWidget(c, cover) },
            onDone = { pendingResult.finish() }
        )
    }

    companion object {
        private const val COVER_WIDTH_DP = 48
        private const val COVER_HEIGHT_DP = 64

        /**
         * Refreshes every placed instance of the widget.
         *
         * The system already ticks this provider through `updatePeriodMillis`,
         * so this exists for the app to call after something it knows has
         * changed the count - the platform cannot be told a number moved before
         * its next scheduled broadcast. It runs while the app's process is alive,
         * so the cover fetch needs no `goAsync()` here.
         */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, AniSequelWidgetProvider::class.java)
            )
            WidgetUpdater.updateAll(
                context = context,
                manager = manager,
                ids = ids,
                coverWidthDp = COVER_WIDTH_DP,
                coverHeightDp = COVER_HEIGHT_DP,
                coverUrl = { AniSequelWidgetData.activityArt(context) },
                render = { c, cover -> renderWidget(c, cover) }
            )
        }

        internal fun renderWidget(context: Context, cover: Bitmap?): RemoteViews {
            val count = AniSequelWidgetData.recentActivityCount(context)
            val title = AniSequelWidgetData.latestActivityTitle(context)
            val username = AniSequelWidgetData.username(context)

            val caption = context.getString(
                if (count == 0) R.string.widget_caption_empty else R.string.widget_caption
            )

            return RemoteViews(context.packageName, R.layout.widget_anisequel).apply {
                setTextViewText(R.id.widget_count, count.toString())
                setTextViewText(R.id.widget_caption, caption)
                // The title hides itself when there is no latest entry to name.
                // Falling back to the app name read as a headline naming a show
                // nobody could have watched, which is the last thing a tile that
                // is about *activity* should claim.
                setTextViewText(R.id.widget_title, title.orEmpty())
                setViewVisibility(
                    R.id.widget_title,
                    if (title.isNullOrBlank()) View.GONE else View.VISIBLE
                )
                setTextViewText(R.id.widget_username, username?.let { "@$it" }.orEmpty())
                setViewVisibility(
                    R.id.widget_username,
                    if (username.isNullOrBlank()) View.GONE else View.VISIBLE
                )
                // Activity art has no stored dominant colour, so the placeholder
                // is transparent and the layout's own neutral frame shows through.
                setImageViewBitmap(
                    R.id.widget_cover,
                    cover ?: WidgetCover.placeholder(
                        context,
                        null,
                        WidgetCover.dp(context, COVER_WIDTH_DP),
                        WidgetCover.dp(context, COVER_HEIGHT_DP)
                    )
                )
                setOnClickPendingIntent(R.id.widget_root, openActivityPendingIntent(context))
            }
        }

        /**
         * The tap target: the app, on the profile's Activity tab.
         *
         * One `PendingIntent` for every instance - request code 0, and an intent
         * with nothing instance-specific in it. Passing the widget id (as this
         * did) makes each instance's intent distinct, so N widgets meant N
         * `PendingIntent`s held by the system for the same destination.
         *
         * `EXTRA_WIDGET_OPEN_ACTIVITY` carries the request into `MainActivity`,
         * which is where the extra is actually read; the widget cannot navigate
         * on its own, and starting an Activity is all a `PendingIntent` can do.
         */
        private fun openActivityPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_WIDGET_OPEN_ACTIVITY, true)
            }

            return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
