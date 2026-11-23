package com.anisequel.data.repository

import android.content.Context
import java.io.File

/**
 * Where the profile (and list) snapshots live: `noBackupFilesDir`, never `cacheDir`.
 *
 * ## The bug this location fixes
 *
 * The snapshots were written under `context.cacheDir`. That directory is Android's
 * to empty: a low-storage sweep deletes it without telling the app, and "Clear
 * cache" in the system Settings does the same on request. Everything the profile
 * screen is made of was sitting in it - the bio and its Markdown, the banner and
 * avatar URLs, the favourites, the totals and the list - so a cache wipe turned
 * the next profile open into a full re-download of a page that had not changed.
 * The image cache was moved out of `cacheDir` for exactly this reason; the
 * snapshots, which are the *page*, were left behind.
 *
 * `noBackupFilesDir` is private app storage that survives a restart, a cache wipe
 * and an update. It is deliberately not `filesDir`: every one of these files can be
 * re-fetched and restored from the current account, so a hundred kilobytes of
 * somebody's bio has no business in a cloud backup or on a new phone in place of
 * today's profile.
 *
 * ## The one-time adoption
 *
 * A build before this one wrote to `cacheDir/anilist_snapshots`. If that directory
 * is still there, it is renamed into the new location on the first run, so an
 * update keeps the profile the reader already had instead of making them download
 * it once more. A rename within the app's own storage is atomic and moves no
 * bytes; if it fails (a directory Android has already reclaimed, a filesystem that
 * refuses) the store simply starts empty, which is the behaviour every build
 * before this one had anyway.
 */
internal fun snapshotDirectory(applicationContext: Context): File {
    val target = File(applicationContext.noBackupFilesDir, SNAPSHOT_DIRECTORY)
    if (target.exists()) return target

    val legacyCacheDirectory = File(applicationContext.cacheDir, SNAPSHOT_DIRECTORY)
    if (legacyCacheDirectory.isDirectory) legacyCacheDirectory.renameTo(target)
    return target
}

/** The name of the snapshots directory, under whichever root holds it. */
internal const val SNAPSHOT_DIRECTORY = "anilist_snapshots"
