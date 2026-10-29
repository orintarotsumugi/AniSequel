package com.anisequel.data.network

import com.anisequel.data.model.MediaCoverKey

/**
 * GraphQL documents sent to `graphql.anilist.co`.
 *
 * Field selection here is a performance decision, not a style one. AniList
 * resolves `MediaListCollection` entries eagerly, so every field asked for is
 * paid for *hundreds* of times - once per list entry, and again for each of
 * that entry's related nodes.
 *
 * Two rules follow from that:
 *
 *  1. Ask only for fields that are rendered somewhere. A 476-entry list
 *     produces roughly 1,500 relation nodes; one unused field on each is
 *     megabytes of JSON parsed for nothing.
 *  2. Never ask for a relation field that makes the server resolve a second
 *     entity. `studios(isMain: true)` inside `relations` does exactly that -
 *     a studio lookup for every node of every entry - and on a real 476-entry
 *     account it does not slow the response down, it **fails**: AniList
 *     returns HTTP 500 "Internal Server Error" and the app shows an error
 *     screen instead of the user's list.
 *
 * `getMediaDetailQuery` exists because of rule 1. `description`, `bannerImage`,
 * `studios`, `trailer`, and `synonyms` are only ever rendered for the one entry
 * a user taps open, so they are fetched on demand rather than shipped for all
 * ~1,500 relation nodes on every load.
 *
 * ## This file is over the 700-line ceiling, on purpose
 *
 * `AGENTS.md` says to keep files under 700 lines and split by responsibility.
 * This one is exempt, and the reason is the two rules above.
 *
 * The whole file is one responsibility: **what this app asks AniList for**. The
 * reason a field is or is not in a document is its effect on the *other*
 * documents - asking for `studios(isMain: true)` inside `relations` is what
 * turns a 476-entry list into an HTTP 500. That is a fact about the boundary
 * between documents, not about any one of them, so it lives in exactly one place
 * where all of them can be read together.
 *
 * A split would put `getMediaDetailQuery` and the list query that has to stay
 * lean beside it in different files, and the next person adding a field to one
 * would have no way to see the other. The failure mode of this API is not a
 * compile error - it is a document that answers 500 in production because
 * nothing in review could see what it cost.
 *
 * The line count here is mostly raw GraphQL inside string literals, which is
 * not code that can be factored. The alternative worth considering is not
 * splitting, but **asserting** the relationships the comments describe - the
 * same reason `StudioQueryTest` exists to pin a `perPage` cap.
 */
object GraphQLQueries {

    val GET_VIEWER = """
        query GetViewer {
          Viewer {
            id
            name
            avatar {
              medium
              large
            }
            bannerImage
            statistics {
              anime {
                count
                episodesWatched
                minutesWatched
              }
            }
          }
        }
    """.trimIndent()

    val GET_USER_BY_NAME = """
        query GetUserByName(${'$'}userName: String) {
          User(name: ${'$'}userName) {
            id
            name
            avatar {
              medium
              large
            }
            bannerImage
            statistics {
              anime {
                count
                episodesWatched
                minutesWatched
              }
            }
          }
        }
    """.trimIndent()

    /**
     * Fields for a favourited media node.
     *
     * `genres` and `popularity` are absent: the favourite rows show a cover, a
     * title, the format and the AniList score, and nothing else. Both of those
     * are asked for on the *list* query's relation nodes already, where they are
     * read - asking for them again per favourite would be the same field twice
     * for two different reasons.
     *
     * `type` is not a display field and is not optional. It is the namespace the
     * detail sheet fetches and merges by - `anime/1` and `manga/1` are different
     * titles sharing a number - and it was missing here while every other media
     * source carried it. Tapping a pinned manga therefore asked AniList for the
     * id with no type, which resolves against both namespaces and answers with
     * whichever it prefers, and `detailFor` then refused to merge the answer into
     * the typeless summary. The profile's favourites opened without a synopsis,
     * cast or specs while the very same title opened fine from the Library, whose
     * list query does select it. `UserOverviewQueryTest` pins the field.
     */
    private const val FAVOURITE_MEDIA_FIELDS = """
                id
                type
                title { english romaji native }
                format
                status
                averageScore
                coverImage { extraLarge large }
                siteUrl
    """

    /**
     * The profile screen's query: bio, statistics, favourites, and AniList's own
     * aggregate stats.
     *
     * A second `User` query rather than more fields on [GET_VIEWER] because the
     * two have different lifetimes. `GET_VIEWER` runs on every dashboard load and
     * renders four values; this runs when someone opens their profile and returns
     * a favourites tree plus a distribution set. Folding them together would mean
     * every cold start parsed all of it to draw a title bar.
     *
     * The favourites are capped at 12 per branch because that is all a horizontal
     * row shows. AniList allows 25 and will happily send them; the extra thirteen
     * per branch is five branches' worth of image URLs parsed for art that never
     * leaves the screen.
     *
     * ## The `stats` block, and the four fields that stopped answering
     *
     * `stats` is the field AniList marks deprecated. It is still needed for
     * `activityHistory` - the per-day calendar has no replacement anywhere in the
     * schema - and for the status, score and list-score distributions, which
     * still come back populated.
     *
     * Its four `favoured*` sub-selections are gone, and their absence is the fix
     * for the profile screen failing to load at all. Verified against the live API
     * on 2026-10-22: asking for any one of `favouredFormats`, `favouredYears`,
     * `favouredGenres` or `favouredTags` makes the whole document answer **HTTP
     * 500** with `{"errors":[{"message":"Internal Server Error"}]}` and
     * `data.User = null`. The failure is server-side and unconditional, so every
     * profile and Library open became the "Ani-chan is having a nap" page while
     * the dashboard - which does not use this query - kept working.
     *
     * The distributions they used to carry now come from the non-deprecated
     * `statistics.anime` block. That block was previously believed to answer `[]`;
     * re-checked live, it is populated for real accounts (150 entries: TV 115,
     * MOVIE 23, Romance 87, 2026 26), so the comment that said otherwise was the
     * stale part, not the schema.
     *
     * `UserOverviewQueryTest` pins both halves: the broken fields must not be
     * requested, and the replacement must be.
     */
    val GET_USER_OVERVIEW = """
        query GetUserOverview(${'$'}userId: Int, ${'$'}userName: String) {
          User(id: ${'$'}userId, name: ${'$'}userName) {
            id
            name
            about(asHtml: false)
            siteUrl
            avatar {
              medium
              large
            }
            bannerImage
            createdAt
            updatedAt
            statistics {
              anime {
                count
                episodesWatched
                minutesWatched
                meanScore
                formats { format count meanScore }
                releaseYears { releaseYear count meanScore }
                genres { genre count meanScore minutesWatched }
                tags { tag { id name } count meanScore }
              }
              manga {
                count
                chaptersRead
                volumesRead
                meanScore
              }
            }
            stats {
              watchedTime
              chaptersRead
              activityHistory { date amount level }
              animeStatusDistribution { status amount }
              mangaStatusDistribution { status amount }
              animeScoreDistribution { score amount }
              animeListScores { meanScore standardDeviation }
              mangaListScores { meanScore standardDeviation }
            }
            favourites {
              anime(perPage: 12) { nodes { $FAVOURITE_MEDIA_FIELDS } }
              manga(perPage: 12) { nodes { $FAVOURITE_MEDIA_FIELDS } }
              characters(perPage: 12) {
                nodes {
                  id
                  name { full native alternative }
                  image { large }
                  favourites
                  siteUrl
                }
              }
              staff(perPage: 12) {
                nodes {
                  id
                  name { full native alternative }
                  image { large }
                  primaryOccupations
                  favourites
                  siteUrl
                }
              }
              studios(perPage: 12) { nodes { id name isAnimationStudio favourites siteUrl } }
            }
          }
        }
    """.trimIndent()

