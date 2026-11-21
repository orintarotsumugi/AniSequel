package com.anisequel

import com.anisequel.domain.ui.DisplayDpi
import com.anisequel.domain.ui.DpiMode
import com.anisequel.domain.ui.UiSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Density mode, read as pure functions.
 *
 * The failure this guards is a mode that looks right in the picker but cannot be
 * trusted at the composition root: if Automatic inherited the manual clamp, a
 * tablet reporting 640 dpi would be quietly drawn at 600 and the page would still
 * print the device's real number. The two paths have to stay different.
 */
class DpiSettingsTest {

    @Test
    fun `automatic follows the device even past the manual ceiling`() {
        assertEquals(
            640f / 160f,
            DisplayDpi.densityFor(DpiMode.AUTOMATIC, manualDpi = 447, deviceDpi = 640),
            0.0001f
        )
    }

    @Test
    fun `manual keeps the clamp so a hand-edited value stays drawable`() {
        assertEquals(
            DisplayDpi.clamp(900) / 160f,
            DisplayDpi.densityFor(DpiMode.MANUAL, manualDpi = 900, deviceDpi = 480),
            0.0001f
        )
    }

    @Test
    fun `a device that reports no density falls back to the shipped default`() {
        assertEquals(
            DisplayDpi.DEFAULT_DPI / 160f,
            DisplayDpi.densityFor(DpiMode.AUTOMATIC, manualDpi = 400, deviceDpi = 0),
            0.0001f
        )
    }

    @Test
    fun `an unknown stored mode falls back to manual, not automatic`() {
        assertEquals(DpiMode.MANUAL, DpiMode.fromStorage(null))
        assertEquals(DpiMode.MANUAL, DpiMode.fromStorage("a value from a newer build"))
        assertEquals(DpiMode.AUTOMATIC, DpiMode.fromStorage("automatic"))
        assertEquals(DpiMode.MANUAL, DpiMode.fromStorage("manual"))
    }

    /**
     * The named sizes really are ordered, and in the direction their names claim.
     *
     * Compose's density is *pixels per dp*, so raising it makes every dp - and every
     * card, caption and gap drawn in one - physically larger, which fits less on the
     * screen. "Large" therefore has to be the highest number of the three and
     * "Small" the lowest; swapped, the picker would do the opposite of its own
     * labels, which is what the Settings copy used to promise as well.
     */
    @Test
    fun `large draws bigger than default and small draws smaller`() {
        assertTrue(
            "Small must draw a finer, busier page than Default",
            DisplayDpi.density(UiSize.COMPACT.dpi) < DisplayDpi.density(UiSize.DEFAULT.dpi)
        )
        assertTrue(
            "Large must draw a larger page than Default",
            DisplayDpi.density(UiSize.LARGE.dpi) > DisplayDpi.density(UiSize.DEFAULT.dpi)
        )
    }

    /**
     * The two Settings paragraphs describe the direction the slider actually moves.
     *
     * Both said the opposite - "a higher number fits more on the page" - so a reader
     * who wanted a bigger app dragged the number *down* and got a finer one. The
     * sentences are the only place the app explains the setting, which is why this
     * pins them rather than leaving them to a future edit.
     */
    @Test
    fun `the density copy sends the reader the right way up the slider`() {
        val files = listOf(
            "app/src/main/java/com/anisequel/ui/screens/settings/DpiSettingsTab.kt",
            "app/src/main/java/com/anisequel/ui/screens/settings/UiSizeSection.kt"
        )
        files.forEach { path ->
            val file = File(findProjectRoot(), path)
            assertTrue("$path is missing", file.isFile)
            // Comments first: this test's own explanation of the old wording quotes it,
            // and the sentence is written as concatenated string literals that a raw
            // `contains` sees split across two lines.
            val copy = file.readText()
                .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
                .replace(Regex("//[^\\n]*"), "")
                .replace(Regex("\"\\s*\\+\\s*\""), "")
                .replace(Regex("\\s+"), " ")
            assertTrue(
                "$path must say that a higher density draws everything larger",
                copy.contains("draws text, cards and gaps larger")
            )
            assertFalse(
                "$path still claims a higher density fits more on screen, which is " +
                    "the reverse of what it does",
                copy.contains("higher dpi fits more") ||
                    copy.contains("higher number fits more")
            )
        }
    }

    private fun findProjectRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir") ?: ".")
        while (candidate != null) {
            if (File(candidate, "gradle.properties").isFile) return candidate
            candidate = candidate.parentFile
        }
        error("could not locate gradle.properties")
    }
}
