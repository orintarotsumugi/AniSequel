package com.anisequel

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guards the floating bar's progress draw transform against out-of-range values. */
class InfoFloatingBarProgressGuardTest {
    @Test
    fun `progress transform is finite and clamped`() {
        val start = File(System.getProperty("user.dir") ?: ".")
        var root: File? = start
        while (root != null && !File(root, "gradle.properties").isFile) root = root.parentFile
        val project = root ?: error("could not locate project root")
        val source = File(project, "app/src/main/java/com/anisequel/ui/components/detail/info/InfoFloatingBar.kt")
            .readText()
        assertTrue(source.contains("takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f"))
    }
}
