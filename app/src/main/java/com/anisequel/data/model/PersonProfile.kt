package com.anisequel.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Locale

/**
 * One character, as their in-app profile page.
 *
 * Modelled beside [StudioNode] rather than folded into it, because the two answer
 * different questions with different fields: a studio has a filmography and a
 * `isAnimationStudio` flag, and a character has aliases, a birth date and the shows
 * they appear in. The renderer does share one composable for both - see
 * `PersonProfileSheet` - which is the reuse worth having, not a shared model.
 *
 * [age] is a `String` on purpose. AniList reports `"27"` on most characters and
 * `"23 (Physical), 77 (Actual)"` on a character whose age is the joke, so an `Int?`
 * here would either fail to parse or silently truncate a character's age. See the
 * same reasoning on [MediaNode.season], where Moshi refused an enum string as an int
 * and threw out of the only call that fetches a user's whole list.
 *
 * [favourites] is the one number that says whether a character is well known, and it
 * is why the card is a control rather than a caption - the same argument as
 * `StudioNode.favourites`.
 */
@JsonClass(generateAdapter = true)
data class CharacterProfile(
    val id: Int,
    val name: MediaName? = null,
    val image: MediaImage? = null,
    /** AniList's own biography, as markdown-with-HTML-glue. */
    val description: String? = null,
    val gender: String? = null,
    val age: String? = null,
    val bloodType: String? = null,
    val favourites: Int? = null,
    val siteUrl: String? = null,
    val isFavourite: Boolean? = null,
    val dateOfBirth: FuzzyDate? = null,
    /**
     * The shows they appear in, most popular first.
     *
     * Edges rather than nodes, because the edge is where AniList hangs the two
     * things this page shows beside a title: the role in it, and who voiced the
     * character in it. See [ProfileAppearances].
     */
    val media: ProfileAppearances? = null
) {
    /**
     * The facts worth printing, in the order a person is normally introduced.
     *
     * Every entry is nullable because AniList has all of them and answers all of them
     * null constantly - `bloodType` is null on Spike Spiegel, and it is null on most
     * characters who are not from a shounen series. A row that printed a label with
     * nothing under it would be worse than the row not being there.
     *
     * Ordered rather than sorted: the order is the one a person is introduced in, and
     * it is fixed, so the card does not re-order itself between two characters of the
     * same kind.
     */
    val facts: List<Pair<String, String>>
        get() = buildList {
            gender?.takeIf { it.isNotBlank() }?.let { add("Gender" to it) }
            age?.takeIf { it.isNotBlank() }?.let { add("Age" to it) }
            dateOfBirth?.formatted?.takeIf { it != "TBA" }?.let { add("Born" to it) }
            bloodType?.takeIf { it.isNotBlank() }?.let { add("Blood type" to it) }
            favourites?.takeIf { it > 0 }?.let { add("Favourites" to it.toString()) }
        }
}

/** Payload of the character profile query. */
@JsonClass(generateAdapter = true)
data class CharacterProfileData(
    @Json(name = "Character") val character: CharacterProfile? = null
)

/**
 * One staff member, as their in-app profile page.
 *
 * Deliberately the same shape as [CharacterProfile] where the fields overlap, because
 * they are rendered by the same composable and a crew member that laid out differently
 * from an actor on the same page reads as two different screens.
 *
 * [yearsActive] is AniList's `[startYear, endYear]` pair - a lone `[startYear]` means
 * the person is still working - so [facts] renders it as a range rather than joining a
 * list of years that would read as a career with gaps in it.
 */
