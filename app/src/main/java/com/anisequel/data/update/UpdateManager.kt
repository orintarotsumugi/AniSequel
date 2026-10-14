package com.anisequel.data.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.anisequel.BuildConfig
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The subdirectory of the app's own storage that holds downloaded releases.
 *
 * Top level rather than tucked into the class because it is a *contract*, not an
 * implementation detail: `res/xml/update_file_paths.xml` has to publish the same
 * directory through the `FileProvider`, because the system installer is handed a
 * `content://` URI and runs as a different app that cannot read this app's
 * storage directly. When the downloads moved out of `cacheDir` (#61) the
 * provider's paths did not follow them, `getUriForFile` threw on the downloaded
 * APK, and every update ended on "This device has no app installer" - on phones
 * with a working package installer.
 *
 * `UpdateFileProviderPathsTest` compares this against that XML, so the two
 * cannot drift apart again without a test going red.
 */
internal const val UPDATE_DOWNLOAD_DIR = "updates"

/**
 * Fetches the published update manifest, downloads a release, and vets the file
 * it produced.
 *
 * ## Why this exists as a self-contained piece of app code
 *
 * There is no updater library here, deliberately. The only job is: fetch a
 * small JSON file, compare one integer against `BuildConfig.VERSION_CODE`, and
 * stream a ~2.5 MB APK into a file of the app's own. A library would add a
 * dependency and a Play Services requirement for that, and the app is
 * deliberately not on the Play Store - which is also why the install handoff
 * uses `ACTION_VIEW` on a `FileProvider` URI rather than Play Core's in-app
 * update flow.
 *
 * ## What this cannot do
 *
 * It cannot install silently. Since Android 8 every install is user-confirmed,
 * and from Android 8 the app must hold `REQUEST_INSTALL_PACKAGES` *and* have
 * that permission granted per-app in Settings before the installer will even
 * open.
 *
 * ## What this no longer does
 *
 * Two things, both of which used to be here and neither of which is about
 * deciding whether a release may be installed:
 *
 *  - The transfer. [ApkDownloader] writes the `.part` file and continues one
 *    that is already there; this class still decides what those bytes are worth.
 *  - The permission check, the Settings hand-off and the installer intents. They
 *    moved to `UpdateInstallation` in the presentation layer, because they are all
 *    about *this device's* UI and none of them are about fetching or verifying a
 *    release - and because the order they have to be called in is the actual logic
 *    of the install flow, which is easier to read when the order is the file's
 *    shape.
 *
 * What stays here is everything that is true regardless of which device is
 * asking.
 */
open class UpdateManager(private val context: Context) {

    private val moshi = Moshi.Builder().build()

    // Its own client, deliberately not the shared one: that client carries the
    // AniList auth interceptor, and the update manifest is a public file that
    // must never be requested with a user's AniList bearer token attached.
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * The bytes, as opposed to the judgement.
     *
     * Held rather than constructed per download so the connection pool and the
     * thread pool are the ones configured above, not a fresh pair per press.
     */
    private val downloader = ApkDownloader(client)

    /**
     * Asks GitHub whether something newer exists.
     *
     * Never throws. A failed check is a normal outcome - the user is offline,
     * or GitHub is down - and it must not be able to take down whatever screen
     * asked, so every failure path is folded into [UpdateCheckResult.Failed]
     * with a message that can be shown verbatim.
     */
    open suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val installedVersion = BuildConfig.VERSION_NAME

        val body = try {
            val request = Request.Builder()
                .url(UPDATE_MANIFEST_URL)
                // Force a revalidation. OkHttp's cache is per-client and this
                // client has no cache configured, but being explicit costs
                // nothing and survives someone adding one later.
                .header("Cache-Control", "no-cache")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateCheckResult.Failed(
                        "GitHub answered ${response.code}."
                    )
                }
                response.body?.string()
            }
        } catch (e: IOException) {
            return@withContext UpdateCheckResult.Failed(
                "Could not reach GitHub. Check your connection and try again."
            )
        } ?: return@withContext UpdateCheckResult.Failed("GitHub sent an empty response.")

        val manifest = try {
            moshi.adapter(UpdateManifest::class.java).fromJson(body)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Malformed JSON, or a field of the wrong type. Worth saying so:
            // this file is generated by a workflow, so a parse failure is a
            // real signal that the manifest shape changed.
            return@withContext UpdateCheckResult.Failed(
                "The update manifest could not be read."
            )
        } ?: return@withContext UpdateCheckResult.Failed("The update manifest was empty.")

        if (!manifest.isUsable) {
            return@withContext UpdateCheckResult.Failed(
                "The update manifest is missing a download link."
            )
        }

        if (manifest.isNewerThan(BuildConfig.VERSION_CODE.toLong())) {
            UpdateCheckResult.Available(manifest)
        } else {
            UpdateCheckResult.UpToDate(installedVersion)
        }
    }

    /**
     * Fetches the release into the app's own storage, then vets it.
     *
     * The transfer is [ApkDownloader]'s: it writes a `.part` file, continuing
     * one that is already there, and this method is what decides whether those
     * bytes are an installable APK.
     *
     * ## The `.part` file is the point
     *
     * Nothing renames a `.part` into its final name until it has been read back
     * and checked, so a transfer interrupted by a phone call, a process death or
     * a lift cannot leave a truncated APK behind for a later run to hand to the
     * package installer - which fails with an opaque
     * `INSTALL_PARSE_FAILED_NO_CERTIFICATES`, or worse, appears to succeed.
     *
     * And it is *kept*, rather than deleted on every failure, because it is a
     * valid prefix of the same asset: the next press asks for the rest of it
     * instead of fetching the first 2.6 MB again. The one thing that does throw
     * the file away is [verifyAndInstall] refusing it - at that point the bytes
     * are not a prefix of anything installable, and resuming onto them would
     * only produce a file that fails the same check with more of itself.
     */
    open suspend fun download(
        manifest: UpdateManifest,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): UpdateDownloadResult = withContext(Dispatchers.IO) {
        val directory = downloadDirectory()
        val target = targetFileFor(directory, manifest)
        val partFile = File(directory, "${target.name}.part")

        try {
            when (val transfer = downloader.transfer(manifest, partFile, onProgress)) {
                is TransferResult.Failed -> UpdateDownloadResult.Failed(transfer.reason)
                is TransferResult.Completed -> verifyAndInstall(partFile, target, manifest)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // The `.part` deliberately survives an unexpected failure too: it is
            // still a valid prefix of the release, and this is the path the
            // download never used to take (it deleted whatever was on disk).
            UpdateDownloadResult.Failed(
                e.message?.takeIf { it.isNotBlank() }
                    ?: "The download failed. Check your connection."
            )
        }
    }

    /**
     * Reads the finished `.part` back and only then lets it have a real name.
     *
     * Every failure below deletes the file rather than leaving it. That is the
     * opposite of what [download] does with a *transfer* failure, and it is the
     * right way round: these are not "not enough bytes yet", they are "these
     * bytes are not the release", and the next attempt has to start from
     * nothing or it would append a good APK onto them.
     */
    private fun verifyAndInstall(
        partFile: File,
        target: File,
        manifest: UpdateManifest
    ): UpdateDownloadResult {
        // A 200 response is not proof the body is an APK. A redirect that lands
        // on an HTML error page, or a rate-limit notice, is still a successful
        // HTTP response, and the size guard catches only bodies that are too
        // *large* - so that page would otherwise be written out as
        // anisequel-<version>.apk and handed to the package installer, which
        // fails with an opaque parse error and nothing on screen to explain it.
        // Every APK is a zip and every zip opens with a local file header, so
        // four bytes are enough to tell them apart.
        if (!looksLikeApk(partFile)) {
            partFile.delete()
            return UpdateDownloadResult.Failed(
                "That download was not an APK, so nothing was installed."
            )
        }

        // Being a zip is not the same as being *our* app. Until this existed,
        // whatever the URL served was handed straight to the package installer:
        // a release asset replaced at the CDN, a hijacked mirror, or a future
        // mistake in the manifest's download_url would all install, and the
        // failure mode is a successful install of someone else's code.
        //
        // Android will not let a different key update an existing app, so this
        // mostly protects against a first install from a tampered manifest rather
        // than a silent upgrade - but it also catches a mismatched build *before*
        // the user reaches a system dialog that says only "App not installed".
        //
        // It is also the last line of defence for a resumed download: a `.part`
        // and a body that came from different versions of the same asset can
        // produce a file that is a perfectly good zip and the wrong APK.
        if (!isSignedByThisApp(partFile)) {
            partFile.delete()
            return UpdateDownloadResult.Failed(
                "That APK is not signed with AniSequel's key, so it was not " +
                    "installed. The release may have been tampered with."
            )
        }

        if (!partFile.renameTo(target)) {
            partFile.delete()
            return UpdateDownloadResult.Failed("Could not save the download.")
        }

        return UpdateDownloadResult.Success(target, manifest)
    }

    /**
     * The already-downloaded APK for [manifest], if there is a usable one.
     *
     * ## Why this exists
     *
     * The install flow has a mandatory Settings round trip the first time: from
     * Android 8 the app has to be granted `REQUEST_INSTALL_PACKAGES` before it
     * may open the installer at all. The download happens before that check,
     * deliberately - the user has already asked to update, and refusing to start
     * over a setting they may have granted since last launch is worse than
     * downloading and then asking.
     *
     * Which means a user who has *not* yet granted it used to pay for the
     * download, go to Settings, grant it, come back, and then have to press
     * install again - and pressing "download" at any point started the download
     * over from zero, because every run wiped the cache directory first. That is
     * the one flow in the app where the user waits on the network twice for the
     * same bytes.
     *
     * The file name is derived from the manifest's version, so this returns the
     * right APK or nothing at all. A zero-length file, or one that somehow lost
     * its zip header, is rejected here rather than at the installer.
     */
    open fun downloadedApkFor(manifest: UpdateManifest): File? {
        val file = targetFileFor(downloadDirectory(), manifest)
        if (!file.isFile || file.length() <= 0L) return null

        return file.takeIf { looksLikeApk(it) }
    }

    /**
     * Whether [apk] begins with a zip local file header.
     *
     * Every APK is a zip, and a truncated or refused download is the other thing
     * that ends up at this path - so this is asked of the same bytes twice, in
     * two places that must not drift: once before a file is accepted as a
     * finished download, and once when it is offered as an installable one.
     */
    private fun looksLikeApk(apk: File): Boolean = runCatching {
        apk.inputStream().use { stream ->
            val magic = ByteArray(4)
            stream.read(magic) == 4 &&
                magic[0] == 0x50.toByte() &&
                magic[1] == 0x4B.toByte() &&
                magic[2] == 0x03.toByte() &&
                magic[3] == 0x04.toByte()
        }
    }.getOrDefault(false)

    /**
     * Where the APK for a release is written.
     *
     * Named after the version *code*, not the version name. The updater compares
     * codes because "1.0.9" sorts above "1.0.10", so a cache keyed on the name
     * could serve one release's APK to another when the code advanced without a
     * name change - and the name string reaches the filesystem unvalidated. The
     * code is the GitHub run number: monotonic by construction and safe in a path.
     */
    private fun targetFileFor(directory: File, manifest: UpdateManifest): File =
        File(directory, "anisequel-${manifest.versionCode ?: "unversioned"}.apk")

    /**
     * `filesDir`, not `cacheDir`, and that distinction is the whole reason this
     * line is worth reading twice.
     *
     * A downloaded APK is not a cache entry. It is a ~2.5 MB file the user is
     * waiting on in order to install, and it has to survive at least until they
     * come back from granting `REQUEST_INSTALL_PACKAGES` in Settings - which is
     * a guaranteed trip out of the app, and can be a long one. `cacheDir` is
     * the one directory Android is explicitly free to delete whenever it likes:
     * under storage pressure, or the moment anybody taps "Clear cache" in the
     * system settings. There is no warning and no callback.
     *
     * That made the updater lose its work in precisely the flow it exists for.
     * The download finished, the app handed the user off to Settings, the cache
     * was cleared, and on the way back there was nothing to install - so the
     * only way forward was to fetch the same bytes a second time. Which is the
     * symptom this whole change is about: an app that forgets where it saved
     * the file, and downloads it again.
     *
     * It would also have been wrong for a subtler reason: "did we already
     * download this?" is answered entirely by the file still being there, so
     * any directory the system may empty turns that answer into a lie.
     *
     * ## The other half of this decision
     *
     * This directory has to be reachable by the `FileProvider` in
     * `res/xml/update_file_paths.xml`, because the system installer is handed a
     * `content://` URI rather than a path - it runs as a different app and
     * cannot read this app's storage directly. When the move from `cacheDir` to
     * `filesDir` happened, the provider's paths did not follow it, and
     * `getUriForFile` threw on the downloaded APK at the moment the user pressed
     * Install. That throw was swallowed into "This device has no app installer",
     * which is how a working phone ended up being reported as broken.
     *
     * `UpdateFileProviderPathsTest` is what stops the two halves drifting again:
     * it reads that XML and checks it against [UPDATE_DOWNLOAD_DIR].
     */
    private fun downloadDirectory(): File =
        File(context.filesDir, UPDATE_DOWNLOAD_DIR).apply { mkdirs() }

    /**
     * Whether [apk] is signed by AniSequel's official key or matches the running app's key.
     *
     * Validates against:
     * 1. AniSequel's pinned official release signing certificate SHA-256
     * 2. The currently installed app's signing certificates or rotation history
     *
     * A debug build additionally accepts anything, so a locally signed APK can
     * be exercised end to end. A release build accepts nothing it could not
     * positively identify.
     */
    private fun isSignedByThisApp(apk: File): Boolean {
        val packageManager = context.packageManager

        // `GET_SIGNING_CERTIFICATES` alone, with no `GET_SIGNATURES` fallback.
        // The old code OR'd in the deprecated `GET_SIGNATURES` behind an
        // `SDK_INT >= P` fork, and P (28) is below the `minSdk = 31` floor, so
        // that flag described devices this build cannot be installed on.
        //
        // The deprecated `int` overload below is still correct to call on API
        // 31/32: it was only deprecated in 33, and it maps these flags onto
        // `signingInfo` on 28+. It is the `PackageInfoFlags` branch above that
        // is still needed - 33 is *above* this floor.
        val flags = PackageManager.GET_SIGNING_CERTIFICATES

        val archiveInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageArchiveInfo(
                    apk.absolutePath,
                    PackageManager.PackageInfoFlags.of(flags.toLong())
                )
            } else {
                packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            }
        } catch (e: Exception) {
            null
        }

        // Validate package name if readable
        if (archiveInfo?.packageName != null && archiveInfo.packageName != context.packageName) {
            return false
        }

        val theirs = archiveInfo?.let { signerCertificates(it) }

        // 1. Check against AniSequel's pinned official release key
        if (!theirs.isNullOrEmpty()) {
            val matchesOfficial = theirs.any { cert ->
                sha256(cert).equals(OFFICIAL_SIGNER_SHA256, ignoreCase = true)
            }
            if (matchesOfficial) return true
        }

        // 2. Check against currently running app's certificates
        val installedInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(flags.toLong())
                )
            } else {
                packageManager.getPackageInfo(context.packageName, flags)
            }
        } catch (e: Exception) {
            null
        }

        val ours = installedInfo?.let { signerCertificates(it) }

        if (!ours.isNullOrEmpty() && !theirs.isNullOrEmpty()) {
            val matchesCurrent = ours.any { ourCert ->
                theirs.any { theirCert -> ourCert.contentEquals(theirCert) }
            }
            if (matchesCurrent) return true
        }

        // 3. In debug builds, allow installation so dev/test environments can test updates
        if (BuildConfig.DEBUG) {
            return true
        }

        // 4. A release must not fall through here.
        //
        // `getPackageArchiveInfo` returns null - and so leaves `theirs` empty -
        // for anything it cannot parse as an installed package: a zip that is
        // not an APK, an APK with no signature block, or an unreadable one. The
        // previous version returned `true` in that case for *every* build, on
        // the strength of a comment about OEM limitations extracting signatures
        // from uninstalled APKs. That claim is not a reason to trust a release
        // APK: the one thing this function exists to establish is that the
        // download carries AniSequel's key, and "we could not read a key off
        // it" is the absence of that proof rather than a substitute for it.
        //
        // The cost of being strict is a refused update on the rare device that
        // cannot be read, and the message says so. The cost of being lenient is
        // an app whose update channel accepts an APK whose signature was never
        // checked - the exact outcome the pinned fingerprint above exists to
        // prevent.
        return false
    }

    /**
     * The certificates a package is signed with, or null if there are none.
     *
     * Reads `signingInfo` only. The deprecated `PackageInfo.signatures` fallback
     * sat behind the same `SDK_INT >= P` check as the flags above, and P (28) is
     * below the `minSdk = 31` floor. It is also the wrong field to reach for:
     * `signingInfo` is what knows about rotation, which is the entire reason this
     * function exists.
     */
    private fun signerCertificates(info: PackageInfo): List<ByteArray>? {
        val signers = info.signingInfo?.apkContentsSigners?.map { it.toByteArray() }
        if (!signers.isNullOrEmpty()) return signers

        val history = info.signingInfo?.signingCertificateHistory?.map { it.toByteArray() }
        if (!history.isNullOrEmpty()) return history
        return null
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    /**
     * Deletes downloaded APKs, optionally keeping one.
     *
     * This used to delete every file in the directory. It is called from the one
     * place that has already established the *current* version's APK is not
     * among them, so on the happy path it removed nothing useful - but it also
     * removed any other release still on disk, and it did so *before* the
     * replacement download had succeeded. A download that then failed left the
     * user with nothing at all, where a still-valid previous APK would have been
     * something to fall back on.
     *
     * [keep] is the file the caller wants preserved - the one it has just found
     * and is about to install. Null when there was none.
     */
    open fun clearDownloadedApk(keep: File? = null) {
        val directory = File(context.filesDir, UPDATE_DOWNLOAD_DIR)
        directory.listFiles()?.forEach { file ->
            if (keep == null || file.absolutePath != keep.absolutePath) file.delete()
        }
    }

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val OFFICIAL_SIGNER_SHA256 = "01924c4a7503820489802deb6993e5e030103a1a1a36482ecb54688fa3311f82"
    }
}