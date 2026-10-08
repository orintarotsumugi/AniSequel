package com.example.data.repository

import com.example.data.model.ActivityHistoryDay
import com.example.data.model.AniListUserStats
import com.example.data.model.AnimeStats
import com.example.data.model.FavouriteCharacter
import com.example.data.model.FavouriteCharacterConnection
import com.example.data.model.FavouriteMediaConnection
import com.example.data.model.FavouriteStaff
import com.example.data.model.FavouriteStaffConnection
import com.example.data.model.Favourites
import com.example.data.model.FollowUser
import com.example.data.model.FormatAmount
import com.example.data.model.GenreAmount
import com.example.data.model.ListActivity
import com.example.data.model.ListScoreStats
import com.example.data.model.MangaStats
import com.example.data.model.MediaCoverImage
import com.example.data.model.MediaNode
import com.example.data.model.MediaTag
import com.example.data.model.MediaTitle
import com.example.data.model.PersonImage
import com.example.data.model.PersonName
import com.example.data.model.ScoreAmount
import com.example.data.model.StatusAmount
import com.example.data.model.StudioConnection
import com.example.data.model.StudioNode
import com.example.data.model.TagAmount
import com.example.data.model.UserAvatar
import com.example.data.model.UserOverview
import com.example.data.model.UserStatistics
import com.example.data.model.YearAmount

/**
 * The demo profile screen's fixture.
 *
 * Separate from [DemoDataProvider] because the two change for different reasons:
 * that file is the missed-sequel graph - which shows are finished and which
 * sequels they lead to - and every entry in it exists to make a relation edge.
 * This one is a person's bio, list statistics and pinned favourites, which the
 * sequel walk never reads. Folding them together would mean editing the sequel
 * fixture to change a studio chip, and editing the sequel fixture to change a
 * chip is how the graph quietly stops testing what it was written to test.
 *
 * The numbers here are deliberately not consistent with [DemoDataProvider]'s
 * five-entry list. The list is a worked example of five franchises; a person with
 * 128 completed and 118 planned is what the screen is actually for, and a demo
 * whose statistics card said "5 completed" would read as the feature reporting
 * the wrong thing rather than as a small sample.
 */
object DemoProfileProvider {