@JsonClass(generateAdapter = true)
data class StaffProfile(
    val id: Int,
    val name: MediaName? = null,
    val image: MediaImage? = null,
    val description: String? = null,
    /** `Voice Actor`, `Director`, `Animation`, `Story & Art`, ... - often several. */
    val primaryOccupations: List<String>? = null,
    val gender: String? = null,
    val age: String? = null,
    val yearsActive: List<String>? = null,
    val homeTown: String? = null,
    val bloodType: String? = null,
    val favourites: Int? = null,
    val siteUrl: String? = null,
    val isFavourite: Boolean? = null,
    val dateOfBirth: FuzzyDate? = null,
    val dateOfDeath: FuzzyDate? = null,
    /**
     * The titles they worked on, most popular first.
     *
     * The wire field is `staffMedia`, but the model calls it `media` so the mapper and
     * the sheet can read a person's films the same way a character's appearances are
     * read. Without this annotation Moshi looked for a field literally named `media`
     * in the staff response, found none, and left this null - so every creator's
     * "Worked on" row was empty while the same row on a character page filled
     * normally. The data was on the wire the whole time.
     */
    @Json(name = "staffMedia")
    val media: ProfileAppearances? = null,
    /**
     * The characters they voiced, most popular title first.
     *
     * Null for a creator who never performed - most crew are not voice actors - and
     * the sheet hides the row rather than drawing an empty one. See
     * [StaffCharacterMedia] for why this is a separate connection from [media].
     */
    val characterMedia: StaffCharacterMedia? = null
) {
    /**
     * The same fact list as [CharacterProfile.facts], plus the two a credit never
     * carries and a person sheet should.
     *
     * Death is only printed when AniList gave one: it is null for almost everyone
     * alive, and a card reading "Died —" next to a living person's portrait is not an
     * omission to paper over with a dash.
     */
    val facts: List<Pair<String, String>>
        get() = buildList {
            primaryOccupations?.takeIf { it.isNotEmpty() }
                ?.let { add("Role" to it.joinToString(", ")) }
            gender?.takeIf { it.isNotBlank() }?.let { add("Gender" to it) }
            age?.takeIf { it.isNotBlank() }?.let { add("Age" to it) }
            yearsActive?.takeIf { it.isNotEmpty() }?.let { years ->
                // AniList's `[startYear, endYear]`, or `[startYear]` while the person
                // still works. This used to be `joinToString(", ")`, which printed
                // "Active 1998, 2022" - a pair of years that reads as a career with a
                // gap in it rather than as the one span AniList recorded.
                val active = if (years.size >= 2) "${years[0]} - ${years[1]}" else "since ${years[0]}"
                add("Active" to active)
            }
            homeTown?.takeIf { it.isNotBlank() }?.let { add("From" to it) }
            dateOfBirth?.formatted?.takeIf { it != "TBA" }?.let { add("Born" to it) }
            dateOfDeath?.formatted?.takeIf { it != "TBA" }?.let { add("Died" to it) }
            favourites?.takeIf { it > 0 }?.let { add("Favourites" to it.toString()) }
        }
}

/** Payload of the staff profile query. */
@JsonClass(generateAdapter = true)
data class StaffProfileData(
    @Json(name = "Staff") val staff: StaffProfile? = null
)

/**
 * The titles a person appears in or worked on.
 *
 * No `pageInfo`: the row on a profile sheet is horizontal and bounded at the page
 * size the query asks for, so pagination here would be a cursor nobody advances. The
 * same decision as `StudioMediaConnection`, which the studio page's paging rebuilds
 * because it really does page.
 *
 * The bound is larger than the number of cards a screen shows at once because a
 * staff connection spends its edges on credits, not titles: one film can arrive
 * several times, so asking for twelve edges once produced four posters. See
 * [titlesFor].
 */
