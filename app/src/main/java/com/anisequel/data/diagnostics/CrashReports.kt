package com.anisequel.data.diagnostics

import android.content.Context
import android.os.Build
import com.anisequel.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The last time the process died of an uncaught exception, kept on disk where the
 * reader can copy it out.
 *
 * ## Why this exists
 *
 * AniSequel ships from GitHub Releases, not a store, and has no analytics and no
 * crash backend - that is the privacy promise on the About page, and it is not
 * going to change. The cost of that promise is that a force-stop is currently
 * invisible: the system dialog says the app stopped, and by the time anybody asks
 * for a log the trace is gone from `logcat` and the report has nowhere to come
 * from.
 *
 * That cost was paid on the comment sheet. Two rounds of hardening went into that
 * screen - every repository call wrapped, the markdown parser's failures turned
 * into plain text, the list keyed so a repeated id cannot throw - and the report
 * that came back was still "it force-closes when loading the comments", with no
 * trace to say which of the remaining paths it was. Reading the code can only
 * rule things out; it cannot see the line the device actually died on. This file
 * is what turns the next occurrence into an answer instead of another guess.
 *
 * ## What it stores, and what it does not
 *
 * One report: the exception, its stack, the app version and the device model. No
 * account id, no token, no comment text, no browsing history - the handler only
 * ever sees a `Throwable` and the build it was thrown by. It is written to the
 * app's own private storage (`files/diagnostics/`), so nothing leaves the device
 * unless the reader taps Copy and pastes it somewhere.
 *
 * And only when the reader asked for it: reports are **off by default**, turned
 * on in Settings > Info > Diagnostics. An app that promises no telemetry does not
 * get to start writing its own failures to disk because the code found it useful.
 *
 * ## Why the handler is chained rather than replaced
 *
 * Android installs its own handler (`KillApplicationHandler`), and it is what
 * makes a crash a crash: it reports to the system and then ends the process.
 * Swallowing that would leave the app running in the broken state that just threw,
 * which is worse than the force-stop. So the report is written first and the
 * previous handler is called after - the app still dies, it just leaves a note.
 *
 * Every step of that is wrapped: an exception thrown *inside* a crash handler is
 * an exception nobody can catch, and a diagnostics feature that turns one crash
 * into two is not a diagnostics feature.
 */
object CrashReports {

    /** The directory under `filesDir`. Its own name so it is obvious in a backup. */
    private const val DIRECTORY = "diagnostics"

    /** One report, overwritten by the next crash. See [record] for why one. */
    private const val FILE_NAME = "last-crash.txt"

    /**
     * How much of a trace is kept.
     *
     * The head of a stack trace is the part that identifies the crash - the
     * exception type and the first frames - so a truncated report keeps the start
     * and drops the tail. 24k characters is several hundred frames, far past
     * anything a real trace needs, and small enough that the file can be read and
     * shown in a dialog without a scroll fight.
     */
    private const val MAX_REPORT_CHARS = 24_000

    /**
     * True once a handler has been installed.
     *
     * `install` is idempotent because it is called from `Application.onCreate`,
     * which a test or a second process could reach twice; chaining to *itself*
     * would then write the report and recurse until the stack ran out.
     */
    @Volatile
    private var installed = false

