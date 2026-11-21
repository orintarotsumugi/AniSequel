package com.anisequel

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Calls that exist on the compile SDK but not on `minSdk` are crashes, not warnings.
 *
 * ## What this caught
 *
 * `DemoDataProvider.daysAgo` built its relative dates with
 *
 * ```kotlin
 * java.time.LocalDate.ofInstant(Instant.now().minusSeconds(...), ZoneOffset.UTC)
 * ```
 *
 * `LocalDate.ofInstant` is a Java 9 addition that Android only exposes from API
 * 34. `minSdk` is 31, so opening the demo on Android 12 or 13 threw
 * `NoSuchMethodError` - inside the demo repository, i.e. the first screen of the
 * demo mode. It compiles cleanly, because `compileSdk` is 37 and the method is in
 * the compile-time android.jar.
 *
 * `LocalDate.now(ZoneOffset.UTC).minusDays(days)` is the same arithmetic and has
 * been there since API 26.
 *
 * ## Why this is a test and not a note
 *
 * Android Lint already reports it (`NewApi`, as an *error*), and that is the
 * problem: `lintRelease` is advisory, it does not run on pull requests at all
 * (see the `if:` on the workflow's lint job), and `abortOnError = false` means it
 * cannot fail a run even when it does. A green pull request therefore says
 * nothing about API-level calls, and this repository's floor is a promise to
 * every device on Android 12.
 *
 * [MinimumSdkReachabilityTest] is the other half of the same rule: that one reads
 * `SDK_INT` *branches* that the floor made constant, this one reads *calls* the
 * floor does not reach.
 *
 * ## What it does not check
 *
 * Only the `java.time` additions that were found by running lint against this
 * project - the `ofInstant` family. It is not a general API-level analysis, and
 * adding a call to the set is deliberate: each entry names the API level it
 * needs and the call that replaces it.
 */
class ApiLevelCallTest {

    private val root: File = findProjectRoot()

    private fun findProjectRoot(): File {
        val start = File(System.getProperty("user.dir") ?: ".")
        var candidate: File? = start
        while (candidate != null) {
            if (File(candidate, "gradle.properties").isFile) return candidate
            candidate = candidate.parentFile
        }
        error("could not locate gradle.properties from $start")
    }

    /**
     * Each banned call, with the level it requires and what to write instead.
     *
     * `LocalDate.ofInstant`, `LocalDateTime.ofInstant`, `ZonedDateTime.ofInstant`,
     * `OffsetDateTime.ofInstant` and `Instant.ofEpochSecond(long, long)` are all
     * API 34; the first is the one that shipped, and the rest are here because
     * they are the same `java.time` Java 9 additions and would fail identically.
     */
    private val banned = listOf(
        Triple(
            "LocalDate.ofInstant",
            34,
            "LocalDate.now(zone).minusDays(n) - or LocalDate.ofEpochDay(...)"
        ),
        Triple(
            "LocalDateTime.ofInstant",
            34,
            "LocalDateTime.ofEpochSecond(...) (API 26), or Instant + atZone"
        ),
        Triple(
            "ZonedDateTime.ofInstant",
            34,
            "instant.atZone(zone), which is API 26"
        ),
        Triple(
            "OffsetDateTime.ofInstant",
            34,
            "instant.atOffset(offset), which is API 26"
        )
    )

    private fun mainSources(): List<File> {
        val dir = File(root, "app/src/main/java")
        assertTrue(
            "app/src/main/java is missing; a guard that silently passes on a missing " +
                "source tree is the failure it exists to prevent",
            dir.isDirectory
        )
        return dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    /**
     * The file with its comments removed, so only real code is scanned.
     *
     * The `DemoDataProvider` fix explains itself by naming the call it replaced,
     * and a guard that cannot tell an explanation from a call would fail on the
     * comment documenting the very bug it prevented. Line comments are stripped
     * conservatively - a `//` inside a URL literal is cut with them - which loses
     * nothing here, because deleting text can only hide a call, never invent one.
     */
    private fun codeOnly(source: String): String =
        source
            .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
            .replace(Regex("//[^\\n]*"), "")

    @Test
    fun `no source calls a java time API the minimum SDK cannot reach`() {
        val violations = mainSources().flatMap { file ->
            val text = codeOnly(file.readText())
            banned.mapNotNull { (call, level, replacement) ->
                if (!text.contains(call)) {
                    null
                } else {
                    "${file.relativeTo(root)} calls `$call`, which needs API $level " +
                        "while minSdk is 31. Use $replacement instead."
                }
            }
        }
        assertTrue(
            "an API call above the floor compiles cleanly and then throws " +
                "NoSuchMethodError on the devices the floor exists to support:\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }
}
