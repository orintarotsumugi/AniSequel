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
 * `GET_MEDIA_DETAIL` exists because of rule 1. `description`, `bannerImage`
 * and `studios` are only ever rendered for the one entry a user taps open, so
 * they are fetched on demand (718 bytes) rather than shipped for all ~1,500
 * relation nodes on every load.
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
     *
     * Omitted on purpose: `description`, `bannerImage`, `studios` (see
     * [GET_MEDIA_DETAIL]), `coverImage.extraLarge` (the cards render 96dp
     * posters - the 2x asset is invisible at that size and is the largest
     * string on the node), `coverImage.color` and `nextAiringEpisode.airingAt`
     * (nothing reads either).
     */
    private const val RELATED_NODE_FIELDS = """
                id
                title { romaji english native userPreferred }
                format
                status
                episodes
                averageScore
                popularity
                genres
                siteUrl
                startDate { year month day }
                coverImage { large }
                nextAiringEpisode { episode }
                mediaListEntry { status }
    """

    /**
     * `episodes` is the only parent field the detector needs: an entry counts as
     * watched when its progress reaches the parent's episode count even if the
     * user never set a status. The parent's own cover, description, studios and
     * banner are never rendered - only its title is - so they are not requested.
     *
     * MediaListCollection is *not* paginated: it returns the whole list in one
     * response unless `chunk`/`perChunk` are passed, which this app does not do.
     */
    val GET_USER_ANIME_LIST = """
        query GetUserAnimeList(${'$'}userId: Int, ${'$'}userName: String) {
          MediaListCollection(userId: ${'$'}userId, userName: ${'$'}userName, type: ANIME) {
            lists {
              name
              status
              entries {
                id
                status
                score
                progress
                media {
                  id
                  title { romaji english native userPreferred }
                  episodes
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