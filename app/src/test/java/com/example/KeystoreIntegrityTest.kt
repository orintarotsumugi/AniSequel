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

    /**
     * Drops whole-line `#` comments from a shell script embedded in YAML.
     *
     * These steps carry long explanations of what used to go wrong, and those
     * explanations have to quote the old command in order to be useful. Reading
     * them back as code would report `keytool -genkeypair` as still being run
     * by a step whose only job is to prove it is not.
     */
    private fun stripShellComments(script: String): String =
        script.lineSequence()
            .filterNot { it.trimStart().startsWith("#") }
            .joinToString("\n")

    /**
     * The `run:` body of a workflow step, with the comments removed.
     *
     * Reading the whole step back is not good enough for anything that has to
     * be *used*: a step's `env:` block sits above its `run:` block, so a
     * substring check for a variable name is satisfied by the line that merely
     * declares it. That is exactly how
     * [the release signer is asserted, not just printed] passed for a step that
     * printed the digest and never compared it.
     */
    private fun runScriptOf(stepYaml: String): String =
        stripShellComments(stepYaml.substringAfter("run: |", ""))

    /** Key-shaped files that must never be committed, checked at the repo root. */
    private val forbiddenKeyFiles = listOf(
        "debug.keystore.base64",
        "release-key.jks",
        "my-upload-key.jks",
        "anisequel-release.jks",
        "keystore.jks"
    )

    /**
     * Every path git currently tracks, or fails the test if git cannot answer.
     *
     * Failing rather than returning an empty list matters: a git failure here
     * would otherwise look exactly like a clean repository, and the test would
     * pass without having checked anything. This method *used to* return
     * `emptyList()` on every one of those paths, while this comment claimed it
     * failed - so the guard was green precisely when it could not see the repo.
     */
    private fun trackedFiles(): List<String> {
        val gitDir = File(root, ".git")
        if (!gitDir.exists()) {
            // No repository to ask. Scanning the working tree is not a
            // substitute: the release workflow decodes `release-key.jks` into
            // the workspace before this task runs (see the test below), so a
            // disk scan cannot tell a committed key from a generated one. When
            // we cannot enumerate tracked files we have not checked anything,
            // and a silent pass is the failure mode this guard exists to stop.
            error(
                "No .git at ${root.path}: cannot enumerate tracked files, so the " +
                    "committed-key guard did not run. Check out the repository with git."
            )
        }
        val process = try {
            ProcessBuilder("git", "ls-files")
                .directory(root)
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            error("Could not run `git ls-files` in ${root.path}: $e")
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exit = process.waitFor()

        check(exit == 0) {
            "`git ls-files` exited $exit in ${root.path}; the workspace may not be a " +
                "git repository, so the committed-key guard could not run. Output:\n$output"
        }

        // One path per line. A path containing a space survives this intact,
        // and the only thing this test does with the names is match their
        // extensions.
        return output.split('\n').map { it.trim() }.filter { it.isNotBlank() }
    }

    /**
     * Asserts on what git *tracks*, not on what is on disk.
     *
     * The distinction is load-bearing: the release workflow decodes
     * `release-key.jks` into the workspace before the test task runs, and a
     * pull request generates a throwaway key in the same place. Both files are
     * expected to exist on disk during a run - they are just never committed.
     * Checking for their absence on disk therefore fails every real release
     * build while telling us nothing about the one thing that matters.
     */
    @Test
    fun `no signing key material is committed to the repository`() {
        val leaked = trackedFiles().filter { path ->
            val name = path.substringAfterLast('/')
            name in forbiddenKeyFiles ||
                name.endsWith(".jks") ||
                name.endsWith(".p12") ||
                name.endsWith(".pfx") ||
                name.endsWith(".keystore")
        }

        assertTrue(
            "Signing key material must never be committed - found: $leaked. The release " +
                "key belongs in the KEYSTORE_BASE64 secret, because a committed key lets " +
                "anyone sign an update APK that the in-app updater accepts.",
            leaked.isEmpty()
        )
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
        // here is easy to get wrong, because whitespace can match zero
        // characters and then the lookahead sees the space before the
        // expression and wrongly succeeds.
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

    /**
     * The gap that let this happen.
     *
     * The test above checks that the workflow *references* `secrets.STORE_PASSWORD`
     * and that no YAML `env:` key hardcodes it. It passed throughout, because the
     * real defect was neither of those: both call sites fell back to a literal -
     * `${STORE_PASSWORD:-anisequel123r}` in the workflow and
     * `System.getenv("STORE_PASSWORD") ?: "anisequel123r"` in the build script.
     *
     * The literal was also *wrong*, so it could not have opened the real keystore
     * even had the secret been present. What it did instead was push the workflow
     * down its own fallback branch, which generated a fresh random signing key on
     * every run - three consecutive releases, three different keys, none of them
     * installable over the last. Every run was green.
     *
     * The workflow half came back. The same shape - a `${VAR:-literal}` default
     * and a `keytool -genkeypair` behind it - was reintroduced in the restore
     * step with its three `require_secret` calls commented out "so builds never
     * fail on missing secrets", so both halves are asserted here.
     */
    @Test
    fun `no signing password is guessed when the secret is absent`() {
        val workflow = read(".github", "workflows", "android-release.yml")
        val buildScript = read("app", "build.gradle.kts")

        assertFalse(
            "app/build.gradle.kts must not fall back to a literal for " +
                "System.getenv(\"STORE_PASSWORD\") ?: ... - leave it null and let AGP " +
                "fail the release build instead",
            buildScript.contains("System.getenv(\"STORE_PASSWORD\") ?:")
        )
        assertFalse(
            "app/build.gradle.kts must not fall back to a literal for " +
                "System.getenv(\"KEY_PASSWORD\") ?: ... - leave it null and let AGP " +
                "fail the release build instead",
            buildScript.contains("System.getenv(\"KEY_PASSWORD\") ?:")
        )

        val restoreStep = workflow
            .substringAfter("name: Restore the release signing key from secrets")
            .substringBefore("- name: Validate the signing key")

        val restoreScript = runScriptOf(restoreStep)

        for (variable in listOf("KEYSTORE_BASE64", "STORE_PASSWORD", "KEY_PASSWORD")) {
            assertFalse(
                "the restore step must not default \$$variable to a literal " +
                    "(`\${$variable:-...}`). A repository with no secrets configured " +
                    "then signs every release with a substitute key, and Android refuses " +
                    "to install it over the app it is meant to update.",
                Regex("""\$\{${variable}:-""").containsMatchIn(restoreScript)
            )
        }

        assertFalse(
            "the restore step must not generate a replacement signing key. Rotating " +
                "the release key is unrecoverable for anyone who already installed the " +
                "app, so a missing secret has to fail the build instead. A throwaway " +
                "key belongs only in the pull-request step, which never publishes.",
            restoreScript.contains("keytool -genkeypair")
        )

        for (variable in listOf("KEYSTORE_BASE64", "STORE_PASSWORD", "KEY_PASSWORD")) {
            assertTrue(
                "the restore step must call require_secret $variable, not merely " +
                    "reference secrets.$variable. A reference proves the value can be " +
                    "read; it does not prove the build stops when it is empty.",
                restoreScript.contains("require_secret $variable")
            )
        }
    }

    /**
     * The release workflow restores keystore material from secrets.
     */
    @Test
    fun `the release key is restored from secrets`() {
        val workflow = read(".github", "workflows", "android-release.yml")

        val restoreStep = stripShellComments(
            workflow
                .substringAfter("name: Restore the release signing key from secrets")
                .substringBefore("- name: Validate the signing key")
        )

        assertTrue(
            "the release-signing step must handle KEYSTORE_BASE64 secret",
            restoreStep.contains("KEYSTORE_BASE64")
        )
    }

    /**
     * The signer is checked, not merely printed.
     *
     * This step used to run `apksigner verify --print-certs`, which exits zero
     * for any well-formed signature and just prints the digest. A release signed
     * with the wrong key passed it exactly as readily as the right one - which is
     * why three keys in a row shipped without a single red build.
     *
     * Pinning the expected digest in the `env:` block fixed that and then hid
     * the next one: the name went unused in the script, so the digest was
     * extracted, echoed, and the step ended green whatever it was. Because the
     * `env:` block is part of the step's text, a check for the name alone was
     * satisfied by the declaration. The assertions below therefore read the
     * `run:` body only, and require an actual comparison that can fail the job.
     */
    @Test
    fun `the release signer is asserted, not just printed`() {
        val workflow = read(".github", "workflows", "android-release.yml")

        val verifyStep = workflow
            .substringAfter("name: Verify APK Release Signing Integrity")
            .substringBefore("- name: Upload Build Artifacts")

        val verifyScript = runScriptOf(verifyStep)

        assertTrue(
            "the verification step must compare the signer against a pinned " +
                "fingerprint, not only print it",
            verifyScript.contains("EXPECTED_SIGNER_SHA256")
        )

        assertTrue(
            "the verification step must read the digest it is going to compare " +
                "(no \$ACTUAL means nothing is being checked)",
            verifyScript.contains("ACTUAL")
        )

        val comparesSigner = verifyScript.lineSequence().any { line ->
            line.contains("ACTUAL") &&
                line.contains("EXPECTED_SIGNER_SHA256") &&
                (line.contains("!=") || line.contains("=="))
        }
        assertTrue(
            "the verification step must compare the digest it extracted against " +
                "EXPECTED_SIGNER_SHA256 on one line. Declaring the expected value and " +
                "then never using it leaves the step green for any key at all.",
            comparesSigner
        )

        assertTrue(
            "the verification step must fail the job on a mismatch (exit 1). Without " +
                "a failing exit a mismatch is only a log line, and a release signed with " +
                "the wrong key is still published.",
            verifyScript.lineSequence().any { it.trim() == "exit 1" }
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