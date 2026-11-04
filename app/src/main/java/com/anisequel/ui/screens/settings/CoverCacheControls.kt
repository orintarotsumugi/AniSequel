package com.anisequel.ui.screens.settings

import android.content.Context
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
/**
 * The two cover-cache questions the Device management card asks the image pipeline.
 *
 * Small and separate because the card must not learn how to reach Coil: the loader is
 * built once per process in `AniSequelApplication`, and the cache the card reports on is
 * that object's, not something the settings page owns.
 *
 * `ImageLoader.diskCache` is Coil's experimental accessor - the builder that *sets* the
 * ceiling is stable, reading it back is not - so both helpers carry the opt-in rather
 * than the call sites, and the settings page never has to know which half of Coil is
 * signed off.
 */
@OptIn(ExperimentalCoilApi::class)
internal fun Context.coverCacheBytes(): Long = imageLoader.diskCache?.size ?: 0L

@OptIn(ExperimentalCoilApi::class)
internal fun Context.clearCoverCache() {
    imageLoader.diskCache?.clear()
}
