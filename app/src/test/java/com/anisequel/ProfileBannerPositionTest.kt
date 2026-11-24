package com.anisequel

import com.anisequel.domain.profile.ProfileBannerAnchor
import com.anisequel.domain.profile.ProfileLayout
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The banner's crop position and its drift, from the stored choice to the picture.
 *
 * ## Why the fractions are pinned
 *
 * The three positions are a UI promise ("left holds the left of the picture"), and
 * the only thing that makes the promise true is the mapping from the enum to the
 * backdrop's anchor fraction. That mapping is one character away from being
 * mirrored - and a mirrored one still draws a plausible crop, just the wrong side
 * of it - so it is asserted exactly rather than described.
 *
 * ## Why the rest is a source scan
 *
 * The store, the editor, the page and the header are four files apart, and removing
 * any one of them compiles: a preference nobody writes, a control whose callback is
 * dropped, or a header that takes a position and never hands it to the backdrop all
 * look finished and do nothing. The scan is the only thing that notices. It is the
 * same shape as [ProfileBannerShapeTest], because they guard the two halves of one
 * setting pair.
 */
class ProfileBannerPositionTest {

    private fun source(path: String): String =
        Regex("""/[*][\s\S]*?[*]/""")
            .replace(File(findProjectRoot(), path).readText()) { "" }
            .lines()
            .filterNot { it.trimStart().startsWith("//") }
            .joinToString("\n")

    @Test
    fun `left centre and right are the three fractions the transform takes`() {
        assertEquals(0f, ProfileBannerAnchor.LEFT.fraction, 0.0001f)
        assertEquals(0.5f, ProfileBannerAnchor.CENTER.fraction, 0.0001f)
        assertEquals(1f, ProfileBannerAnchor.RIGHT.fraction, 0.0001f)
    }

    @Test
    fun `storage values are unique and unknown ones fall back to centre`() {
        assertEquals(
            ProfileBannerAnchor.entries.size,
            ProfileBannerAnchor.entries.map { it.storageValue }.distinct().size
        )
        ProfileBannerAnchor.entries.forEach {
            assertEquals(it, ProfileBannerAnchor.fromStorage(it.storageValue))
        }
        assertEquals(ProfileBannerAnchor.DEFAULT, ProfileBannerAnchor.fromStorage(null))
        assertEquals(
            ProfileBannerAnchor.DEFAULT,
            ProfileBannerAnchor.fromStorage("nolongerhere")
        )
        assertEquals(
            "an upgraded install must keep the crop it already had, not move the " +
                "banner to one side",
            ProfileBannerAnchor.CENTER,
            ProfileLayout.DEFAULT.bannerAnchor
        )
        assertFalse(
            "a banner that starts sweeping on its own would be an animation the " +
                "reader never asked for",
            ProfileLayout.DEFAULT.bannerDrift
        )
    }

    @Test
    fun `the position is stored, edited and applied`() {
        val preferences = source(
            "app/src/main/java/com/anisequel/data/repository/ProfileLayoutPreferences.kt"
        )
        val sheet = source(
            "app/src/main/java/com/anisequel/ui/screens/profile/ProfileLayoutSheet.kt"
        )
        val screen = source(
            "app/src/main/java/com/anisequel/ui/screens/profile/UserProfileScreen.kt"
        )
        val page = source(
            "app/src/main/java/com/anisequel/ui/screens/profile/ProfileHomeTab.kt"
        )
        val header = source(
            "app/src/main/java/com/anisequel/ui/screens/profile/ProfileHeaderCard.kt"
        )

        assertTrue(
            "the position is never written, so the choice is forgotten on dismiss",
            preferences.contains("setBannerAnchor(") &&
                preferences.contains("profile_banner_anchor")
        )
        assertTrue(
            "the drift is never written, so the switch does nothing after a restart",
            preferences.contains("setBannerDrift(") &&
                preferences.contains("profile_banner_drift")
        )
        assertTrue(
            "Reset leaves the position and the drift behind, so 'reset' keeps two " +
                "of the things it was meant to reset",
            preferences.contains("preferences.remove(Keys.BANNER_ANCHOR)") &&
                preferences.contains("preferences.remove(Keys.BANNER_DRIFT)")
        )

        assertTrue(
            "the editor has no control for the position",
            sheet.contains("profile_layout_banner_anchor") &&
                sheet.contains("onBannerAnchor:")
        )
        assertTrue(
            "the editor has no control for the drift",
            sheet.contains("profile_layout_banner_drift") &&
                sheet.contains("onBannerDrift:")
        )
        assertTrue(
            "the sheet's page never turns the two callbacks into writes",
            screen.contains("layoutPreferences.setBannerAnchor(anchor)") &&
                screen.contains("layoutPreferences.setBannerDrift(drift)")
        )

        assertTrue(
            "the page draws the shipped position rather than the reader's choice",
            page.contains("bannerAnchor = layout.bannerAnchor") &&
                page.contains("bannerDrift = layout.bannerDrift")
        )

        assertTrue(
            "the header does not take a position at all",
            header.contains("bannerAnchor: ProfileBannerAnchor")
        )
        assertTrue(
            "the position is snapped into place, so the reader's largest picture " +
                "jumps between crops",
            header.contains("animateFloatAsState(") &&
                header.contains("ExpressiveMotion.SettleSpatial")
        )
        assertTrue(
            "the position is read during composition instead of in the draw phase, " +
                "so the slide recomposes the header on every frame",
            header.contains("anchor = { if (drift) 0.5f else anchorFraction.value }")
        )
        assertTrue(
            "the drift is never handed to the backdrop, so the switch is a no-op",
            header.contains("drift = drift,")
        )
    }

    private fun findProjectRoot(): File {
        val start = File(System.getProperty("user.dir") ?: ".")
        var candidate: File? = start
        while (candidate != null) {
            if (File(candidate, "gradle.properties").isFile) return candidate
            candidate = candidate.parentFile
        }
        error("could not locate gradle.properties from $start")
    }
}
