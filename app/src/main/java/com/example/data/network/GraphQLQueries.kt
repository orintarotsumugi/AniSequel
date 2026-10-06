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
                coverImage { large color }
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
     */
    val GET_USER_ANIME_LIST = """
        query GetUserAnimeList(${'$'}userId: Int, ${'$'}userName: String) {
          MediaListCollection(userId: ${'$'}userId, userName: ${'$'}userName, type: ANIME) {
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
                  startDate { year month day }
                  coverImage { large color }
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
