package com.anisequel

import com.anisequel.data.model.CharacterProfile
import com.anisequel.data.model.MediaImage
import com.anisequel.data.model.MediaName
import com.anisequel.data.model.MediaTitle
import com.anisequel.data.model.PersonKind
import com.anisequel.data.model.PersonProfileState
import com.anisequel.data.model.ProfileAppearance
import com.anisequel.data.model.ProfileAppearanceEdge
import com.anisequel.data.model.ProfileAppearances
import com.anisequel.data.model.StaffCharacterEdge
import com.anisequel.data.model.StaffCharacterMedia
import com.anisequel.data.model.StaffCharacterNode
import com.anisequel.data.model.StaffProfile
import com.anisequel.data.model.VoiceActor
import com.anisequel.data.model.toLoadedState
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.SimpleMediaListEntry
import com.anisequel.data.model.MediaListCollection
import com.anisequel.data.model.ViewerProfile
import com.anisequel.data.network.GraphQLQueries
import com.anisequel.data.repository.AniListRepository
import com.anisequel.ui.viewmodel.PersonProfileStore
import com.anisequel.ui.viewmodel.toMediaNode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
@OptIn(ExperimentalCoroutinesApi::class)
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
            yearsActive = listOf("1998", "2022"),
            homeTown = "Tokyo"
        )

        val facts = yatate.facts
        assertEquals("Director, Original Creator", facts.first { it.first == "Role" }.second)
        assertTrue("the role is the point of a crew card", facts.any { it.first == "Role" })
        assertTrue("years active", facts.any { it.first == "Active" })
        assertTrue("hometown", facts.any { it.first == "From" })
    }

    /**
     * `yearsActive` is AniList's `[startYear, endYear]`, not a list of every year the
     * person worked and not one already-formatted range string.
     *
     * Joined as a list it printed "Active 1998, 2022" - two years that read as a career
     * with a twenty-four year hole in it - and the lone-year form ("still active")
     * printed as a bare year. The schema spells both out: `[startYear, endYear] (If the
     * 2nd value is not present staff is still active)`.
     */
    @Test
    fun `active years render as the span AniList recorded`() {
        val finished = StaffProfile(id = 1, yearsActive = listOf("1998", "2022"))
        val working = StaffProfile(id = 2, yearsActive = listOf("1998"))

        assertEquals("1998 - 2022", finished.facts.first { it.first == "Active" }.second)
        assertEquals("since 1998", working.facts.first { it.first == "Active" }.second)
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

        // Released rather than left hanging. `runTest` fails on a coroutine still
        // suspended when the body returns, and the character request is parked on
        // this gate for the whole test. Letting it land also shows the guard holding
        // for the id-sharing case and not only the replace-with-a-different-id one.
        characterGate.complete(CharacterProfile(id = 1, name = MediaName(full = "Character One")))
        runCurrent()
        assertEquals("Staff One", loaded(store).name)
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

    // ---- the voice actors on a character's page --------------------------------

    /**
     * Japanese before English, and one row per actor however many edges name them.
     *
     * One performer usually voices a character across every title they appear in, so
     * the raw connection answers the same name on every edge. Drawn as sent, "Voiced
     * by" would be twelve identical rows; deduplicated, it is the two answers a
     * reader is looking for.
     */
    @Test
    fun `voice actors are deduplicated per language and ordered Japanese first`() {
        val spike = CharacterProfile(
            id = 1,
            name = MediaName(full = "Spike Spiegel"),
            media = ProfileAppearances(
                edges = listOf(
                    edge(
                        node = ProfileAppearance(id = 1, type = "ANIME"),
                        japanese = VoiceActor(95012, MediaName(full = "Koichi Yamadera")),
                        english = VoiceActor(95013, MediaName(full = "Steven Blum"))
                    ),
                    edge(
                        node = ProfileAppearance(id = 5, type = "ANIME"),
                        japanese = VoiceActor(95012, MediaName(full = "Koichi Yamadera")),
                        english = VoiceActor(95013, MediaName(full = "Steven Blum"))
                    )
                )
            )
        )

        assertEquals(
            listOf(
                "Koichi Yamadera" to "Japanese",
                "Steven Blum" to "English"
            ),
            spike.toLoadedState().voiceActors.map { it.name to it.language }
        )
    }

    /**
     * An actor AniList did not name is not a row.
     *
     * The name is the whole row - there is no second field to fall back on - so one
     * without it would be a face with a blank line beside it.
     */
    @Test
    fun `a voice actor without a name is left out`() {
        val character = CharacterProfile(
            id = 1,
            media = ProfileAppearances(
                edges = listOf(
                    edge(
                        node = ProfileAppearance(id = 1),
                        japanese = VoiceActor(1, name = null)
                    ),
                    edge(
                        node = ProfileAppearance(id = 2),
                        japanese = VoiceActor(2, MediaName(full = "  "))
                    )
                )
            )
        )

        assertTrue(character.toLoadedState().voiceActors.isEmpty())
    }

    /**
     * A character's titles now arrive as edges - the half that also carries the role
     * and the voice actors - so the row must read the node out of each one.
     */
    @Test
    fun `a character's titles are the nodes of its edges`() {
        val character = CharacterProfile(
            id = 1,
            media = ProfileAppearances(
                edges = listOf(
                    edge(node = ProfileAppearance(id = 7, type = "MANGA")),
                    // The schema lets an edge answer without a node; it is dropped
                    // rather than drawn as "Unknown".
                    ProfileAppearanceEdge(node = null)
                )
            )
        )

        assertEquals(listOf(7), character.toLoadedState().appearances.map { it.id })
    }

    /** A staff member still asks for nodes, and the row still reads them. */
    @Test
    fun `a staff member's titles are its nodes`() {
        val staff = StaffProfile(
            id = 1,
            media = ProfileAppearances(nodes = listOf(ProfileAppearance(id = 9)))
        )

        assertEquals(listOf(9), staff.toLoadedState().appearances.map { it.id })
        assertTrue("a person is not voiced", staff.toLoadedState().voiceActors.isEmpty())
    }

    /**
     * The query is what populates "Voiced by", and each half of it is silent when it
     * is missing.
     *
     * `voiceActors` answers **null** when it is asked without a language - checked
     * against the live API - and a media connection asked for `nodes` carries no
     * voice actors at all. A refactor that "tidies away" the alias or the edges
     * therefore leaves the section empty rather than failing anywhere, which is
     * exactly the kind of change this test is here to catch.
     */
    @Test
    fun `the character query asks for the edge and both voice actor languages`() {
        val query = GraphQLQueries.getCharacterProfileQuery()

        assertTrue("the media connection must be asked for edges", query.contains("edges"))
        assertTrue("the original performance", query.contains("voiceActors(language: JAPANESE)"))
        assertTrue(
            "the dub, under its own alias",
            query.contains("voiceActorsEnglish: voiceActors(language: ENGLISH)")
        )
    }

    // ---- what the store remembers ---------------------------------------------

    /**
     * Looking at the same face twice costs one request, and the second is instant.
     *
     * The reader taps through a cast and goes back to someone they have already read.
     * Without the store's memory that re-open re-entered `Loading`, asked AniList the
     * same question and painted the same answer back a frame later - a spinner where
     * the page already was.
     */
    @Test
    fun `reopening a profile is answered from memory and sends nothing`() = runTest {
        var fetches = 0
        val store = PersonProfileStore(
            repository = FakeRepository(characterProfile = {
                fetches++
                CharacterProfile(id = it, name = MediaName(full = "Character $it"))
            }),
            scope = this
        )

        store.openCharacter(1, "Character 1")
        runCurrent()
        assertEquals(1, fetches)

        store.close()
        store.openCharacter(1, "Character 1")

        // Synchronously, before the scheduler runs anything: a remembered profile
        // never goes through `Loading` at all.
        assertEquals("Character 1", loaded(store).name)
        runCurrent()
        assertEquals("a remembered profile costs no second request", 1, fetches)

        // And the memory does not swallow a question it has no answer to.
        store.close()
        store.openCharacter(2, "Character 2")
        runCurrent()
        assertEquals(2, fetches)
        assertEquals("Character 2", loaded(store).name)
    }

    // ---- the staff page's credits ----------------------------------------------

    /**
     * The credit lives on the edge and `characters` hangs beside it, so both
     * connections arrive as edges - lost in a flattening to nodes, the rows go empty
     * and nothing fails to say so.
     */
    @Test
    fun `a staff page maps its credits and the characters they voiced`() {
        val staff = StaffProfile(
            id = 1,
            media = ProfileAppearances(
                edges = listOf(
                    ProfileAppearanceEdge(
                        node = ProfileAppearance(id = 20, type = "ANIME"),
                        staffRole = "Director"
                    ),
                    ProfileAppearanceEdge(
                        node = ProfileAppearance(id = 21, type = "ANIME"),
                        staffRole = "ADR Script (English)"
                    )
                )
            ),
            characterMedia = StaffCharacterMedia(
                edges = listOf(
                    characterEdge("MAIN", voicedNode(3, "Orochimaru", "https://img/oro.jpg"))
                )
            )
        )

        val loaded = staff.toLoadedState()
        assertEquals(
            "every staffRole has to survive the edges-to-nodes flattening",
            listOf("Director", "ADR Script (English)"),
            loaded.appearances.map { it.staffRole }
        )
        val voiced = loaded.voicedCharacters.single()
        assertEquals(3, voiced.id)
        assertEquals("Orochimaru", voiced.name)
        assertEquals("https://img/oro.jpg", voiced.imageUrl)
        assertEquals("MAIN", voiced.characterRole)
        assertTrue("a person is not voiced", loaded.voiceActors.isEmpty())
    }

    /**
     * A character entry AniList did not name or identify is not drawn: the id is the
     * tap into the character's page and the name is the whole caption under the
     * portrait, so a card missing either has nothing to be. The raw node's id is
     * nullable precisely so this filter can exist rather than a parse failure taking
     * the whole staff page down with it.
     */
    @Test
    fun `a voiced character without an id or a name is dropped`() {
        val staff = StaffProfile(
            id = 1,
            characterMedia = StaffCharacterMedia(
                edges = listOf(
                    characterEdge(
                        null,
                        voicedNode(null, "No Id"),
                        voicedNode(4, null),
                        voicedNode(4, "  "),
                        voicedNode(5, "Real")
                    )
                )
            )
        )

        assertEquals(listOf(5), staff.toLoadedState().voicedCharacters.map { it.id })
    }

    /**
     * The same character across a franchise's edges is one card. The first credit -
     * the edges arrive most popular first - is the one a reader recognises.
     */
    @Test
    fun `a character credited on two titles is one card`() {
        val staff = StaffProfile(
            id = 1,
            characterMedia = StaffCharacterMedia(
                edges = listOf(
                    characterEdge("MAIN", voicedNode(3, "Orochimaru")),
                    characterEdge("SUPPORTING", voicedNode(3, "Orochimaru"))
                )
            )
        )

        val voiced = staff.toLoadedState().voicedCharacters
        assertEquals("one card however many titles name the character", 1, voiced.size)
        assertEquals("the first credit wins", "MAIN", voiced.single().characterRole)
    }

    /**
     * A `staffMedia` asked for as `nodes` carries no `staffRole`, and a
     * `characterMedia` asked for as `nodes` carries no `characters` - either way the
     * rows just go empty, so the document's shape is pinned here rather than left to
     * the mapper, which can only see what arrived.
     */
    @Test
    fun `the staff query asks for the credit and the voiced characters`() {
        val query = GraphQLQueries.getStaffProfileQuery()
        assertTrue(
            "staffMedia must be edges: the credit hangs on them",
            query.contains("staffMedia(perPage: 12, sort: [POPULARITY_DESC])") &&
                query.contains("edges") && query.contains("staffRole")
        )
        assertTrue(
            "the characters only exist under the characterMedia connection",
            query.contains("characterMedia(perPage: 12, sort: [POPULARITY_DESC])") &&
                query.contains("characters {")
        )
    }

    /**
     * The character side of the sheet is untouched by the staff additions: its titles
     * still come out of the edges and its voice actors are still deduplicated, and a
     * character gains no voiced-character row of its own.
     */
    @Test
    fun `a character's titles and voice actors still hold`() {
        val character = CharacterProfile(
            id = 1,
            media = ProfileAppearances(
                edges = listOf(
                    edge(
                        node = ProfileAppearance(id = 7, type = "ANIME"),
                        japanese = VoiceActor(11, MediaName(full = "Koichi Yamadera"))
                    )
                )
            )
        )

        assertEquals(listOf(7), character.media?.titles?.map { it.id })
        assertEquals(listOf("Koichi Yamadera"), character.media?.voiceActorCredits?.map { it.name })
        assertTrue(character.toLoadedState().voicedCharacters.isEmpty())
    }

    private fun edge(
        node: ProfileAppearance?,
        japanese: VoiceActor? = null,
        english: VoiceActor? = null
    ) = ProfileAppearanceEdge(
        node = node,
        voiceActors = japanese?.let { listOf(it) },
        voiceActorsEnglish = english?.let { listOf(it) }
    )

    /** One `characterMedia` edge, with the characters credited on it. */
    private fun characterEdge(role: String?, vararg nodes: StaffCharacterNode) =
        StaffCharacterEdge(characterRole = role, characters = nodes.toList())

    /** A character on a `characterMedia` edge, as the query parses one. */
    private fun voicedNode(id: Int?, name: String? = null, image: String? = null) =
        StaffCharacterNode(
            id = id,
            name = name?.let { MediaName(full = it) },
            image = image?.let { MediaImage(large = it) }
        )

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

        /**
         * The reads this test does not exercise.
         *
         * These six are the interface's abstract members - the ones the app actually
         * depends on, which is why they have no default and everything else does.
         * A fake that left them abstract would not compile, and one that invented
         * plausible answers for them would be a second thing under test.
         */
        private fun unused(): Nothing =
            throw UnsupportedOperationException("not exercised by PersonProfileTest")

        override suspend fun getViewer(): Result<ViewerProfile> = unused()
        override suspend fun getUserByName(userName: String): Result<ViewerProfile> = unused()
        override suspend fun getMediaDetail(mediaId: Int, mediaType: String?): Result<MediaNode> = unused()
        override suspend fun addToPlanning(mediaId: Int): Result<SimpleMediaListEntry> = unused()

        override suspend fun getUserAnimeList(
            userId: Int,
            forceRefresh: Boolean
        ): Result<MediaListCollection> = unused()

        override suspend fun getUserAnimeListByUsername(
            userName: String,
            forceRefresh: Boolean
        ): Result<MediaListCollection> = unused()

        override suspend fun getCharacterProfile(characterId: Int): Result<CharacterProfile> =
            runCatching { characterProfile(characterId) }

        override suspend fun getStaffProfile(staffId: Int): Result<StaffProfile> =
            runCatching { staffProfile(staffId) }

        // The demo fixtures. Not `suspend` - they are fixtures rather than reads, so
        // they carry no query and no network. The default answers them rather than
        // refusing, because the demo *does* have a list to show; this fake simply
        // is not the demo.
        override fun getDemoProfile(): ViewerProfile = unused()
        override fun getDemoAnimeList(): MediaListCollection = unused()
    }
}