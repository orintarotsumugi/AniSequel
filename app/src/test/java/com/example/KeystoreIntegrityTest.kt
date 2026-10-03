package com.example

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the release signing key against the two ways it can leak.
 *
 * The key used to be committed to the repository as `debug.keystore.base64`.
 * Base64 is an encoding, not encryption, so every clone of the repo could
 * decode it and sign an arbitrary "update" APK. That matters more than it
 * sounds, because `UpdateManager` accepts a downloaded APK whenever its
 * certificate matches the running app's - a check an attacker with the key can
 * satisfy. The same key also has to stay *stable* forever: Android refuses to
 * update an app signed with a different key
 * (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).
 *
 * Those two requirements pull in opposite directions, so this test pins both
 * ends: no key material in the repository, and no way to accidentally sign with
 * a substitute.
 *
 * The key itself now lives only in the `KEYSTORE_BASE64`, `STORE_PASSWORD` and
 * `KEY_PASSWORD` repository secrets. This test cannot check those - they are
 * not readable from a build - so it checks everything around them instead.
 */
class KeystoreIntegrityTest {

    private val root: File = findProjectRoot()

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

    private fun read(vararg parts: String): String = File(root, parts.joinToString("/")).readText()

    /** Key-shaped files that must never be committed, checked at the repo root. */
    private val forbiddenKeyFiles = listOf(
        "debug.keystore.base64",
        "release-key.jks",
        "my-upload-key.jks",
        "anisequel-release.jks",
        "keystore.jks"
    )

    @Test
    fun `no signing key material is committed in the repository root`() {
        for (name in forbiddenKeyFiles) {
            val file = File(root, name)
            assertFalse(
                "$name must not exist in the repository. The release signing key belongs " +
                    "in the KEYSTORE_BASE64 secret, not in version control - a committed key " +
                    "lets anyone sign an update APK that the in-app updater accepts.",
                file.exists()
            )
        }
    }

    @Test
    fun `gitignore blocks keystore extensions from being committed again`() {
        val ignored = read(".gitignore")
        for (pattern in listOf("*.jks", "*.keystore", "*.p12", "*.pfx")) {
            assertTrue(
                ".gitignore must contain '$pattern' so a keystore cannot be committed by " +
                    "accident and silently re-expose the release signing key.",
                ignored.lineSequence().any { it.trim() == pattern }
            )
        }
    }

    @Test
    fun `release workflow reads the key and passwords from secrets`() {
        val workflow = read(".github", "workflows", "android-release.yml")

        for (secret in listOf("KEYSTORE_BASE64", "STORE_PASSWORD", "KEY_PASSWORD")) {
            assertTrue(
                "android-release.yml must reference secrets.$secret. Without it the release " +
                    "build has no signing material and cannot produce an installable APK.",
                workflow.contains("secrets.$secret")
            )
        }

        // The hardcoded values this workflow used to carry. These are public in
        // a public repo, so each one on its own defeats having a secret key at
        // all - the password is as much a part of the secret as the key bytes.
        //
        // Checked line by line rather than with a regex: a negative lookahead
        // here is easy to get wrong, because `\s*` can match zero characters and
        // then the lookahead sees the space before `${{` and wrongly succeeds.
        for (variable in listOf("STORE_PASSWORD", "KEY_PASSWORD")) {
            val hardcoded = workflow.lineSequence().filter { line ->
                val trimmed = line.trim()
                trimmed.startsWith("$variable:") && !trimmed.contains("\${")
            }.toList()
            assertTrue(
                "android-release.yml must not hardcode $variable (found: $hardcoded). A " +
                    "password in a public workflow file makes the keystore secret pointless.",
                hardcoded.isEmpty()
            )
        }

        assertFalse(
            "android-release.yml must not reference the deleted debug.keystore.base64.",
            workflow.contains("debug.keystore.base64")
        )
    }

    @Test
    fun `release signing config reads the key from the environment`() {
        val buildScript = read("app", "build.gradle.kts")

        assertTrue(
            "app/build.gradle.kts must read the keystore path from KEYSTORE_PATH so CI and a " +
                "local signed build agree on one key.",
            buildScript.contains("KEYSTORE_PATH")
        )
        assertTrue(
            "app/build.gradle.kts must read the store password from the environment rather " +
                "than embedding it.",
            buildScript.contains("System.getenv(\"STORE_PASSWORD\")")
        )
        assertTrue(
            "app/build.gradle.kts must read the key password from the environment rather " +
                "than embedding it.",
            buildScript.contains("System.getenv(\"KEY_PASSWORD\")")
        )
    }
}
