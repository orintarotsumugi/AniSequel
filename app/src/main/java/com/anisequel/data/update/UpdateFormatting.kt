package com.anisequel.data.update

import java.util.Locale

/**
 * Display formatters for the updater's numbers.
 *
 * ## Why these are not in [UpdateManifest.kt]
 *
 * They were. The wire model and the strings a person reads were in one file,
 * which put a pure display concern next to a policy decision — and the test
 * suite paid for it, because `UpdateManifestTest` had to import a UI helper to
 * check a unit conversion.
 *
 * `isNewerThan` stays on the manifest, where it belongs: it is a fact about a
 * manifest relative to what is installed, it has four dedicated tests, and every
 * field has a default so none of them needs a whole manifest to construct. The
 * original note in TO-DO proposed moving it "so it could get a proper test
 * without constructing a full manifest" — that test already existed, one line
 * long, and the split would not have bought it.
 */

/**
 * Human-readable size, for the "12.4 MB" line under the update button.
 *
 * `Locale.ROOT` on the format, not the platform default: a device set to a
 * locale that writes decimals with a comma rendered "2,3 MB" beside an English
 * sentence, and a download size is a number, not prose to be localised. The
 * comma also read as a thousands separator at a glance - "2,3" looks like two
 * to three.
 */
fun formatBytes(bytes: Long?): String {
    if (bytes == null || bytes < 0) return "unknown size"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.ROOT, "%.0f KB", kb)
    return String.format(Locale.ROOT, "%.1f MB", kb / 1024.0)
}

/**
 * Human-readable speed, for the download line.
 *
 * Empty rather than "0 B/s" when there is no reading yet. The two are different
 * things to read - "not moving" is worth waiting out, "we do not know yet" is
 * the first second of every transfer - and printing a rate that is really just
 * an absence of one would tell the user their download had stopped.
 *
 * A byte counter over a second is too small to be worth reading, so it is
 * reported in KB; there is no number to show below that which is not noise.
 */
fun formatRate(bytesPerSecond: Long?): String {
    if (bytesPerSecond == null || bytesPerSecond < 1024) return ""
    return "${formatBytes(bytesPerSecond)}/s"
}
