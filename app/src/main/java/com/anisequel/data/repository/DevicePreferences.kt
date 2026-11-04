package com.anisequel.data.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The file this setting lives in - a plain preferences file, for the reason below. */
private const val DEVICE_PREFERENCES_FILE = "anisequel_device"
private const val KEY_COVER_CACHE_CEILING = "cover_cache_ceiling"

/**
 * How much cover art the app may keep on the device.
 *
 * ## Why the smallest rung is 256 MB
 *
 * That is what the app already used, so the default rung changes nothing for an
 * install that never opens this page. The rungs above it exist because AniList's
 * artwork is *large* - a poster is a few hundred kilobytes and a full-board tier
 * export reads every cover in the list - and a reader with a four-thousand-title
 * library outran 256 MB long before they outran the storage on the phone.
 *
 * ## Why this is a choice and not a heuristic
 *
 * Free disk is a fact about the device that no API in Android reports usefully to
 * an app that wants to know "how much of this phone may I spend on posters". The
 * reader knows. A ceiling too small means the cache evicts art they have already
 * downloaded and every scroll re-fetches it; too large and the app is the reason
 * their storage is full.
 *
 * ## Why SharedPreferences rather than the DataStore every other preference uses
 *
 * The number is read exactly once per process - when Coil builds the app's image
 * loader in `AniSequelApplication.newImageLoader()`, which is a synchronous
 * factory called from the first image request, on a thread that must not stop to
 * await. DataStore is suspend-first and holds a file lock, which is the right
 * shape for the reader's list and the wrong shape for a value the image pipeline
 * needs *now*. One string key in a preferences file is a microsecond; a blocking
 * DataStore read at that point is a stall whose cause is invisible, because the
 * symptom is a slow first screen rather than a hung call.
 */
enum class CoverCacheCeiling(
    val storageValue: String,
    val displayName: String,
    val blurb: String,
    val bytes: Long
) {
    MB_256(
        storageValue = "256mb",
        displayName = "256 MB",
        blurb = "What the app has always kept. A large library will still re-fetch art.",
        bytes = 256L * 1024 * 1024
    ),
    MB_512(
        storageValue = "512mb",
        displayName = "512 MB",
        blurb = "Roughly twice a full list of posters, kept on disk.",
        bytes = 512L * 1024 * 1024
    ),
    GB_1(
        storageValue = "1gb",
        displayName = "1 GB",
        blurb = "Every cover you have ever opened, plus room for the tier exports.",
        bytes = 1024L * 1024 * 1024
    ),
    GB_5(
        storageValue = "5gb",
        displayName = "5 GB",
        blurb = "As much as you like. The cache still only fills as you browse.",
        bytes = 5L * 1024 * 1024 * 1024
    );

    companion object {
        val DEFAULT = MB_256

        fun fromStorage(value: String?): CoverCacheCeiling =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT

        /**
         * The ceiling to build the image loader with, read synchronously.
         *
         * Absent, unreadable or written by a future build that renamed a rung: all
         * answer [DEFAULT], because the cost of guessing low is a re-download and
         * the cost of guessing high is a phone full of art nobody asked to keep.
         */
        fun readBytes(context: Context): Long = fromStorage(
            context.applicationContext
                .getSharedPreferences(DEVICE_PREFERENCES_FILE, Context.MODE_PRIVATE)
                .getString(KEY_COVER_CACHE_CEILING, null)
        ).bytes
    }
}

/**
 * The Device management page's settings.
 *
 * A single value today. Built once per process by the navigation root and read by
 * the page, so the pill the reader taps and the number the image loader was built
 * with are the same fact - the same reason the seasonal page shares one
 * [SeasonalPreferences] with the overlay.
 */
class DevicePreferences(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(DEVICE_PREFERENCES_FILE, Context.MODE_PRIVATE)

    private val _ceiling = MutableStateFlow(
        CoverCacheCeiling.fromStorage(preferences.getString(KEY_COVER_CACHE_CEILING, null))
    )

    val ceiling: StateFlow<CoverCacheCeiling> = _ceiling.asStateFlow()

    /**
     * Remembers [ceiling] for the next time the app opens.
     *
     * The change cannot resize the cache that is running now: Coil's `DiskCache`
     * publishes its ceiling as a `val` (checked against 2.7.0), so it is fixed when
     * the loader is built. The page says so rather than letting the reader tap a
     * pill and watch nothing happen.
     */
    fun setCeiling(ceiling: CoverCacheCeiling) {
        preferences.edit().putString(KEY_COVER_CACHE_CEILING, ceiling.storageValue).apply()
        _ceiling.value = ceiling
    }
}
