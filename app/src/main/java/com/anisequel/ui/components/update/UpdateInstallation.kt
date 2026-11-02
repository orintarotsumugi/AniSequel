package com.anisequel.ui.components.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * What happened when the installer was asked to open.
 *
 * Two answers rather than a boolean, because the failure used to be reported as
 * the one thing it almost never was.
 *
 * [CouldNotOpen] covers a `FileProvider` URI that could not even be built - the
 * provider's paths no longer match the directory the app downloads into - and
 * that was previously swallowed into "nothing opened", so `openInstaller` said no
 * and the updater said "This device has no app installer". #61 moved the
 * downloads out of the cache and left the provider behind, so every APK threw
 * here, and every user on every device was told their phone could not install
 * apps. A bug in this app, reported as a fact about their hardware, sending
 * nobody to the only place the answer was.
 *
 * There is deliberately no separate "this device has no installer" case. It
 * cannot be told apart from the above: on API 30+ a `resolveActivity` probe for
 * the package installer returns null whenever the app has not declared the
 * installer in a `<queries>` element, so such a check cannot distinguish the two -
 * and guessing wrong in the direction of "your phone is broken" is what caused
 * this in the first place. Both causes are answered by the same advice, which
 * works for both: take the release from GitHub.
 */
sealed interface InstallerLaunch {
    /** Something took the intent and opened. */
    data object Opened : InstallerLaunch

    /** Nothing opened - the file could not be shared, or nothing could install it. */
    data object CouldNotOpen : InstallerLaunch
}

/**
 * The three questions the install flow has to ask the platform, in one place.
 *
 * These used to be methods scattered across [UpdateManager] and the update
 * controller, which made the order they have to be asked in - the single thing
 * that actually matters here - invisible. Kept apart, the sequencing *is* the
 * logic:
 *
 *  1. [canInstallPackages] - is this app allowed to open the installer at all?
 *  2. [installPermissionSettings] - if not, where does the user go to grant it?
 *  3. [installIntents] - what to fire once they have.
 *
 * ## The permission is asked for once, then it is just "download and install"
 *
 * `REQUEST_INSTALL_PACKAGES` is a per-app grant from Android 8 onward, and it
 * is *not* granted by the manifest declaration - declaring it only puts AniSequel
 * on the list of packages allowed to ask. So the first time somebody updates,
 * there is necessarily a Settings round trip, and the whole flow is shaped around
 * making that happen exactly once:
 *
 *  - The round trip is entered from a button whose label says what it does, so
 *    the tap is never a surprise.
 *  - Coming back is detected ([UpdateController.onAppResumed]) rather than
 *    polled, so granting the permission continues straight into the installer
 *    with no second "Install now" tap and no re-download.
 *  - The downloaded APK is kept across the round trip, so the user waits for the
 *    download once instead of twice.
 *
 * The permission is never *requested* at launch. It is only ever raised in
 * response to the user asking to update, which is the only moment asking for it
 * is not an unrequested interruption.
 *
 * ## No repository client
 *
 * Nothing here downloads or fetches anything - it is purely "ask the platform,
 * hand back an Intent". That is what keeps it a leaf with no constructor
 * dependencies: an earlier version took an [UpdateManager] it never called, and
 * the default value quietly built a *second* one (and a second OkHttp client and
 * thread pool) on every installation object created. Anything that needs to
 * fetch or download belongs to [UpdateController], which holds the one real
 * `com.anisequel.data.update.UpdateManager`.
 */
open class UpdateInstallation(private val context: Context) {

