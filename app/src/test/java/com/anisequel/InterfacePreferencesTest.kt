package com.anisequel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anisequel.data.repository.InterfacePreferences
import com.anisequel.domain.card.MediaCardStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the Interface page remembers about how cards are drawn.
 *
 * The failure this guards is the one a preference with a string value always has:
 * writing the display label or the ordinal instead of the stable key. An ordinal
 * makes every existing install repaint when the entries are reordered, and a label
 * makes the stored value change when somebody fixes the wording - neither of which
 * is a bug anyone would connect to a rename.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InterfacePreferencesTest {

    private fun preferences(): InterfacePreferences =
        InterfacePreferences(ApplicationProvider.getApplicationContext<Context>())

    /** The preferences file is shared by every test in this class; start clean. */
    @Before
    fun clearStoredPreferences() = runTest {
        preferences().reset()
    }

    @Test
    fun `the shipped layout is the plain one`() = runTest {
        assertEquals(
            "an install that never saw the setting keeps the card it already had",
            MediaCardStyle.DEFAULT,
            preferences().cardStyle.first()
        )
    }

    @Test
    fun `a chosen style is read back by its key, not its label or its position`() = runTest {
        MediaCardStyle.entries.forEach { style ->
            preferences().reset()
            preferences().setCardStyle(style)
            assertEquals("${style.name} round trip", style, preferences().cardStyle.first())
        }
    }
}
