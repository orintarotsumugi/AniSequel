package com.anisequel.data.model

import java.util.Locale

/**
 * The display derivations the detail sheet reads off a [MediaNode].
 *
 * They live on the node rather than in the sheet because two shapes render a
 * media as a detail view: a [MissedSequel] (a recommendation from the list,
 * which wraps the node it is a sequel to) and a bare [MediaNode] (an arriving
 * row or a profile activity item, which has no parent). Deriving them here lets
 * both go through the same components instead of a row with no parent inventing
 * a fake [MissedSequel] - the exact thing [ArrivingEntry]'s doc says not to do.
 *
 * The names avoid the node's own fields (`format`, `status`, `episodes`,
 * `genres`, `source`, `duration`, `synonyms`, `meanScore`, `rankings`,
 * `description`) so a derived value cannot be mistaken for the raw one.
 */

/** The title to show, falling back the way [MediaTitle.displayTitle] does. */
val MediaNode.displayName: String get() = title?.displayTitle ?: "Unknown Title"

val MediaNode.romajiName: String? get() = title?.romaji?.takeIf { it.isNotBlank() }
val MediaNode.englishName: String? get() = title?.english?.takeIf { it.isNotBlank() }
val MediaNode.nativeName: String? get() = title?.native?.takeIf { it.isNotBlank() }

/** The key visual, at the best quality AniList sent. */
val MediaNode.posterUrl: String? get() = coverImage?.bestUrl

/**
 * AniList's dominant colour for the key visual, as `#RRGGBB`.
 *
 * Used to tint the placeholder behind a poster that has not loaded, so a view
 * waiting on art is recognisably the right show instead of a grey rectangle.
 */
val MediaNode.posterColor: String? get() = coverImage?.color

val MediaNode.isManga: Boolean get() = type.equals("MANGA", ignoreCase = true)

/**
 * An AniList music video.
 *
 * `MUSIC` is a `MediaFormat`, not a `MediaType` - the entry still lives in the
 * anime namespace, which is why `relatedMedia` cannot find it by medium alone.
 * AniList connects a music video to the show whose characters it features with
 * a `CHARACTER` relation, the same relation type it uses for crossovers, so the
 * row has to look at the format to tell a song from a crossover.
 */
val MediaNode.isMusicVideo: Boolean
    get() = format.equals("MUSIC", ignoreCase = true) ||
        format.equals("MUSIC_VIDEO", ignoreCase = true)

/**
 * Whether this title has not aired yet, so there is no episode of it to have watched.
 *
 * AniList's own `status`, not an inference from a missing start date: a show can
 * carry a date and still be unreleased, and a planner entry for a title two years
 * out is exactly the case the entry editor must not ask "how many episodes?".
 *
 * `CANCELLED` is included because a cancelled show is not going to air either.
 * `null` status is *not* treated as unaired: AniList leaves it null for media it
 * has little data on, and hiding the row on a missing field would take the
 * control away from a title the user can plainly see episodes of. Guessing
 * wrong in that direction is worse than asking one extra question.
 */
val MediaNode.hasNotAired: Boolean
    get() = status.equals("NOT_YET_RELEASED", ignoreCase = true) ||
        status.equals("CANCELLED", ignoreCase = true)

/**
 * The format, in the words a person uses for it.
 *
 * Was the raw enum with an underscore in it - `TV_SHORT`, `ONE_SHOT` - uppercased
 * at the call site into `TV_SHORT`, printed in a hero chip and in the Details grid
 * beside "Episodes" and "Season". That is the raw GraphQL vocabulary of the API
 * appearing in the same typeface as the show's name, and it is exactly what the
 * KDoc on [statusLabel] says was fixed for status and left broken for format.
 *
 * `MANGA` and `NOVEL` are kept as the words rather than lowercased, because that is
 * how they are written on a shelf; `TV` and `OVA`/`ONA` are acronyms and stay
 * upper. An unrecognised value is passed through untouched rather than replaced,
 * for the same reason [statusLabel] does it: an unfamiliar word honestly signals
 * that it is unfamiliar, where a confident wrong label does not.
 */
val MediaNode.formatLabel: String
    get() = formatLabelOf(format, isManga)

/**
 * The format word for a raw enum string, shared by every carrier of a `format`.
 *
 * Extracted so [MediaNode.formatLabel] and [ProfileAppearance.formatLabel] cannot
 * disagree. A person profile's appearances row and a title page's Details grid sit
 * side by side in this app; a second copy of this `when` would drift the first time
 * someone added a format, and one surface would print `TV_SHORT` while the other
 * printed "TV Short" - the exact defect the KDoc on [formatLabel] describes.
 *
 * [rawFormat] is AniList's enum string or null. [isManga] only decides the fallback
 * when it is absent, since a missing format still has a namespace to answer from.
 */
