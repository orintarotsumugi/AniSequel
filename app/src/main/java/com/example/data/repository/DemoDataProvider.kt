package com.example.data.repository

import com.example.data.model.FuzzyDate
import com.example.data.model.MediaCoverImage
import com.example.data.model.MediaListCollection
import com.example.data.model.MediaListEntryItem
import com.example.data.model.MediaListGroup
import com.example.data.model.MediaNode
import com.example.data.model.MediaRelationEdge
import com.example.data.model.MediaRelations
import com.example.data.model.MediaTitle
import com.example.data.model.NextAiringEpisode
import com.example.data.model.StudioConnection
import com.example.data.model.StudioNode
import com.example.data.model.UserAvatar
import com.example.data.model.ViewerProfile

object DemoDataProvider {

    fun getDemoViewer(): ViewerProfile {
        return ViewerProfile(
            id = 999999,
            name = "OtakuExplorer",
            avatar = UserAvatar(
                large = "https://s4.anilist.co/file/anilistcdn/user/avatar/large/default.png",
                medium = "https://s4.anilist.co/file/anilistcdn/user/avatar/medium/default.png"
            ),
            bannerImage = "https://s4.anilist.co/file/anilistcdn/media/anime/banner/101922-YfZhKBUDDS6L.jpg"
        )
    }