    /**
     * Whether the system will let this app open the installer right now.
     *
     * The per-app install permission arrived in Android 8 (API 26), so with
     * `minSdk = 31` every device this builds on has it. The
     * `SDK_INT < O || ...` short-circuit that used to sit in front of this meant
     * "below Android 8, installing is not gated at all" - a device class the app
     * can no longer be installed on.
     */
    open fun canInstallPackages(): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /**
     * The Settings screen where the install permission is granted.
     *
     * `ACTION_MANAGE_UNKNOWN_APP_SOURCES` is scoped to this package by the data
     * URI, so the user lands on AniSequel's own row rather than a list.
     *
     * The `NEW_TASK` flag is not decoration: the update controller holds the
     * *application* context, and `startActivity` from a non-Activity context throws
     * `AndroidRuntimeException` without it. That throw was caught by the caller's
     * `runCatching`, turned into `false`, and reported as "This device has no
     * Settings app" - on every device, for the one screen the user needed. The
     * release-page intent beside this one had always carried the flag; this one
     * had not.
     */
    fun installPermissionSettings(): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * The app's own details page, the fallback when the per-app install screen is
     * not offered.
     *
     * `ACTION_MANAGE_UNKNOWN_APP_SOURCES` is a platform action, but the screen
     * behind it is drawn by whichever Settings app the OEM shipped, and some of
     * them do not register it for a package-scoped URI. The details page is the
     * one every device has, and it is where the "Install unknown apps" switch lives
     * on those builds. Same `NEW_TASK` reason as [installPermissionSettings].
     */
    internal fun appDetailsSettings(): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Every Settings screen that can hold the switch, best match first.
     *
     * Five of them, because the phone this runs on is not a device this app gets to
     * see. The package-scoped install-permission screen is the documented route and
     * the one a Pixel answers; the app-details page is the row an OEM re-skin tends
     * to keep when it drops the first; the unscoped form of the same action is what a
     * build that registers only the *list* of apps answers to; the security page is
     * where the older-skinned builds parked the switch; and the Settings home is the
     * last one, chosen over "nothing opened" because a user staring at a dead button
     * has nowhere to go while a user standing in Settings can search for the row -
     * which is exactly what [InstallPermissionGuide] tells them to type.
     *
     * Every route carries `FLAG_ACTIVITY_NEW_TASK`: these are started from the
     * *application* context, and `startActivity` without the flag throws before
     * Settings is anywhere near reached. That throw used to be swallowed, so the
     * update said "This device has no Settings app" on every phone in existence.
     */
    internal fun permissionSettingsRoutes(): List<Intent> {
        // No data on this one: the same action, asked for its list of apps rather
        // than for one package, because some builds register only that form.
        @Suppress("DEPRECATION")
        val unscopedList = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val security = Intent(Settings.ACTION_SECURITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val settingsHome = Intent(Settings.ACTION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return listOf(
            installPermissionSettings(),
            appDetailsSettings(),
            unscopedList,
            security,
            settingsHome
        )
    }

    /**
     * The intents that can open the system installer on [apk], best first.
     *
     * Two of them, because one is not enough in practice. `ACTION_VIEW` on the
     * `FileProvider` URI is the documented route and is what has always worked;
     * `ACTION_INSTALL_PACKAGE` is what several OEM builds actually register a
     * handler for. Handing back both lets the caller try the second only if the
     * first has no handler, which is the difference between an update working
     * and an opaque `ActivityNotFoundException` on somebody else's phone.
     *
     * Both carry `FLAG_GRANT_READ_URI_PERMISSION`, without which the installer
     * cannot read the file it has been handed.
     */
    fun installIntents(apk: File): List<Intent> {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )

        @Suppress("DEPRECATION")
        val fallbackInstallIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setData(uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return listOf(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, APK_MIME_TYPE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            fallbackInstallIntent
        )
    }

    /**
     * Opens the installer on [apk], trying each known route in turn.
     *
     * Returns [InstallerLaunch.Opened] if something took it, and
     * [InstallerLaunch.CouldNotOpen] otherwise. Losing a downloaded APK to a
     * crash here would be the worst possible outcome for a feature the user asked
     * for, which is why building the intents and firing them are each contained -
     * but contained is not the same as indistinguishable, and collapsing both
     * failures into one "no" is what turned a provider misconfiguration into
     * "This device has no app installer" for every user of the app.
     */
    open fun openInstaller(apk: File): InstallerLaunch {
        val intents = runCatching { installIntents(apk) }.getOrElse {
            return InstallerLaunch.CouldNotOpen
        }

        val opened = intents.any { intent ->
            runCatching { context.startActivity(intent) }.isSuccess
        }

        return if (opened) InstallerLaunch.Opened else InstallerLaunch.CouldNotOpen
    }

    /**
     * Sends the user to whichever Settings screen this phone actually has.
     *
     * Returns whether one opened, so the caller can tell "off you go" from "nothing on
     * this device will show that switch", rather than sending the user to a screen that
     * never appeared.
     *
     * [permissionSettingsRoutes] in order, stopping at the first that starts. Trying
     * several is not belt-and-braces: the primary action is unanswered on some OEM
     * builds and the details page is unanswered on others, and the only thing that
     * distinguishes them is the attempt. Only the first failure used to be reported at
     * all - and before these intents carried `NEW_TASK`, *every* launch failed here, so
     * the fallback did not exist and the message blamed the user's phone for a bug in
     * this app.
     */
    open fun openInstallPermissionSettings(): Boolean =
        permissionSettingsRoutes().any { intent ->
            runCatching { context.startActivity(intent) }.isSuccess
        }

    /**
     * Opens a release's page on GitHub.
     *
     * The manifest has carried a `release_url` since the first version, and it is
     * the only way to reach the full release notes - the changelog plus the
     * installation and signing guidance. `notes` in the manifest now carries the
     * same changelog entry and is shown in the dialog, but it is capped and has
     * nowhere to put the install instructions, so the page is still the link
     * that matters.
     *
     * Failures are swallowed deliberately: a device with no browser at all should
     * lose the link, not crash the dialog.
     */
    open fun openReleasePage(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
    }

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}