    fun getDemoUserOverview(): UserOverview = UserOverview(
        id = 999999,
        name = "OtakuExplorer",
        about = """
            ### How I rate

            * 10 / 10 — Peak fiction / personal favourite
            * 9 / 10 — Highly enjoyable and deeply satisfying
            * 8 / 10 — Solid entry, open to minor adjustments over time
            * 7 / 10 — Okay / decent watch
            * 6 & below — Not good. Completed purely out of commitment

            ### Leave a comment!

            I love hearing feedback. Tell me what you think of my list, or what
            you thought of anything I recommended.

            *You're always welcome here.*
        """.trimIndent(),
        siteUrl = "https://anilist.co/user/OtakuExplorer",
        avatar = UserAvatar(
            large = "https://s4.anilist.co/file/anilistcdn/user/avatar/large/default.png",
            medium = "https://s4.anilist.co/file/anilistcdn/user/avatar/medium/default.png"
        ),
        bannerImage = "https://s4.anilist.co/file/anilistcdn/media/anime/banner/101922-YfZhKBUDDS6L.jpg",
        statistics = UserStatistics(
            anime = AnimeStats(
                count = 261,
                episodesWatched = 3842,
                minutesWatched = 92_208
            ),
            manga = MangaStats(
                count = 21,
                chaptersRead = 1_946,
                volumesRead = 188
            )
        ),
        createdAt = 1_587_561_600,
        updatedAt = 1_762_000_000,
        stats = demoStats(),
        favourites = Favourites(
            anime = FavouriteMediaConnection(
                nodes = listOf(
                    favourite(101922, "Demon Slayer: Kimetsu no Yaiba", "TV", "FINISHED", 84,
                        "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx101922-WBsBl0ClmgLd.jpg"),
                    favourite(154587, "Frieren: Beyond Journey's End", "TV", "FINISHED", 91,
                        "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx154587-n6onjReL2gwM.jpg"),
                    favourite(113415, "Jujutsu Kaisen", "TV", "FINISHED", 86,
                        "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx113415-Yf3QlMhCyczL.jpg"),
                    favourite(20605, "Spy x Family", "TV", "FINISHED", 80,
                        "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx20605-4RA8h8JiqdPU.jpg"),
                    favourite(20958, "Haibane Renmei", "TV", "FINISHED", 82,
                        "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx20958-HuFJyr54Mmir.jpg")
                )
            ),
            manga = FavouriteMediaConnection(
                nodes = listOf(
                    favourite(30013, "Black Clover", "MANGA", "FINISHED", 78,
                        "https://s4.anilist.co/file/anilistcdn/media/manga/cover/large/bx30013-tXMKBoU8Xfqu.jpg"),
                    favourite(109660, "Tokyo Ghoul", "MANGA", "FINISHED", 79,
                        "https://s4.anilist.co/file/anilistcdn/media/manga/cover/large/bx109660-qBTByh7YC0VE.jpg"),
                    favourite(11061, "Hunter x Hunter", "MANGA", "FINISHED", 90,
                        "https://s4.anilist.co/file/anilistcdn/media/manga/cover/large/bx11061-f8ItEwX3XDNM.jpg")
                )
            ),
            characters = FavouriteCharacterConnection(
                nodes = listOf(
                    character(151317, "Rintarou Tsumugi", 78_430,
                        "https://s4.anilist.co/file/anilistcdn/character/large/bx151317-K0O2xt5vGxAg.png"),
                    character(128670, "Kaoruko Waguri", 41_220,
                        "https://s4.anilist.co/file/anilistcdn/character/large/bx128670-MNBqHFn6V1Fr.png"),
                    character(134588, "Gabimaru", 22_870,
                        "https://s4.anilist.co/file/anilistcdn/character/large/bx134588-2L0GmfjRTJmq.png"),
                    character(140602, "Hitori Gotou", 19_004,
                        "https://s4.anilist.co/file/anilistcdn/character/large/bx140602-1S2RZgQJHscV.png"),
                    character(135781, "Subaru Natsuki", 17_665,
                        "https://s4.anilist.co/file/anilistcdn/character/large/bx135781-FhHDmSBLVoSp.png")
                )
            ),
            staff = FavouriteStaffConnection(
                nodes = listOf(
                    staff(112401, "Saka Mikami", listOf("Director"), 4_812,
                        "https://s4.anilist.co/file/anilistcdn/staff/large/bx112401-BIkOAtW2o0Qv.png"),
                    staff(109733, "Yuuji Kaku", listOf("Story & Art"), 9_140,
                        "https://s4.anilist.co/file/anilistcdn/staff/large/bx109733-MiEQqR0mX6Oz.png"),
                    staff(113352, "Mika Yamamori", listOf("Art Director"), 2_276,
                        "https://s4.anilist.co/file/anilistcdn/staff/large/bx113352-8p2pXQ2xK4nJ.png")
                )
            ),
            studios = StudioConnection(
                listOf(
                    StudioNode(569, "MAPPA", isAnimationStudio = true),
                    StudioNode(805, "CloverWorks", isAnimationStudio = true),
                    StudioNode(43, "ufotable", isAnimationStudio = true),
                    StudioNode(11, "Madhouse", isAnimationStudio = true)
                )
            )
        )
    )

    /**
     * A recent activity feed, newest first.
     *
     * Relative timestamps rather than fixed ones, for the same reason
     * [com.example.data.repository.DemoDataProvider.daysAgo] is: a fixture
     * pinned to 2024 would say "2 years ago" forever and the feed would look
     * broken. The `MediaNode` ids are the real AniList ids of the demo shows, so
     * tapping a card opens something that exists.
     */
    fun getDemoActivity(): List<ListActivity> = listOf(
        activity(1, 100465, "completed", null, hoursAgo(2), likes = 8, replies = 0),
        activity(2, 142329, "current", "7", hoursAgo(9), likes = 3, replies = 1),
        activity(3, 154587, "planning", null, hoursAgo(26), likes = 1, replies = 0),
        activity(4, 113415, "completed", null, hoursAgo(31), likes = 12, replies = 4),
        activity(5, 20605, "current", "22", hoursAgo(50), likes = 2, replies = 0),
        activity(6, 20958, "paused", "5", hoursAgo(74), likes = 0, replies = 0),
        activity(7, 164212, "completed", null, hoursAgo(120), likes = 5, replies = 1),
        // The same show as row 4, dropped earlier. Two rows on one media is the
        // normal case for a feed - watched, then completed - and it is why the
        // list is keyed on the activity id rather than the media id.
        activity(8, 113415, "dropped", null, hoursAgo(168), likes = 0, replies = 0)
    )

    /**
     * Followers and following, as other real AniList accounts.
     *
     * Real usernames on purpose: tapping one of these opens that person's actual
     * profile, and a demo whose grid of faces opened an error screen would make
     * the Social tab look broken rather than demonstrate it.
     */
    fun getDemoFollowers(): List<FollowUser> = listOf(
        FollowUser(2, "matchai", avatarFor("b2-2qclWjtFUXkI")),
        FollowUser(7, "jamiejakov", avatarFor("b7-1yTxkLdGnfdO")),
        FollowUser(12, "JesterOW", avatarFor("b12-QwPnKlEbrFGf")),
        FollowUser(100, "GuardianLettuce", avatarFor("b100-1yTxkLdGnfdO")),
        FollowUser(41457, "shujinkou", avatarFor("b41457-1yTxkLdGnfdO"))
    )

