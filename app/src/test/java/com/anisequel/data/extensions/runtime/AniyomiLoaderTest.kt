package com.anisequel.data.extensions.runtime

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Pins the dex extraction without an Android runtime: which entries count, and
 * that they come back in load order. A multidex APK whose second dex loaded
 * first would resolve classes against the wrong code.
 */
class AniyomiLoaderTest {

    @Test
    fun `only dex entries are extracted, in load order`() {
        val first = byteArrayOf(1, 2, 3)
        val second = byteArrayOf(4, 5)
        val apk = zip(
            "classes2.dex" to second,
            "AndroidManifest.xml" to byteArrayOf(9),
            "classes.dex" to first,
            "res/layout.xml" to byteArrayOf(8)
        )
        val dex = AniyomiLoader.extractDex(ByteArrayInputStream(apk))
        assertEquals(2, dex.size)
        assertArrayEquals(first, dex[0].toByteArray())
        assertArrayEquals(second, dex[1].toByteArray())
    }

    @Test
    fun `an archive with no dex yields nothing`() {
        val apk = zip("AndroidManifest.xml" to byteArrayOf(1))
        assertTrue(AniyomiLoader.extractDex(ByteArrayInputStream(apk)).isEmpty())
    }

    @Test
    fun `numbered dex files sort numerically`() {
        val apk = zip(
            "classes10.dex" to byteArrayOf(10),
            "classes2.dex" to byteArrayOf(2),
            "classes.dex" to byteArrayOf(1)
        )
        val dex = AniyomiLoader.extractDex(ByteArrayInputStream(apk))
        assertEquals(listOf(1, 2, 10), dex.map { it.toByteArray().first().toInt() })
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        val duplicate = duplicate()
        val bytes = ByteArray(duplicate.remaining())
        duplicate.get(bytes)
        return bytes
    }
}