fun formatLabelOf(rawFormat: String?, isManga: Boolean): String {
    val raw = rawFormat?.trim()?.takeIf { it.isNotBlank() }
        ?: return if (isManga) "Manga" else "TV"
    return when (raw.uppercase(Locale.ROOT)) {
        "TV" -> "TV"
        "TV_SHORT" -> "TV Short"
        "MOVIE" -> "Movie"
        "SPECIAL" -> "Special"
        "OVA" -> "OVA"
        "ONA" -> "ONA"
        "MANGA" -> "Manga"
        "NOVEL" -> "Novel"
        "ONE_SHOT" -> "One Shot"
        // AniList spells the format `MUSIC`; `MUSIC_VIDEO` is kept as an alias
        // because it is the word the API documentation uses and a fixture or a
        // future field could carry it. Mapping only the alias meant every real
        // music video fell through to the generic branch and read "Music".
        "MUSIC", "MUSIC_VIDEO" -> "Music Video"
        else -> raw.lowercase(Locale.ROOT)
            .replace('_', ' ')
            .replaceFirstChar { it.uppercase(Locale.ROOT) }
    }
}

/**
 * Airing status, worded the way a person writes it.
 *
 * Was `status ?: "UNKNOWN"`, which printed the **raw GraphQL enum name** into
 * the Info tab: a currently-airing show read "RELEASING", a finished one
 * "FINISHED", and an unrecognised or absent value read "UNKNOWN". Every one of
 * those is an implementation detail of the API's vocabulary, set in the same
 * typeface as "24 episodes · Winter 2023" beside it.
 *
 * [formatLabel] already solved this for `format` - it is why the grid says "TV
 * Short" and not "TV_SHORT". Status needed the same treatment and did not get
 * it, which is the same class of drift the favourites rows used to have.
 *
 * Unknown values are passed through untouched rather than replaced. A status
 * AniList adds later should read as what it is, not be silently rewritten into
 * "Unknown" - the honest failure is showing an unfamiliar word, and the
 * dishonest one is a confident label that is wrong.
 */
val MediaNode.statusLabel: String get() = when (status?.uppercase(Locale.ROOT)) {
    "FINISHED" -> "Finished"
    "RELEASING" -> "Airing"
    "NOT_YET_RELEASED" -> "Upcoming"
    "CANCELLED" -> "Cancelled"
    "HIATUS" -> "On hiatus"
    null -> "Unknown"
    else -> status
}
val MediaNode.episodesLabel: String get() = if (isManga) {
    chapters?.let { "$it chs" } ?: "Chapters TBA"
} else {
    episodes?.let { "$it eps" } ?: "Episodes TBA"
}
val MediaNode.chaptersLabel: String? get() = chapters?.let { "$it chs" }
val MediaNode.volumesLabel: String? get() = volumes?.let { "$it vols" }
val MediaNode.scoreLabel: String get() = averageScore?.let { "$it%" } ?: "N/A"
val MediaNode.meanScoreLabel: String? get() = meanScore?.let { "$it%" }
val MediaNode.releaseLabel: String get() = startDate?.formatted ?: "TBA"
val MediaNode.genreList: List<String> get() = genres ?: emptyList()

/**
 * One studio credited on a title.
 *
 * [id] is nullable because not every source carries it - the *list* query's
 * `studios { nodes { id name } }` does, but a fixture or a hand-built node may
 * not - and a credit with no id is a name that cannot be tapped rather than a
 * name that opens the wrong studio.
 */
data class StudioCredit(
    val id: Int?,
    val name: String,
    val isMain: Boolean
)

/**
 * Every studio credited on this title, the animation studio first.
 *
 * The title page used to draw one comma-joined line of names and nothing was
 * tappable, because the detail query asked AniList only for its **main** studios
 * and had no id to route to for the rest. AniList answers `studios` with both:
 * an `edges` connection flagged `isMain`, and a flat `nodes` list. Reading the
 * edges is what makes the page able to say "Shaft made this, Kodansha published
 * it" instead of printing six names as if they were six studios of equal weight.
 *
 * `nodes` is still read as the fallback, because the list query and the bio
 * covers carry no edges at all and a title with no studios shown is worse than a
 * title whose credits are unranked.
 */