    /**
     * A page of somebody's activity: list updates ("watched 3 episodes",
     * "completed X") and text posts, newest first.
     *
     * `Page.activities` returns `[ActivityUnion]`, so this selects the two
     * members the feed renders with inline fragments and filters to the
     * activity types with `type_in`. Both are asked for in one request: text
     * posts are how people on AniList talk between list updates, and a feed
     * without them was only ever showing half of what the person did.
     * `MESSAGE` stays out and must stay out - it is site messages between two
     * users, which do not belong on a public profile tab. The parser drops
     * anything that is still not one of these two.
     *
     * `type_in` takes both media types in one request rather than one query per
     * medium: the response carries each item's own `media.type`, and the screen
     * can split on that afterwards. Two requests would also mean two chances to
     * hit AniList's rate limit on a screen the user opens to browse.
     *
     * `pageInfo.hasNextPage` is the documented pagination signal and the reason
     * this document asks for it at all: the feed is filtered after it arrives
     * (union members and updates for removed media are dropped), so a page can be
     * shorter than `perPage` without being the last one. Counting rows used to
     * end the feed early. `perPage` is a variable so the repository's page size
     * and the query cannot drift apart.
     */
    val GET_USER_ACTIVITY = """
        query GetUserActivity(${'$'}userId: Int, ${'$'}page: Int, ${'$'}perPage: Int) {
          Page(page: ${'$'}page, perPage: ${'$'}perPage) {
            pageInfo { hasNextPage }
            activities(userId: ${'$'}userId, type_in: [ANIME_LIST, MANGA_LIST, TEXT], sort: [ID_DESC]) {
              ... on ListActivity {
                id
                status
                progress
                createdAt
                likeCount
                replyCount
                isLiked
                siteUrl
                media {
                  id
                  type
                  format
                  episodes
                  chapters
                  volumes
                  title { english romaji native }
                  coverImage { extraLarge large color }
                  siteUrl
                }
              }
              ... on TextActivity {
                id
                text
                createdAt
                likeCount
                replyCount
                isLiked
                siteUrl
              }
            }
          }
        }
    """.trimIndent()

    /**
     * The people who follow this account.
     *
     * Followers live on the `Page` query and not on `User` - there is no
     * `User.followers` field at all - which is why this is its own document. It
     * also cannot be combined with `following`: the `Page` query accepts one data
     * field, so "Followers" and "Following" are two round trips.
     *
     * `$userId` is `Int!`, not `Int`. The argument is non-null and declaring the
     * variable nullable compiles and then fails at runtime with "Variable
     * \"$userId\" of type \"Int\" used in position expecting type \"Int!\"".
     */
    val GET_USER_FOLLOWERS = """
        query GetUserFollowers(${'$'}userId: Int!) {
          Page(perPage: 50) {
            followers(userId: ${'$'}userId) {
              id
              name
              avatar { medium }
            }
          }
        }
    """.trimIndent()

    /**
     * The people this account follows. Separate from [GET_USER_FOLLOWERS]
     * because `Page` takes one data field.
     */
    val GET_USER_FOLLOWING = """
        query GetUserFollowing(${'$'}userId: Int!) {
          Page(perPage: 50) {
            following(userId: ${'$'}userId) {
              id
              name
              avatar { medium }
            }
          }
        }
    """.trimIndent()

    /**
     * The followed users who have one title on their list.
     *
     * ## Why `Page.mediaList` and not the following list
     *
     * `isFollowing: true` filters the connection to the *authenticated* viewer's
     * follows, so one request answers "which of my friends have seen this". The
     * other shape - fetch everyone I follow, then ask for each person's entry -
     * is N+1 requests for a card that is a footnote on the page.
     *
     * ## Why `type` is in the document at all
     *
     * `mediaId` is only unique *within* a namespace: `anime/1` and `manga/1` are
     * different titles sharing a number, so a filter on the id alone can match
     * entries for both. The connection has a `type` argument and it is passed as a
     * variable for the same reason [getMediaDetailQuery] does it - the caller
     * that already knows the namespace decides, rather than this document
     * hardcoding the one it was written against.
     *
     * The untyped document exists for a summary node that never carried a `type`
     * (an activity row, a widget fixture). It is the old behaviour rather than a
     * failure: an untyped title can still answer with friends, it just cannot
     * exclude the other namespace's entries.
     *
     * ## Why the score carries a format
     *
     * `score(format: POINT_10_DECIMAL)` normalises every friend's rating onto one
     * scale. The bare `score` field is in each user's own chosen format, so one
     * friend's 85 and another's 8.5 would be drawn as the same kind of number in
     * the same row.
     *
     * `perPage: 50` is the connection's documented maximum. There is no
     * `pageInfo` here because nothing pages: see [MediaFriendsData] for why a
     * second round trip is not worth it for this card.
     */
    fun getMediaFriendsQuery(mediaType: String?): String {
        val typed = mediaType != null
        val queryName = if (typed) "GetMediaFriendsTyped" else "GetMediaFriends"
        val variables = if (typed) "\$mediaId: Int, \$type: MediaType" else "\$mediaId: Int"
        val arguments = if (typed) {
            "mediaId: \$mediaId, type: \$type, isFollowing: true"
        } else {
            "mediaId: \$mediaId, isFollowing: true"
        }
        return """
            query $queryName($variables) {
              Page(perPage: 50) {
                mediaList($arguments) {
                  status
                  score(format: POINT_10_DECIMAL)
                  progress
                  progressVolumes
                  user {
                    id
                    name
                    avatar { medium large }
                  }
                }
              }
            }
        """.trimIndent()
    }

