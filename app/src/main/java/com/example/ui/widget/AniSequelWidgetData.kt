package com.example.ui.widget

import android.content.Context
import com.example.data.repository.DemoProfileProvider

/**
 * Where the widgets' numbers and labels come from.
 *
 * ## Fast, crash-proof, and offline-ready
 *
 * App widgets are updated by the OS at arbitrary times outside the app's process
 * lifecycle. This class reads from lightweight SharedPreferences where the app
 * writes its latest dashboard and profile snapshot whenever data loads.
 *
 * The demo fixtures below are a *preview* for a widget placed before the app has
 * ever synced. Once an account has synced, an absent value means the real value
 * is zero (or not tracked), not "show demo data" - the fallbacks used to apply
 * unconditionally, so a signed-in user with nothing missed still saw "3" and
 * "Demon Slayer...". `KEY_USERNAME` being present is what marks a real session.
 */
object AniSequelWidgetData {

    private const val PREFS_NAME = "anisequel_widget_cache"
    private const val KEY_ACTIVITY_COUNT = "activity_count"
    private const val KEY_MISSED_COUNT = "missed_count"
    private const val KEY_ARRIVING_COUNT = "arriving_count"
    private const val KEY_TOP_MISSED_TITLE = "top_missed_title"
    private const val KEY_TOP_MISSED_PARENT = "top_missed_parent"
    private const val KEY_USERNAME = "username"

    /** How many list entries the viewer logged recently. */
    fun recentActivityCount(context: Context? = null): Int {
        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.contains(KEY_ACTIVITY_COUNT)) return prefs.getInt(KEY_ACTIVITY_COUNT, 0)
            // The dashboard has no recent-update count to write yet, so a synced
            // account shows the honest zero rather than a fabricated demo figure.
            if (prefs.contains(KEY_USERNAME)) return 0
        }
        return DemoProfileProvider.getDemoActivity().size
    }

    /** How many unmissed sequels are currently available. */
    fun missedSequelsCount(context: Context? = null): Int {
        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.contains(KEY_MISSED_COUNT)) return prefs.getInt(KEY_MISSED_COUNT, 0)
            if (prefs.contains(KEY_USERNAME)) return 0
        }
        return 3
    }

    /** How many sequels are arriving soon or currently airing. */
    fun arrivingCount(context: Context? = null): Int {
        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.contains(KEY_ARRIVING_COUNT)) return prefs.getInt(KEY_ARRIVING_COUNT, 0)
            if (prefs.contains(KEY_USERNAME)) return 0
        }
        return 2
    }

    /** Top missed sequel title for rich widget previews. */
    fun topMissedTitle(context: Context? = null): String? {
        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString(KEY_TOP_MISSED_TITLE, null)?.let { return it }
            // Synced but no title left: the missed list is empty, so there is
            // nothing to preview - and no stale "Top: ..." from before.
            if (prefs.contains(KEY_USERNAME)) return null
        }
        return "Demon Slayer: Entertainment District Arc"
    }

    /** The viewer's username if known. */
    fun username(context: Context? = null): String? {
        if (context == null) return null
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_USERNAME, null)
    }

    /** Persists latest stats from the app so widgets reflect live data. */
    fun updateData(
        context: Context,
        missedCount: Int? = null,
        activityCount: Int? = null,
        arrivingCount: Int? = null,
        topMissedTitle: String? = null,
        topMissedParent: String? = null,
        username: String? = null
    ) {
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        // A null argument means *clear*, not "leave whatever was there". Writing
        // only non-null values left the previous snapshot in place: when the
        // missed list emptied, the widget showed its "all caught up" caption
        // under a stale "Top: ..." title from the last non-empty load.
        if (missedCount != null) editor.putInt(KEY_MISSED_COUNT, missedCount) else editor.remove(KEY_MISSED_COUNT)
        if (activityCount != null) editor.putInt(KEY_ACTIVITY_COUNT, activityCount) else editor.remove(KEY_ACTIVITY_COUNT)
        if (arrivingCount != null) editor.putInt(KEY_ARRIVING_COUNT, arrivingCount) else editor.remove(KEY_ARRIVING_COUNT)
        if (topMissedTitle != null) editor.putString(KEY_TOP_MISSED_TITLE, topMissedTitle) else editor.remove(KEY_TOP_MISSED_TITLE)
        if (topMissedParent != null) editor.putString(KEY_TOP_MISSED_PARENT, topMissedParent) else editor.remove(KEY_TOP_MISSED_PARENT)
        // Never cleared: the username is what marks a session as synced, and
        // removing it would flip every fallback above back to demo data.
        username?.let { editor.putString(KEY_USERNAME, it) }
        editor.apply()
    }
}