val MediaNode.studioCredits: List<StudioCredit>
    get() {
        val fromEdges = studios?.edges.orEmpty().mapNotNull { edge ->
            // Bound once instead of read twice off the edge. `edge.node?.name ...
            // ?: return` proves the node non-null for the next line, and the price
            // of that proof was a compiler warning: `edge.node?.id` is a safe call
            // on a receiver the data flow has already established as non-null. The
            // local makes the code say what the compiler knows.
            val node = edge.node ?: return@mapNotNull null
            val name = node.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            StudioCredit(id = node.id, name = name, isMain = edge.isMain == true)
        }
        val credits = fromEdges.ifEmpty {
            // No edges means the answer is *unranked*, not that every credit is a
            // supporting one - so these are marked main, and the page draws them at
            // full weight. The alternative dims the animation studio on every path
            // that carries a flat list, which reads as AniList having told us it
            // was secondary when it said nothing at all.
            studios?.nodes.orEmpty().mapNotNull { node ->
                node.name?.takeIf { it.isNotBlank() }
                    ?.let { StudioCredit(id = node.id, name = it, isMain = true) }
            }
        }
        // Main first, and stable within each half, so the first row of chips is
        // the studio that actually made the thing even when the edges arrive in
        // AniList's own order.
        return credits.sortedByDescending { it.isMain }
    }

/**
 * The name of the studio that made this, for a one-line chip.
 *
 * Prefers a main credit, so a title whose `studios` now arrives with the
 * publisher first still headlines its animation studio - which is what it did
 * when the query asked for main studios only.
 */
val MediaNode.studioName: String? get() = studioCredits.firstOrNull()?.name

/** The entry's page on AniList, derived from the id when the query omitted it. */
val MediaNode.pageUrl: String get() = siteUrl ?: if (isManga) "https://anilist.co/manga/$id" else "https://anilist.co/anime/$id"

/** AniList's `MediaSource` enum, spelled the way a person writes it. */
val MediaNode.sourceLabel: String?
    get() = source?.replace('_', ' ')?.lowercase(Locale.ROOT)?.split(' ')
        ?.joinToString(" ") { word -> word.replaceFirstChar { it.uppercase(Locale.ROOT) } }

val MediaNode.durationLabel: String? get() = duration?.let { "$it mins / ep" }
val MediaNode.trailerYoutubeUrl: String? get() = trailer?.youtubeUrl

/**
 * The links that actually lead to somewhere the title can be watched.
 *
 * AniList's `externalLinks` is one list of three kinds, and the kind is what
 * decides whether a row belongs here: `SOCIAL` is a Facebook page, `INFO` is a
 * wiki, and only `STREAMING` is a place to watch the show. A card that drew all
 * three as "Where to watch" would offer a fan wiki as a streaming service.
 *
 * Both a site name and a URL are required, because a half-populated edge would
 * draw a blank chip. Services are deduplicated by name: AniList often lists the
 * same service twice for two regions, and two identical Crunchyroll chips are
 * one choice, not two.
 */
val MediaNode.streamingLinks: List<MediaExternalLink>
    get() = externalLinks.orEmpty()
        .filter { it.type.equals(STREAMING_LINK_TYPE, ignoreCase = true) }
        .filter { !it.site.isNullOrBlank() && !it.url.isNullOrBlank() }
        .distinctBy { it.site?.lowercase(Locale.ROOT) }

/** AniList's `ExternalLinkType` member that means "a place to watch this". */
private const val STREAMING_LINK_TYPE = "STREAMING"

val MediaNode.synonymList: List<String> get() = synonyms?.filter { it.isNotBlank() } ?: emptyList()

/**
 * The synopsis as prose: AniList's HTML stripped, and its `(Source: …)` line gone.
 *
 * Delegates to [descriptionBody] rather than reading [description] itself, because the
 * attribution is not part of the synopsis and counting it made the "Read more"
 * threshold fire on a description that fits on screen once the credit line is removed.
 */
