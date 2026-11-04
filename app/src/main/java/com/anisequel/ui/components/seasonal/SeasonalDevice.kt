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

/**
 * What this screen is running at, and what it says it can run at.
 *
 * Two numbers because they answer two different questions. The pacing helper needs
 * the *active* rate - a sky paced above the panel asks for frames that never land -
 * while the Settings page needs the whole list, because a phone that is currently
 * set to 60 Hz in the system display settings still supports 120, and the reader
 * who switches it back on should get the pill without reinstalling anything.
 *
 * Deliberately **not** cached for the process, unlike the quality tier. The active
 * rate is a system setting: the reader can change it while this app is open, and a
 * reading frozen at first composition is the reason a hidden pill would stay hidden
 * until the app restarted. The lookup is a local `Display` struct, not a binder
 * round trip, so re-reading it on resume is cheap.
 *
 * A context with no display - a test, a non-visual context - answers zero and an
 * empty list, which the pure helpers read as a plain 60 Hz panel.
 */
data class SeasonalScreen(
    val currentHz: Float,
    val supportedHz: List<Float>
)

fun detectSeasonalScreen(context: Context): SeasonalScreen {
    val display = runCatching { context.display }.getOrNull()
    val current = runCatching { display?.refreshRate ?: 0f }.getOrDefault(0f)
    val supported = runCatching {
        display?.supportedRefreshRates?.toList() ?: emptyList()
    }.getOrDefault(emptyList())
    return SeasonalScreen(currentHz = current, supportedHz = supported)
}
