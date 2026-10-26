package com.anisequel.data.repository

import android.content.Context

/**
 * Whether AniSequel keeps the last crash on this device.
 *
 * ## Off by default
 *
 * The report is a diagnostic, not a feature, so it is opt-in: a build that
 * quietly started writing a trace of its own failures to disk would be a
 * surprise in an app whose About page promises there is no telemetry. The
 * reader turns it on in Settings > Info > Diagnostics, which is also where the
 * report is read and deleted.
 *
 * ## Why SharedPreferences, when every other preference here is DataStore
 *
 * Because of who reads it. The flag is consulted from an uncaught-exception
 * handler, in a process that is already dying, on whatever thread threw. A
 * DataStore read is a suspend call: it needs a coroutine and a dispatcher, and
 * neither is something to rely on at that moment. `getBoolean` on an
 * already-loaded `SharedPreferences` is a map lookup. One second preferences
 * file is the price of a setting that has to be readable at the worst possible
 * time.
 *
 * The instance is created once and held, so the file is read while the app is
 * healthy rather than in the handler. See `CrashReports.install`, which takes
 * the value as a lambda for exactly this reason: the read happens at crash time,
 * the load happened long before.
 */
class DiagnosticsPreferences(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var crashReportsEnabled: Boolean
        get() = preferences.getBoolean(KEY_CRASH_REPORTS, false)
        set(value) {
            // `apply` rather than `commit`: the in-memory value is updated
            // synchronously either way, and a settings toggle must not block the
            // frame on a disk write.
            preferences.edit().putBoolean(KEY_CRASH_REPORTS, value).apply()
        }

    private companion object {
        const val FILE_NAME = "anisequel_diagnostics"
        const val KEY_CRASH_REPORTS = "crash_reports_enabled"
    }
}
