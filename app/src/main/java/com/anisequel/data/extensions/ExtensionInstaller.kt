package com.anisequel.data.extensions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Downloads extension files and hands APKs to the platform installer.
 *
 * This class never executes extension code. It fetches the file, puts it in the
 * app's own `filesDir/extensions` (not the cache, which Android may empty between
 * the download and the permission round trip) and opens the system package
 * installer. Loading Aniyomi/CloudStream code into the app is a separate runtime
 * and is not in this beta.
 */
class ExtensionInstaller(context: Context) {

    private val appContext = context.applicationContext

    private val directory: File
        get() = appContext.filesDir.resolve("extensions").apply { mkdirs() }

    /** Whether Android will let this app open its package installer. */
    fun canInstallPackages(): Boolean =
        appContext.packageManager.canRequestPackageInstalls()

    /**
     * The permission is per-app and lives in Settings, which this app cannot
     * grant itself. The package-scoped screen is the first route and the others
     * are the same ladder the app updater documents: re-skinned builds register
     * different subsets of these.
     */
    fun openInstallPermissionSettings() {
        val packageUri = Uri.parse("package:${appContext.packageName}")
        val routes = listOf(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES),
            Intent(Settings.ACTION_SECURITY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (route in routes) {
            route.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val opened = runCatching { appContext.startActivity(route) }.isSuccess
            if (opened) return
        }
    }

    /**
     * Streams [url] into the extensions directory.
     *
     * A 120 MB ceiling is generous for an extension APK and small enough that a
     * wrong URL cannot fill the phone. The partial file is renamed only after the
     * body has been read completely, so a truncated download is never handed to
     * the installer.
     */
    suspend fun download(
        url: String,
        fileName: String,
        onProgress: (Float) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        val safeName = fileName
            .substringAfterLast('/')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(80)
            .ifBlank { "extension.apk" }
        val target = directory.resolve(safeName)
        val part = directory.resolve("$safeName.part")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "AniSequel extension installer")
            .build()

        ExtensionHttp.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Download failed (HTTP ${response.code})")
            }
            val body = response.body ?: throw IOException("The download was empty")
            val total = body.contentLength()
            if (total > MAX_EXTENSION_BYTES) throw IOException("This extension is too large")

            part.delete()
            body.byteStream().use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var readTotal = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        readTotal += read
                        if (readTotal > MAX_EXTENSION_BYTES) {
                            throw IOException("This extension is too large")
                        }
                        output.write(buffer, 0, read)
                        if (total > 0) {
                            onProgress((readTotal.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                    output.flush()
                }
            }
        }
        if (target.exists()) target.delete()
        if (!part.renameTo(target)) throw IOException("Could not save the extension")
        onProgress(1f)
        target
    }

    /**
     * Copies a file the reader picked from storage into the extension directory.
     *
     * The picker hands us a content URI the app cannot hand to the installer;
     * the package installer needs a real file (and this app's FileProvider path
     * is a real file). Copying once also means the reader can revoke the
     * document permission afterwards and the install still completes.
     */
    suspend fun copyFromUri(uri: Uri, fileName: String): File = withContext(Dispatchers.IO) {
        val safeName = fileName
            .substringAfterLast('/')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(80)
            .ifBlank { "local-extension.apk" }
        val target = directory.resolve(safeName)
        val resolver = appContext.contentResolver
        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("That file could not be opened")
        target
    }

    /** Opens the system installer for a downloaded APK. */
    fun launchInstaller(file: File): Boolean {
        val uri = runCatching {
            FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                file
            )
        }.getOrNull() ?: return false

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { appContext.startActivity(intent) }.isSuccess
    }

    /**
     * The package currently installed by [packageName], if the platform lets us
     * see it. Used to turn "install requested" into "installed" on resume, not as
     * the source of truth - see [InstalledExtension].
     */
    fun isPackageInstalled(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return runCatching {
            @Suppress("DEPRECATION")
            appContext.packageManager.getPackageInfo(packageName, 0)
        }.isSuccess
    }

    /** The APK path a record points at, when it still exists. */
    fun apkFor(record: InstalledExtension): File? =
        record.apkPath?.let(::File)?.takeIf { it.isFile }

    /**
     * Restarts the app into a fresh process.
     *
     * `makeRestartActivityTask` is what clears the existing task and starts the
     * launcher activity in a new one; a plain launcher intent would stack a second
     * copy of the app and leave the installed extension invisible until the reader
     * swiped the old task away, which is the exact "I installed it and nothing
     * changed" report the restart prompt exists to prevent.
     */
    fun restartApp(): Boolean {
        val launch = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
            ?: return false
        val component = launch.component ?: return false
        val restart = Intent.makeRestartActivityTask(component)
        return runCatching {
            appContext.startActivity(restart)
        }.isSuccess
    }

    private companion object {
        const val MAX_EXTENSION_BYTES = 120L * 1024 * 1024
    }
}
