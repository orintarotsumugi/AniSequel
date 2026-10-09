package com.example.data.network

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
 * `GET_MEDIA_DETAIL` exists because of rule 1. `description`, `bannerImage`,
 * `studios`, `trailer`, and `synonyms` are only ever rendered for the one entry
 * a user taps open, so they are fetched on demand rather than shipped for all
 * ~1,500 relation nodes on every load.
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
     */
    private const val FAVOURITE_MEDIA_FIELDS = """
                id
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
     * ## The `stats` block
     *
     * `stats` is the field AniList marks deprecated, and it is here because the
     * replacement is not served: `statistics.anime.statuses`, `.genres`,
     * `.formats` and `.releaseYears` all answer `[]` for every account tried,
     * including ones with hundreds of scored entries, while the same query's
     * `count`, `meanScore` and `minutesWatched` come back populated. The
     * per-distribution data the Stats tab is built around only exists under
     * `stats`. It costs about 6 KB in the same request - one round trip, not two.
     *
     * `UserOverviewQueryTest` asserts the block is present, so that whoever finds
     * this the day AniList retires `stats` sees the removal noted rather than
     * discovering it as an empty Stats tab.
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
              favouredFormats { format amount }
              favouredYears { year amount meanScore }
              favouredGenres { genre amount meanScore }
              favouredTags { tag { id name } amount meanScore }
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
              studios(perPage: 12) { nodes { id name isAnimationStudio siteUrl } }
            }
          }
        }
    """.trimIndent()

    /**
     * A page of somebody's list activity: "watched 3 episodes", "completed X",
     * "added Y to planning".
     *
     * `Page.activities` returns `[ActivityUnion]`, so this selects the
     * `ListActivity` member with an inline fragment and filters to the two list
     * types with `type_in`. Without the filter the same query answers with
     * `MessageActivity` - forum posts by the user, which is not what the feed is
     * for. The parser drops anything that is still not a list update.
     *
     * `type_in` takes both media types in one request rather than one query per
     * medium: the response carries each item's own `media.type`, and the screen
     * can split on that afterwards. Two requests would also mean two chances to
     * hit AniList's rate limit on a screen the user opens to browse.
     */
    val GET_USER_ACTIVITY = """
        query GetUserActivity(${'$'}userId: Int, ${'$'}page: Int) {
          Page(page: ${'$'}page, perPage: 25) {
            activities(userId: ${'$'}userId, type_in: [ANIME_LIST, MANGA_LIST], sort: [ID_DESC]) {
              ... on ListActivity {
                id
                status
                progress
                createdAt
                likeCount
                replyCount
                media {
                  id
                  type
                  format
                  episodes
                  title { english romaji native }
                  coverImage { extraLarge large color }
                  siteUrl
                }
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
     * Fields for a related node, rendered in the card list.
     */
    private const val RELATED_NODE_FIELDS = """
                id
                title { romaji english native userPreferred }
                format
                status
                episodes
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
    val GET_USER_ANIME_LIST = """
        query GetUserAnimeList(${'$'}userId: Int, ${'$'}userName: String, ${'$'}chunk: Int) {
          MediaListCollection(userId: ${'$'}userId, userName: ${'$'}userName, type: ANIME, chunk: ${'$'}chunk) {
            hasNextChunk
            lists {
              name
              status
              entries {
                status
                progress
                completedAt { year month day }
                media {
                  id
                  title { romaji english native userPreferred }
                  episodes
                  status
                  format
                  countryOfOrigin
                  startDate { year month day }
                  coverImage { extraLarge large color }
                  nextAiringEpisode { episode airingAt }
                  relations {
                    edges {
                      relationType
                      node { $RELATED_NODE_FIELDS }
                    }
                  }
                }
              }
            }
          }
        }
    """.trimIndent()

    /**
     * The heavy, single-item fields, fetched only for the entry the user opens.
     */
    val GET_MEDIA_DETAIL = """
        query GetMediaDetail(${'$'}id: Int) {
          Media(id: ${'$'}id) {
            id
            description(asHtml: false)
            bannerImage
            studios(isMain: true) {
              nodes { name }
            }
            source
            duration
            trailer {
              id
              site
            }
            synonyms
            meanScore
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
          }
        }
    """.trimIndent()

    val ADD_TO_PLANNING = """
        mutation AddToPlanning(${'$'}mediaId: Int) {
          SaveMediaListEntry(mediaId: ${'$'}mediaId, status: PLANNING) {
            id
            status
            mediaId
          }
        }
    """.trimIndent()
}