val MediaNode.synopsis: String?
    get() = descriptionBody
        ?.replace(TAG_PATTERN, "")
        ?.replace(WHITESPACE_PATTERN, " ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

/**
 * The description with the `(Source: …)` attribution AniList appends removed.
 *
 * ## Why this exists rather than being left for the markdown renderer to drop
 *
 * AniList's `description` is prose followed by a credit in parentheses - One Piece's
 * ends `\n<br><br>\n(Source: VIZ Media)`, Cowboy Bebop's ends `\n(Source: AniList)`.
 * The Info card renders the description through the app's AniList markdown pipeline,
 * which treats that line as what it syntactically is: a paragraph. So every title's
 * synopsis ended with `(Source: Wikipedia)` printed in body type under the last line
 * of the story, which reads as part of the synopsis rather than as provenance - and
 * it counted toward the "Read more" threshold, so a description that comfortably fit
 * on screen still grew a button.
 *
 * Stripped only when the line is *nothing but* the attribution. AniList writes it as
 * its own line and may write several - one per section of a long description - so the
 * pattern is anchored per line; a synopsis that merely mentions a source mid-sentence
 * is left alone rather than losing text.
 */
val MediaNode.descriptionBody: String?
    get() = description
        ?.replace(SOURCE_ATTRIBUTION, "")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

/**
 * Every non-spoiler tag AniList returned, highest ranked first, with the rank kept.
 *
 * [rankedTags] strips the rank and caps the list at eight because a list card has
 * room for eight chips; the Info page wants the whole distribution and the
 * percentage, so it reads this instead. The two exist side by side on purpose: a
 * card that showed thirty tags would drown the artwork it sits under.
 */
val MediaNode.weightedTags: List<MediaTag>
    get() = tags
        ?.filter { it.isMediaSpoiler != true && !it.name.isNullOrBlank() }
        ?.sortedByDescending { it.rank ?: 0 }
        ?: emptyList()

/**
 * AniList's tag `rank`, which is the share of viewers who applied the tag.
 *
 * "Urban Fantasy" at rank 96 is not a score - it means 96% of the people who
 * tagged this title tagged it that - which is why it is rendered as a percentage.
 */
fun MediaTag.weightLabel(): String? = rank?.let { "$name $it%" }

/** The cast, each entry with the actor who plays them. */
val MediaNode.characterList: List<MediaCharacterEdge> get() = characters?.edges ?: emptyList()

/** The crew, in AniList's relevance order. */
val MediaNode.staffList: List<MediaStaffEdge> get() = staff?.edges ?: emptyList()

/**
 * The related titles the Info page draws as a row, in the order the row reads.
 *
 * ## Which edges count
 *
 * This went through two wrong answers before this one, and the two failures point in
 * opposite directions - which is why both halves are pinned by the test.
 *
 * It used to keep only `ANIME` nodes unconditionally, and then - after that was fixed -
 * only nodes **of the subject's own medium**. Each answer dropped a real relation. The
 * anime-only rule emptied a manga page of its own franchise, verified against the live
 * API on One Piece (`manga/30013`), whose 51 relations are a `SPIN_OFF` per volume, a
 * `SIDE_STORY`, a `PREQUEL` (`Monsters`), an `ALTERNATIVE`, with every `ANIME` entry an
 * `ADAPTATION`. The own-medium rule fixed that and then hid the adaptation instead:
 * AniList files one in **both** directions (the manga is an `ADAPTATION` from the
 * anime's point of view and the anime from the manga's - checked live on `anime/1` and
 * `manga/30013`), so a reader on a Cowboy Bebop manga could no longer see the anime,
 * and the manga page of One Piece never showed the TV series at all.
 *
 * The row therefore carries the same-medium edges **plus** the cross-medium
 * `ADAPTATION`/`SOURCE` pair, and nothing else. A `CHARACTER` edge is a connection
 * between two titles rather than a title, and the novel, the guidebook and the
 * character song stay off the row because they are neither the same medium nor the
 * adaptation of this one - which is what "related" has to mean before the row can be
 * walked as a franchise.
 *
 * ## Why the order is imposed rather than taken from AniList
 *
 * AniList returns edges in insertion order, not sorted: checked live, One Piece's
 * relations interleave a spin-off per volume with the adaptations, and Cowboy Bebop's
 * anime lists both side stories *before* its two manga adaptations. The row caps at
 * [MAX_RELATIONS], so on a long franchise the raw order decided whether a reader saw
 * the sequels or an arbitrary twelve entries. The sort reads the way the row is walked:
 *
 *  1. the franchise spine - `PREQUEL`, `SEQUEL`, `PARENT` - with the adaptation next;
 *  2. within each group, oldest first, so sequels read as story order and the manga a
 *     show was made from (or the show a manga became) leads its group rather than
 *     trailing it - which is also what keeps the adaptation inside the cap on a
 *     franchise with a dozen spin-offs;
 *  3. music videos next, so a long show's songs are not pushed past the cap by its
 *     specials;
 *  4. everything else (`SIDE_STORY`, `SPIN_OFF`, `ALTERNATIVE`, `SUMMARY`, and any type
 *     AniList adds later) after it.
 *
 * A `CHARACTER` edge is kept only when one end is a music video. AniList files a
 * song as `CHARACTER` rather than `SIDE_STORY`, so the old blanket exclusion hid
 * every music video on every anime page - the exact relation the format field is
 * there to explain.
 *
 * Deduplicated by namespace and id, because the same title can be related twice
 * through two different arcs and an anime and a manga can share a number.
 *
 * A node whose own `type` is null is treated as anime - [isManga] is false for it, and
 * the activity feed and the widget fixtures are the two sources that can build a node
 * without one, so keeping their old behaviour is the safe reading of an unknown.
 */
val MediaNode.relatedMedia: List<MediaRelationEdge>
    get() {
        val ownType = if (isManga) "MANGA" else "ANIME"
        val subjectIsMusicVideo = isMusicVideo
        return relations?.edges
            ?.filter { edge ->
                val isCharacterEdge = edge.relationType.equals("CHARACTER", ignoreCase = true)
                // A music video is attached to the show it features with a
                // `CHARACTER` edge, which is otherwise the one relation type this
                // row must not draw: a `CHARACTER` edge between two ordinary titles
                // is a crossover, not a franchise entry. Keep the edge when either
                // end is a music video, and only then.
                val isMusicVideoConnection = edge.node.isMusicVideo || subjectIsMusicVideo
                edge.node.id != id &&
                    (!isCharacterEdge || isMusicVideoConnection) &&
                    (
                        edge.node.type.equals(ownType, ignoreCase = true) ||
                            edge.relationType.isAdaptationRelation
                        )
            }
            // Namespace and id, not the id alone: an anime and a manga adaptation can
            // share a number, and collapsing them dropped one of the two.
            ?.distinctBy { it.node.type?.uppercase(Locale.ROOT).orEmpty() to it.node.id }
            ?.sortedWith(RELATION_ROW_ORDER)
            ?.take(MAX_RELATIONS)
            ?: emptyList()
    }

/** Whether this relation is the same story in the other medium. */
private val String?.isAdaptationRelation: Boolean
    get() = equals("ADAPTATION", ignoreCase = true) || equals("SOURCE", ignoreCase = true)

/**
 * Where a relation sits in the row: the franchise spine first, then the adaptation,
 * then music videos, then the side material, then the types this app has never been
 * taught.
 *
 * Music videos get their own rank because AniList attaches them with `CHARACTER`
 * edges and a long-running show can have dozens of side stories. Leaving them in
 * the catch-all rank put them past the twelve-card cap on exactly the franchises
 * that have music videos to show - One Piece's three are behind thirty specials.
 */
private val MediaRelationEdge.relationRank: Int
    get() = when {
        node.isMusicVideo -> 2
        relationType?.uppercase(Locale.ROOT) in setOf("PREQUEL", "SEQUEL", "PARENT") -> 0
        relationType?.uppercase(Locale.ROOT) in setOf("ADAPTATION", "SOURCE") -> 1
        relationType?.uppercase(Locale.ROOT) in
            setOf("SIDE_STORY", "SPIN_OFF", "ALTERNATIVE", "SUMMARY") -> 3
        else -> 4
    }

/**
 * A start date packed into one ascending number, or [Int.MAX_VALUE] when unknown.
 *
 * Null years sort last rather than first: a relation AniList has no date for is the one
 * it knows least about, and opening a row with undated cards reads as a glitch. A known
 * year with an unknown month counts as the start of that year.
 */
private val MediaNode.relationStartKey: Int
    get() {
        val year = startDate?.year ?: return Int.MAX_VALUE
        return year * 10_000 + (startDate?.month ?: 0) * 100 + (startDate?.day ?: 0)
    }

/**
 * [MediaRelationEdge.relationRank] first, then the start date.
 *
 * `sortedWith` is stable, so entries sharing a rank and a date - or sharing an unknown
 * one - keep the order AniList returned them in, which is the best remaining evidence
 * of their importance.
 */
private val RELATION_ROW_ORDER = compareBy<MediaRelationEdge>(
    { it.relationRank },
    { it.node.relationStartKey }
)

/**
 * AniList's `MediaRelation` enum, spelled the way a person writes it.
 *
 * Same rule as [statusLabel]: a relation the app has never seen reads as what it
 * is, rather than being flattened into "Related".
 */
val MediaRelationEdge.typeLabel: String
    get() {
        val raw = relationType?.takeIf { it.isNotBlank() } ?: return "Related"
        // `CHARACTER` normally means a crossover, but AniList uses the same edge
        // for "this song features these characters". The format is the only thing
        // that tells the two apart, and "Character" is not what the card is.
        if (node.isMusicVideo && raw.equals("CHARACTER", ignoreCase = true)) {
            return "Music video"
        }
        return when (raw.uppercase(Locale.ROOT)) {
            "PREQUEL" -> "Prequel"
            "SEQUEL" -> "Sequel"
            "SIDE_STORY" -> "Side story"
            "SPIN_OFF" -> "Spin-off"
            "PARENT" -> "Parent"
            "ALTERNATIVE" -> "Alternative"
            "SUMMARY" -> "Summary"
            "FULL_STORY" -> "Full story"
            "ADAPTATION" -> "Adaptation"
            "SOURCE" -> "Source"
            "COMPILATION" -> "Compilation"
            "OTHER" -> "Other"
            else -> raw.lowercase(Locale.ROOT)
                .replace('_', ' ')
                .replaceFirstChar { it.uppercase(Locale.ROOT) }
        }
    }

/**
 * [typeLabel] read from [subject]'s side of the edge.
 *
 * A music video's only relation is a `CHARACTER` edge back to the show whose
 * characters it features. On the song's page "Character" would name the wrong
 * end of the relationship, so the label says where the show appears instead.
 */
fun MediaRelationEdge.typeLabelFor(subject: MediaNode): String =
    if (relationType.equals("CHARACTER", ignoreCase = true) &&
        subject.isMusicVideo &&
        !node.isMusicVideo
    ) {
        "Featured in"
    } else {
        typeLabel
    }

/**
 * The one-line summary under a related title's name.
 *
 * The title page's relation cards used to be a poster, a relation label and a name,
 * which is what a poster *looks* like rather than what a reader needs in order to
 * decide whether to tap it. Format, how long it is, when it came out and whether it
 * is out yet are the four facts the hero chips already spell out for the title you
 * are looking at, and the one thing the row exists to answer - "is this the season I
 * have not watched, or the film I have not heard of" - is not answerable from any of
 * the three it had.
 *
 * Built from [formatLabel] and [statusLabel], not the raw AniList enums, so the row
 * cannot print `TV_SHORT` or `NOT_YET_RELEASED` next to a cleaned-up hero chip - the
 * same class of drift those two derivations exist to stop.
 *
 * The year prefers `seasonYear` and falls back to the start date's year: AniList sets
 * the season on anything it has scheduled, and a title old enough to have aired is
 * often missing it while carrying a full start date. The length is only added when
 * it is a real count - a zero there is the API saying it does not know, and
 * "0 eps" is a claim rather than a gap. Episodes for an anime, chapters (or volumes
 * when chapters are unknown) for a manga: "eps" rather than "episodes" because the
 * whole line has to fit a 144dp card beside the format and the year.
 *
 * Status is shown only when it is *news* - airing, upcoming, on hiatus, cancelled.
 * A finished relation's status is the least interesting thing about it and its year
 * already says it, whereas a sequel that has not started is exactly the case where
 * the row should say so.
 */
val MediaRelationEdge.metaLabel: String
    get() {
        val node = node
        val parts = buildList {
            add(node.formatLabel)
            // The length is a different fact per namespace: an anime has episodes,
            // a manga has chapters and (sometimes) volumes. Only reading
            // `episodes` here meant every manga relation card silently lost its
            // length, while an anime card beside it said "TV · 12 eps".
            val length = when {
                node.isManga && (node.chapters ?: 0) > 0 -> {
                    val chapters = node.chapters!!
                    if (chapters == 1) "1 ch" else "$chapters ch"
                }
                node.isManga && (node.volumes ?: 0) > 0 -> {
                    val volumes = node.volumes!!
                    if (volumes == 1) "1 vol" else "$volumes vols"
                }
                (node.episodes ?: 0) > 0 -> {
                    val episodes = node.episodes!!
                    if (episodes == 1) "1 ep" else "$episodes eps"
                }
                else -> null
            }
            length?.let(::add)
            (node.seasonYear ?: node.startDate?.year)?.let { add(it.toString()) }
            val status = node.statusLabel
            if (status != "Finished" && status != "Unknown") add(status)
        }
        return parts.joinToString(" · ")
    }

/** `MAIN`, `SUPPORTING` or `BACKGROUND`, worded for a credit line. */
val MediaCharacterEdge.roleLabel: String
    get() = when (role?.uppercase(Locale.ROOT)) {
        "MAIN" -> "Main"
        "SUPPORTING" -> "Supporting"
        "BACKGROUND" -> "Background"
        null -> "Cast"
        else -> role.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase(Locale.ROOT) }
    }