    fun getDemoMediaList(): MediaListCollection {
        val ufotable = StudioConnection(listOf(StudioNode(43, "ufotable")))
        val mappa = StudioConnection(listOf(StudioNode(569, "MAPPA")))
        val a1 = StudioConnection(listOf(StudioNode(51, "A-1 Pictures")))
        val wit = StudioConnection(listOf(StudioNode(858, "WIT Studio")))
        val madhouse = StudioConnection(listOf(StudioNode(11, "Madhouse")))

        // 1. Demon Slayer S1 (Watched) -> S2 Mugen Train Arc (Missed Sequel)
        val kimetsuS2 = MediaNode(
            id = 142329,
            title = MediaTitle(english = "Demon Slayer: Kimetsu no Yaiba Entertainment District Arc", romaji = "Kimetsu no Yaiba: Yuukaku-hen"),
            format = "TV",
            status = "FINISHED",
            episodes = 11,
            averageScore = 88,
            meanScore = 89,
            source = "MANGA",
            duration = 24,
            popularity = 340000,
            description = "Tanjiro, Zenitsu, and Inosuke accompany the Sound Hashira, Tengen Uzui, on a perilous mission inside Yoshiwara's Entertainment District.",
            genres = listOf("Action", "Fantasy", "Supernatural"),
            coverImage = MediaCoverImage(large = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx142329-3L1p5rR0kZ0H.jpg"),
            startDate = FuzzyDate(2021, 12, 5),
            studios = ufotable,
            trailer = com.example.data.model.MediaTrailer(id = "VQGCKyvzIM4", site = "youtube"),
            rankings = listOf(
                com.example.data.model.MediaRanking(rank = 15, context = "Highest Rated All Time", allTime = true)
            ),
            tags = listOf(
                com.example.data.model.MediaTag(name = "Demons", rank = 96),
                com.example.data.model.MediaTag(name = "Historical", rank = 92),
                com.example.data.model.MediaTag(name = "Swordplay", rank = 90),
                com.example.data.model.MediaTag(name = "Shounen", rank = 88)
            )
        )

        val kimetsuS1 = MediaNode(
            id = 101922,
            title = MediaTitle(english = "Demon Slayer: Kimetsu no Yaiba", romaji = "Kimetsu no Yaiba"),
            format = "TV",
            status = "FINISHED",
            episodes = 26,
            averageScore = 84,
            popularity = 600000,
            description = "It is the Taisho Period in Japan. Tanjiro, a kindhearted boy who sells charcoal for a living, finds his family slaughtered by a demon.",
            genres = listOf("Action", "Fantasy", "Supernatural"),
            coverImage = MediaCoverImage(large = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx101922-WBsBl0ClmgLd.jpg"),
            startDate = FuzzyDate(2019, 4, 6),
            studios = ufotable,
            relations = MediaRelations(listOf(MediaRelationEdge("SEQUEL", kimetsuS2)))
        )

        // 2. Jujutsu Kaisen S1 (Watched) -> S2 Shibuya Incident (Missed Sequel)
        val jjkS2 = MediaNode(
            id = 145064,
            title = MediaTitle(english = "Jujutsu Kaisen Season 2", romaji = "Jujutsu Kaisen 2nd Season"),
            format = "TV",
            status = "FINISHED",
            episodes = 23,
            averageScore = 89,
            popularity = 410000,
            description = "The past comes to light as Satoru Gojo and Suguru Geto take on a fateful mission in their youth, leading to the devastating Shibuya Incident.",
            genres = listOf("Action", "Supernatural", "Drama"),
            coverImage = MediaCoverImage(large = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx145064-7O1F9aQhFz4C.jpg"),
            startDate = FuzzyDate(2023, 7, 6),
            studios = mappa,
            trailer = com.example.data.model.MediaTrailer(id = "O6qVieflwqs", site = "youtube"),
            rankings = listOf(
                com.example.data.model.MediaRanking(rank = 8, context = "Most Popular 2023", year = 2023)
            ),
            tags = listOf(
                com.example.data.model.MediaTag(name = "Urban Fantasy", rank = 95),
                com.example.data.model.MediaTag(name = "Curse", rank = 90),
                com.example.data.model.MediaTag(name = "Martial Arts", rank = 86),
                com.example.data.model.MediaTag(name = "Shounen", rank = 85)
            )
        )

        val jjkS1 = MediaNode(
            id = 113415,
            title = MediaTitle(english = "Jujutsu Kaisen", romaji = "Jujutsu Kaisen"),
            format = "TV",
            status = "FINISHED",
            episodes = 24,
            averageScore = 86,
            popularity = 650000,
            genres = listOf("Action", "Supernatural", "Fantasy"),
            coverImage = MediaCoverImage(large = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx113415-bbBWj4pQC3mZ.jpg"),
            startDate = FuzzyDate(2020, 10, 3),
            studios = mappa,
            relations = MediaRelations(listOf(MediaRelationEdge("SEQUEL", jjkS2)))
        )

        // 3. Kaguya-sama S1 (Watched) -> S2 (Missed Sequel)
        val kaguyaS2 = MediaNode(
            id = 112641,
            title = MediaTitle(english = "Kaguya-sama: Love is War?", romaji = "Kaguya-sama wa Kokurasetai? Tensai-tachi no Renai Zunousen"),
            format = "TV",
            status = "FINISHED",
            episodes = 12,
            averageScore = 87,
            popularity = 380000,
            description = "The battle of wits between Miyuki Shirogane and Kaguya Shinomiya escalates as new challenges and elections shake the student council.",
            genres = listOf("Comedy", "Romance", "Psychological"),
            coverImage = MediaCoverImage(large = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx112641-f761jG2jL6bC.jpg"),
            startDate = FuzzyDate(2020, 4, 11),
            studios = a1
        )

        val kaguyaS1 = MediaNode(
            id = 101921,
            title = MediaTitle(english = "Kaguya-sama: Love is War", romaji = "Kaguya-sama wa Kokurasetai: Tensai-tachi no Renai Zunousen"),
            format = "TV",
            status = "FINISHED",
            episodes = 12,
            averageScore = 83,
            popularity = 450000,
            genres = listOf("Comedy", "Romance", "Psychological"),
            coverImage = MediaCoverImage(large = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx101921-VvdGQygnkGol.jpg"),
            startDate = FuzzyDate(2019, 1, 12),
            studios = a1,
            relations = MediaRelations(listOf(MediaRelationEdge("SEQUEL", kaguyaS2)))
        )

        // 4. Spy x Family Part 1 (Watched) -> S2 (Missed Sequel)
        val spyS2 = MediaNode(
            id = 158870,
            title = MediaTitle(english = "SPY x FAMILY Season 2", romaji = "SPY×FAMILY Season 2"),
            format = "TV",
            status = "FINISHED",
            episodes = 12,
            averageScore = 80,
            popularity = 290000,
            description = "The Forger family embarks on luxury cruise adventures and secret assassin duties while maintaining peace between Westalis and Ostania.",
            genres = listOf("Action", "Comedy", "Slice of Life"),
            coverImage = MediaCoverImage(large = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx158870-N7zKzKz2X1sH.jpg"),
            startDate = FuzzyDate(2023, 10, 7),
            studios = wit
        )

        val spyS1 = MediaNode(
            id = 140960,
            title = MediaTitle(english = "SPY x FAMILY", romaji = "SPY×FAMILY"),
            format = "TV",
            status = "FINISHED",
            episodes = 12,
            averageScore = 85,
            popularity = 520000,
            genres = listOf("Action", "Comedy", "Slice of Life"),
            coverImage = MediaCoverImage(large = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx140960-Y4yP48Y9B2uM.jpg"),
            startDate = FuzzyDate(2022, 4, 9),
            studios = wit,
            relations = MediaRelations(listOf(MediaRelationEdge("SEQUEL", spyS2)))
        )

        // 5. Frieren (Watched) -> S2 Announced (Upcoming Sequel)
        val frierenS2 = MediaNode(
            id = 181775,
            title = MediaTitle(english = "Frieren: Beyond Journey's End Season 2", romaji = "Sousou no Frieren 2nd Season"),
            format = "TV",
            status = "NOT_YET_RELEASED",
            episodes = null,
            averageScore = null,
            popularity = 120000,
            description = "The continuation of elf mage Frieren's poignant journey to Ende at the northern edge of the continent.",
            genres = listOf("Adventure", "Drama", "Fantasy"),
            coverImage = MediaCoverImage(large = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx181775-v9M1n0qZ987y.jpg"),
            startDate = FuzzyDate(2025, 10, null),
            studios = madhouse
        )

        val frierenS1 = MediaNode(
            id = 154587,
            title = MediaTitle(english = "Frieren: Beyond Journey's End", romaji = "Sousou no Frieren"),
            format = "TV",
            status = "FINISHED",
            episodes = 28,
            averageScore = 93,
            popularity = 480000,
            genres = listOf("Adventure", "Drama", "Fantasy"),
            coverImage = MediaCoverImage(large = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx154587-n6onjReL2gwM.jpg"),
            startDate = FuzzyDate(2023, 9, 29),
            studios = madhouse,
            relations = MediaRelations(listOf(MediaRelationEdge("SEQUEL", frierenS2)))
        )

        // 6. A brand-new original currently airing (demo for the arriving section).
        // No relations, so it never joins the missed-sequel graph; the airing
        // timestamp is computed at load time so the countdown stays alive however
        // old this demo build gets.
        val originalAiring = MediaNode(
            id = 999001,
            title = MediaTitle(english = "Starlight Runner", romaji = "Starlight Runner"),
            format = "TV",
            status = "RELEASING",
            episodes = 12,
            averageScore = null,
            popularity = 90000,
            description = "A courier girl races across a neon megacity, outrunning corporate pursuers to deliver a message that could tip the balance of a silent war.",
            genres = listOf("Action", "Sci-Fi"),
            coverImage = MediaCoverImage(large = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx999001-placeholder.jpg", color = "#7C4DFF"),
            startDate = FuzzyDate(2026, 4, 5),
            nextAiringEpisode = NextAiringEpisode(
                episode = 7,
                airingAt = (System.currentTimeMillis() / 1000) + 2 * 24 * 60 * 60
            )
        )

        // 7. An upcoming original still in production (demo for the arriving
        // section). Also relation-free so discovery stops at the watched shows.
        val upcomingOriginal = MediaNode(
            id = 999002,
            title = MediaTitle(english = "Midnight Vanguard", romaji = "Midnight Vanguard"),
            format = "TV",
            status = "NOT_YET_RELEASED",
            episodes = null,
            averageScore = null,
            popularity = 45000,
            description = "Former elite soldiers are lured back into the field for one final contract that none of them is sure they will survive.",
            genres = listOf("Action", "Thriller"),
            coverImage = MediaCoverImage(large = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx999002-placeholder.jpg", color = "#E53935"),
            startDate = FuzzyDate(2026, 10, 15)
        )

        return MediaListCollection(
            lists = listOf(
                MediaListGroup(
                    name = "Completed",
                    status = "COMPLETED",
                    entries = listOf(
                        MediaListEntryItem(status = "COMPLETED", media = kimetsuS1),
                        MediaListEntryItem(status = "COMPLETED", media = jjkS1),
                        MediaListEntryItem(status = "COMPLETED", media = kaguyaS1),
                        MediaListEntryItem(status = "COMPLETED", media = spyS1),
                        MediaListEntryItem(status = "COMPLETED", media = frierenS1)
                    )
                ),
                MediaListGroup(
                    name = "Watching",
                    status = "CURRENT",
                    entries = listOf(
                        MediaListEntryItem(status = "CURRENT", progress = 6, media = originalAiring),
                        MediaListEntryItem(status = "PLANNING", progress = 0, media = upcomingOriginal)
                    )
                )
            )
        )
    }
}
