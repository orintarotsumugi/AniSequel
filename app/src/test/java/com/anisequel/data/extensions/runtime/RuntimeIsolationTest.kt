package com.anisequel.data.extensions.runtime

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Pins the sandbox boundary structurally, the way KeystoreIntegrityTest pins the
 * signing boundary. The runtime's safety is a property of the manifest and of
 * *where the ABI is referenced*, neither of which a behaviour test can see:
 *
 *  - the service must stay isolated and unexported, or extension code runs with
 *    the app's permissions;
 *  - only the isolated runtime's bridges may touch the clean-room `eu.kanade` /
 *    Injekt ABI. If a screen or a repository imports it, third-party-linked code
 *    has leaked into the host process and the sandbox is gone.
 */
class RuntimeIsolationTest {

    private val root: File = findProjectRoot()

    private fun findProjectRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir") ?: ".")
        while (candidate != null) {
            if (File(candidate, "gradle.properties").isFile) return candidate
            candidate = candidate.parentFile
        }
        error("could not locate the project root")
    }

    private fun read(vararg parts: String): String = File(root, parts.joinToString("/")).readText()

    @Test
    fun `the runtime service is isolated, unexported and in its own process`() {
        val manifest = read("app", "src", "main", "AndroidManifest.xml")
        val service = Regex("<service\\b[^>]*?ExtensionRuntimeService[^>]*?/>", RegexOption.DOT_MATCHES_ALL)
            .find(manifest)?.value
        assertTrue("the runtime service must be declared", service != null)
        val block = service.orEmpty()
        assertTrue("isolatedProcess must be true", "android:isolatedProcess=\"true\"" in block)
        assertTrue("the service must not be exported", "android:exported=\"false\"" in block)
        assertTrue("the service must run in its own process", "android:process=\":extension-runtime\"" in block)
    }

    @Test
    fun `the AIDL surface keeps the four source calls`() {
        val aidl = read(
            "app", "src", "main", "aidl", "com", "anisequel", "data", "extensions",
            "runtime", "IExtensionRuntime.aidl"
        )
        listOf("search", "details", "episodes", "resolve").forEach { call ->
            assertTrue("the runtime must expose $call", Regex("String\\s+$call\\(").containsMatchIn(aidl))
        }
    }

    @Test
    fun `only the runtime bridges reference the extension ABI`() {
        val allowedBridges = setOf("AniyomiLoader.kt", "AniyomiMapping.kt", "RuntimeNetwork.kt")
        val offenders = mutableListOf<String>()
        File(root, "app/src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val path = file.invariantSeparatorsPath
                val isAbi = "/eu/kanade/" in path || "/uy/kohesive/" in path
                val isBridge = file.name in allowedBridges
                if (isAbi || isBridge) return@forEach
                val importsAbi = file.readLines().any { line ->
                    line.trimStart().startsWith("import eu.kanade.") ||
                        line.trimStart().startsWith("import uy.kohesive.")
                }
                if (importsAbi) offenders += path.substringAfter("app/src/main/java/")
            }
        if (offenders.isNotEmpty()) {
            fail("extension ABI referenced outside the isolated runtime: ${offenders.joinToString()}")
        }
    }

    @Test
    fun `the ABI is kept against reflective linkage`() {
        val rules = read("app", "proguard-rules.pro")
        assertTrue("-keep class eu.kanade.tachiyomi.**" in rules)
        assertTrue("-keep class uy.kohesive.injekt.**" in rules)
    }
}