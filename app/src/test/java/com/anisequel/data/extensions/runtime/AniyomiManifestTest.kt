package com.anisequel.data.extensions.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the manifest contract the loader depends on. The class-name shape is the
 * regression: a leading `.` is package-relative and several classes are
 * `;`-separated, and getting either wrong loads the wrong class or none.
 */
class AniyomiManifestTest {

    @Test
    fun `relative class names are package qualified`() {
        assertEquals(
            listOf("com.ext.Foo", "com.ext.Bar"),
            AniyomiManifest.sourceClasses("com.ext", ".Foo;.Bar", null)
        )
    }

    @Test
    fun `absolute class names are kept`() {
        assertEquals(
            listOf("com.full.Baz"),
            AniyomiManifest.sourceClasses("com.ext", "com.full.Baz", null)
        )
    }

    @Test
    fun `the factory key is the fallback when class is blank`() {
        assertEquals(
            listOf("com.ext.Factory"),
            AniyomiManifest.sourceClasses("com.ext", "   ", ".Factory")
        )
        assertEquals(
            listOf("com.ext.Factory"),
            AniyomiManifest.sourceClasses("com.ext", null, ".Factory")
        )
    }

    @Test
    fun `duplicate class names collapse`() {
        assertEquals(
            listOf("com.ext.Foo"),
            AniyomiManifest.sourceClasses("com.ext", ".Foo; .Foo", null)
        )
    }

    @Test
    fun `no metadata yields no classes`() {
        assertTrue(AniyomiManifest.sourceClasses("com.ext", null, null).isEmpty())
    }

    @Test
    fun `both anime and legacy feature flags are recognised`() {
        assertTrue(AniyomiManifest.isAnimeExtension(listOf("tachiyomi.animeextension")))
        assertTrue(AniyomiManifest.isAnimeExtension(listOf("tachiyomi.extension")))
        assertFalse(AniyomiManifest.isAnimeExtension(listOf("android.hardware.camera")))
        assertFalse(AniyomiManifest.isAnimeExtension(emptyList()))
    }
}