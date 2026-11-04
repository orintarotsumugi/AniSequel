package com.anisequel.ui.screens.settings

import android.content.Context
import coil.imageLoader

/**
 * The two cover-cache questions the Device management card asks the image pipeline.
 *
 * Small and separate because the card must not learn how to reach Coil: the loader is
 * built once per process in `AniSequelApplication`, and the cache the card reports on is
 * that object's, not something the settings page owns.
 */

/** Bytes of cover art on disk now. A counter the loader keeps, not a directory walk. */
internal fun Context.coverCacheBytes(): Long =
    imageLoader.diskCache?.size ?: 0L

/** Empties the saved covers. The ceiling itself only changes at the next start. */
internal fun Context.clearCoverCache() {
    imageLoader.diskCache?.clear()
}