/**
 * The viewer's progress as bare numbers - "1 / 11".
 *
 * [watchFractionLabel] already says the same thing in a sentence ("6 / 12 eps"),
 * which is right in a card body and too wordy for the corner of a progress card
 * where the fact is already labelled "Watching Progress".
 */
val MediaNode.progressCountLabel: String?
    get() {
        val watched = watchedEpisodeCount ?: return null
        val total = if (isManga) chapters else episodes
        return total?.takeIf { it > 0 }?.let { "$watched / $it" }
    }

val MediaNode.isUpcoming: Boolean get() = status == "NOT_YET_RELEASED"
val MediaNode.isAiringNow: Boolean get() = status == "RELEASING"
val MediaNode.isOnHiatus: Boolean get() = status.equals("HIATUS", ignoreCase = true)

val MediaNode.rankingList: List<MediaRanking> get() = rankings ?: emptyList()

/**
 * Whether this title has a ranking worth drawing.
 *
 * The Info page adds its rankings row as a `LazyColumn` item, and an item that
 * renders nothing still costs a slot in the list's `Arrangement.spacedBy` - a
 * zero-height item is followed by the full gap anyway, so a title with no ranking
 * ended up with a 14dp hole above the extras card. The page has to be able to ask
 * this *before* it adds the item, and it has to get the same answer the section
 * gives, so the predicate is derived here rather than in the composable.
 *
 * Equivalent to "any ranking carries a rank", which is also exactly what
 * [topRankingLabel] and the section's own guard reduce to: a ranking with a null
 * rank has neither a badge to draw nor a fallback to print.
 */