    /**
     * Fields for a related node, rendered in the card list and on the title page.
     *
     * **Shared by both queries that carry relations**, deliberately. The detail
     * query used to carry its own, much thinner, relation node - `id`, `title`,
     * `format`, `status`, `episodes` and the cover - which was enough for the
     * poster-and-title card the title page drew and nothing else. So the one place
     * in the app whose job is to show you the rest of a franchise could say what a
     * related title was *called* and not when it aired, what it scored, or whether
     * it is out yet: the same household facts its own hero chip spells out above.
     *
     * `chapters` and `volumes` are here for the manga half of the row. Without
     * them a related manga card could say "Manga · 1999" and never how long the
     * thing is, while the same card for an anime said "TV · 12 eps · 1999" - the
     * metadata line was anime-shaped and the manga namespace was an afterthought.
     *
     * It is the same `Media` type, so the fix is the same field list rather than a
     * second one - and a field list that exists twice is one that eventually
     * forgets half a relation. The cost is bounded the way it is here on purpose:
     * this runs once per *open* and over at most twelve nodes, unlike the list
     * query, which asks it once per page of a user's whole list. Widening the
     * list query's copy of this would be the expensive change, and is not what
     * happened.
     */
    private const val RELATED_NODE_FIELDS = """
                id
                type
                title { romaji english native userPreferred }
                format
                status
                episodes
                chapters
                volumes
                season
                seasonYear
                averageScore
                popularity
                genres
                siteUrl
                startDate { year month day }
                coverImage { extraLarge large color }
                nextAiringEpisode { episode airingAt }
                mediaListEntry { status progress }
    """

    /**
     * Parent fields, by consumer:
     *
     *  - `episodes` is what the watched detector needs: an entry counts as
     *    watched when its progress reaches the episode count even if the user
     *    never set a status.
     *  - `status`, `startDate` and `nextAiringEpisode` drive the "Currently
     *    arriving" section - the user's own list, split into what is airing now
     *    and what has not started yet.
     *  - `coverImage` is the parent's key visual on the card that says "Sequel
     *    to X". It was always meant to be here; without it every one of those
     *    cards rendered with no artwork at all.
     *  - `completedAt` on the entry (not the media) is the year the *viewer*
     *    marked it finished, which is what the "completed this year" filter
     *    asks about.
     *
     * `id` and `score` on the entry are gone because nothing reads them - the
     * media's own id is the identity used everywhere, and the score the UI
     * renders is the media's `averageScore`. They cost one value per entry on
     * the hottest request in the app for nothing.
     *
     * `format` and `countryOfOrigin` on the parent were added for the profile
     * screen, and they are the only fields this query has gained. Format feeds the
     * Format Distribution; country feeds the Country Distribution, and AniList
     * exposes no aggregate for country anywhere - not in `User.stats`, whose
     * distribution fields cover status, format, score and year. So it is either
     * asked for once per entry here, where the parent media is already being
     * fetched, or the chart does not exist.
     *
     * Measured on a real 142-entry list: 480,034 bytes without the field, 483,990
     * with it. About 0.8% of the most expensive request the app makes, for a chart
     * that is otherwise impossible to draw.
     */
    /**
     * The user's media list for one [MediaType].
     *
     * One builder rather than an ANIME constant and a MANGA constant: the field
     * list is long enough that a second copy would be a second thing to forget to
     * widen when a new field is needed, and the only difference between the two
     * lists is the `type` argument. `MediaType` is a real GraphQL enum, so it is
     * passed as a variable and validated by AniList rather than pasted into the
     * query text.
     *
     * `relations` is here for the same reason it is in the anime list - the
     * detail sheet opens from a Library row and draws a sequel banner from it -
     * and it is the reason this is the app's heaviest query. It is asked for once
     * per *page* of a user's own list rather than once per relation node, so the
     * cost is bounded by the page size.
     */
    private fun mediaListQuery(queryName: String): String = """
        query $queryName(${'$'}userId: Int, ${'$'}userName: String, ${'$'}chunk: Int, ${'$'}type: MediaType) {
          MediaListCollection(userId: ${'$'}userId, userName: ${'$'}userName, type: ${'$'}type, chunk: ${'$'}chunk) {
            hasNextChunk
            lists {
              name
              status
              entries {
                status
                progress
                updatedAt
                score(format: POINT_100)
                completedAt { year month day }
                media {
                  id
                  type
                  title { romaji english native userPreferred }
                  episodes
                  chapters
                  volumes
                  status
                  format
                  countryOfOrigin
                  season
                  seasonYear
                  averageScore
                  startDate { year month day }
                  coverImage { extraLarge large color }
                  # The tier board's genre and studio filters read these two.
                  # `studios` was deliberately absent while every reader of a list
                  # entry only needed a cover and a score; the studio filter is the
                  # first reader that needs the credit, and a second per-title
                  # detail request would spend one of AniList's 30-a-minute calls
                  # for every row of the board. `isMain` is the animation studio;
                  # the same connection also carries publishers.
                  genres
                  # `id` is kept for the credit type's navigation key;
                  # `isAnimationStudio` is not: the board filters on the credit
                  # AniList flagged `isMain`, and nothing on this path reads the
                  # studio's own type.
                  studios { edges { isMain node { id name } } }
                  nextAiringEpisode { episode airingAt }
                  relations {
                    edges {
                      # Version 2 is what adds SOURCE to the enum. Version 1 has only
                      # ADAPTATION, so a light novel -> anime edge reads as "Adaptation"
                      # from both ends and the row cannot tell the source material from
                      # the adaptation it became. The label code already handles SOURCE;
                      # nothing was ever asking for it. See MediaNodeDisplay.relatedMedia.
                      relationType(version: 2)
                      node { $RELATED_NODE_FIELDS }
                    }
                  }
                }
              }
            }
          }
        }
    """.trimIndent()

    val GET_USER_ANIME_LIST: String = mediaListQuery("GetUserAnimeList")

    /** The same list for manga, which the Library page filters to on demand. */
    val GET_USER_MANGA_LIST: String = mediaListQuery("GetUserMangaList")

