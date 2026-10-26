package com.anisequel

import com.anisequel.data.diagnostics.CrashReports
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The crash report's format, and the file it lives in.
 *
 * ## Why these are JVM tests and not Robolectric ones
 *
 * The half of `CrashReports` worth pinning takes a header string and a `File`
 * directory instead of a `Context`, so the format and the round trip can be tested
 * in milliseconds without standing up an Android runtime. That split is the reason
 * the file has a pure half at all: a diagnostics feature whose own behaviour is
 * only checkable by crashing the app is a diagnostics feature nobody verifies.
 *
 * ## What is actually being guarded
 *
 * Three failures that would make the report useless exactly when it is needed:
 *
 *  - **The exception line not being first.** The About card shows one line, and it
 *    is found by skipping the header to the first blank line. If the header ever
 *    grows a blank line, the card starts showing the app version as the crash.
 *  - **A second crash appending instead of replacing.** `writeText` truncates, but
 *    a future switch to an appending write would leave two traces in one file and
 *    the headline would describe the older one.
 *  - **A pathological trace growing without bound.** A stack trace is written by
 *    the process that is dying; a report that cannot be written is no report.
 */
class CrashReportsTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val header = """
        AniSequel 1.6.2 (362) release
        Android 14 (SDK 34)
        Google Pixel 7
        2026-10-26 12:34:56
    """.trimIndent()

    @Test
    fun `the report leads with the exception line after the header`() {
        val report = CrashReports.buildReport(
            header = header,
            threadName = "main",
            throwable = IllegalStateException("No activity to draw")
        )

        val lines = report.lines()
        assertEquals("AniSequel 1.6.2 (362) release", lines.first())
        assertTrue("The thread the crash happened on is part of the report.", "Thread: main" in report)
        assertEquals(
            "The About card reads the first line after the blank, so this is the " +
                "line that has to be the exception itself.",
            "java.lang.IllegalStateException: No activity to draw",
            CrashReports.headline(report)
        )
        assertTrue("The frames are what makes the report actionable.", "at com.anisequel" in report || "\tat " in report)
    }

    @Test
    fun `the headline falls back to the first line when there is no blank`() {
        assertEquals(
            "A hand-edited or older file must still render something rather than an empty box.",
            "just one line",
            CrashReports.headline("just one line")
        )
    }

    @Test
    fun `a second crash replaces the first rather than appending to it`() {
        val directory = temporaryFolder.newFolder("diagnostics")

        CrashReports.recordIn(directory, "first report")
        CrashReports.recordIn(directory, "second")

        assertEquals("second", CrashReports.readFrom(directory))
    }

    @Test
    fun `a report that was never written reads as null`() {
        val directory = temporaryFolder.newFolder("empty")
        assertNull(CrashReports.readFrom(directory))
    }

    @Test
    fun `clearing a directory with no report succeeds`() {
        val directory = temporaryFolder.newFolder("nothing-to-clear")
        assertTrue(
            "A missing file is already clear; reporting a failure would make the " +
                "Clear button look broken on a device that has never crashed.",
            CrashReports.clearIn(directory)
        )
    }

    @Test
    fun `clearing a written report removes it`() {
        val directory = temporaryFolder.newFolder("written")
        CrashReports.recordIn(directory, "report")

        assertTrue(CrashReports.clearIn(directory))
        assertNull(CrashReports.readFrom(directory))
        assertFalse(File(directory, "last-crash.txt").exists())
    }

    @Test
    fun `a pathological trace is truncated at the head`() {
        // A thousand frames is a few hundred characters more than any real trace and
        // comfortably past the cap, so this exercises the truncation branch rather
        // than the ordinary path.
        val deep = IllegalStateException("deep").apply {
            stackTrace = Array(1_000) { index ->
                StackTraceElement("com.anisequel.Deep", "frame$index", "Deep.kt", index + 1)
            }
        }

        val report = CrashReports.buildReport(
            header = header,
            threadName = "main",
            throwable = deep
        )

        assertTrue("The cap has to hold or the file is not writable in a dying process.", report.length < 25_000)
        assertTrue("The reader has to be told the report is partial.", "truncated" in report)
        assertTrue(
            "The head is kept because that is where the exception and its first frames are.",
            report.startsWith("AniSequel 1.6.2 (362) release")
        )
        assertEquals(
            "java.lang.IllegalStateException: deep",
            CrashReports.headline(report)
        )
    }
}