val MediaNode.hasRankings: Boolean get() = rankingList.any { it.rank != null }

/**
 * Whether this title has a progress card worth drawing.
 *
 * The two conditions are the ones [com.anisequel.ui.components.detail.info.InfoProgressCard]
 * returns early on, kept together so the page cannot disagree with the card: a title
 * the viewer has not started is not at 0%, it is not being watched at all, and a
 * 0% bar would be a claim about a thing that does not exist.
 */
val MediaNode.hasProgressCard: Boolean
    get() = watchFraction() != null && progressCountLabel != null

/**
 * The alternative-titles rows the Info page shows, as label to value.
 *
 * Deduplicated against [displayName], which is the fix for a row that was
 * duplicated on most titles rather than on some. The card used to open with a
 * "Main title" row and then list Romaji, English and Native underneath it - and
 * `displayName` *prefers* English, so on any title AniList has an English name for
 * (nearly all of them) the first two rows read:
 *
 *     Main title   Cowboy Bebop
 *     English      Cowboy Bebop
 *
 * Verbatim, one under the other, in a card whose whole job is to list the names
 * this title goes by. Where the preferred title fell back to Romaji it was the
 * Romaji row that repeated instead. Listing only the names that differ from the
 * one the hero already shows in full removes the duplicate without losing
 * anything: a title whose three names are all identical now correctly shows
 * nothing rather than the same string three times.
 *
 * The labels stay on the *named* forms rather than dropping a single
 * "Main title", because "Romaji / Cowboy Bebop" and "Native / カウボーイビバップ"
 * are the facts a reader is looking up this card for.
 */
