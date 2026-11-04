package com.anisequel.ui.components.seasonal

import android.app.ActivityManager
import android.content.Context
import com.anisequel.domain.seasonal.SeasonalQualityTier
import com.anisequel.domain.seasonal.seasonalQualityTierForDevice

/**
 * Reads RAM and core count and hands them to the pure picker.
 *
 * Kept out of the domain file because [ActivityManager] is an Android lookup,
 * and kept out of the Settings page so the overlay host can resolve Auto on
 * the first frame without the page being open.
 *
 * The answer is cached for the process. There are two callers - the host at the
 * app root and the Seasonal page - and [ActivityManager.getMemoryInfo] is a binder
 * round trip, so opening Settings used to pay a second one for a number that cannot
 * change while the app is installed. Both callers read it from composition, which is
 * the main thread, so the field needs no publication guarantee; a race would only
 * cost one extra lookup returning the same tier.
 */
private var cachedQualityTier: SeasonalQualityTier? = null

fun detectSeasonalQualityTier(context: Context): SeasonalQualityTier {
    cachedQualityTier?.let { return it }
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val memory = ActivityManager.MemoryInfo()
    activityManager?.getMemoryInfo(memory)
    val tier = seasonalQualityTierForDevice(
        totalMemMb = memory.totalMem / (1024L * 1024L),
        cpuCores = Runtime.getRuntime().availableProcessors()
    )
    cachedQualityTier = tier
    return tier
}
