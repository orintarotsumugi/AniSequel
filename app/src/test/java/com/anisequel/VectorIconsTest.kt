package com.anisequel

import androidx.compose.ui.graphics.vector.ImageVector
import com.anisequel.data.repository.MotionStyle
import com.anisequel.ui.components.icons.AppCustomVectors
import com.anisequel.ui.components.icons.AppExtraVectors
import com.anisequel.ui.components.icons.MotionVectors
import com.anisequel.ui.components.icons.motionGlyph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method

/**
 * Guards the contract every hand-authored icon in [AppCustomVectors],
 * [AppExtraVectors] and [MotionVectors] has to keep.
 *
 * The call sites reach these icons by property, and each one is a `by lazy`
 * `ImageVector` builder with no drawable behind it, so a rename, or a
 * copy-pasted builder whose `name` or viewport was not changed, still compiles
 * and fails only as a blank or mis-scaled glyph at runtime. This pins the parts
 * the compiler cannot see - the `name` string and the 24x24 frame - by reflecting
 * over the objects, so adding an icon automatically enrols it in the check.
 *
 * A plain JVM test is enough: `ImageVector` is a pure Kotlin value type with no
 * Android framework or Compose test rule behind it.
 */
class VectorIconsTest {

    /** The six icons whose geometry was redrawn; none may be renamed away. */
    private val redesigned = listOf(
        "CalendarClock",
        "CheckDouble",
        "FranchiseBranch",
        "FullscreenExpand",
        "MotionBolt",
        "SurfaceTones"
    )

    @Test
    fun `every custom icon declares its own name and a 24x24 frame`() {
        val icons = collectIcons(AppCustomVectors) + collectIcons(AppExtraVectors) +
            collectIcons(MotionVectors)

        assertTrue(
            "reflection found no icons; the objects or their accessors changed shape",
            icons.isNotEmpty()
        )

        icons.forEach { (property, icon) ->
            assertFalse(
                "$property.name must not be blank",
                icon.name.isBlank()
            )
            assertEquals(
                "$property must set name = \"$property\" so it stays identifiable",
                property,
                icon.name
            )
            assertEquals(
                "$property.defaultWidth must stay 24.dp",
                24f,
                icon.defaultWidth.value,
                0f
            )
            assertEquals(
                "$property.defaultHeight must stay 24.dp",
                24f,
                icon.defaultHeight.value,
                0f
            )
            assertEquals(
                "$property.viewportWidth must stay 24f",
                24f,
                icon.viewportWidth,
                0f
            )
            assertEquals(
                "$property.viewportHeight must stay 24f",
                24f,
                icon.viewportHeight,
                0f
            )
        }
    }

    /**
     * Five motion styles, five marks.
     *
     * The picker drew Snappy with Smooth's waves and Floaty with Chill's bolt, so two
     * of five choices wore another choice's face - and the glyph is the only part of
     * that picker which is not a word. The names are compared rather than the objects
     * because the test above pins every custom icon's `name` to its property: two
     * different names cannot be the same drawing.
     */
    @Test
    fun `every motion style has a glyph of its own`() {
        val names = MotionStyle.entries.map { style -> motionGlyph(style).name }

        assertEquals(
            "two motion styles share a glyph: $names. A reader choosing between them " +
                "is shown the same picture for both halves of the question. Give the " +
                "style its own mark in MotionVectors and map it in motionGlyph.",
            MotionStyle.entries.size,
            names.distinct().size
        )
    }

    @Test
    fun `the redesigned icons are all still present`() {
        val names = (collectIcons(AppCustomVectors) + collectIcons(AppExtraVectors) +
            collectIcons(MotionVectors))
            .map { it.first }
            .toSet()

        redesigned.forEach { name ->
            assertTrue(
                "$name was renamed or removed; every call site refers to it by " +
                    "this exact property name",
                names.contains(name)
            )
        }
    }

    /**
     * Reads the `ImageVector`-valued properties of a Kotlin `object` through its
     * generated `getXxx()` accessors. The backing fields are not usable directly:
     * a `val by lazy` compiles to a `X$delegate` field plus a getter, not a field
     * named `X`, so the getters are the stable JVM surface.
     */
    private fun collectIcons(container: Any): List<Pair<String, ImageVector>> =
        container::class.java.declaredMethods
            .filter { it.isIconGetter() }
            .map { method ->
                val property = method.name.removePrefix("get")
                property to (method.invoke(container) as ImageVector)
            }

    private fun Method.isIconGetter(): Boolean =
        parameterCount == 0 &&
            name.startsWith("get") &&
            ImageVector::class.java.isAssignableFrom(returnType)
}
