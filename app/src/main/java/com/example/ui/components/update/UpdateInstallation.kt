package com.example.ui.components.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

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
 * `com.example.data.update.UpdateManager`.
 */
class UpdateInstallation(private val context: Context) {

    /**
     * Whether the system will let this app open the installer right now.
     *
     * The per-app install permission arrived in Android 8 (API 26), so with
     * `minSdk = 31` every device this builds on has it. The
     * `SDK_INT < O || ...` short-circuit that used to sit in front of this meant
     * "below Android 8, installing is not gated at all" - a device class the app
     * can no longer be installed on.
     */
    fun canInstallPackages(): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /**
     * The Settings screen where the install permission is granted.
     *
     * `ACTION_MANAGE_UNKNOWN_APP_SOURCES` is scoped to this package by the data
     * URI, so the user lands on AniSequel's own row rather than a list.
     */
    fun installPermissionSettings(): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}")
    )

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

        return listOf(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, APK_MIME_TYPE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                setData(uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    /**
     * Opens the installer on [apk], trying each known route in turn.
     *
     * Returns true if something opened. False means this device has no package
     * installer registered at all - a headless build, or an OEM image with the
     * component removed - which is worth reporting rather than crashing over,
     * because losing a downloaded APK to a crash at the last step is the worst
     * possible outcome for a feature the user asked for.
     */
    fun openInstaller(apk: File): Boolean {
        // `installIntents` builds a FileProvider URI, which throws when the file
        // sits outside the provider's configured paths or the provider is
        // misconfigured. That is exactly the last-step crash this method exists
        // to avoid, so building the intents is inside the `runCatching` too.
        val intents = runCatching { installIntents(apk) }.getOrElse { return false }
        return intents.any { intent ->
            runCatching { context.startActivity(intent) }.isSuccess
        }
    }

    /**
     * Sends the user to the per-app install permission setting.
     *
     * Returns whether the Settings screen actually opened, so the caller can tell
     * the difference between "off you go" and "this device has no Settings app",
     * rather than telling the user to look for a screen that never appeared.
     */
    fun openInstallPermissionSettings(): Boolean {
        val intent = installPermissionSettings()
        return runCatching { context.startActivity(intent) }.isSuccess
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
    fun openReleasePage(url: String?): Boolean {
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