@JsonClass(generateAdapter = true)
data class ProfileAppearances(
    val nodes: List<ProfileAppearance>? = null,
    /**
     * The same connection, as edges.
     *
     * Both queries ask for `edges` rather than `nodes` because the edge is where
     * AniList hangs what a row of posters cannot carry: `characterRole` and the people
     * who voiced a character, and a staff member's own `staffRole` on the titles they
     * worked on. `nodes` is still read by [titles], so a response written before the
     * queries moved to edges - or a cache entry keyed to one - still renders.
     */
    val edges: List<ProfileAppearanceEdge>? = null
) {
    /**
     * The titles, whichever half the query asked for.
     *
     * `edges` wins when AniList sent it: it carries a node *and* the credits beside
     * it, and a connection that answered edges has not answered nodes. A node inside
     * an edge is nullable in the schema, so a missing one is dropped rather than
     * drawn as "Unknown".
     *
     * Each surviving node is copied with the edge's `staffRole`, because the credit
     * arrives on the edge and the row draws nodes. The copy is what makes that
     * attribution survive the flattening: a parallel list keyed by media id would
     * drop one credit whenever an anime and a manga share an id, which is exactly the
     * collision `MediaCoverKey` documents. Null for a character's page, whose edges
     * do not ask for one.
     */
    val titles: List<ProfileAppearance>
        get() = titlesFor()

    /**
     * The titles, with duplicate roles on one title collapsed.
     *
     * A media connection returns one edge per credit, not one edge per title: a
     * director who wrote, storyboarded and directed the same episode of the same show
     * appears three times with the same `node`. Drawn as sent, the row repeated a
     * handful of posters and the connection's page of edges was spent on duplicates -
     * so twelve edges could be four titles, and the rest of a person's work was never
     * requested. Deduplicating by namespace and id is what makes the row a
     * filmography rather than a list of credits.
     *
     * [preferredRoles] is the person's `primaryOccupations`, when the caller has
     * them. The duplicate edges for one title arrive in no useful order ("Script
     * (eps 17, 21)" can precede "Director"), so the first edge is not reliably the
     * credit that says why this person is on the page. A role matching one of their
     * primary occupations wins instead; otherwise the first non-blank role is kept.
     *
     * `nodes` is still read as a fallback for a response written before the queries
     * moved to edges - or a cache entry keyed to one - the same contract this
     * property has always had.
     */
    fun titlesFor(preferredRoles: List<String> = emptyList()): List<ProfileAppearance> {
        val edges = edges ?: return nodes.orEmpty()
        val preferred = preferredRoles
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
            .toSet()

        // Insertion-ordered: the connection arrives most popular first, and the row
        // draws that order, so deduplicating must not reshuffle it.
        val byTitle = LinkedHashMap<Pair<String, Int>, ProfileAppearance>()
        edges.forEach { edge ->
            val node = edge.node ?: return@forEach
            val key = node.type?.uppercase(Locale.ROOT).orEmpty() to node.id
            val existing = byTitle[key]
            byTitle[key] = if (existing == null) {
                node.copy(staffRole = edge.staffRole)
            } else {
                existing.copy(
                    staffRole = betterRole(existing.staffRole, edge.staffRole, preferred)
                )
            }
        }
        return byTitle.values.toList()
    }

    /**
     * The credit to keep when one title arrives with several.
     *
     * A primary occupation is the strongest signal available: AniList says a director
     * is a director, and "Director" is what a reader needs under the poster even when
     * the connection listed "Script" first. With no preference, the first non-blank
     * role wins because that is the connection's own ordering.
     */
    private fun betterRole(current: String?, candidate: String?, preferred: Set<String>): String? {
        if (candidate.isNullOrBlank()) return current
        if (current.isNullOrBlank()) return candidate
        if (current.trim().lowercase(Locale.ROOT) in preferred) return current
        if (candidate.trim().lowercase(Locale.ROOT) in preferred) return candidate
        return current
    }

    /**
     * Who voices this character, Japanese before English.
     *
     * Deduplicated per language: one actor usually voices a character across every
     * title they appear in, and twelve edges answering the same name twelve times
     * would draw twelve identical rows. The edges arrive most popular first, so the
     * first credit kept per language is the one a reader recognises.
     */
    val voiceActorCredits: List<VoiceActorCredit>
        get() = creditsFor({ it.voiceActors }, "Japanese") +
            creditsFor({ it.voiceActorsEnglish }, "English")

    private fun creditsFor(
        actorsOf: (ProfileAppearanceEdge) -> List<VoiceActor>?,
        language: String
    ): List<VoiceActorCredit> {
        val seen = mutableSetOf<Int>()
        return edges.orEmpty()
            .flatMap { actorsOf(it).orEmpty() }
            .filter { seen.add(it.id) }
            .mapNotNull { actor ->
                actor.name?.full?.takeIf { it.isNotBlank() }?.let { name ->
                    VoiceActorCredit(
                        staffId = actor.id,
                        name = name,
                        imageUrl = actor.image?.bestUrl,
                        language = language
                    )
                }
            }
    }
}

