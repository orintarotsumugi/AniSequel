package com.anisequel

import com.anisequel.domain.profile.ProfileBannerShape
import com.anisequel.domain.profile.ProfileBannerSizing
import com.anisequel.domain.profile.ProfileLayout
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The profile banner's shape rule, and the wire from the editor to the header.
 *
 * ## Why the heights are pinned
 *
 * The profile header used to be one fixed height for every banner, which is right
 * only for the pictures that happen to share its ratio - and a banner's ratio is
 * whatever its owner uploaded. The rule that replaced it is a pure function with
 * three named shapes, an adaptive default and two clamps, so it can be pinned
 * exactly: a square banner must be as tall as the page is wide, a portrait one
 * must be taller, and an adaptive panorama must stop at the floor rather than
 * collapsing to a strip the avatar cannot sit on.
 *
 * The clamps are requirements rather than preferences. A 4:1 AniList banner asked
 * to be 90dp tall at a phone's width, and a vertical illustration asked for most of
 * the screen; both answers are unusable, which is why [ProfileBannerSizing] does
 * not return the raw ratio.
 *
 * ## Why the rest is a source scan
 *
 * The preference, the segmented control and the header's callback are a page, a
 * sheet and a composable apart. Removing any one of them compiles and leaves the
 * control visibly doing nothing, which is the failure this file exists to catch.
 */
class ProfileBannerShapeTest {

    private fun source(path: String): String =
        Regex("""/[*][\s\S]*?[*]/""")
            .replace(File(findProjectRoot(), path).readText()) { "" }
            .lines()
            .filterNot { it.trimStart().startsWith("//") }
            .joinToString("\n")

    private fun height(shape: ProfileBannerShape, widthDp: Float, image: Pair<Float, Float>) =
        ProfileBannerSizing.heightDp(
            widthDp = widthDp,
            aspect = ProfileBannerSizing.aspectFor(shape, image.first, image.second)
        )

    @Test
    fun `a square banner is as tall as the page is wide`() {
        assertEquals(360f, height(ProfileBannerShape.SQUARE, 360f, 0f to 0f), 0.01f)
        assertEquals(640f, height(ProfileBannerShape.SQUARE, 640f, 0f to 0f), 0.01f)
    }

    @Test
    fun `a portrait banner is taller than it is wide`() {
        val banner = height(ProfileBannerShape.PORTRAIT, 360f, 0f to 0f)

        assertEquals(450f, banner, 0.01f)
        assertTrue("the portrait preset is not portrait: $banner", banner > 360f)
    }

    @Test
    fun `a landscape banner follows sixteen by nine`() {
        assertEquals(202.5f, height(ProfileBannerShape.LANDSCAPE, 360f, 0f to 0f), 0.01f)
    }

    @Test
    fun `adapt follows the picture it is given`() {
        assertEquals(
            "a 3:2 banner is not drawn at 3:2, so the page is still choosing the crop",
            240f,
            height(ProfileBannerShape.ADAPT, 360f, 3000f to 2000f),
            0.01f
        )
        assertEquals(
            "a 4:5 upload should pass through the adaptive path untouched",
            450f,
            height(ProfileBannerShape.ADAPT, 360f, 1200f to 1500f),
            0.01f
        )
    }

    @Test
    fun `an adaptive panorama stops at the floor instead of becoming a strip`() {
        // 4000x1000 is 4:1 - an ordinary AniList banner - and the honest height at
        // 360dp is 90dp, which fits neither the avatar nor the identity line.
        val banner = height(ProfileBannerShape.ADAPT, 360f, 4000f to 1000f)

        assertEquals(ProfileBannerSizing.MIN_HEIGHT_DP, banner, 0.01f)
    }

    @Test
    fun `an adaptive vertical picture stops at the ceiling instead of eating the page`() {
        // 1:4 at a tablet's 640dp width asks for 2560dp.
        val banner = height(ProfileBannerShape.ADAPT, 640f, 1000f to 4000f)

        assertEquals(ProfileBannerSizing.MAX_HEIGHT_DP, banner, 0.01f)
        assertTrue(
            "a portrait banner on a phone must still leave the name, the stats and " +
                "some of the page on screen",
            height(ProfileBannerShape.ADAPT, 360f, 1000f to 4000f) < 600f
        )
    }

    @Test
    fun `an unmeasured or unusable picture is a landscape banner, not a divide by zero`() {
        assertEquals(
            ProfileBannerSizing.LANDSCAPE,
            ProfileBannerSizing.aspectFor(ProfileBannerShape.ADAPT, 0f, 0f),
            0.001f
        )
        assertEquals(
            "a NaN intrinsic size - an SVG or an animated format - poisoned the ratio " +
                "instead of falling back",
            ProfileBannerSizing.LANDSCAPE,
            ProfileBannerSizing.aspectFor(ProfileBannerShape.ADAPT, Float.NaN, Float.NaN),
            0.001f
        )
        assertEquals(
            ProfileBannerSizing.MIN_HEIGHT_DP,
            ProfileBannerSizing.heightDp(0f, 1f),
            0.01f
        )
    }

    @Test
    fun `storage values are unique and unknown ones fall back to adapt`() {
        assertEquals(
            ProfileBannerShape.entries.size,
            ProfileBannerShape.entries.map { it.storageValue }.distinct().size
        )
        ProfileBannerShape.entries.forEach {
            assertEquals(it, ProfileBannerShape.fromStorage(it.storageValue))
        }
        assertEquals(ProfileBannerShape.DEFAULT, ProfileBannerShape.fromStorage(null))
        assertEquals(ProfileBannerShape.DEFAULT, ProfileBannerShape.fromStorage("nolongerhere"))
        assertEquals(
            "an upgraded install must land on the adaptive banner, not on a shape it " +
                "never chose",
            ProfileBannerShape.ADAPT,
            ProfileLayout.DEFAULT.bannerShape
        )
    }

    @Test
    fun `the shape is stored, edited and applied`() {
        val preferences = source(
            "app/src/main/java/com/anisequel/data/repository/ProfileLayoutPreferences.kt"
        )
        val sheet = source(
            "app/src/main/java/com/anisequel/ui/screens/profile/ProfileLayoutSheet.kt"
        )
        val page = source(
            "app/src/main/java/com/anisequel/ui/screens/profile/ProfileHomeTab.kt"
        )
        val header = source(
            "app/src/main/java/com/anisequel/ui/screens/profile/ProfileHeaderCard.kt"
        )

        assertTrue(
            "the preference is never written, so the choice is forgotten on dismiss",
            preferences.contains("setBannerShape(") &&
                preferences.contains("profile_banner_shape")
        )
        assertTrue(
            "the editor has no control for it",
            sheet.contains("profile_layout_banner_shape") &&
                sheet.contains("onBannerShape:")
        )
        assertTrue(
            "the page draws the stored shape rather than the reader's choice",
            page.contains("bannerShape = layout.bannerShape")
        )
        assertTrue(
            "the header does not take a shape at all",
            header.contains("bannerShape: ProfileBannerShape")
        )
        assertTrue(
            "the banner never learns its picture's size, so 'Adapt' can only be a guess",
            header.contains("onImageSize = { imageSize = it }")
        )
        assertTrue(
            "the measured height is not animated, so a portrait banner snaps the whole " +
                "page down the moment its pixels land",
            header.contains("animateDpAsState(")
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
