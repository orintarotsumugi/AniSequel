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
 * When no session has synced yet, it safely falls back to demo data fixtures
 * so freshly placed widgets always show meaningful previews.
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
            if (prefs.contains(KEY_ACTIVITY_COUNT)) {
                return prefs.getInt(KEY_ACTIVITY_COUNT, 0)
            }
        }
        return DemoProfileProvider.getDemoActivity().size
    }

    /** How many unmissed sequels are currently available. */
    fun missedSequelsCount(context: Context? = null): Int {
        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.contains(KEY_MISSED_COUNT)) {
                return prefs.getInt(KEY_MISSED_COUNT, 0)
            }
        }
        return 3
    }

    /** How many sequels are arriving soon or currently airing. */
    fun arrivingCount(context: Context? = null): Int {
        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.contains(KEY_ARRIVING_COUNT)) {
                return prefs.getInt(KEY_ARRIVING_COUNT, 0)
            }
        }
        return 2
    }

    /** Top missed sequel title for rich widget previews. */
    fun topMissedTitle(context: Context? = null): String? {
        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString(KEY_TOP_MISSED_TITLE, null)?.let { return it }
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
        missedCount?.let { editor.putInt(KEY_MISSED_COUNT, it) }
        activityCount?.let { editor.putInt(KEY_ACTIVITY_COUNT, it) }
        arrivingCount?.let { editor.putInt(KEY_ARRIVING_COUNT, it) }
        topMissedTitle?.let { editor.putString(KEY_TOP_MISSED_TITLE, it) }
        topMissedParent?.let { editor.putString(KEY_TOP_MISSED_PARENT, it) }
        username?.let { editor.putString(KEY_USERNAME, it) }
        editor.apply()
    }
}
