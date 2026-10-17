package com.anisequel

import com.anisequel.data.model.CharacterProfile
import com.anisequel.data.model.MediaName
import com.anisequel.data.model.MediaTitle
import com.anisequel.data.model.PersonKind
import com.anisequel.data.model.PersonProfileState
import com.anisequel.data.model.ProfileAppearance
import com.anisequel.data.model.StaffProfile
import com.anisequel.data.model.toLoadedState
import com.anisequel.data.repository.AniListRepository
import com.anisequel.ui.viewmodel.PersonProfileStore
import com.anisequel.ui.viewmodel.toMediaNode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The person-profile layer: what a character's page is built from, and the two
 * things the sheet can get wrong at runtime - answering the wrong question, and
 * forgetting which question it was asked.
 *
 * The failure these guard is not visible in a screenshot. A response arriving after
 * the reader has moved on paints one person's face over another's, which looks like
 * a wrong lookup rather than a race, so nothing else would catch it.
 *
 * Every test that involves a fetch injects the `TestScope` into the store rather
 * than letting it make its own. That parameter exists for exactly this: the two
 * behaviours that matter are both timing, and a store on `Dispatchers.Default` is
 * timing a test cannot step.
 */
class PersonProfileTest {

    // ---- the model's own arithmetic -------------------------------------------

    /**
     * `age` is free text, and the character whose age is a joke is what proves it.
     *
     * Faye Valentine is `"23 (Physical), 77 (Actual)"` on AniList. An `Int?` here
     * would either fail to parse - taking the whole sheet down - or silently
     * truncate her to twenty-three, which is worse than not shipping the row.
     */
    @Test
    fun `an age that is a sentence survives being a field`() {
        val faye = CharacterProfile(
            id = 2,
            name = MediaName(full = "Faye Valentine"),
            age = "23 (Physical), 77 (Actual)"
        )

        assertEquals(
            "Age" to "23 (Physical), 77 (Actual)",
            faye.facts.first { it.first == "Age" }
        )
    }

    /**
     * A fact row with a label and nothing under it is worse than no row.
     *
     * AniList has `bloodType` and answers it null constantly - it is null on Spike
     * Spiegel and on most characters who are not from a shounen series. Every fact
     * is nullable for the same reason, so all of them are filtered.
     */
    @Test
    fun `facts omit what AniList did not answer`() {
        val spike = CharacterProfile(
            id = 1,
            name = MediaName(full = "Spike Spiegel"),
            gender = "Male",
            age = "27",
            bloodType = null,
            favourites = null,
            dateOfBirth = null
        )

        assertEquals(listOf("Gender", "Age"), spike.facts.map { it.first })
    }

    @Test
    fun `a staff member gains the facts a credit never carried`() {
        val yatate = StaffProfile(
            id = 97197,
            name = MediaName(full = "Hajime Yatate"),
            primaryOccupations = listOf("Director", "Original Creator"),
            yearsActive = listOf("1998-2022"),
            homeTown = "Tokyo"
        )

        val facts = yatate.facts
        assertEquals("Director, Original Creator", facts.first { it.first == "Role" }.second)
        assertTrue("the role is the point of a crew card", facts.any { it.first == "Role" })
        assertTrue("years active", facts.any { it.first == "Active" })
        assertTrue("hometown", facts.any { it.first == "From" })
    }

    /**
     * `Died` is only printed when AniList gave a date.
     *
     * A card reading "Died -" beside a living person's portrait is not an omission
     * to paper over with a dash; it is an invention.
     */
    @Test
    fun `nobody is reported dead without a date`() {
        val living = StaffProfile(id = 1, name = MediaName(full = "Living Person"))
        assertTrue(living.facts.none { it.first == "Died" })
    }

    /**
     * The canonical name is not an alias of itself.
     *
     * AniList really does list "Spike Spiegel" among Spike Spiegel's alternatives,
     * because a character is credited under several names. Printing the row would
     * show the heading twice, one line apart.
     */
    @Test
    fun `aliases exclude the name already shown as the heading`() {
        val spike = CharacterProfile(
            id = 1,
            name = MediaName(
                full = "Spike Spiegel",
                alternative = listOf("Spike Spike", "Swimming Bird", "Spike Spiegel", " ")
            )
        )

        assertEquals(
            listOf("Spike Spike", "Swimming Bird"),
            spike.toLoadedState().aliases
        )
    }

    /**
     * A staff member has no alternatives on AniList, so the sheet hides the row
     * rather than drawing an empty one.
     */
    @Test
    fun `a staff member has no alias row to show`() {
        val staff = StaffProfile(id = 1, name = MediaName(full = "Hajime Yatate"))
        assertTrue(staff.toLoadedState().aliases.isEmpty())
    }

    // ---- the store's two runtime hazards --------------------------------------

