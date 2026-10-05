package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

private val Context.refreshIntervalDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "anisequel_refresh_prefs"
)

/**
 * Effectively never. A `Long` offset big enough that no elapsed wall clock can
 * reach it, so the "manual only" choice needs no special case at every comparison
 * - it is just an interval nothing ever outgrows.
 */
private const val NEVER_STALE_MILLIS = Long.MAX_VALUE

/**
 * How old the saved anime list is allowed to get before AniSequel re-fetches it.
 *
 * ## Why this is a user setting at all
 *
 * The list used to be cached for a flat hour inside
 * [AniListRepositoryImpl.listCache], and nothing observed the app coming back to
 * the foreground. Android keeps the process alive when you switch away from an
 * app, so "closing and reopening AniSequel" does not restart anything - the next
 * [AniListRepository.getUserAnimeList] was answered from that same in-memory
 * entry for up to an hour. The list went stale the moment it was fetched, so
 * finishing something on AniList in another app, or letting a new episode air,
 * produced exactly the same dashboard until the hour elapsed, and the only way
 * out was the refresh button. It read as the app ignoring what the user had just
 * done elsewhere.
 *
 * Thirty minutes is the default because it is short enough that a list is
 * current when someone opens the app to check for a new sequel, and long enough
 * that re-entering the app a handful of times in a session does not spend
 * AniList's ~30 requests a minute re-buying a multi-megabyte answer.
 *
 * [MANUAL_ONLY] is the opt-out for anyone on a metered or slow connection: the
 * cached list is reused for the whole session and only the refresh gesture
 * re-fetches.
 */
enum class RefreshInterval(
    val storageValue: String,
    val displayName: String,
    /** How old the list may be before it counts as due for a re-fetch. */
    val staleAfterMillis: Long
) {
    /** Re-fetch on every return to the foreground, never on a timer. */
    ALWAYS("always", "Always", 0L),

    FIFTEEN_MINUTES("fifteen_minutes", "15 min", 15 * 60 * 1000L),

    THIRTY_MINUTES("thirty_minutes", "30 min", 30 * 60 * 1000L),

    ONE_HOUR("one_hour", "1 hour", 60 * 60 * 1000L),

    /** Never automatically. The refresh gesture is the only route to fresh data. */
    MANUAL_ONLY("manual_only", "Manual", NEVER_STALE_MILLIS);

    companion object {
        val DEFAULT = THIRTY_MINUTES

        /**
         * Reads a stored value, falling back to [DEFAULT].
         *
         * Falls back rather than throwing, for the reason [ThemeMode.fromStorage]
         * does: the preferences file outlives the build that wrote it, and an
         * unrecognised value must not cost someone their settings.
         */
        fun fromStorage(value: String?): RefreshInterval =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}

/**
 * Whether the list loaded at [loadedAtMillis] is old enough to re-fetch.
 *
 * Pure, and takes the clock as an argument so the boundary is testable rather
 * than something to reason about: [RefreshInterval.staleAfterMillis] is the
 * inclusive edge, so a 30-minute interval is due at exactly 30 minutes.
 *
 * A negative age means the wall clock moved backwards - a timezone correction,
 * or the user changing the date. Treating that as "not yet due" would pin the
 * cached list for as long as the clock stayed behind, which is the one case
 * where keeping stale data is strictly worse than re-fetching.
 */
fun isRefreshDue(nowMillis: Long, loadedAtMillis: Long, interval: RefreshInterval): Boolean {
    val age = nowMillis - loadedAtMillis
    if (age < 0L) return true
    return age >= interval.staleAfterMillis
}

/**
 * Milliseconds to wait until the list goes stale, or `null` when no background
 * timer should run for [interval].
 *
 * `null` is the answer for [RefreshInterval.ALWAYS] and [RefreshInterval.MANUAL_ONLY]
 * because both are decided entirely by the foreground check, and both would be
 * actively harmful as a timer. A `null` here means "sleep until the app is
 * resumed again", which is precisely what those two choices ask for; scheduling
 * [RefreshInterval.ALWAYS] on a loop would re-fetch continuously instead, because
 * an interval of zero is due the instant it is measured.
 *
 * Zero is a legitimate return value - it means "due right now", and the caller
 * refreshing before the first sleep rather than delaying by nothing.
 */
fun millisUntilRefreshDue(
    nowMillis: Long,
    loadedAtMillis: Long,
    interval: RefreshInterval
): Long? {
    if (interval == RefreshInterval.ALWAYS || interval == RefreshInterval.MANUAL_ONLY) return null
    val age = (nowMillis - loadedAtMillis).coerceAtLeast(0L)
    return (interval.staleAfterMillis - age).coerceAtLeast(0L)
}

/**
 * How long a completed list response may be reused, as the user chose it.
 *
 * Read through a [Flow] rather than stored as a value, and consulted at the
 * moment a lookup happens, so changing the setting takes effect on the next
 * load without the repository having to be rebuilt. The default keeps the
 * pre-existing behaviour for callers that pass nothing - the demo dashboard and
 * the public-profile dashboard both build without a store.
 *
 * ## Why there is also a plain value here
 *
 * `AniListRepositoryImpl.cachedList` is not a suspend function - it runs on the
 * path that answers a list query from memory - so it cannot read a Flow. The
 * obvious fixes are both wrong: `runBlocking` on the main thread is a deadlock
 * waiting for AniList to answer, and making the repository suspend would push a
 * DataStore read onto every list lookup it is meant to avoid.
 *
 * So [observeInterval] keeps [currentStalenessMillis] up to date in the
 * background, and the repository reads that. Until the first value arrives it
 * reports [RefreshInterval.DEFAULT], which is the interval the app shipped with
 * and the conservative direction to be wrong in - a list that refreshes 30
 * minutes early is a delay, not a stale dashboard.
 */
class RefreshIntervalPreferences(private val context: Context) {

    private val KEY_INTERVAL = stringPreferencesKey("refresh_interval")

    @Volatile
    private var cachedInterval: RefreshInterval = RefreshInterval.DEFAULT

    /**
     * The mirror is attached to the flow itself, not set by a second collector.
     *
     * Reading `data` on one coroutine and publishing the result on another would
     * make [currentStalenessMillis] depend on a race between DataStore's threads
     * and whoever asked for the value - and that race is invisible in the app and
     * a coin flip in a test. `onEach` runs before the value reaches any collector,
     * so the mirror is updated by the same emission that reports it.
     */
    val interval: Flow<RefreshInterval> = context.refreshIntervalDataStore.data.map { preferences ->
        RefreshInterval.fromStorage(preferences[KEY_INTERVAL])
    }.onEach { cachedInterval = it }

    /**
     * Keeps [currentStalenessMillis] up to date until cancelled.
     *
     * Collects forever rather than reading once, so a change made in Settings
     * reaches the repository's cache window without anything having to be
     * rebuilt - which is the whole point of the setting.
     */
    suspend fun observeInterval() {
        interval.collect()
    }

    /** The current window, for callers that cannot suspend. See the class comment. */
    fun currentStalenessMillis(): Long = cachedInterval.staleAfterMillis

    suspend fun setInterval(interval: RefreshInterval) {
        context.refreshIntervalDataStore.edit { preferences ->
            preferences[KEY_INTERVAL] = interval.storageValue
        }
    }

    /** Back to the shipped default. Used when the user wants the app's own cadence. */
    suspend fun reset() {
        context.refreshIntervalDataStore.edit { preferences ->
            preferences.remove(KEY_INTERVAL)
        }
    }
}