val MediaNode.alternativeTitleRows: List<Pair<String, String>>
    get() {
        val preferred = displayName
        return buildList {
            romajiName?.let { add("Romaji" to it) }
            englishName?.let { add("English" to it) }
            nativeName?.let { add("Native" to it) }
            if (synonymList.isNotEmpty()) add("Synonyms" to synonymList.joinToString(", "))
        }.filterNot { (_, value) -> value == preferred }
    }

/** AniList's best ranking for this entry, formatted, or null. */
val MediaNode.topRankingLabel: String?
    get() {
        val best = rankingList.firstOrNull { it.rank != null } ?: return null
        val rank = best.rank ?: return null
        val context = best.context?.takeIf { it.isNotBlank() } ?: "Ranked"
        return "#$rank $context"
    }

/** The most relevant thematic tags, highest ranked first. */
val MediaNode.rankedTags: List<String>
    get() = tags
        ?.filter { it.isMediaSpoiler != true && !it.name.isNullOrBlank() }
        ?.sortedByDescending { it.rank ?: 0 }
        ?.mapNotNull { it.name }
        ?.take(MAX_TAGS) ?: emptyList()

/**
 * "Winter 2019", from AniList's `season` and `seasonYear`.
 *
 * AniList reports the two separately and `season` is an enum string, so the
 * pair is joined here rather than in the UI. Null unless AniList knows both -
 * an unannounced entry has neither.
 */
val MediaNode.seasonLabel: String?
    get() {
        val name = season?.takeIf { it.isNotBlank() } ?: return null
        val readable = name.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase(Locale.ROOT) }
        return seasonYear?.let { "$readable $it" } ?: readable
    }

/**
 * How many episodes of this entry the viewer has already watched.
 *
 * AniList returns it as `mediaListEntry { progress }` for anything on their
 * list; for an entry the app is offering to add, that is only ever
 * `progress`/`current`, which is what makes a card able to say "you're 4
 * episodes in" rather than only "not on your list".
 */
val MediaNode.watchedEpisodeCount: Int? get() = mediaListEntry?.progress
val MediaNode.airingEpisodeNumber: Int? get() = nextAiringEpisode?.episode

/** AniList's `airingAt`: seconds since the epoch, not milliseconds. */
val MediaNode.airingAtSeconds: Long? get() = nextAiringEpisode?.airingAt

/** How long until the next episode, or null when nothing is scheduled. */
fun MediaNode.airingCountdown(nowMillis: Long = System.currentTimeMillis()): String? =
    AiringCountdown.format(airingAtSeconds, nowMillis)

/**
 * How far through this entry the viewer is, as a 0..1 fraction.
 *
 * Null when there is nothing meaningful to show - no total episode count, or no
 * progress recorded - so callers can skip the bar entirely rather than drawing
 * an empty one.
 */
