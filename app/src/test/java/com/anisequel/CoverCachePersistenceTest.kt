package com.anisequel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Covers have to stay saved, and for a while they did not.
 *
 * Both image paths cached into `cacheDir` - Coil's disk cache at
 * `cacheDir/anilist_cover_cache`, and the widgets' bytes nowhere at all beyond a
 * decoded bitmap in an `LruCache`. `cacheDir` belongs to Android: it is emptied
 * whenever the system wants the space back, and "Clear cache" in Settings does the
 * same on request. So the app forgot artwork it had already downloaded, redrew
 * placeholders over a list that had not changed, and bought every poster again on
 * a cold start. On the launcher, the bitmap cache simply died with the process, so
 * every widget refresh after an app kill downloaded the same four covers.
 *
 * The fix is a location, which is exactly the kind of thing a later "tidy-up" can
 * undo without any test noticing: moving the directory back to `cacheDir`
 * compiles, passes lint, and the only symptom is that the reader sees grey boxes
 * again a few weeks later. These are source assertions because the failure is
 * "the call names the wrong directory" rather than a value a unit test can
 * observe - `Context.cacheDir` and `noBackupFilesDir` are both just `File`s until
 * the system decides to clean one of them.
 */
class CoverCachePersistenceTest {

    private val root = findProjectRoot()

    /**
     * Comments are stripped before matching.
     *
     * These files explain the old `cacheDir` location in the comments above the
     * new one - that explanation is the reason the change is not reverted, and it
     * must not make a test that looks for `cacheDir` fail.
     */
    private fun source(path: String): String = File(root, path).readText()
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""//[^\n]*"""), "")

    @Test
    fun `coil's disk cache is not in a directory android may empty`() {
        val network = source(
            "app/src/main/java/com/anisequel/data/network/NetworkClient.kt"
        )

        assertTrue(
            "the cover disk cache is not under noBackupFilesDir, so a cache wipe or " +
                "the system reclaiming space makes the app forget artwork it already " +
                "downloaded and fetch every poster again",
            network.contains("noBackupFilesDir.resolve(\"anilist_cover_cache\")")
        )
        assertFalse(
            "something in NetworkClient still points the image cache at cacheDir. " +
                "That directory is the system's to empty; covers are page content, " +
                "not scratch data.",
            network.contains("cacheDir.resolve")
        )
    }

    @Test
    fun `a saved cover is keyed by its url so replaced art is a miss`() {
        val network = source(
            "app/src/main/java/com/anisequel/data/network/NetworkClient.kt"
        )

        // AniList puts a content hash in an artwork URL, so this is what makes
        // "the cover only changes when AniList changes it" true with no TTL and no
        // invalidation: a new picture is a new URL, and a new URL is a miss.
        assertTrue(
            "the image loader no longer disables response-header cache policy. " +
                "A shared host's short max-age would expire saved art that AniList " +
                "has not changed, which is the opposite of a saved cover.",
            network.contains("respectCacheHeaders(false)")
        )
    }

    @Test
    fun `widget covers are saved beyond the life of the process`() {
        val cover = source(
            "app/src/main/java/com/anisequel/ui/widget/WidgetCover.kt"
        )

        assertTrue(
            "widget covers are not written to noBackupFilesDir, so they are lost " +
                "with the process and every launcher refresh downloads them again",
            cover.contains("context.noBackupFilesDir")
        )
        assertFalse(
            "widget covers still use cacheDir, which Android may empty at any time",
            cover.contains("cacheDir")
        )
        // A half-written file carrying the cover's own name would be read on the
        // next refresh, fail to decode, and - because the URL has not changed -
        // keep failing. The temp file plus the rename makes that unrepresentable.
        assertTrue(
            "widget cover writes are not atomic: a truncated file would keep the " +
                "cover broken for as long as its URL stays the same",
            cover.contains(".part") && cover.contains("renameTo(")
        )
    }

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