/**
 * One title in a character's media connection, with what only the edge carries.
 *
 * `voiceActors` is asked for twice, under two names, because the schema's field takes
 * a language argument and **answers null without one** - checked against the live API:
 * `voiceActors { id name { full } }` on a character's media came back
 * `"voiceActors": null`, while `voiceActors(language: JAPANESE)` answered the actors.
 * The query aliases the second one as `voiceActorsEnglish`, so a dub and an original
 * performance travel in the one request. Two languages rather than a picker: this is
 * a profile sheet, not AniList's own page, and the dubbing languages an app can show
 * in a row are the two a reader is most likely to recognise.
 */
@JsonClass(generateAdapter = true)
data class ProfileAppearanceEdge(
    val node: ProfileAppearance? = null,
    /** `MAIN`, `SUPPORTING`, `BACKGROUND`, ... - the role in this one title. */
    val characterRole: String? = null,
    /**
     * What the person did on this title - `Director`, `ADR Script (English)`, ...
     *
     * A staff page's edges carry it; a character's edges answer it null, because a
     * character is not crew. See [ProfileAppearances.titles] for where it is copied
     * onto the node the row actually draws.
     */
    val staffRole: String? = null,
    /** The actors who voiced the character in this title, in one language. */
    val voiceActors: List<VoiceActor>? = null,
    /** The same, for the English dub. See the class note. */
    val voiceActorsEnglish: List<VoiceActor>? = null
)

/**
 * The characters a staff member voiced, as the staff page's "Voiced" row draws them.
 *
 * `Staff.staffMedia` answers what a person *worked on*; this answers who they
 * *performed*. On AniList those are different lists - a director's filmography is not
 * their roles - and a voice actor's page that only listed productions could say where
 * their voice appears and never who it was.
 *
 * A media connection, not a plain list: `characters` hangs on the edge beside the
 * `characterRole` that says how the character figures in the title, so the connection
 * is asked for as `edges` - the same reason [ProfileAppearances] is. Flattening the
 * edges into one [VoicedCharacter] per person happens in [characters].
 */
@JsonClass(generateAdapter = true)
data class StaffCharacterMedia(
    val edges: List<StaffCharacterEdge>? = null
) {
    /**
     * The characters, flattened from every edge and de-duplicated, in the order
     * AniList sent them (most popular title first).
     *
     * The edge's list is flattened rather than its first entry read, because one
     * person can be credited with more than one character in a title - two roles in
     * one show is the ordinary case for a prolific actor. The same character then
     * recurs across a franchise's edges, so the first credit wins and the later
     * duplicates are dropped before they draw identical cards.
     *
     * Entries without an id or a name are skipped rather than drawn: the id is the
     * tap into the character's page and the name is the whole caption under the
     * portrait, so a card missing either is a control that cannot work or a face with
     * a blank line beside it.
     */
    val characters: List<VoicedCharacter>
        get() {
            val seen = mutableSetOf<Int>()
            return edges.orEmpty().flatMap { edge ->
                edge.characters.orEmpty().mapNotNull { node ->
                    val id = node.id ?: return@mapNotNull null
                    val name = node.name?.full?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    if (!seen.add(id)) return@mapNotNull null
                    VoicedCharacter(
                        id = id,
                        name = name,
                        imageUrl = node.image?.bestUrl,
                        characterRole = edge.characterRole
                    )
                }
            }
        }
}

/**
 * One title in a staff member's `characterMedia` connection.
 *
 * `characterRole` is the *character's* part in the title (`MAIN`, `SUPPORTING`), not
 * the person's - it is drawn under the name as the caption that says whether this was
 * a lead or a cameo. The person's own credit on a title is `staffRole` on
 * [ProfileAppearanceEdge], from the other connection.
 */
@JsonClass(generateAdapter = true)
data class StaffCharacterEdge(
    /** `MAIN`, `SUPPORTING`, `BACKGROUND`, ... */
    val characterRole: String? = null,
    val characters: List<StaffCharacterNode>? = null
)

