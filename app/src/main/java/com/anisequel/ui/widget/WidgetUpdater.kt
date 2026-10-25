package com.anisequel.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.RemoteViews

/**
 * The update path every widget provider shares.
 *
 * ## Why this is a helper and not a base class
 *
 * The four providers differ in exactly one thing - which layout they inflate and
 * which data they read. Everything else, the synchronous placeholder render, the
 * `goAsync()`-kept cover fetch, the "always finish the pending result" contract,
 * was copy-pasted verbatim between them, so a fix to the fetch had to be made
 * four times and a mistake in one copy shipped quietly in the other three.
 *
 * It is an object rather than a superclass because [WidgetDeclarationTest] reads
 * each provider's source and requires the literal `: AppWidgetProvider()`; a
 * shared base would hide that and fail the guard. The render callback keeps the
 * per-widget differences where they belong - in the provider.
 */
internal object WidgetUpdater {

    /**
     * Paints every instance immediately, with no cover.
     *
     * This is the frame a freshly placed tile shows before its fetch lands: the
     * count and caption are data the app already has, and drawing them first is
     * what stops a new tile from sitting blank.
     */
    fun updateWidgets(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
        render: (Context, Bitmap?) -> RemoteViews
    ) {
        // No instances placed: there is nothing to update and, more to the
        // point, nothing to inflate a layout for. The system's own tick also
        // fires for a provider with zero instances.
        if (ids.isEmpty()) return
        manager.updateAppWidget(ids, render(context, null))
    }

    /**
     * Fetches the cover off the main thread and repaints, then always calls
     * [onDone].
     *
     * A widget update that throws must not escape a background thread, so the
     * render is wrapped too.
     */
    fun refreshCover(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
        coverWidthDp: Int,
        coverHeightDp: Int,
        coverUrl: () -> String?,
        render: (Context, Bitmap?) -> RemoteViews,
        onDone: () -> Unit
    ) {
        if (ids.isEmpty()) {
            onDone()
            return
        }

        val display = context.resources.displayMetrics
        WidgetCover.loadAsync(
            context = context,
            url = coverUrl(),
            targetWidthPx = WidgetCover.dp(context, coverWidthDp),
            targetHeightPx = WidgetCover.dp(context, coverHeightDp),
            // One image per tile today. Passed per call rather than hardcoded in
            // the cover loader so a future collection widget can divide the same
            // display budget across its posters instead of multiplying it.
            maxPixels = WidgetImageBudget.maxPixels(
                displayWidthPx = display.widthPixels,
                displayHeightPx = display.heightPixels,
                imageCount = 1
            )
        ) { bitmap ->
            try {
                manager.updateAppWidget(ids, render(context, bitmap))
            } catch (t: Throwable) {
                // Never let a widget update take the process down.
            } finally {
                onDone()
            }
        }
    }

    /**
     * App-triggered refresh: paint the placeholder now, then fetch the cover.
     *
     * Runs while the app's process is alive, so the fetch needs no `goAsync()`.
     */
    fun updateAll(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
        coverWidthDp: Int,
        coverHeightDp: Int,
        coverUrl: () -> String?,
        render: (Context, Bitmap?) -> RemoteViews,
        onDone: () -> Unit = {}
    ) {
        updateWidgets(context, manager, ids, render)
        refreshCover(context, manager, ids, coverWidthDp, coverHeightDp, coverUrl, render, onDone)
    }
}
