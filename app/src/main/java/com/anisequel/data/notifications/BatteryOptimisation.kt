package com.anisequel.data.notifications

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * The one Android switch that lets the background check keep its schedule.
 *
 * AniList has no push stream, so an alert is a periodic WorkManager request that
 * has to wake up to find the event. Doze and the app standby buckets are allowed
 * to defer that request past its interval, and on a phone that has not been
 * plugged in for a day they routinely do - which is why "every 30 minutes" can
 * read as hours in the delivery status line. Exempting the app from battery
 * optimisation is the only thing a reader can grant that lifts that deferral.
 *
 * The exemption is not a promise of instant delivery and the Settings copy says
 * so: some manufacturers' task killers ignore the platform list entirely, a
 * check still needs a connection, and WorkManager's 15-minute floor stands. What
 * it removes is Android *choosing* to hold the check back.
 *
 * [request] asks for the exemption directly through
 * `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. That dialog only appears when
 * the manifest declares `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` - without it the
 * intent falls through to a device that cannot answer it, and the reader taps a
 * button and lands nowhere. [requestRoutes] is the ladder for the builds that
 * still drop it: the direct dialog, then the battery-optimisation list, then the
 * app's own details page, every rung carrying `FLAG_ACTIVITY_NEW_TASK` because
 * the callers are composables holding an application-ish context as often as an
 * Activity.
 */
object BatteryOptimisation {

    /**
     * Whether Android is leaving the app's background work alone.
     *
     * Read from the platform every time rather than remembered: the reader can
     * change it in a Settings screen this app cannot see, and a cached answer
     * would keep the card saying "optimised" after they fixed it. `PowerManager`
     * is a system service on every device this app installs on, but a null one is
     * treated as exempt - showing a warning the reader cannot act on is worse than
     * showing none.
     */
    fun isExempt(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return true
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Every screen that can grant the exemption, best match first.
     *
     * The direct request is package-scoped, so the dialog names AniSequel's own
     * row. The list is the screen a build that does not register the request
     * action answers to, and the details page is the last resort on a re-skin
     * that keeps neither - the same ladder the install-permission route takes in
     * `UpdateInstallation.permissionSettingsRoutes()`, for the same reason.
     */
    internal fun requestRoutes(context: Context): List<Intent> {
        val packageUri = Uri.parse("package:" + context.packageName)
        val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return listOf(request, batterySettings(), details)
    }

    /**
     * Android's own list of apps that are exempt, where the reader can flip the
     * switch by hand.
     *
     * The second action on the card, and the fallback when the direct dialog is
     * not offered. No package data: the list is the screen, and a package-scoped
     * URI on this action is not what any build registers.
     */
    internal fun batterySettings(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Asks for the exemption, or opens the best screen this device has.
     *
     * Returns false only when no rung started, which is a device with no Settings
     * app at all - the caller still has to tell the reader rather than leave the
     * button looking dead.
     */
    fun request(context: Context): Boolean = requestRoutes(context).any { intent ->
        runCatching { context.startActivity(intent) }.isSuccess
    }

    /** Opens the exemption list directly, for the card's secondary action. */
    fun openBatterySettings(context: Context): Boolean =
        runCatching { context.startActivity(batterySettings()) }.isSuccess
}