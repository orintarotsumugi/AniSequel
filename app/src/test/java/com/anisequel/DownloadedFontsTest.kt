package com.anisequel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anisequel.data.repository.CustomFontStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The Font tab's list of families already on disk.
 *
 * The list is read from the directory rather than from a remembered set, which is
 * what makes a download survive an update and makes a family downloaded by an
 * older build appear. What is pinned here is that the read reports what the
 * directory actually holds: a sidecar name beats the index, a missing one falls
 * back to the index, the order is by name, and a family Google publishes with no
 * bold cut says so instead of promising a bold file that is not there.
 *
 * The bytes are not real fonts. Nothing in this read decodes a file - it stats it -
 * and a test that needed a valid TrueType face would fail on a fixture rather than
 * on this app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DownloadedFontsTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val fontsDir: File
        get() = File(context.filesDir, "fonts")

    /** Every test starts with an empty font directory: the list is the directory. */
    @Before
    fun clearFonts() {
        fontsDir.deleteRecursively()
        fontsDir.mkdirs()
    }

    private fun store(id: String, bold: Boolean = false, name: String? = null) {
        File(fontsDir, "google_${id}_400.ttf").writeBytes(ByteArray(64))
        if (bold) File(fontsDir, "google_${id}_700.ttf").writeBytes(ByteArray(64))
        if (name != null) File(fontsDir, "google_$id.name").writeText(name)
    }

    @Test
    fun `a stored family is listed, in name order`() = runTest {
        store("roboto", name = "Roboto")
        store("jetbrains_mono", name = "JetBrains Mono")

        val families = CustomFontStore.downloadedFamilies(context)

        assertEquals(
            listOf("JetBrains Mono", "Roboto"),
            families.map { it.displayName }
        )
    }

    @Test
    fun `the name written at download time wins over the index`() = runTest {
        // "Plus Jakarta Sans" is not in the local index; without the sidecar the
        // list would draw it as "Plus Jakarta Sans" only by luck of the id.
        store("plus_jakarta_sans", name = "Plus Jakarta Sans")

        assertEquals(
            "Plus Jakarta Sans",
            CustomFontStore.downloadedFamilies(context).single().displayName
        )
    }

    @Test
    fun `a family downloaded by an older build is still listed`() = runTest {
        // No `.name` and no `.done`: exactly the two files the previous downloader
        // wrote. It must still appear, because the file is what makes it a font.
        store("lora")

        val family = CustomFontStore.downloadedFamilies(context).single()

        assertEquals("lora", family.id)
        assertEquals("Lora", family.displayName)
        assertTrue(family.path.endsWith("google_lora_400.ttf"))
    }

    @Test
    fun `a regular only family reports no bold file`() = runTest {
        store("lora", bold = false)

        assertNull(CustomFontStore.downloadedFamilies(context).single().boldPath)
    }

    @Test
    fun `a bold file is reported when it is on disk`() = runTest {
        store("inter", bold = true)

        val family = CustomFontStore.downloadedFamilies(context).single()

        assertTrue(family.boldPath!!.endsWith("google_inter_700.ttf"))
    }

    @Test
    fun `an empty regular file is not a downloaded family`() = runTest {
        File(fontsDir, "google_lora_400.ttf").writeBytes(ByteArray(0))

        assertTrue(CustomFontStore.downloadedFamilies(context).isEmpty())
    }

    @Test
    fun `imported copies and sidecars are not listed as downloaded families`() = runTest {
        File(fontsDir, "custom_font_123.ttf").writeBytes(ByteArray(64))
        File(fontsDir, "google_lora.name").writeText("Lora")

        assertTrue(CustomFontStore.downloadedFamilies(context).isEmpty())
    }
}