fun MediaNode.watchFraction(): Float? {
    if (isManga) {
        val total = chapters ?: return null
        if (total <= 0) return null
        val read = watchedEpisodeCount ?: return null
        return (read.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }
    val total = episodes ?: return null
    if (total <= 0) return null
    val watched = watchedEpisodeCount ?: return null
    return (watched.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

/** "6 / 12 eps watched" or "14 / 32 chs read" - null when the viewer has not started it. */
fun MediaNode.watchFractionLabel(): String? {
    if (isManga) {
        val total = chapters ?: return null
        val read = watchedEpisodeCount ?: return null
        if (read <= 0) return null
        return "$read / $total chs read"
    }
    val total = episodes ?: return null
    val watched = watchedEpisodeCount ?: return null
    if (watched <= 0) return null
    return "$watched / $total eps"
}

/**
 * The full record once the sheet's detail query has answered, keeping the
 * viewer's progress from the summary the tap came from.
 *
 * A sequel's list node is the richer of the two and merges through
 * [MediaNode.withDetail]; an arriving or activity row is the thinner one, so
 * the fetched detail is the display record there. Progress is the exception:
 * the detail query is not viewer-scoped, so a progress the summary already knew
 * must survive.
 */
fun MediaNode.withSummaryProgress(summary: MediaNode): MediaNode =
    if (mediaListEntry != null || summary.mediaListEntry == null) this
    else copy(mediaListEntry = summary.mediaListEntry)

/**
 * The viewer's list status, folded into a node that came off a list query.
 *
 * AniList puts the status on the *entry* and not on the media, so `entry.media`
 * knows the viewer's progress and nothing about whether it is Planned, Watching
 * or Complete. The title page needs the status: it is what decides between the
 * quick-add control and its "on your list" badge, and a wrong answer there is
 * not cosmetic - AniList's add mutation overwrites the entry, so offering
 * "Add to Planning" on a completed show would move it back to Planning and drop
 * the status the reader chose. Without this the Library drew the add button
 * until its detail query answered.
 *
 * A status the detail query already resolved always wins; this only fills a
 * blank.
 */
fun MediaNode.withListStatus(status: String?): MediaNode {
    if (status.isNullOrBlank() || mediaListEntry?.status != null) return this
    return copy(
        mediaListEntry = (mediaListEntry ?: SimpleMediaListEntry()).copy(
            mediaId = id,
            status = status
        )
    )
}

/**
 * The fetched detail for [summary], but only when it is the same media namespace.
 *
 * `MediaDetailStore` keys its fetch cache by type-and-id because `anime/1` and
 * `manga/1` are different titles. The map the screens read is keyed by id alone,
 * which was safe while a screen only ever held one title - and stopped being safe
 * when a relation card let one screen walk from an anime to a manga. A manga
 * relation with the same numeric id as a previously opened anime then painted the
 * anime's synopsis, cast and cover over the manga until the real fetch landed.
 *
 * A missing node is the right answer here: the sheet keeps drawing its summary
 * and the in-flight fetch publishes the correct detail for the new namespace.
 *
 * A summary with **no** type is the one exception, and it accepts the detail. It
 * cannot be namespace-checked, and discarding the answer is how the profile's
 * pinned titles used to open with no synopsis, cast or specs at all: their query
 * did not select `type`, so `"ANIME".equals(null)` was false and the fetched page
 * was thrown away while the Library - whose query does select it - opened the very
 * same title correctly. The query asks for `type` now, which is the real fix; this
 * is the half that stops a future source which forgets the field from degrading to
 * an empty page. The cost is bounded: a typeless summary also fires a typeless
 * `Media(id:)`, so the sheet was already asking an ambiguous question about this
 * id, and this shows that answer rather than discarding it.
 */
fun Map<Int, MediaNode>.detailFor(summary: MediaNode): MediaNode? =
    this[summary.id]?.takeIf { detail ->
        summary.type == null || detail.type.equals(summary.type, ignoreCase = true)
    }

/** HTML tags AniList interleaves into `description`. */
private val TAG_PATTERN = Regex("<[^>]*>")

/**
 * AniList's provenance credit: a line that is nothing but `(Source: …)`.
 *
 * [RegexOption.MULTILINE] is load-bearing. Without it Kotlin anchors `^` and `$` to
 * the whole description rather than to each line, so the pattern only matched a
 * description that was *entirely* a credit - which no real entry is - and every
 * synopsis kept printing its `(Source: Wikipedia)` as the last paragraph of the card.
 *
 * Case-insensitive because AniList writes `Source`, `source` and occasionally
 * `Sources` depending on the entry. Deliberately narrow otherwise: it will not match a
 * source mentioned inside a sentence, which is prose rather than provenance.
 */
private val SOURCE_ATTRIBUTION = Regex(
    """^[ \t]*\((?:Source|Sources):[^)]*\)[ \t]*$""",
    // `setOf`, not `a or b`: `RegexOption` is a plain enum and has no `or` operator.
    // The set overload is the one that takes more than one flag, and it is easy to
    // reach for the bitwise spelling out of habit -- CI rejected it.
    setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
)

/** Runs of whitespace left behind once the tags are gone. */
private val WHITESPACE_PATTERN = Regex("\\s+")

/** How many thematic tags the detail sheet renders. */
private const val MAX_TAGS = 8

/** How many related titles the Info page's relation row draws before it stops. */
private const val MAX_RELATIONS = 12
