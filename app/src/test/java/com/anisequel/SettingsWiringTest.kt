package com.anisequel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Source-level guards for Settings defects that a device is not needed to prove.
 *
 * The Compose UI test source set is unavailable in this development environment, so
 * each of these pins a structural fact instead: a platform value that has to be
 * re-read rather than remembered, a tone that has to come from the emphasis rung
 * rather than an alpha, and a preference write that has to store the value the rest
 * of the page compares against.
 */
class SettingsWiringTest {

    private val root = findProjectRoot()

    /**
     * Resolved upward from the working directory so the test does not depend on
     * which module's test task runs it.
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

    private fun source(path: String): String = File(root, path).readText()
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""//[^\n]*"""), "")

    @Test
    fun `the app lock switch re-reads the device lock`() {
        val code = source(
            "app/src/main/java/com/anisequel/ui/screens/settings/DeviceManagerTab.kt"
        )

        assertTrue(
            "the card must watch the resume state",
            code.contains("rememberIsResumed()")
        )
        assertTrue(
            "the keyguard read must be repeated in a resume-keyed effect, not only " +
                "seeded into a remember",
            code.contains("LaunchedEffect(resumed)") &&
                Regex("isDeviceSecure").findAll(code).count() >= 2
        )
    }

    @Test
    fun `settings dividers and boundaries come from the emphasis rung`() {
        val settings = File(
            root,
            "app/src/main/java/com/anisequel/ui/screens/settings"
        )
        val offenders = settings.listFiles()
            ?.filter { it.extension == "kt" }
            ?.filter { it.readText().contains("outlineVariant.copy(alpha") }
            ?.map { it.name }
            .orEmpty()

        assertTrue(
            "an outlineVariant at an alpha is about 1.4:1 on a light card and reads " +
                "as no line at all; use AniSequelTheme.emphasis.divider/boundary " +
                "instead: $offenders",
            offenders.isEmpty()
        )
    }

    @Test
    fun `the persona fields store what the stamp chips compare against`() {
        val code = source(
            "app/src/main/java/com/anisequel/ui/screens/settings/JokeSettingsTab.kt"
        )

        assertTrue(
            "the name is stored trimmed, or a trailing space stops every stamp chip " +
                "from matching the stored value",
            code.contains("setSpeakerName(trimmed)")
        )
        assertTrue(
            "the stamp is stored trimmed for the same reason",
            code.contains("setStamp(trimmed)")
        )
        assertFalse(
            "an over-long paste must be clamped rather than dropped",
            code.contains("if (next.length <= MAX_SPEAKER_NAME_LENGTH)") ||
                code.contains("if (next.length <= MAX_STAMP_LENGTH)")
        )
    }
}
