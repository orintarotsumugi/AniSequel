package com.example.data.network

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

    // MediaListCollection returns the user's whole list in one response (up to
    // AniList's 11,000-entry cap), so there is nothing to paginate and
    // `hasNextChunk` - a leftover from the old chunked API - was only ever
    // asking the server to send a field nothing read.
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
                  title {
                    romaji
                    english
                    native
                    userPreferred
                  }
                  format
                  status
                  episodes
                  averageScore
                  popularity
                  description(asHtml: false)
                  genres
                  siteUrl
                  bannerImage
                  startDate {
                    year
                    month
                    day
                  }
                  coverImage {
                    medium
                    large
                    extraLarge
                    color
                  }
                  studios(isMain: true) {
                    nodes {
                      id
                      name
                    }
                  }
                  relations {
                    edges {
                      relationType
                      node {
                        id
                        title {
                          romaji
                          english
                          native
                          userPreferred
                        }
                        format
                        status
                        episodes
                        averageScore
                        popularity
                        description(asHtml: false)
                        genres
                        siteUrl
                        bannerImage
                        startDate {
                          year
                          month
                          day
                        }
                        nextAiringEpisode {
                          episode
                          airingAt
                        }
                        coverImage {
                          medium
                          large
                          extraLarge
                          color
                        }
                        studios(isMain: true) {
                          nodes {
                            id
                            name
                          }
                        }
                        mediaListEntry {
                          id
                          status
                        }
                      }
                    }
                  }
                }
              }
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