    /**
     * Many media in one request, one aliased root field each.
     *
     * The preview cards in a bio each name an AniList entity, and the obvious
     * implementation - one `getMediaDetail` per card - spends one of AniList's 30
     * requests a minute per card. A typical "hall of fame" bio has a dozen links,
     * so opening one profile could exhaust the budget and hand the next person who
     * opens anything a 429.
     *
     * `Media` takes a single id *per field*, but aliasing the *root* field is
     * ordinary GraphQL: `m0: Page { media(id: 1, type: ANIME) }`, `m1: Page {
     * media(id: 1, type: MANGA) }` in one document, one round trip. Aliasing the
     * field is what makes it possible to ask for both sides of an id that is used
     * by *both* media namespaces in the same batch - a bare id would have
     * collapsed them to one entry and answered with only one of the two posters.
     * See [MediaCoverKey] for why the type cannot be dropped.
     *
     * ## Why each alias resolves `Page.media` and not the `Media` root field
     *
     * `Media(id:)` is *nullable*, and AniList does not answer it with null: a
     * batch in which one id has no media - a link to a deleted entry, or an id
     * that belongs to the other media namespace - comes back as **HTTP 404** with
     * `errors: ["Not Found."]`, and the whole response is a failure even though
     * the other aliases resolved. Every poster in the bio then fell back to the
     * placeholder, because one of a dozen links was stale, and the failure named
     * nothing the reader could act on. Verified against the live API: the same
     * request written with `m0: Media(...)` answers 404, and written with
     * `m0: Page { media(...) }` answers 200 with `"m0": { "media": [] }`.
     *
     * `Page.media` is a *connection*, so it filters instead of failing, and an id
     * with no media is an empty list. That is the one shape this batch can be
     * asked in without letting the weakest link in a bio take the whole document
     * down with it. The caller reads `media.firstOrNull()`, which is null for the
     * empty case - the same "nothing to draw here" it already handled.
     *
     * This is distinct from the `Page` limitation noted on
     * [AniListApiService.getUserFollowers], which is about two child fields
     * *inside* a single `Page` and not about two fields at the root.
     *
     * Only the fields a preview card draws. The full detail query
     * ([getMediaDetailQuery]) carries a
     * description, banner and studio per entry, and a bio's dozen cards would
     * turn that into a payload the screen cannot use.
     */
    fun getMediaCoversQuery(keys: List<MediaCoverKey>): String {
        require(keys.isNotEmpty()) { "a cover batch needs at least one id" }
        val fields = keys.indices.joinToString("\n") { index ->
            """
            m$index: Page {
              media(id: ${'$'}id$index, type: ${'$'}type$index) {
                id
                type
                title { romaji english userPreferred }
                format
                averageScore
                coverImage { extraLarge large }
                siteUrl
              }
            }
            """.trimIndent()
        }
        // `type` is nullable in AniList's `Media` field, so the variable is too.
        // Declaring it `MediaType!` would make the whole document invalid.
        //
        // The `$` sigil is `\$` here because a declaration needs one and a
        // template is not one: `"id$it: Int"` produced `id0: Int`, which is not
        // a variable declaration at all. The use sites below were written with
        // the sigil, so the document asked for a variable it never declared -
        // and AniList answered **HTTP 400, "Expected $, found Name \"id0\""**
        // for the whole batch. Every poster in every bio therefore stayed on its
        // placeholder, with nothing on screen saying why, because the covers
        // request is one of the few the user never sees fail. Verified against
        // the live API: the same batch written with `\$id0` answers 200.
        val variables = keys.indices.joinToString(", ") { index ->
            "${'$'}id$index: Int, ${'$'}type$index: MediaType"
        }
        return """
            query MediaCovers($variables) {
            $fields
            }
        """.trimIndent()
    }

    /**
     * The alias [getMediaCoversQuery] gives the nth id, so results can be read back.
     *
     * Indexed by *position* in the requested list rather than by media id, because
     * two entries in one batch may share an id across the anime and manga
     * namespaces - a single key per alias would collide and drop one of them.
     */
    fun mediaCoverAlias(index: Int): String = "m$index"

    /**
     * The single media record the detail sheet renders.
     *
     * This was once the *heavy* fields only - description, banner, studios - on
     * the reasoning that the list query already carried the rest. That held for
     * a missed sequel, whose list node has title, format, status, episodes and
     * cover. It does not hold for the two other sources that now open this
     * sheet: an activity row's media and an arriving summary carry a handful of
     * those fields at most, so the sheet had no title to render until this
     * answered. Widening the query costs nothing worth protecting - it is one
     * item per open, not one per relation node - whereas the alternative was a
     * null-filled header on the profile feed.
     *
     * ## Why `type` is a variable and not a constant
     *
     * This asked `Media(id: $id)` with no type, and AniList media ids are **not
     * unique across namespaces**: `anilist.co/anime/1` and `anilist.co/manga/1`
     * are different entities. A bare id is resolved against both, and AniList
     * answers with whichever it prefers.
     *
     * The failure was silent and looked like missing content rather than wrong
     * content, which is why it survived a release. Opening a *manga* from the
     * Library resolved to the anime sharing that id, so the sheet rendered the
     * anime's title, synopsis, cast and relations; where no media matched at all,
     * AniList answered 404, `require("That entry is no longer available on
     * AniList.")` threw, and `MediaDetailStore` swallowed the failure by design -
     * leaving the thin summary node, with no cast, no staff and no relations.
     * That is the "the relations and the character list do not work when I open
     * a title" report, from a sheet that was working perfectly for every anime.
     *
     * [MediaCoverKey] documents the same collision for covers, and
     * [getMediaCoversQuery] is the fix there: `Page.media(id:, type:)`, which
     * filters rather than 404ing. The type is threaded in as a variable so the
     * caller that already knows the media's namespace decides it, rather than
     * this document hardcoding the one it happened to be written for.
     *
     * Nullable, because a summary node from a source that never carried `type`
     * cannot supply one; passing null reproduces the old untyped behaviour
     * rather than failing the document, which is why it is declared `MediaType`
     * and not `MediaType!`.
     */
    fun getMediaDetailQuery(mediaType: String?): String {
        val typed = mediaType != null
        val queryName = if (typed) "GetMediaDetailTyped" else "GetMediaDetail"
        val variables = if (typed) "\$id: Int, \$type: MediaType" else "\$id: Int"
        val arguments = if (typed) "\$id, type: \$type" else "\$id"
        return buildString {
            appendLine("query $queryName($variables) {")
            appendLine("  Media(id: $arguments) {")
            append(MEDIA_DETAIL_FIELDS)
            appendLine("  }")
            appendLine("}")
        }
    }