    /**
     * Records the next uncaught exception, then lets the platform do what it was
     * going to do.
     *
     * Deliberately not gated on a debug build: the crashes worth capturing are the
     * ones that happen on the release build the reader installed.
     *
     * [enabled] is a provider rather than a `Boolean` because the setting is read
     * at crash time, not at install time: turning reports off in Settings has to
     * take effect for the next crash without a restart, and turning them on again
     * has to work in the same process. The provider must not suspend - it is
     * called on the thread that is already throwing, and a coroutine dispatched
     * from a dying process may never run. See `DiagnosticsPreferences`, which
     * answers from an already-loaded `SharedPreferences` for that reason.
     */
    fun install(context: Context, enabled: () -> Boolean) {
        if (installed) return
        installed = true

        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // A provider that throws reads as "off" - the reader's choice is the
            // safe default, and this is the one place where guessing "on" would
            // write to disk against a setting that says not to.
            if (runCatching(enabled).getOrDefault(false)) {
                runCatching { record(app, thread.name, throwable) }
            }
            // A null previous handler is not a state Android produces - the
            // runtime installs `KillApplicationHandler` before `Application` is
            // created - but a `?.` costs nothing and a crash handler is the one
            // place that must not throw.
            previous?.uncaughtException(thread, throwable)
        }
    }

    /**
     * The report from the last crash, or null if there has not been one.
     *
     * Read from disk rather than held in memory: the process that wrote it is
     * gone by the time anybody asks, so the file is the only thing that survives.
     */
    fun read(context: Context): String? = readFrom(directory(context))

    /** Deletes the stored report. Returns true when there is nothing left. */
    fun clear(context: Context): Boolean = clearIn(directory(context))

    private fun directory(context: Context) = File(context.filesDir, DIRECTORY)

    private fun record(context: Context, threadName: String, throwable: Throwable) {
        val report = buildReport(
            header = header(context),
            threadName = threadName,
            throwable = throwable
        )
        recordIn(directory(context), report)
    }

    /**
     * The lines above the stack: which build, which device, when.
     *
     * A trace without the version is a trace about a build the reader may have
     * replaced two releases ago, which is exactly the confusion this exists to
     * avoid. `BuildConfig.DEBUG` is included because a debug build has a different
     * R8 story and a different set of reachable code paths.
     */
    private fun header(context: Context): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val build = if (BuildConfig.DEBUG) "debug" else "release"
        return buildString {
            appendLine("AniSequel ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) $build")
            appendLine("Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("${Build.MANUFACTURER} ${Build.MODEL}")
            append(stamp)
        }
    }

    // ---- The pure half. Kept free of Android types so it can be tested on the JVM. ----

    /**
     * The file's contents for one throwable.
     *
     * [header] is passed in rather than read from `Build` so this stays a pure
     * function: the format is the part worth pinning in a test, and a test that
     * has to stand up Robolectric to check string layout is a test nobody runs.
     */
    internal fun buildReport(header: String, threadName: String, throwable: Throwable): String {
        val stack = StringWriter().also { writer ->
            PrintWriter(writer).use { throwable.printStackTrace(it) }
        }.toString()

        val report = buildString {
            appendLine(header)
            appendLine("Thread: $threadName")
            appendLine()
            append(stack)
        }
        return if (report.length <= MAX_REPORT_CHARS) {
            report
        } else {
            // The head, not the tail: the exception type and the frames nearest the
            // throw are the part that identifies the bug, and the bottom of a long
            // trace is framework plumbing every crash shares.
            report.take(MAX_REPORT_CHARS) +
                "\n… truncated, ${report.length - MAX_REPORT_CHARS} characters dropped."
        }
    }

    /**
     * Writes [report] over any previous one. Returns the file, or null on failure.
     *
     * One file rather than a rotating set: the report is read on the next launch
     * and the interesting crash is almost always the most recent one. A directory
     * of five traces would also mean five dialogs, or a picker nobody wants.
     */
    internal fun recordIn(directory: File, report: String): File? = runCatching {
        directory.mkdirs()
        val file = File(directory, FILE_NAME)
        // `writeText` truncates first, so a shorter report cannot leave the tail of
        // an older, longer one behind it.
        file.writeText(report)
        file
    }.getOrNull()

    internal fun readFrom(directory: File): String? = runCatching {
        File(directory, FILE_NAME).takeIf { it.isFile }?.readText()
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /**
     * The one line worth putting on a card: the exception the report is about.
     *
     * [buildReport] writes a fixed header, a blank line, then the trace, so the
     * first non-blank line after that blank is `java.lang.IllegalStateException: …`
     * - the line that says whether this was a network failure, an out-of-memory, or
     * something in the UI. Falling back to the first line keeps a hand-edited or
     * older file from rendering an empty box.
     */
    internal fun headline(report: String): String =
        report.lineSequence().dropWhile { it.isNotBlank() }.firstOrNull { it.isNotBlank() }
            ?: report.lineSequence().firstOrNull().orEmpty()

    internal fun clearIn(directory: File): Boolean = runCatching {
        val file = File(directory, FILE_NAME)
        // `delete` on a missing file answers false, which would read as a failed
        // clear on a device that simply has nothing to clear.
        !file.exists() || file.delete()
    }.getOrDefault(false)
}