/**
 * One character on a [StaffCharacterEdge], as the API sends it.
 *
 * The id is nullable where the schema declares it non-null, deliberately: this is the
 * raw shape [StaffCharacterMedia.characters] filters, so a response missing the one
 * field the tap is routed on is droppable. Modelled non-null, Moshi would throw on
 * that entry and take the whole staff page down with it rather than one card.
 */
@JsonClass(generateAdapter = true)
data class StaffCharacterNode(
    val id: Int? = null,
    val name: MediaName? = null,
    val image: MediaImage? = null
)

/**
 * One character a voice actor performed, as the "Voiced" row draws them.
 *
 * The mapped shape: [StaffCharacterMedia.characters] has already dropped the entries
 * a card cannot draw and flattened the edges, so `id` and `name` here are the ones
 * the row needs rather than the ones the API sends - see [StaffCharacterNode] for why
 * those are nullable. [characterRole] rides along from the edge and is the small
 * caption under the name; null pages draw the name alone.
 */
data class VoicedCharacter(
    val id: Int,
    val name: String,
    val imageUrl: String?,
    val characterRole: String? = null
)

/** One actor credited on one media edge. */
@JsonClass(generateAdapter = true)
data class VoiceActor(
    val id: Int,
    val name: MediaName? = null,
    val image: MediaImage? = null
)

/**
 * One actor credited with voicing a character, as the "Voiced by" row draws it.
 *
 * Not a [ProfileAppearance] and not a [ProfileAppearanceEdge]: this is a *person*,
 * and the tap on it opens their page rather than a title. [language] is AniList's
 * `StaffLanguage`, already spelled the way the row prints it.
 */
data class VoiceActorCredit(
    val staffId: Int,
    val name: String,
    val imageUrl: String?,
    val language: String
)

/**
 * One title in a person's profile row.
 *
 * `type` travels with every node because a character can appear in a manga and a tap
 * has to open the right detail sheet - an id alone resolves against whichever
 * namespace AniList prefers, which is the same collision `MediaCoverKey` documents.
 */
@JsonClass(generateAdapter = true)
data class ProfileAppearance(
    val id: Int,
    val type: String? = null,
    val title: MediaTitle? = null,
    val format: String? = null,
    val status: String? = null,
    val averageScore: Int? = null,
    val siteUrl: String? = null,
    /**
     * What this person did on this title, when the page was built from a staff
     * member's edges.
     *
     * Copied from [ProfileAppearanceEdge.staffRole] by [ProfileAppearances.titles]:
     * the credit lives on the edge, the row draws nodes, and this is where the two
     * meet. Null on a character's page and on any response whose query did not ask
     * for it.
     */
    val staffRole: String? = null,
    /**
     * When the title began, as AniList records it.
     *
     * Carried because a creator's page lists their work by year, and the year is
     * not derivable from anything else on the node: a popularity-ordered
     * connection jumps between decades, so without this the list reads as a
     * jumble rather than as a career. `year` is the only component asked for -
     * the month and day of a release are trivia on a filmography, and asking for
     * them would be three fields to ignore.
     *
     * Null for a title AniList has not dated yet, and for any response written
     * before the query asked for it. Both draw under "Year unknown" rather than
     * being dropped.
     */
    val startDate: FuzzyDate? = null,
    val coverImage: ProfileCover? = null
) {
    /**
     * The format word, worded the way a person writes it.
     *
     * Delegates to the same [formatLabelOf] the title page uses rather than carrying
     * its own mapping: this row sits under a cover on a person profile, and a
     * separate `when` would eventually print `TV_SHORT` here and "TV Short" there.
     *
     * `type` decides the fallback, since a title with no format is still a manga or
     * an anime as far as the label is concerned.
     */
    val formatLabel: String get() = formatLabelOf(format, isManga)

    /** Whether this appearance is a manga, from the `type` AniList sent. */
    val isManga: Boolean get() = type?.uppercase() == "MANGA"
}

/**
 * A cover at the sizes the appearance row can ask for.
 *
 * The row draws `APPEARANCE_CARD_WIDTH = 112.dp`, about 336px on a 3x screen, so
 * `large` (230px) is upscaled before it is drawn and `extraLarge` (460px) is the
 * size that actually arrives sharp. Both are carried because a response from
 * before the query asked for `extraLarge` - or a cache entry keyed to one - still
 * has the smaller field, and this row is the only consumer.
 */