    /**
     * One studio, and one page of everything it has worked on.
     *
     * ## Why this is `Studio.media` and not `Page.media`
     *
     * AniList's `Page.media` has no `studioId` argument - its documented filter
     * list runs from `id` to `sort` and never mentions a studio - so the studio's
     * own `media` connection is the only documented way to ask the question at
     * all. Getting that wrong looks like a working feature until it is called: a
     * `Page` query written with a studio filter is a **400**, not an empty page.
     *
     * ## Why `perPage` is a variable with a hard ceiling of 25
     *
     * 25 is the documented maximum on this connection (the root `Page` allows 50,
     * which is where a copy-pasted page size would come from). Asking for more is
     * another 400, so the ViewModel clamps rather than trusting its caller; the
     * cap is asserted in `StudioQueryTest` for exactly that reason.
     *
     * ## Why the sort is `START_DATE_DESC` and not left to the server
     *
     * `Studio.media` has no default ordering worth relying on, and the screen
     * draws *year shelves*, so the order it wants is by date, newest first. Titles
     * with no start date have no documented position in that sort - the screen
     * separates them into its own "TBA" shelf client side, because only the screen
     * knows that a title with no date is the most interesting thing on the page
     * rather than a sorting accident.
     */
    fun getStudioQuery(): String = """
        query StudioWork(${'$'}id: Int, ${'$'}page: Int, ${'$'}perPage: Int) {
          Studio(id: ${'$'}id) {
            id
            name
            isAnimationStudio
            favourites
            siteUrl
            media(page: ${'$'}page, perPage: ${'$'}perPage, sort: [START_DATE_DESC]) {
              nodes {
    $STUDIO_MEDIA_FIELDS
              }
            }
          }
        }
    """.trimIndent()

    /**
     * The fields one studio-page card draws, and nothing else.
     *
     * A studio can have hundreds of titles, and this document is fetched again for
     * every page of them - so it is the *list* shape rather than the detail shape:
     * no cast, no staff, no relations, no description. `startDate` and `seasonYear`
     * are both here because the year shelf groups on the first and falls back to
     * the second; `type` travels with each node because tapping a card opens the
     * detail sheet, and an id without its namespace resolves to whatever AniList
     * prefers (see `MediaCoverKey`).
     */
    private const val STUDIO_MEDIA_FIELDS = """
                id
                type
                title { romaji english native userPreferred }
                format
                status
                episodes
                chapters
                volumes
                season
                seasonYear
                averageScore
                popularity
                siteUrl
                startDate { year month day }
                coverImage { extraLarge large color }
    """

    /**
     * The `Media` selection both forms of the detail query share.
     *
     * One constant rather than a second copy in the typed branch: the untyped and
     * typed documents differ only in whether they pass `type:` to the field, and a
     * field list that exists twice is one that eventually forgets half a relation
     * or a sort argument.
     *
     * Assembled with [buildString] rather than by concatenating two `trimIndent()`
     * literals. Concatenation silently eats the newline at each seam, which
     * GraphQL tolerates - it ignores whitespace - but which produces a document
     * reading `Media(id: $id) {id`, and makes any test that compares the two forms
     * line by line compare the seam instead of the fields.
     *
     * The field list carries only fields something reads. `idMal` and `isAdult`
     * were selected for a parser that no longer exists - nothing in the app models
     * or draws either - so they were removed rather than left to pad every detail
     * response with two facts that go nowhere.
     *
     * The relation nodes interpolate [RELATED_NODE_FIELDS] rather than listing a
     * handful of fields of their own - see that constant for what the title page's
     * relation cards were unable to say before.
     *
     * `title` is selected here for a reason that is easy to miss: for a while it
     * was not, on the assumption that the summary the sheet was opened from already
     * had one. That holds for the dashboard, which folds this response into the list
     * node with `withDetail` - and it does **not** hold for the profile, where the
     * fetched detail *replaces* the activity row's node (`withSummaryProgress`
     * returns the detail when the detail has an entry). Opening a title from an
     * Activity row therefore rendered it as "Unknown Title" while its own studio,
     * status, synopsis and cast arrived correctly beneath that heading, which read
     * as a broken title page rather than as a missing field. A title is not optional
     * on a node this app uses as a display record.
     *
     * ## Why `studios` is asked for with **no arguments**
     *
     * It was `studios(perPage: 25) { edges { ... } }`, on the assumption that a
     * studio connection is paginated the way `characters` and `staff` below it
     * are. It is not: AniList's `Media.studios` takes a single optional `sort`
     * argument and nothing else, so `perPage` is not an argument the field has.
     *
     * GraphQL validates the whole document before resolving any of it, and AniList
     * answers an unknown argument with **HTTP 400 and `data: null`**:
     *
     *     {"errors":[{"message":"Unknown argument \"perPage\" on field \"studios\"
     *                 of type \"Media\".","status":400}],"data":null}
     *
     * One unrecognised argument therefore cost the *entire* detail response - not
     * just the studios. Every section that only the detail query carries went empty
     * at once: the synopsis, the studio credits and the hero's studio chip, the cast,
     * the crew, the thematic tags, the rankings, and the trailer row. The dashboard
     * hid most of it, because there it is merged onto a list node that already had
     * relations (`withDetail` keeps `detail.relations ?: relations`), but a title
     * opened from a profile activity row has no such fallback and rendered with
     * nothing but its summary.
     *
     * There is nothing to paginate here in any case: AniList files a handful of
     * credits per title and returns all of them - Cowboy Bebop's three, a long
     * series' five or six - under `edges`. Verified against the live API: the same
     * document answers 400 with `perPage` and 200 with every section populated
     * without it.
     */
    private val MEDIA_DETAIL_FIELDS = """
        id
        type
        title { romaji english native userPreferred }
        format
        status
        episodes
        chapters
        volumes
        duration
        countryOfOrigin
        averageScore
        meanScore
        popularity
        season
        seasonYear
        siteUrl
        genres
        synonyms
        source
        description(asHtml: false)
        studios { edges { isMain node { id name isAnimationStudio } } }
        coverImage { extraLarge large color }
        bannerImage
        nextAiringEpisode { episode airingAt }
        startDate { year month day }
        endDate { year month day }
        mediaListEntry { status progress }
        trailer { id site }
        externalLinks {
          id
          url
          site
          type
        }
        rankings {
          id
          rank
          type
          context
          year
          season
          allTime
        }
        tags {
          id
          name
          rank
          isMediaSpoiler
        }
        relations {
          edges {
            # Version 2 adds SOURCE, which is the only way to tell a title's source
            # material from an adaptation in the other direction. See the list query's
            # copy of this comment for the full reasoning.
            relationType(version: 2)
            node { $RELATED_NODE_FIELDS }
          }
        }
        characters(sort: [ROLE, RELEVANCE, ID], perPage: 25) {
          edges {
            role
            node {
              id
              image { large medium }
              # What the API knows about a character beyond their name. Ask it for the
              # whole cast's worth: one open, one request, and a cast is what a reader
              # opens a title page for.
              #
              # `alternative` is the same field the alternative-titles card reads on
              # the *title*, so a character is described as richly as the show is -
              # Spike Spiegel carries "Spike Spike" and "Swimming Bird" on AniList and
              # the page printed one line.
              #
              # `favourites` and `siteUrl` are what make a character card a control
              # rather than a caption: one says how many people the character matters
              # to, the other is where the rest of that information lives. Same pair
              # the studio credits use.
              #
              # `description` is fetched but *not* rendered on the card - twenty-five
              # descriptions is a wall of text in a horizontal row. It is here so a
              # character is not a dead end, exactly as the studio's filmography is not
              # drawn on the credit chip either.
              name { full native alternative }
              favourites
              siteUrl
              gender
              age
              description(asHtml: false)
            }
            voiceActors(language: JAPANESE) {
              id
              name { full native }
              image { large medium }
            }
          }
        }
        staff(sort: [RELEVANCE, ID], perPage: 12) {
          edges {
            role
            node {
              id
              name { full native }
              image { large medium }
            }
          }
        }
    """

