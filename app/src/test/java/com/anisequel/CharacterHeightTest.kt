package com.anisequel

import com.anisequel.data.model.CharacterProfile
import com.anisequel.data.model.Height
import com.anisequel.data.model.HeightValue
import com.anisequel.data.model.StaffProfile
import com.anisequel.data.network.GraphQLQueries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AniList added a dedicated `height` field to characters and staff on 2026-11-23.
 *
 * It is one type with three shapes - a single measurement, a range, and an initial
 * height for a character who changes - and the server states that equal `min` and
 * `max` are a single value that "shouldn't be displayed as a range". Printing
 * "185 - 185 cm" for every character with a known height is exactly the kind of
 * conversion artefact a reader reads as a bug, so the rule is pinned here rather
 * than trusted to each call site.
 */
class CharacterHeightTest {

    private fun value(metric: Double?, imperial: String?) =
        HeightValue(metric = metric, imperial = imperial)

    @Test
    fun `a single height is printed once, not as a range`() {
        val height = Height(
            min = value(185.0, "6'1\""),
            max = value(185.0, "6'1\"")
        )

        assertEquals("185 cm (6'1\")", height.display)
    }

    @Test
    fun `a genuine range keeps both ends`() {
        val height = Height(
            min = value(160.0, "5'3\""),
            max = value(170.0, "5'7\"")
        )

        assertEquals("160 cm (5'3\") - 170 cm (5'7\")", height.display)
    }

    @Test
    fun `an initial height says so`() {
        // `initial` is the shape for a character who grows; AniList leaves
        // `min`/`max` null in that case.
        val height = Height(initial = value(150.0, "4'11\""))

        assertEquals("Initially 150 cm (4'11\")", height.display)
    }

    @Test
    fun `both units are shown, and one is enough when the other is missing`() {
        assertEquals("169.5 cm (5'7\")", value(169.5, "5'7\"").text)
        assertEquals("169.5 cm", value(169.5, null).text)
        assertEquals("5'7\"", value(null, "5'7\"").text)
        // A blank imperial string is not a measurement.
        assertEquals("185 cm", value(185.0, "  ").text)
    }

    @Test
    fun `a whole height loses the conversion decimal, a fractional one keeps it`() {
        // `Double.toString` gives 185.0 for every whole height AniList stores, and
        // "185.0 cm" reads as raw conversion output rather than a measurement.
        assertEquals("185 cm", value(185.0, null).text)
        assertEquals("169.5 cm", value(169.5, null).text)
    }

    @Test
    fun `a height with neither unit is not a fact`() {
        assertNull(Height().display)
        assertNull(Height(min = HeightValue(), max = HeightValue()).display)
        assertNull(HeightValue().text)
    }

    @Test
    fun `the height sits after age in a character's facts`() {
        val character = CharacterProfile(
            id = 1,
            gender = "Male",
            age = "27",
            height = Height(min = value(185.0, "6'1\""), max = value(185.0, "6'1\""))
        )

        assertEquals(
            listOf("Gender" to "Male", "Age" to "27", "Height" to "185 cm (6'1\")"),
            character.facts
        )
    }

    @Test
    fun `the height sits after age in a staff member's facts`() {
        val staff = StaffProfile(
            id = 2,
            gender = "Male",
            age = "45",
            height = Height(min = value(175.0, "5'9\""), max = value(175.0, "5'9\""))
        )

        assertEquals(
            listOf("Gender" to "Male", "Age" to "45", "Height" to "175 cm (5'9\")"),
            staff.facts
        )
    }

    @Test
    fun `both person queries ask for all three height shapes`() {
        // The API defines the shapes as sibling fields, and a partial selection is
        // not an error - it is a silently missing height. Verified against the live
        // API: the field is `Height { initial min max }`, each a `HeightValue`
        // with `metric: Float` (centimetres) and `imperial: String` ("5'7\"").
        listOf(
            "character" to GraphQLQueries.getCharacterProfileQuery(),
            "staff" to GraphQLQueries.getStaffProfileQuery()
        ).forEach { (who, query) ->
            assertTrue("$who query does not ask for a height", query.contains("height {"))
            assertTrue("$who query is missing the initial height", query.contains("initial { metric imperial }"))
            assertTrue("$who query is missing the range floor", query.contains("min { metric imperial }"))
            assertTrue("$who query is missing the range ceiling", query.contains("max { metric imperial }"))
        }
    }
}
