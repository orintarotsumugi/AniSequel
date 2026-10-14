package com.anisequel

import androidx.compose.ui.graphics.vector.ImageVector
import com.anisequel.ui.components.icons.AppCustomVectors
import com.anisequel.ui.components.icons.AppExtraVectors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method

/**
 * Guards the contract every hand-authored icon in [AppCustomVectors] and
 * [AppExtraVectors] has to keep.
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
        val icons = collectIcons(AppCustomVectors) + collectIcons(AppExtraVectors)

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

    @Test
    fun `the redesigned icons are all still present`() {
        val names = (collectIcons(AppCustomVectors) + collectIcons(AppExtraVectors))
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