@JsonClass(generateAdapter = true)
data class ProfileCover(
    val extraLarge: String? = null,
    val large: String? = null
) {
    /** The biggest size AniList sent, preferring the one this row draws at. */
    val bestUrl: String?
        get() = extraLarge?.takeIf { it.isNotBlank() } ?: large?.takeIf { it.isNotBlank() }
}

/**
 * A person whose profile sheet is open, as a state the sheet can render.
 *
 * One type for both characters and staff, because the sheet is one composable and
 * because the states are the same: not asked for, loading, loaded, or failed. The two
 * payloads are kept apart in the sealed interface rather than merged, so the sheet
 * can read `facts` and `appearances` without a null check on which kind it is.
 */
sealed interface PersonProfileState {

    /** Null when no profile is open - the sheet's visibility is derived from this. */
    data object Closed : PersonProfileState

    data class Loading(val kind: PersonKind, val name: String?) : PersonProfileState

    data class Loaded(
        /**
         * The AniList id of the person, so two people can be told apart.
         *
         * Carried because the sheet is one composable that swaps from one person to
         * the next - a tap on a voice actor opens their page in the same window - and
         * the body has to be re-keyed on it or the new person inherits the scroll
         * position of the last one.
         */
        val id: Int,
        val kind: PersonKind,
        val name: String?,
        val portraitUrl: String?,
        val aliases: List<String>,
        val facts: List<Pair<String, String>>,
        val description: String?,
        val siteUrl: String?,
        val appearances: List<ProfileAppearance>,
        /**
         * The people who voiced this character, Japanese before English.
         *
         * Empty for a staff member - a person is not voiced - and for a character
         * whose media answered no voice actors at all.
         */
        val voiceActors: List<VoiceActorCredit> = emptyList(),
        /**
         * The characters a voice actor performed, as the staff page's "Voiced" row.
         *
         * The mirror image of [voiceActors]: that answers who voiced a *character*,
         * this answers which *characters* a person voiced. Empty for a character,
         * and for staff who never performed.
         */
        val voicedCharacters: List<VoicedCharacter> = emptyList()
    ) : PersonProfileState

    data class Failed(
        val kind: PersonKind,
        val id: Int,
        val name: String?,
        val message: String
    ) : PersonProfileState

    val kindOrNull: PersonKind? get() = when (this) {
        is Loading -> kind
        is Loaded -> kind
        is Failed -> kind
        Closed -> null
    }
}

/** Which of the two person profiles a state is, so the sheet can label itself. */
enum class PersonKind { CHARACTER, STAFF }

/** Builds a [PersonProfileState.Loaded] from either payload. */
fun CharacterProfile.toLoadedState(): PersonProfileState.Loaded = PersonProfileState.Loaded(
    id = id,
    kind = PersonKind.CHARACTER,
    name = name?.full,
    portraitUrl = image?.bestUrl,
    aliases = name?.alternative.orEmpty().filter { it.isNotBlank() && it != name?.full },
    facts = facts,
    description = description,
    siteUrl = siteUrl,
    appearances = media?.titles.orEmpty(),
    voiceActors = media?.voiceActorCredits.orEmpty()
)

/** Builds a [PersonProfileState.Loaded] from either payload. */
fun StaffProfile.toLoadedState(): PersonProfileState.Loaded = PersonProfileState.Loaded(
    id = id,
    kind = PersonKind.STAFF,
    name = name?.full,
    portraitUrl = image?.bestUrl,
    // A staff member has no aliases on AniList, so this is always empty for one and
    // the sheet simply hides the row rather than drawing an empty one.
    aliases = emptyList(),
    facts = facts,
    description = description,
    siteUrl = siteUrl,
    // Primary occupations are passed through so a title credited more than once
    // ("Director" and "Script (eps 17, 21)") keeps the role the person is known for.
    appearances = media?.titlesFor(primaryOccupations.orEmpty()).orEmpty(),
    // A person is not voiced, so the character-side row stays empty here; what a
    // voice actor *performed* is the other connection and the other row.
    voicedCharacters = characterMedia?.characters.orEmpty()
)