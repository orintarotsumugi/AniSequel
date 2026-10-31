package com.anisequel.ui.widget

import android.content.Context
import com.anisequel.data.model.displayName
import com.anisequel.data.model.posterUrl
import com.anisequel.data.repository.DemoProfileProvider

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
    private const val KEY_TOP_MISSED_COVER = "top_missed_cover"
    private const val KEY_TOP_MISSED_COLOR = "top_missed_color"
    private const val KEY_ARRIVING_TITLE = "arriving_title"
    private const val KEY_ARRIVING_COVER = "arriving_cover"
    private const val KEY_ARRIVING_COLOR = "arriving_color"
    private const val KEY_ACTIVITY_TITLE = "activity_title"
    private const val KEY_ACTIVITY_ART = "activity_art"
    private const val KEY_USERNAME = "username"

    /** How many list entries the viewer logged recently. */
    fun recentActivityCount(context: Context? = null): Int {
        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.contains(KEY_ACTIVITY_COUNT)) return prefs.getInt(KEY_ACTIVITY_COUNT, 0)
            // A saved username with no count is an account whose last sync had
            // no recent updates to report; zero is the honest answer. The demo
            // fixture below is only for a widget that has never synced.
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

    /**
     * The stored string for [key], or the offline fixture when nothing has synced.
     *
     * The rule every string accessor below shares, and the same one
     * [topMissedTitle] spells out: a key that is *present* wins (even when it
     * holds an empty string), and once `KEY_USERNAME` exists an absent value is
     * genuinely absent - not "show demo data". A signed-in user whose top
     * missed entry has no artwork gets the colour placeholder, not a poster for
     * a show they never watched.
     */
    private fun storedString(context: Context?, key: String, demo: () -> String?): String? {
        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString(key, null)?.let { return it }
            if (prefs.contains(KEY_USERNAME)) return null
        }
        return demo()
    }

    /** Top missed sequel's cover URL, for the rich widgets. */
    fun topMissedCover(context: Context? = null): String? =
        storedString(context, KEY_TOP_MISSED_COVER) { null }

    /** AniList's dominant colour for the top missed sequel, as `#RRGGBB`. */
    fun topMissedColor(context: Context? = null): String? =
        storedString(context, KEY_TOP_MISSED_COLOR) { null }

    /** The entry the top missed sequel continues ("Sequel to ..."). */
    fun topMissedParent(context: Context? = null): String? =
        storedString(context, KEY_TOP_MISSED_PARENT) { null }

    /** The first arriving entry's title. */
    fun arrivingTitle(context: Context? = null): String? =
        storedString(context, KEY_ARRIVING_TITLE) { null }

    /** The first arriving entry's cover URL. */
    fun arrivingCover(context: Context? = null): String? =
        storedString(context, KEY_ARRIVING_COVER) { null }

    /** AniList's dominant colour for the first arriving entry. */
    fun arrivingColor(context: Context? = null): String? =
        storedString(context, KEY_ARRIVING_COLOR) { null }

    /**
     * The media on the viewer's latest activity.
     *
     * The offline fallback comes from [DemoProfileProvider.getDemoActivity],
     * the same fixture [recentActivityCount] counts, so a placed-but-unsynced
     * tile is recognisably the demo feed rather than a blank rectangle.
     */
    fun latestActivityTitle(context: Context? = null): String? =
        storedString(context, KEY_ACTIVITY_TITLE) {
            DemoProfileProvider.getDemoActivity().firstOrNull()?.media?.displayName
        }

    /**
     * The image on the activity tile.
     *
     * The dashboard has no viewer-activity feed to draw from, so what the app
     * actually has is the viewer's avatar - the face the tile is about. Synced,
     * that is what is stored; unsynced, the fixture's first poster keeps the tile
     * from being a blank rectangle before the first real load lands.
     */
    fun activityArt(context: Context? = null): String? =
        storedString(context, KEY_ACTIVITY_ART) {
            DemoProfileProvider.getDemoActivity().firstOrNull()?.media?.posterUrl
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
        topMissedCover: String? = null,
        topMissedColor: String? = null,
        arrivingTitle: String? = null,
        arrivingCover: String? = null,
        arrivingColor: String? = null,
        activityTitle: String? = null,
        activityArt: String? = null,
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
        if (topMissedCover != null) editor.putString(KEY_TOP_MISSED_COVER, topMissedCover) else editor.remove(KEY_TOP_MISSED_COVER)
        if (topMissedColor != null) editor.putString(KEY_TOP_MISSED_COLOR, topMissedColor) else editor.remove(KEY_TOP_MISSED_COLOR)
        if (arrivingTitle != null) editor.putString(KEY_ARRIVING_TITLE, arrivingTitle) else editor.remove(KEY_ARRIVING_TITLE)
        if (arrivingCover != null) editor.putString(KEY_ARRIVING_COVER, arrivingCover) else editor.remove(KEY_ARRIVING_COVER)
        if (arrivingColor != null) editor.putString(KEY_ARRIVING_COLOR, arrivingColor) else editor.remove(KEY_ARRIVING_COLOR)
        if (activityTitle != null) editor.putString(KEY_ACTIVITY_TITLE, activityTitle) else editor.remove(KEY_ACTIVITY_TITLE)
        if (activityArt != null) editor.putString(KEY_ACTIVITY_ART, activityArt) else editor.remove(KEY_ACTIVITY_ART)
        // Never cleared by an update: the username is what marks a session as
        // synced, and removing it would flip every fallback above back to demo
        // data. [clear] is the one path that removes it, and only on sign-out.
        username?.let { editor.putString(KEY_USERNAME, it) }
        editor.apply()
    }

    /**
     * Forgets the synced account, because the viewer signed out.
     *
     * The username is the flag every read above uses to decide whether the stored
     * values are a real session or the fixture, and nothing ever removed it - so the
     * four tiles kept drawing the previous account's handle, counts and top titles
     * indefinitely after a sign-out, on a launcher anyone holding the phone can see.
     * Emptying the store puts them back to the placeholder state they have before the
     * first sync.
     */
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
