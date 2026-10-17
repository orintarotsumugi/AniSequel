package com.anisequel.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

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
    /** The shows they appear in, most popular first. */
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
 * [yearsActive] is a list on AniList's side - a person can have a non-contiguous
 * career - so it is modelled as a list and joined for display rather than collapsed
 * into a single string here.
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
    /** The titles they worked on, most popular first. */
    val media: ProfileAppearances? = null
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
            yearsActive?.takeIf { it.isNotEmpty() }
                ?.let { add("Active" to it.joinToString(", ")) }
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
 * No `pageInfo`: the row on a profile sheet is horizontal and bounded at twelve, so
 * pagination here would be a cursor nobody advances. The same decision as
 * `StudioMediaConnection`, which the studio page's paging rebuilds because it really
 * does page.
 */
@JsonClass(generateAdapter = true)
data class ProfileAppearances(
    val nodes: List<ProfileAppearance>? = null
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

/** A cover at the one size this row draws at. */
@JsonClass(generateAdapter = true)
data class ProfileCover(
    val large: String? = null
) {
    val bestUrl: String? get() = large?.takeIf { it.isNotBlank() }
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
        val kind: PersonKind,
        val name: String?,
        val portraitUrl: String?,
        val aliases: List<String>,
        val facts: List<Pair<String, String>>,
        val description: String?,
        val siteUrl: String?,
        val appearances: List<ProfileAppearance>
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
    kind = PersonKind.CHARACTER,
    name = name?.full,
    portraitUrl = image?.bestUrl,
    aliases = name?.alternative.orEmpty().filter { it.isNotBlank() && it != name?.full },
    facts = facts,
    description = description,
    siteUrl = siteUrl,
    appearances = media?.nodes.orEmpty()
)

/** Builds a [PersonProfileState.Loaded] from either payload. */
fun StaffProfile.toLoadedState(): PersonProfileState.Loaded = PersonProfileState.Loaded(
    kind = PersonKind.STAFF,
    name = name?.full,
    portraitUrl = image?.bestUrl,
    // A staff member has no aliases on AniList, so this is always empty for one and
    // the sheet simply hides the row rather than drawing an empty one.
    aliases = emptyList(),
    facts = facts,
    description = description,
    siteUrl = siteUrl,
    appearances = media?.nodes.orEmpty()
)