    /**
     * A response for a question the reader has already replaced is discarded.
     *
     * Tap a character, dismiss, tap a staff member while the first is still in
     * flight: the character's answer comes back last, and without the guard it
     * replaces the staff member's face with the character's.
     */
    @Test
    fun `a late response cannot overwrite the profile that replaced it`() = runTest {
        val characterGate = CompletableDeferred<CharacterProfile>()
        val store = PersonProfileStore(
            repository = FakeRepository(
                characterProfile = { characterGate.await() },
                staffProfile = { StaffProfile(id = 42, name = MediaName(full = "The Director")) }
            ),
            scope = this
        )

        store.openCharacter(1, "Spike Spiegel")
        store.openStaff(42, "The Director")
        runCurrent()
        assertEquals("The Director", loaded(store).name)

        characterGate.complete(CharacterProfile(id = 1, name = MediaName(full = "Spike Spiegel")))
        runCurrent()

        assertEquals("the late answer must not win", "The Director", loaded(store).name)
        assertEquals(PersonKind.STAFF, loaded(store).kind)
    }

    /**
     * A retry that cannot name its own subject is a button that does nothing,
     * which is the state this whole sheet exists to end.
     */
    @Test
    fun `retry re-runs the request that failed`() = runTest {
        var attempts = 0
        val store = PersonProfileStore(
            repository = FakeRepository(staffProfile = {
                attempts++
                if (attempts == 1) throw IllegalStateException("rate limited")
                StaffProfile(id = 7, name = MediaName(full = "Second Try"))
            }),
            scope = this
        )

        store.openStaff(7, "Second Try")
        runCurrent()
        assertTrue(store.state.value is PersonProfileState.Failed)

        store.retry()
        runCurrent()

        assertEquals(2, attempts)
        assertEquals("Second Try", loaded(store).name)
    }

    /**
     * The id has to survive the failure, because that is all a retry has to go on.
     */
    @Test
    fun `a failure carries the id it failed on`() = runTest {
        val store = PersonProfileStore(
            repository = FakeRepository(
                characterProfile = { throw IllegalStateException("gone") }
            ),
            scope = this
        )

        store.openCharacter(99, "Nobody")
        runCurrent()

        val failed = store.state.value as PersonProfileState.Failed
        assertEquals(99, failed.id)
        assertEquals(PersonKind.CHARACTER, failed.kind)
    }

    /**
     * `Character(1)` and `Staff(1)` are different people, so a bare-id key would
     * treat the second request as a repeat of the first.
     *
     * That collision is why the store's pending key is a `(PersonKind, Int)` pair,
     * and it is why the repository's profile cache is keyed the same way.
     */
    @Test
    fun `a character and a staff member sharing an id are different requests`() = runTest {
        val characterGate = CompletableDeferred<CharacterProfile>()
        val store = PersonProfileStore(
            repository = FakeRepository(
                characterProfile = { characterGate.await() },
                staffProfile = { StaffProfile(id = 1, name = MediaName(full = "Staff One")) }
            ),
            scope = this
        )

        store.openCharacter(1, "Character One")
        store.openStaff(1, "Staff One")
        runCurrent()

        assertEquals(
            "the staff request must not be swallowed as a repeat of the character one",
            "Staff One",
            loaded(store).name
        )
    }

    /**
     * A host with no repository draws captions rather than buttons that can only
     * fail, so the store answers rather than throwing on that path.
     */
    @Test
    fun `the demo answers rather than throwing`() = runTest {
        val store = PersonProfileStore(repository = null, isDemo = true, scope = this)

        store.openCharacter(1, "Spike Spiegel")
        runCurrent()

        val failed = store.state.value as PersonProfileState.Failed
        assertTrue("the message has to say why: ${failed.message}", failed.message.contains("demo"))
    }

    @Test
    fun `an appearance keeps the type its tap will be routed on`() {
        val appearance = ProfileAppearance(
            id = 5,
            type = "MANGA",
            title = MediaTitle(english = "A Manga"),
            averageScore = 71
        )

        val node = appearance.toMediaNode()
        assertEquals(5, node.id)
        // A character can appear in a manga. An id alone resolves against whichever
        // namespace AniList prefers, and a tap routed on it opens the wrong sheet.
        assertEquals("MANGA", node.type)
        assertEquals(71, node.averageScore)
    }

    private fun loaded(store: PersonProfileStore): PersonProfileState.Loaded =
        store.state.value as PersonProfileState.Loaded

    // ---- the fakes ------------------------------------------------------------

    private class FakeRepository(
        private val characterProfile: suspend (Int) -> CharacterProfile = {
            CharacterProfile(id = it, name = MediaName(full = "Character $it"))
        },
        private val staffProfile: suspend (Int) -> StaffProfile = {
            StaffProfile(id = it, name = MediaName(full = "Staff $it"))
        }
    ) : AniListRepository {
        override suspend fun getCharacterProfile(characterId: Int): Result<CharacterProfile> =
            runCatching { characterProfile(characterId) }

        override suspend fun getStaffProfile(staffId: Int): Result<StaffProfile> =
            runCatching { staffProfile(staffId) }
    }
}