    fun getDemoFollowing(): List<FollowUser> = listOf(
        FollowUser(12, "JesterOW", avatarFor("b12-QwPnKlEbrFGf")),
        FollowUser(100, "GuardianLettuce", avatarFor("b100-1yTxkLdGnfdO")),
        FollowUser(1535, "smkybear15", avatarFor("b1535-1yTxkLdGnfdO"))
    )

    private fun avatarFor(hash: String) = UserAvatar(
        medium = "https://s4.anilist.co/file/anilistcdn/user/avatar/medium/$hash.png",
        large = "https://s4.anilist.co/file/anilistcdn/user/avatar/large/$hash.png"
    )

    /**
     * The explicit [index] is the activity id.
     *
     * Derived from the media id and the age instead, the two rows for the same show
     * would collide - and the feed is a `LazyColumn` keyed on that id, which throws
     * on a duplicate key rather than quietly drawing one row.
     */
    private fun activity(
        index: Int,
        mediaId: Int,
        status: String,
        progress: String?,
        createdAtSecondsAgo: Long,
        likes: Int,
        replies: Int
    ): ListActivity = ListActivity(
        id = index,
        status = status,
        progress = progress,
        createdAt = (System.currentTimeMillis() / 1000 - createdAtSecondsAgo).toInt(),
        likeCount = likes,
        replyCount = replies,
        media = demoMediaFor(mediaId)
    )

    private fun hoursAgo(hours: Long): Long = hours * 60L * 60L

    /**
     * The aggregate statistics block.
     *
     * Deliberately the *same* [stats] shape the real query reads, deprecated
     * field and all - the demo is the only place this screen's statistics are
     * ever exercised end to end without a session, and a fixture built on
     * anything else would not have caught a change to that block.
     *
     * The counts are internally consistent with the Status Distribution and add
     * up to the 261 in `statistics.anime.count`, because a Stats tab whose own
     * cards disagree with each other is worse than an empty one.
     */
    private fun demoStats() = AniListUserStats(
        watchedTime = 92_208,
        chaptersRead = 1_946,
        activityHistory = demoActivityHistory(),
        animeStatusDistribution = listOf(
            StatusAmount("CURRENT", 7),
            StatusAmount("COMPLETED", 128),
            StatusAmount("PAUSED", 8),
            StatusAmount("DROPPED", 4),
            StatusAmount("PLANNING", 114)
        ),
        mangaStatusDistribution = listOf(
            StatusAmount("CURRENT", 13),
            StatusAmount("COMPLETED", 8)
        ),
        animeScoreDistribution = demoScoreDistribution(),
        animeListScores = ListScoreStats(meanScore = 84, standardDeviation = 18),
        mangaListScores = ListScoreStats(meanScore = 89, standardDeviation = 12),
        favouredFormats = listOf(
            FormatAmount("TV", 104),
            FormatAmount("MOVIE", 21),
            FormatAmount("ONA", 6),
            FormatAmount("SPECIAL", 4)
        ),
        favouredYears = listOf(
            YearAmount(2006, 1, 72), YearAmount(2009, 1, 68), YearAmount(2011, 1, 81),
            YearAmount(2013, 2, 75), YearAmount(2014, 4, 88), YearAmount(2015, 3, 71),
            YearAmount(2016, 5, 90), YearAmount(2017, 2, 66), YearAmount(2018, 5, 84),
            YearAmount(2019, 9, 77), YearAmount(2020, 6, 92), YearAmount(2021, 12, 79),
            YearAmount(2022, 17, 85), YearAmount(2023, 20, 73), YearAmount(2024, 16, 88),
            YearAmount(2025, 12, 69), YearAmount(2026, 20, 82)
        ),
        favouredGenres = listOf(
            GenreAmount("Action", 96, 78, 38_040),
            GenreAmount("Fantasy", 74, 85, 27_210),
            GenreAmount("Adventure", 61, 82, 24_880),
            GenreAmount("Romance", 44, 88, 12_400),
            GenreAmount("Comedy", 39, 71, 9_120),
            GenreAmount("Drama", 33, 90, 14_600)
        ),
        favouredTags = listOf(
            TagAmount(MediaTag(id = 1, name = "Male Protagonist"), 88, 74),
            TagAmount(MediaTag(id = 2, name = "Female Protagonist"), 61, 81),
            TagAmount(MediaTag(id = 3, name = "Ensemble Cast"), 44, 86),
            TagAmount(MediaTag(id = 4, name = "Isekai"), 39, 68)
        )
    )