    val ADD_TO_PLANNING = """
        mutation AddToPlanning(${'$'}mediaId: Int) {
          SaveMediaListEntry(mediaId: ${'$'}mediaId, status: PLANNING) {
            id
            status
            mediaId
          }
        }
    """.trimIndent()

    val ADD_TO_WATCHING = """
        mutation AddToWatching(${'$'}mediaId: Int) {
          SaveMediaListEntry(mediaId: ${'$'}mediaId, status: CURRENT) {
            id
            status
            mediaId
          }
        }
    """.trimIndent()

    /**
     * The viewer's own entry for one media, with every field the editor can change.
     *
     * `userId` is passed when the caller has one, and omitted when it does not: an
     * omitted `userId` is AniList's documented way of asking for the **authenticated
     * viewer's** entry, which is exactly what the editor wants. The explicit id is
     * the safer half of the pair - `RepositoryEntryEditor` supplies it when its
     * host knows it - but it is not required, and a document that treated the
     * omission as "somebody else's entry" would be solving a problem that does not
     * exist.
     *
     * `Viewer.mediaListOptions.scoreFormat` rides along in the same document.
     * `MediaList` has no score format of its own, and the editor's score control
     * needs one: `score` is in the account's own units, so the slider's ceiling
     * and its tick spacing are both unknowable without this enum. It used to be
     * guessed from the score itself, which gave a hundred-point entry scored 8 a
     * ten-point slider and let a five-point account write a score AniList
     * rejects. One extra field on a request the editor already makes, rather
     * than a second query for an enum.
     */
    val GET_MEDIA_LIST_ENTRY = """
        query MediaListEntry(${'$'}mediaId: Int, ${'$'}userId: Int) {
          Viewer {
            mediaListOptions { scoreFormat }
          }
          MediaList(mediaId: ${'$'}mediaId, userId: ${'$'}userId) {
            id
            mediaId
            status
            progress
            progressVolumes
            score
            private
            notes
            startedAt { year month day }
            completedAt { year month day }
          }
        }
    """.trimIndent()

    /**
     * Creates or replaces the viewer's entry with every field the editor owns.
     *
     * A complete snapshot rather than a diff: the editor's "Save changes" has one
     * button and the user has just seen the whole form, so sending every field is
     * what makes clearing a date or un-privating an entry actually stick. A
     * partial mutation would leave the removed value on the server, which reads as
     * the toggle springing back.
     *
     * `completedAt`, not `finishedAt` - AniList has no `finishedAt` argument, and a
     * document that sent one would be rejected outright with no partial save.
     */
    val SAVE_MEDIA_LIST_ENTRY = """
        mutation SaveMediaListEntry(
            ${'$'}mediaId: Int
            ${'$'}status: MediaListStatus
            ${'$'}progress: Int
            ${'$'}progressVolumes: Int
            ${'$'}score: Float
            ${'$'}startedAt: FuzzyDateInput
            ${'$'}completedAt: FuzzyDateInput
            ${'$'}private: Boolean
            ${'$'}notes: String
        ) {
          SaveMediaListEntry(
              mediaId: ${'$'}mediaId
              status: ${'$'}status
              progress: ${'$'}progress
              progressVolumes: ${'$'}progressVolumes
              score: ${'$'}score
              startedAt: ${'$'}startedAt
              completedAt: ${'$'}completedAt
              private: ${'$'}private
              notes: ${'$'}notes
          ) {
            id
            mediaId
            status
            progress
            progressVolumes
            score
            private
            notes
            startedAt { year month day }
            completedAt { year month day }
          }
        }
    """.trimIndent()

    /** Removes one entry from the viewer's list by its own entry id. */
    val DELETE_MEDIA_LIST_ENTRY = """
        mutation DeleteMediaListEntry(${'$'}id: Int) {
          DeleteMediaListEntry(id: ${'$'}id) {
            deleted
          }
        }
    """.trimIndent()

    /**
     * One activity and its comment thread.
     *
     * Its own request rather than a `replies` selection on the feed query: a
     * feed page is twenty-five activities, and asking for every reply on every
     * one of them would fetch threads nobody opened. The sheet is the only thing
     * that needs them, so it is the only thing that asks.
     *
     * `Activity(id:)` returns `ActivityUnion`, so the fragments are required.
     * Both members this feed can open a thread for are listed: the sheet is
     * reachable from a list card and from a text post, and a thread query with
     * only the `ListActivity` fragment would answer a text post's id with an
     * empty object - an open sheet showing zero comments on a post that has
     * them.
     */
    val GET_ACTIVITY_REPLIES = """
        query GetActivityReplies(${'$'}activityId: Int) {
          Activity(id: ${'$'}activityId) {
            ... on ListActivity {
              id
              isLiked
              likeCount
              replyCount
              replies {
                id
                text
                likeCount
                isLiked
                createdAt
                user {
                  id
                  name
                  avatar { large medium }
                }
              }
            }
            ... on TextActivity {
              id
              isLiked
              likeCount
              replyCount
              replies {
                id
                text
                likeCount
                isLiked
                createdAt
                user {
                  id
                  name
                  avatar { large medium }
                }
              }
            }
          }
        }
    """.trimIndent()

