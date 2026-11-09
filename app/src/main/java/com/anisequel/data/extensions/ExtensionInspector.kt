package com.anisequel.data.extensions

import android.content.Context
import java.io.File

/** What can be learned about an extension file without executing its code. */
data class ExtensionFileInfo(
    val packageName: String,
    val version: String,
    val label: String,
    val sourceNames: List<String> = emptyList()
)

/**
 * Reads an extension file's own metadata. Never loads or executes the file.
 *
 * The catalogue's package name is the index's claim. The APK's manifest is the
 * APK's claim, and before this class existed the install path trusted the index
 * and handed whatever it downloaded to the package installer. A mirror whose
 * `apk` URL points at the wrong package would therefore install an unrelated app
 * under the row the reader tapped. Inspecting the archive first makes that a
 * named refusal.
 */
class ExtensionInspector(context: Context) {

    private val packageManager = context.applicationContext.packageManager

    fun inspect(file: File): ExtensionFileInfo? = when (file.extension.lowercase()) {
        "apk" -> inspectApk(file)
        "js" -> inspectModule(file)
        // A DEX/JAR is binary. Running the JS metadata regex over it produced
        // plausible-looking garbage, so those formats keep their file name and
        // "local" version until a real archive reader is added.
        "jar", "cs3", "dex" -> ExtensionFileInfo(
            packageName = "",
            // Empty so the catalogue's own version survives; these formats do
            // not carry a readable version without executing the archive.
            version = "",
            label = file.nameWithoutExtension
        )
        else -> null
    }

    /**
     * Reads the manifest from the downloaded file.
     *
     * `getPackageArchiveInfo` does not install anything and works for a file this
     * app owns. `loadLabel` needs the sourceDir pointers filled in because an
     * archive's ApplicationInfo has no installed resource path.
     */
    private fun inspectApk(file: File): ExtensionFileInfo? {
        val archive = runCatching {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        }.getOrNull() ?: return null
        val application = archive.applicationInfo ?: return null
        application.sourceDir = file.absolutePath
        application.publicSourceDir = file.absolutePath
        val label = runCatching { application.loadLabel(packageManager).toString() }
            .getOrDefault("")
            .ifBlank { archive.packageName }
        val version = archive.versionName?.takeIf { it.isNotBlank() }
            ?: archive.longVersionCode.toString()
        return ExtensionFileInfo(
            packageName = archive.packageName.orEmpty(),
            version = version,
            label = label
        )
    }

    /**
     * Pulls the fields a JS module declares at the top of its file.
     *
     * This is metadata only: the module is never evaluated, and a missing field
     * falls back to the filename. The 96 KB read is deliberately bounded so a
     * module that is mostly minified payload cannot turn a listing into a large
     * allocation.
     */
    private fun inspectModule(file: File): ExtensionFileInfo? {
        val text = runCatching {
            file.inputStream().bufferedReader().use { reader ->
                val buffer = CharArray(96 * 1024)
                val read = reader.read(buffer)
                if (read <= 0) "" else String(buffer, 0, read)
            }
        }.getOrNull() ?: return null
        val name = firstString(text, "pluginName", "name")
            ?: file.nameWithoutExtension
        val version = firstString(text, "version", "versionName") ?: "local"
        val sources = Regex("""["']sourceName["']\s*:\s*["']([^"']+)["']""")
            .findAll(text)
            .map { it.groupValues[1] }
            .distinct()
            .take(24)
            .toList()
        return ExtensionFileInfo(
            packageName = "",
            version = version,
            label = name.take(80),
            sourceNames = sources
        )
    }

    private fun firstString(text: String, vararg keys: String): String? {
        for (key in keys) {
            val match = Regex("""["']?$key["']?\s*[:=]\s*["']([^"']{1,80})["']""")
                .find(text)
            val value = match?.groupValues?.get(1)?.trim().orEmpty()
            if (value.isNotEmpty()) return value
        }
        return null
    }

    /**
     * True only when the platform lets this app see the package.
     *
     * On Android 11+ that is a best-effort answer, not a verdict: package
     * visibility can hide an installed extension. The install record remains the
     * source of truth, as documented on [InstalledExtension].
     */
    fun isPackageInstalled(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return runCatching {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }.isSuccess
    }

}
