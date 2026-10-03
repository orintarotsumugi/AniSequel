package com.example

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64

/**
 * Asserts that the committed release signing key material (`debug.keystore.base64`)
 * is always present, valid, and uncorrupted in the repository root.
 *
 * Android app upgrades require consecutive builds to be signed with the identical
 * certificate (otherwise failing with INSTALL_FAILED_UPDATE_INCOMPATIBLE).
 */
class KeystoreIntegrityTest {

    private val root: File = findProjectRoot()

    private fun findProjectRoot(): File {
        val start = File(System.getProperty("user.dir") ?: ".")
        var candidate: File? = start
        while (candidate != null) {
            if (File(candidate, "gradle.properties").isFile) return candidate
            candidate = candidate.parentFile
        }
        error("could not locate project root from $start")
    }

    @Test
    fun `debug keystore base64 file exists and is not empty`() {
        val keystoreFile = File(root, "debug.keystore.base64")
        assertTrue(
            "debug.keystore.base64 must exist in project root to ensure consistent release signing across CI builds",
            keystoreFile.exists() && keystoreFile.isFile
        )
        assertTrue(
            "debug.keystore.base64 must not be empty",
            keystoreFile.length() > 100
        )
    }

    @Test
    fun `debug keystore base64 is valid decodable bytes`() {
        val keystoreFile = File(root, "debug.keystore.base64")
        val content = keystoreFile.readText().trim()
        val decoded = try {
            Base64.getMimeDecoder().decode(content)
        } catch (e: Exception) {
            null
        }

        assertTrue(
            "debug.keystore.base64 must decode to valid binary keystore bytes",
            decoded != null && decoded.isNotEmpty()
        )
        assertTrue(
            "Decoded keystore must have a realistic file size (> 1KB)",
            decoded!!.size > 1024
        )
    }
}
