package com.anisequel

import com.anisequel.data.repository.CoverCacheCeiling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rungs the Device management card offers, and the number the image loader is
 * built from.
 *
 * The ceiling is read exactly once per process - the loader's disk cache fixes its
 * own ceiling at construction - so the only chance to catch a wrong value here is at
 * the boundary between the stored string and the bytes.
 */
class CoverCacheCeilingTest {

    @Test
    fun `the rungs step up and the default is what the app already kept`() {
        val bytes = CoverCacheCeiling.entries.map { it.bytes }
        bytes.forEachIndexed { index, value ->
            if (index > 0) {
                assertTrue(
                    "${CoverCacheCeiling.entries[index]} is not bigger than the rung below it",
                    value > bytes[index - 1]
                )
            }
        }
        assertEquals(
            "an install that never opens the page must keep the size it has always had",
            256L * 1024 * 1024,
            CoverCacheCeiling.DEFAULT.bytes
        )
    }

    @Test
    fun `the storage values round-trip and an unknown one falls back`() {
        CoverCacheCeiling.entries.forEach { rung ->
            assertEquals(rung, CoverCacheCeiling.fromStorage(rung.storageValue))
        }
        assertEquals(CoverCacheCeiling.DEFAULT, CoverCacheCeiling.fromStorage("10gb"))
        assertEquals(CoverCacheCeiling.DEFAULT, CoverCacheCeiling.fromStorage(null))
    }

    @Test
    fun `the labels name the rung in the units a reader counts in`() {
        assertEquals("256 MB", CoverCacheCeiling.MB_256.displayName)
        assertEquals("512 MB", CoverCacheCeiling.MB_512.displayName)
        assertEquals("1 GB", CoverCacheCeiling.GB_1.displayName)
        assertEquals("5 GB", CoverCacheCeiling.GB_5.displayName)
        // 5 GB has to be the largest ask, or the card's top rung is a smaller cache
        // than the one under it.
        assertEquals(CoverCacheCeiling.GB_5, CoverCacheCeiling.entries.maxBy { it.bytes })
    }
}
