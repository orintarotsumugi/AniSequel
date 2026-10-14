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
 * The "Featured Sequel" widget: the top missed sequel as a large poster.
 *
 * The three original tiles each answer a count at a glance; this one gives the
 * single most relevant recommendation room to breathe - the poster, what it is a
 * sequel *to*, and how many are waiting - so a user can decide whether to open
 * the app from the home screen.
 *
 * `onReceive` and the update path are shared with the other three providers via
 * [WidgetUpdater]; see [AniSequelWidgetProvider] for why `goAsync()` matters.
 */
class FeaturedSequelWidgetProvider : AppWidgetProvider() {

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
            ?: manager.getAppWidgetIds(ComponentName(context, FeaturedSequelWidgetProvider::class.java))
        if (ids.isEmpty()) return

        val pendingResult = goAsync()
        WidgetUpdater.refreshCover(
            context = context,
            manager = manager,
            ids = ids,
            coverWidthDp = COVER_WIDTH_DP,
            coverHeightDp = COVER_HEIGHT_DP,
            coverUrl = { AniSequelWidgetData.topMissedCover(context) },
            render = { c, cover -> renderWidget(c, cover) },
            onDone = { pendingResult.finish() }
        )
    }

    companion object {
        private const val COVER_WIDTH_DP = 88
        private const val COVER_HEIGHT_DP = 120

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, FeaturedSequelWidgetProvider::class.java)
            )
            WidgetUpdater.updateAll(
                context = context,
                manager = manager,
                ids = ids,
                coverWidthDp = COVER_WIDTH_DP,
                coverHeightDp = COVER_HEIGHT_DP,
                coverUrl = { AniSequelWidgetData.topMissedCover(context) },
                render = { c, cover -> renderWidget(c, cover) }
            )
        }

        internal fun renderWidget(context: Context, cover: Bitmap?): RemoteViews {
            val count = AniSequelWidgetData.missedSequelsCount(context)
            val title = AniSequelWidgetData.topMissedTitle(context)
            val parent = AniSequelWidgetData.topMissedParent(context)
            val username = AniSequelWidgetData.username(context)

            val caption = context.getString(
                if (count == 0) R.string.widget_featured_caption_empty else R.string.widget_featured_caption
            )
            // A recommendation with nothing it is a sequel *to* has no parent line
            // to draw. The placeholder string ("Sequel to…") would render as a
            // sentence with its subject missing, which is worse than no line.
            val parentLine = if (parent.isNullOrBlank()) {
                null
            } else {
                context.getString(R.string.widget_featured_sequel_to, parent)
            }

            return RemoteViews(context.packageName, R.layout.widget_featured_sequel).apply {
                setTextViewText(
                    R.id.widget_featured_title,
                    title?.takeIf { it.isNotBlank() }
                        ?: context.getString(R.string.widget_featured_title_initial)
                )
                setTextViewText(R.id.widget_featured_parent, parentLine.orEmpty())
                setViewVisibility(
                    R.id.widget_featured_parent,
                    if (parentLine == null) View.GONE else View.VISIBLE
                )
                setTextViewText(R.id.widget_featured_count, count.toString())
                setTextViewText(R.id.widget_featured_caption, caption)
                setTextViewText(R.id.widget_featured_username, username?.let { "@$it" }.orEmpty())
                setViewVisibility(
                    R.id.widget_featured_username,
                    if (username.isNullOrBlank()) View.GONE else View.VISIBLE
                )
                setImageViewBitmap(
                    R.id.widget_featured_cover,
                    cover ?: WidgetCover.placeholder(
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
