package com.anisequel

import com.anisequel.domain.card.CardTitlePlacement
import com.anisequel.domain.card.MediaCardStyle
import com.anisequel.domain.ui.HomeSequelStyle
import com.anisequel.domain.ui.LibraryViewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InterfaceModesTest {

    @Test
    fun `new interface modes keep stable storage keys`() {
        assertEquals(LibraryViewMode.CARDS, LibraryViewMode.fromStorage("cards"))
        assertEquals(LibraryViewMode.SHELF, LibraryViewMode.fromStorage("shelf"))
        assertEquals(HomeSequelStyle.BANNER, HomeSequelStyle.fromStorage("banner"))
        assertEquals(HomeSequelStyle.SHELF, HomeSequelStyle.fromStorage("shelf"))
        assertEquals(MediaCardStyle.COVER_ONLY, MediaCardStyle.fromStorage("cover_only"))
    }

    @Test
    fun `cover only card intentionally hides the repeated title`() {
        assertEquals(
            CardTitlePlacement.HIDDEN,
            MediaCardStyle.COVER_ONLY.titlePlacement
        )
        assertTrue(MediaCardStyle.COVER_ONLY.scoreOnPoster)
    }
}
