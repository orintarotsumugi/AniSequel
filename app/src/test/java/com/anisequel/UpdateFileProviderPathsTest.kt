package com.anisequel

import com.anisequel.data.update.UPDATE_DOWNLOAD_DIR
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The `FileProvider` paths, against the directory the updater actually writes to.
 *
 * ## What shipped
 *
 * Every update on every device ended with **"This device has no app installer"**,
 * on phones with a working package installer. The download was fine - the file
 * was on disk, verified, and correct - and the message told the user their phone
 * could not install apps, which is the one thing they would go and check and the
 * one thing that was not wrong.
 *
 * ## Why
 *
 * Installing hands the system installer a `content://` URI, because the
 * installer runs as a different app and cannot read this app's private storage by
 * path. The `FileProvider` in `AndroidManifest.xml` publishes what may be
 * shared, and it does that by listing directory roots in
 * `res/xml/update_file_paths.xml`.
 *
 * #61 moved the downloads out of `cacheDir` and into the app's private files
 * storage, for a good reason written at length in `UpdateManager`: the cache can
 * be emptied by the system at any moment, and a download that vanished during
 * the trip to Settings had to be fetched from zero again. The provider's paths
 * did not follow. It still published `<cache-path path="updates/"/>` - a
 * directory nothing wrote to any more.
 *
 * So `FileProvider.getUriForFile` threw `IllegalArgumentException` on the
 * downloaded APK, every time, at the moment the user pressed Install.
 * `openInstaller` contained the throw - correctly, it exists so the last step of
 * an install cannot crash the app - and contained it into `false`, which the
 * controller reported as "This device has no app installer".
 *
 * ## Why a test and not a careful read
 *
 * Nothing about moving a download directory looks like it can break an install,
 * and the path string in that XML reads as a perfectly ordinary configuration
 * line. It compiles. It lints. It is only wrong at runtime, on a device, after
 * the user has waited for a download, and the only evidence is a message that
 * blames their hardware. Two strings in two files have to agree, and nothing
 * else in the build was checking.
 *
 * This asserts the agreement directly: the directory `UpdateManager` writes into
 * has to be inside one of the roots the provider publishes. Moving either one
 * without the other turns this red.
 */
class UpdateFileProviderPathsTest {

    private val root: File = findProjectRoot()

    private val providerPaths: String =
        File(root, "app/src/main/res/xml/update_file_paths.xml").readText()

    /** One `<files-path>`, `<cache-path>`, ... entry, as (tag, path). */
    private data class Root(val tag: String, val path: String)

    private val declaredRoots: List<Root> =
        Regex("""<([a-z-]+)\s[^>]*?path\s*=\s*"([^"]*)"""")
            .findAll(providerPaths)
            .map { Root(it.groupValues[1], it.groupValues[2]) }
            .toList()

    @Test
    fun `the file provider publishes the directory downloads are written to`() {
        assertTrue(
            "could not read any path entries out of " +
                "app/src/main/res/xml/update_file_paths.xml. If the format has " +
                "changed, this test has to fail rather than pass vacuously - it " +
                "is the only thing checking that the installer can be handed a " +
                "downloaded APK at all.",
            declaredRoots.isNotEmpty()
        )

        // The same containment rule FileProvider applies: a root matches when the
        // file is the root itself or lives under it. A `files-path` of "updates"
        // is written with or without a trailing slash depending on the project,
        // so the comparison trims rather than assuming.
        val covering = declaredRoots.filter { declared ->
            declared.tag == "files-path" &&
                declared.path.trimEnd('/') == UPDATE_DOWNLOAD_DIR
        }

        assertTrue(
            "the download directory is filesDir/$UPDATE_DOWNLOAD_DIR, but " +
                "app/src/main/res/xml/update_file_paths.xml publishes " +
                "$declaredRoots. FileProvider.getUriForFile only accepts a file " +
                "inside a published root, so the installer's content:// URI " +
                "cannot be built and every update reports that the device has no " +
                "app installer.",
            covering.isNotEmpty()
        )
    }

    @Test
    fun `the provider does not publish the cache directory for downloads`() {
        // The directory that is still declared here is the one #61 moved away
        // from, and keeping it is not harmless bookkeeping: it reads as though
        // something is still written there, and it is the entry that was left
        // behind while the `files-path` went missing.
        val cacheEntries = declaredRoots.filter { declared ->
            declared.tag == "cache-path" &&
                declared.path.trimEnd('/') == UPDATE_DOWNLOAD_DIR
        }

        assertTrue(
            "app/src/main/res/xml/update_file_paths.xml still publishes a " +
                "cache-path for $UPDATE_DOWNLOAD_DIR. Nothing writes there: " +
                "UpdateManager downloads into filesDir so the file survives a " +
                "trip to Settings. A stale root is how the provider and the " +
                "download directory came to disagree in the first place.",
            cacheEntries.isEmpty()
        )
    }

    @Test
    fun `the manifest declares the provider these paths belong to`() {
        // The paths file is inert without the provider that reads it, and its
        // authority is interpolated - "${applicationId}.fileprovider" - so the
        // two are joined at build time and cannot be checked by eye.
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()

        assertTrue(
            "AndroidManifest.xml no longer declares a FileProvider, so " +
                "update_file_paths.xml configures nothing and the installer can " +
                "never be handed the APK.",
            manifest.contains("androidx.core.content.FileProvider")
        )
        assertTrue(
            "AndroidManifest.xml must point the provider at " +
                "@xml/update_file_paths.xml; a different resource leaves this " +
                "file unused and the downloads unreachable.",
            manifest.contains("@xml/update_file_paths")
        )

        val provider = Regex("""<provider[\s\S]*?/>""")
            .find(manifest)
            ?.value
            ?: error("could not find a <provider> element in AndroidManifest.xml")

        assertTrue(
            "the FileProvider must stay exported=\"false\". It publishes the " +
                "directory downloaded APKs live in, and anything reachable there " +
                "should be readable only for the one install intent that asks " +
                "for it.",
            provider.contains("android:exported=\"false\"")
        )
        assertTrue(
            "the FileProvider must keep grantUriPermissions=\"true\", or the " +
                "installer receives a content:// URI it has no right to read.",
            provider.contains("android:grantUriPermissions=\"true\"")
        )
    }

    /**
     * The Gradle test task runs with the project directory as the working
     * directory, but resolve upward anyway so the test does not depend on which
     * module is running it.
     */
    private fun findProjectRoot(): File {
        val start = File(System.getProperty("user.dir") ?: ".")
        var candidate: File? = start
        while (candidate != null) {
            if (File(candidate, "gradle.properties").isFile) return candidate
            candidate = candidate.parentFile
        }
        error("could not locate gradle.properties from $start")
    }
}