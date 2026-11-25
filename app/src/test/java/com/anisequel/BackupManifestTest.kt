package com.anisequel

import com.anisequel.data.backup.BackupContent
import com.anisequel.data.backup.BackupManifest
import com.anisequel.data.backup.BackupSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupManifestTest {

    @Test
    fun `round trips the selected contents`() {
        val manifest = BackupManifest(
            schema = BackupManifest.CURRENT_SCHEMA,
            packageName = "com.anisequel",
            appVersion = "2.6.6",
            createdAtMillis = 1_700_000_000_000L,
            contents = BackupSelection(settings = true, offline = false, artwork = true).contents()
        )
        val parsed = BackupManifest.parse(manifest.encode())
        assertEquals(manifest, parsed)
        assertEquals(setOf(BackupContent.SETTINGS, BackupContent.ARTWORK), parsed?.contents)
    }

    @Test
    fun `rejects a schema this build does not know`() {
        val text = "schema=${BackupManifest.CURRENT_SCHEMA + 1}\n" +
            "package=com.anisequel\nversion=9.0.0\ncreated=1\ncontents=settings\n"
        assertNull(BackupManifest.parse(text))
    }

    @Test
    fun `drops an unknown content key but keeps the known ones`() {
        val text = "schema=1\npackage=com.anisequel\nversion=2.6.6\ncreated=1\n" +
            "contents=settings,future-thing\n"
        val parsed = BackupManifest.parse(text)
        assertEquals(setOf(BackupContent.SETTINGS), parsed?.contents)
    }

    @Test
    fun `a selection with nothing ticked is empty`() {
        val selection = BackupSelection(settings = false, offline = false, artwork = false)
        assertTrue(selection.isEmpty())
        assertTrue(selection.contents().isEmpty())
    }

    @Test
    fun `default selection keeps settings and snapshots but not artwork`() {
        val selection = BackupSelection()
        assertFalse(selection.isEmpty())
        assertEquals(setOf(BackupContent.SETTINGS, BackupContent.OFFLINE), selection.contents())
    }
}