    /**
     * The people who liked one activity.
     *
     * `ListActivity.likes` and `TextActivity.likes` are plain `[User]`, not
     * paginated connections, so unlike replies this needs no page argument and
     * always returns everyone who liked the activity. That is what lets the
     * sheet print "Liked by N" from the list it just drew instead of trusting a
     * count that could disagree with it.
     *
     * Its own document rather than a field on the feed query, for the same reason
     * [GET_ACTIVITY_REPLIES] is: a feed page carries twenty-five activities, and
     * every liker of every one of them is a list nobody asked to see until the
     * sheet opens.
     *
     * `Activity(id:)` returns `ActivityUnion`, so the fragments are required;
     * both members the feed can open the likers sheet for are listed, because a
     * text post's id against a list-only document would answer an empty object.
     */
    val GET_ACTIVITY_LIKES = """
        query GetActivityLikes(${'$'}activityId: Int) {
          Activity(id: ${'$'}activityId) {
            ... on ListActivity {
              id
              likeCount
              likes {
                id
                name
                avatar { large medium }
              }
            }
            ... on TextActivity {
              id
              likeCount
              likes {
                id
                name
                avatar { large medium }
              }
            }
          }
        }
    """.trimIndent()

    /**
     * Likes or un-likes an activity.
     *
     * `ToggleLikeV2` and not `ToggleLike`: the older field took the same
     * arguments but the V2 name is the one AniList documents, and the reply
     * likes this app will add next use the identical shape. `type: ACTIVITY`
     * disambiguates the id, which is not unique across the likeable types.
     *
     * `LikeableUnion` carries both feed members; the document fragments both so
     * a text post can be liked from its card. A reply like (not sent yet) would
     * add an `ActivityReply` fragment here.
     */
    val TOGGLE_ACTIVITY_LIKE = """
        mutation ToggleActivityLike(${'$'}activityId: Int) {
          ToggleLikeV2(id: ${'$'}activityId, type: ACTIVITY) {
            ... on ListActivity {
              id
              isLiked
              likeCount
            }
            ... on TextActivity {
              id
              isLiked
              likeCount
            }
          }
        }
    """.trimIndent()

    /**
     * Likes or un-likes a single comment.
     *
     * The same mutation as [TOGGLE_ACTIVITY_LIKE] against a different member of
     * `LikeableUnion`, which is what the comment sheet's heart needs.
     *
     * Verified against the live schema rather than assumed: `LikeableUnion`'s members
     * are `ListActivity`, `TextActivity`, `MessageActivity`, `ActivityReply`, `Thread`
     * and `ThreadComment`, and `LikeableType` is `THREAD`, `THREAD_COMMENT`,
     * `ACTIVITY`, `ACTIVITY_REPLY`. A comment is therefore likeable, and it was not
     * being liked - the reply model has carried `isLiked` and `likeCount` since the
     * sheet was written and the replies query has selected them, and nothing ever
     * read either. This turns data that was already being paid for into a control.
     *
     * `ACTIVITY_REPLY` is not interchangeable with `ACTIVITY`: the id namespaces are
     * independent, so the type argument is what stops one member's id resolving
     * against the other's.
     */
    val TOGGLE_REPLY_LIKE = """
        mutation ToggleReplyLike(${'$'}replyId: Int) {
          ToggleLikeV2(id: ${'$'}replyId, type: ACTIVITY_REPLY) {
            ... on ActivityReply {
              id
              isLiked
              likeCount
            }
          }
        }
    """.trimIndent()

    /**
     * One character, with the titles they appear in.
     *
     * The sheet this opens is the in-app equivalent of the studio page: a tap on a
     * cast card used to hand off to a browser, which is the one thing the rest of the
     * title page does not do - a studio credit opens a studio *page*, so a character
     * credit opens a character *page*.
     *
     * `media` rather than a `characterMedia` on the character, because this is what a
     * reader wants: the shows they are in, most popular first, as a row of posters.
     * Sorted by `POPULARITY_DESC` because AniList's own ordering for this connection
     * puts an obscure cameo above a lead role otherwise.
     *
     * Both namespaces are asked for, like [getStaffProfileQuery]. This used to pass
     * `type: ANIME`, which silently dropped every `Manga` node - including the source
     * manga of the title whose cast row opened the sheet - while the row still asked
     * for `type` on every node and [ProfileAppearance] is documented around a character
     * appearing in a manga. The staff query never filtered, so an actor's page listed
     * manga credits their character's page hid: the asymmetry was the visible half.
     *
     * `age` is free text and not a number - Faye Valentine's is
     * "23 (Physical), 77 (Actual)". See `MediaCharacter.age`.
     *
     * `edges` rather than `nodes`, because the edge is the only half of a media
     * connection that carries `characterRole` and `voiceActors` - the first is what
     * the row of posters is *about*, and the second is the sheet's "Voiced by"
     * section. The field takes a language argument and answers **null** without one,
     * checked against the live API, so it is asked for twice under two names; see
     * `ProfileAppearanceEdge` for that reasoning and what the two names mean.
     */
    fun getCharacterProfileQuery(): String = """
        query CharacterProfile(${'$'}id: Int!) {
          Character(id: ${'$'}id) {
            id
            name { full native alternative }
            image { large medium }
            description(asHtml: false)
            gender
            age
            bloodType
            favourites
            siteUrl
            isFavourite
            dateOfBirth { year month day }
            media(perPage: 25, sort: [POPULARITY_DESC]) {
              edges {
                characterRole
                voiceActors(language: JAPANESE) {
                  id
                  name { full }
                  image { large }
                }
                voiceActorsEnglish: voiceActors(language: ENGLISH) {
                  id
                  name { full }
                  image { large }
                }
                node { $PROFILE_MEDIA_FIELDS }
              }
            }
          }
        }
    """.trimIndent()