    /**
     * A year of daily activity at AniList's own intensity levels.
     *
     * Generated rather than written out because 365 literal lines would be
     * unreadable and untestable, and because the levels have to look plausible -
     * a heatmap where every day is level 1 is a demo of an empty screen.
     */
    private fun demoActivityHistory(): List<ActivityHistoryDay> {
        val today = java.time.LocalDate.now()
        return (364 downTo 0).map { daysBack ->
            val date = today.minusDays(daysBack.toLong())
            val amount = activityAmountFor(daysBack, date.dayOfWeek.value)
            ActivityHistoryDay(
                date = date.atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond().toInt(),
                amount = amount,
                // AniList's levels run 1 to 7.
                level = when {
                    amount == 0 -> 0
                    amount < 2 -> 1
                    amount < 5 -> 3
                    amount < 9 -> 5
                    else -> 7
                }
            )
        }
    }

    /** Weekends and the run-up to them are busier, which is what makes a grid legible. */
    private fun activityAmountFor(daysBack: Int, dayOfWeek: Int): Int {
        if (daysBack > 300 && daysBack % 3 != 0) return 0
        val weekdayBoost = if (dayOfWeek == 6 || dayOfWeek == 7) 2 else 0
        return (daysBack * 7 % 9) + weekdayBoost
    }

    private fun demoScoreDistribution() = listOf(
        ScoreAmount(50, 2), ScoreAmount(60, 5), ScoreAmount(70, 11), ScoreAmount(75, 6),
        ScoreAmount(80, 14), ScoreAmount(85, 9), ScoreAmount(90, 26), ScoreAmount(95, 12),
        ScoreAmount(100, 34)
    )

    private fun demoMediaFor(mediaId: Int): MediaNode = when (mediaId) {
        100465 -> MediaNode(
            id = 100465,
            type = "ANIME",
            title = MediaTitle(english = "Attack on Titan: The Roar of Awakening"),
            format = "MOVIE",
            episodes = 1,
            coverImage = MediaCoverImage(large = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx100465-ivKS0RqXg5dI.png")
        )
        142329 -> MediaNode(
            id = 142329,
            type = "ANIME",
            title = MediaTitle(english = "Demon Slayer: Entertainment District Arc"),
            format = "TV",
            episodes = 11,
            coverImage = MediaCoverImage(large = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx142329-3L1p5rR0kZ0H.jpg")
        )
        154587 -> MediaNode(
            id = 154587,
            type = "ANIME",
            title = MediaTitle(english = "Frieren: Beyond Journey's End"),
            format = "TV",
            episodes = 28,
            coverImage = MediaCoverImage(large = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx154587-n6onjReL2gwM.jpg")
        )
        113415 -> MediaNode(
            id = 113415,
            type = "ANIME",
            title = MediaTitle(english = "Jujutsu Kaisen"),
            format = "TV",
            episodes = 24,
            coverImage = MediaCoverImage(large = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx113415-Yf3QlMhCyczL.jpg")
        )
        20605 -> MediaNode(
            id = 20605,
            type = "ANIME",
            title = MediaTitle(english = "Spy x Family"),
            format = "TV",
            episodes = 25,
            coverImage = MediaCoverImage(large = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx20605-4RA8h8JiqdPU.jpg")
        )
        20958 -> MediaNode(
            id = 20958,
            type = "ANIME",
            title = MediaTitle(english = "Haibane Renmei"),
            format = "TV",
            episodes = 12,
            coverImage = MediaCoverImage(large = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx20958-HuFJyr54Mmir.jpg")
        )
        else -> MediaNode(
            id = 164212,
            type = "ANIME",
            title = MediaTitle(english = "Blue Box"),
            format = "TV",
            episodes = 25,
            coverImage = MediaCoverImage(large = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx164212-1yTxkLdGnfdO.png")
        )
    }

    private fun favourite(
        id: Int,
        title: String,
        format: String,
        status: String,
        score: Int,
        cover: String
    ): MediaNode = MediaNode(
        id = id,
        title = MediaTitle(english = title, romaji = title),
        format = format,
        status = status,
        averageScore = score,
        coverImage = MediaCoverImage(large = cover)
    )

    private fun character(id: Int, name: String, favourites: Int, image: String) =
        FavouriteCharacter(
            id = id,
            name = PersonName(full = name),
            image = PersonImage(large = image),
            favourites = favourites,
            siteUrl = "https://anilist.co/character/$id"
        )

    private fun staff(id: Int, name: String, roles: List<String>, favourites: Int, image: String) =
        FavouriteStaff(
            id = id,
            name = PersonName(full = name),
            image = PersonImage(large = image),
            primaryOccupations = roles,
            favourites = favourites,
            siteUrl = "https://anilist.co/staff/$id"
        )
}