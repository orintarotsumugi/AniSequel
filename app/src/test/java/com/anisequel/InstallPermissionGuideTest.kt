package com.anisequel

import com.anisequel.ui.components.update.InstallPermissionGuide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The words a user is handed when the install permission has to be granted by hand.
 *
 * ## Why the sentences are worth testing
 *
 * The screen they describe belongs to whoever shipped the phone, not to this app, and
 * there is no Android SDK in this environment to look at one. So the only part of this
 * flow that can be verified at all is whether the app says the right thing about the
 * phone in front of it - and a wrong instruction here is not a cosmetic miss: the user
 * is in Settings, cannot find the row, and the update does not install.
 *
 * The failure this guards is the one that would be invisible in review: a list written
 * for one vendor's build and shown to everyone, or an equality check on
 * `Build.MANUFACTURER` that quietly never matches because the string arrives upper-cased.
 */
class InstallPermissionGuideTest {

    private val app = "AniSequel"

    private fun stepsFor(manufacturer: String?): String =
        InstallPermissionGuide.steps(app, manufacturer).joinToString("\n")

    @Test
    fun `a samsung phone is told the special-access route, not the stock one`() {
        listOf("samsung", "SAMSUNG", "Samsung Electronics").forEach { brand ->
            val text = stepsFor(brand)
            assertTrue("$brand: $text", text.contains("Special app access"))
            assertTrue("$brand: $text", text.contains("Allow from this source"))
        }
    }

    @Test
    fun `a xiaomi family phone is told where MIUI keeps the switch`() {
        listOf("xiaomi", "Xiaomi", "redmi", "poco").forEach { brand ->
            val text = stepsFor(brand)
            assertTrue("$brand: $text", text.contains("Manage apps"))
            assertTrue("$brand: $text", text.contains("Other permissions"))
        }
    }

    @Test
    fun `oneplus, oppo and realme get the ColorOS wording`() {
        listOf("OnePlus", "OPPO", "realme").forEach { brand ->
            assertTrue(brand, stepsFor(brand).contains("App management"))
        }
    }

    @Test
    fun `huawei and honor get the name their builds use`() {
        listOf("HUAWEI", "HONOR").forEach { brand ->
            assertTrue(brand, stepsFor(brand).contains("Install apps from unknown sources"))
        }
    }

    /**
     * An unknown build is told the generic path and nothing invented.
     *
     * The specific brands' rows do not exist on a Pixel or a Fairphone, and a user who
     * is sent looking for a menu that is not there is worse off than one who is handed
     * the app row and the search.
     */
    @Test
    fun `a phone this app does not recognise is not given another phone's steps`() {
        val text = stepsFor("Google")

        assertFalse(text, text.contains("Special app access"))
        assertFalse(text, text.contains("Other permissions"))
        assertFalse(text, text.contains("App management"))
        assertTrue(text, text.contains("Settings > Apps"))
        assertTrue(text, text.contains("Allow from this source"))
    }

    @Test
    fun `a missing manufacturer string falls back to the generic steps`() {
        // `Build.MANUFACTURER` is not guaranteed readable on every device or in every
        // harness, and "no idea" must degrade to the instructions that are always true
        // rather than to nothing at all.
        assertEquals(stepsFor("Google"), stepsFor(null))
        assertTrue(stepsFor("").contains("Settings > Apps"))
    }

    /**
     * Every brand's list ends where the names run out: the search box.
     *
     * This is the only step worth promising, because it does not depend on which skin
     * shipped - every Settings app from Android 8 on can reach the screen by that name.
     */
    @Test
    fun `every brand's steps end with the search that always reaches the screen`() {
        listOf("samsung", "xiaomi", "Google", "HUAWEI", "OPPO", null, "").forEach { brand ->
            val steps = InstallPermissionGuide.steps(app, brand)
            assertTrue(
                "$brand must finish with the search fallback",
                steps.last().contains("install unknown apps") && steps.last().contains("Settings")
            )
        }
    }

    @Test
    fun `the steps name the app the user has to find`() {
        listOf("samsung", "xiaomi", "Google", "HUAWEI", "OPPO").forEach { brand ->
            assertTrue(
                "$brand's steps never say which app to open",
                InstallPermissionGuide.steps(app, brand).any { it.contains(app) }
            )
        }
    }

    @Test
    fun `the copy says this is once and that the app cannot do it alone`() {
        // Both matter: the first is why it is not worth fearing, the second is why the
        // app is not silently installing anything.
        assertTrue(InstallPermissionGuide.title(app).contains("once"))
        assertTrue(InstallPermissionGuide.summary(app).contains("Android will not let"))
        assertTrue(InstallPermissionGuide.summary(app).contains("you still confirm"))
    }

    @Test
    fun `the closing line sends the user back rather than to the update button`() {
        // The grant is noticed when the app is foregrounded again, so "press Update
        // again" would send a reader looking for a button that is no longer the way in.
        val text = InstallPermissionGuide.afterGrant(app)
        assertTrue(text, text.contains("Go back"))
        assertTrue(text, text.contains("already downloaded"))
    }
}