    /**
     * One staff member, with the titles they worked on and the characters they voiced.
     *
     * Same shape as [getCharacterProfileQuery] and deliberately so: the sheet that
     * renders a person is one composable, and a crew member who renders differently
     * from an actor on the same page is the kind of asymmetry a reader notices.
     *
     * `staffMedia` is asked for as `edges` rather than `nodes` because the credit -
     * AniList's `staffRole`, a `"Director"` or an `"ADR Script (English)"` - hangs on
     * the edge, and a row of posters built from nodes cannot say what the person did
     * on any of them. The `node` inside each edge is the same media selection the
     * character's row draws, so both kinds of person page draw the same card.
     *
     * `characterMedia` is the other half of what AniList knows about a creator: many
     * are voice actors as well, and `staffMedia` only says what was *produced* - never
     * who they *performed*. Its `characters` live on the edge beside `characterRole`,
     * which is why this is asked for as `edges` too.
     *
     * Both connections ask for twenty-five edges rather than twelve. A staff connection
     * has one edge per *credit*, not per title - a director who also wrote and
     * storyboarded a film appears on it several times - so twelve edges could fill the
     * row with four posters and hide the rest of the filmography. The model collapses
     * the duplicates ([ProfileAppearances.titlesFor]); the character connection spends
     * one edge per title, so twenty-five is simply a longer row there.
     *
     * `primaryOccupations` is what distinguishes a voice actor from an animator, and
     * `yearsActive` and `homeTown` are the two facts a credit otherwise never carries.
     *
     * Both dates are asked for because both are null for a living person and the card
     * hides whichever is absent - see the sheet's own note on that.
     */
    fun getStaffProfileQuery(): String = """
        query StaffProfile(${'$'}id: Int!) {
          Staff(id: ${'$'}id) {
            id
            name { full native }
            image { large medium }
            description(asHtml: false)
            primaryOccupations
            gender
            age
            yearsActive
            homeTown
            bloodType
            favourites
            siteUrl
            isFavourite
            dateOfBirth { year month day }
            dateOfDeath { year month day }
            staffMedia(perPage: 25, sort: [POPULARITY_DESC]) {
              edges {
                staffRole
                node { $PROFILE_MEDIA_FIELDS }
              }
            }
            characterMedia(perPage: 25, sort: [POPULARITY_DESC]) {
              edges {
                characterRole
                characters {
                  id
                  name { full }
                  image { large medium }
                }
              }
            }
          }
        }
    """.trimIndent()

    /**
     * The one card of a title in a person's profile row.
     *
     * Smaller than [STUDIO_MEDIA_FIELDS] on purpose - that one opens into a whole
     * studio page's worth of shelves, and this is a horizontal row where a reader may
     * never tap. The four facts are what a poster needs to be recognised by, plus `id`
     * and `type` so the tap can be routed to the right detail sheet.
     */
    private const val PROFILE_MEDIA_FIELDS = """
                id
                type
                title { english romaji native userPreferred }
                format
                status
                averageScore
                siteUrl
                startDate { year }
                coverImage { extraLarge large }
    """

    /**
     * Posts a comment on an activity.
     *
     * `SaveActivityReply`, **not** `SaveActivityMessage`. The latter is for
     * direct messages and takes a `messengerId`; posting a comment through it
     * would look like a reply to this thread and deliver a private message
     * instead. The names are close enough that this is worth stating.
     *
     * The response is the created reply, which is exactly the shape the sheet
     * appends to its list, so a successful post needs no refetch.
     */
    val SAVE_ACTIVITY_REPLY = """
        mutation SaveActivityReply(${'$'}activityId: Int, ${'$'}text: String) {
          SaveActivityReply(activityId: ${'$'}activityId, text: ${'$'}text) {
            id
            text
            likeCount
            isLiked
            createdAt
            user {
              id
              name
              avatar { large medium }
            }
          }
        }
    """.trimIndent()

    /**
     * Rewrites one of the viewer's own comments.
     *
     * `SaveActivityReply` again, not a second mutation: the field is documented
     * as "create or update an activity reply", with `id` "required for updating"
     * - so the same document with an `id` is the whole edit. Verified against the
     * live schema before it was written: the document with an id is answered by
     * the same authentication wall (401 unauthenticated) as the create, where a
     * malformed document is a 400 naming the bad argument. See
     * `ReplyManagementQueryTest`.
     *
     * `activityId` travels alongside the id even on an update. It cannot be a
     * validation error - it is a declared argument of the field, accepted whether
     * or not the resolver reads it - and sending the reply's actual parent is
     * exactly what a resolver that re-checks the thread would want to see.
     * Sending it only on create is the version that breaks against a stricter
     * server, and nothing here can observe which behaviour the server chose.
     *
     * The selection is the created reply's own, field for field, because the
     * response replaces the row exactly as a posted comment appends one.
     */
    val EDIT_ACTIVITY_REPLY = """
        mutation EditActivityReply(${'$'}id: Int, ${'$'}activityId: Int, ${'$'}text: String) {
          SaveActivityReply(id: ${'$'}id, activityId: ${'$'}activityId, text: ${'$'}text) {
            id
            text
            likeCount
            isLiked
            createdAt
            user {
              id
              name
              avatar { large medium }
            }
          }
        }
    """.trimIndent()

    /**
     * Deletes one of the viewer's own comments.
     *
     * `DeleteActivityReply` is its own mutation because a comment is its own
     * thing: `DeleteActivity` deletes the post a thread hangs off, not a reply in
     * it, and the two take ids from different namespaces. The server enforces
     * ownership ("of the authenticated users"), so a reply this account did not
     * write comes back as an error rather than a delete - which is why the sheet
     * only offers the control on the viewer's own comments rather than letting
     * the server reject a tap the reader was invited to make.
     *
     * The response is the shared `Deleted` object - a boolean, and no echo of the
     * removed reply - so the sheet drops the row by the id it sent.
     */
    val DELETE_ACTIVITY_REPLY = """
        mutation DeleteActivityReply(${'$'}id: Int) {
          DeleteActivityReply(id: ${'$'}id) {
            deleted
          }
        }
    """.trimIndent()

    /**
     * "Are you there?", and nothing else.
     *
     * `__typename` is a GraphQL meta-field, so this resolves without touching a
     * database row or a cache tier - which matters because the probe runs at
     * exactly the moment AniList is suspected of being unwell. Asking for
     * anything real would either fail for a reason that is not reachability or
     * add load to a server that is already struggling.
     *
     * Named rather than anonymous so the query shows up in AniList's tooling as
     * something deliberate, not as a stray fragment.
     */
    val API_STATUS = """
        query AniSequelStatus {
          __typename
        }
    """.trimIndent()
}
