package com.anisequel

import com.anisequel.data.model.MediaListCollection
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.model.MediaListGroup
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MediaTitle
import com.anisequel.data.model.SimpleMediaListEntry
import com.anisequel.data.model.ViewerProfile
import com.anisequel.data.repository.AniListRepository
import com.anisequel.domain.tier.TierMediaType
import com.anisequel.ui.viewmodel.TierLibraryPools
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the tier library pools: loading, failure/retry state, and candidate
 * shaping for the anime and manga shelves.
 *
 * These pools are the boundary between AniList responses and the tier UI. A bug
 * here is silent in the best case (a spinner that never stops, a retry that never
 * fires) and a crash in the worst (duplicate lazy-list keys). The tests use a
 * minimal fake repository and a test scope so each state transition is observed
 * directly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TierLibraryPoolsTest {

    @Test
    fun `a failed anime load is reported and can be retried`() = runTest {
        val repo = FakeTierRepository(
            animeResults = ArrayDeque(
                listOf(
                    Result.failure(Exception("rate limited")),
                    Result.success(collectionOf(entry(id = 1, title = "Show A")))
                )
            )
        )
        val pools = TierLibraryPools(repo, this, userId = 7, isDemo = false)

        pools.loadAnime()
        advanceUntilIdle()

        assertNull("a failed fetch must not publish a pool", pools.anime.value)
        assertTrue(pools.animeLoadFailed.value)
        assertFalse(pools.isLoadingAnime.value)

        pools.loadAnime()
        advanceUntilIdle()

        assertFalse(pools.animeLoadFailed.value)
        assertEquals(listOf(1), pools.anime.value?.map { it.mediaId })
    }

    @Test
    fun `a failed manga load is reported, clears loading, and can be retried`() = runTest {
        val repo = FakeTierRepository(
            mangaResults = ArrayDeque(
                listOf(
                    Result.failure(Exception("network")),
                    Result.success(collectionOf(entry(id = 10, title = "Manga A", type = "MANGA")))
                )
            )
        )
        val pools = TierLibraryPools(repo, this, userId = 7, isDemo = false)

        pools.loadManga()
        advanceUntilIdle()

        assertNull(pools.manga.value)
        assertTrue(pools.mangaLoadFailed.value)
        assertFalse(pools.isLoadingManga.value)

        pools.loadManga()
        advanceUntilIdle()

        assertFalse(pools.mangaLoadFailed.value)
        assertEquals(listOf(10), pools.manga.value?.map { it.mediaId })
    }

    @Test
    fun `duplicate entries across list groups are collapsed by media id`() = runTest {
        val duplicate = entry(id = 5, title = "Repeated")
        val repo = FakeTierRepository(
            animeResults = ArrayDeque(
                listOf(
                    Result.success(
                        MediaListCollection(
                            lists = listOf(
                                MediaListGroup(name = "Watching", entries = listOf(duplicate)),
                                MediaListGroup(name = "Completed", entries = listOf(duplicate, entry(id = 6, title = "Other")))
                            )
                        )
                    )
                )
            )
        )
        val pools = TierLibraryPools(repo, this, userId = 7, isDemo = false)

        pools.loadAnime()
        advanceUntilIdle()

        assertEquals(
            "duplicates would create duplicate lazy keys and repeated titles",
            listOf(5, 6),
            pools.anime.value?.map { it.mediaId }
        )
    }

    @Test
    fun `demo mode can load anime but cannot load manga`() = runTest {
        val repo = FakeTierRepository(
            demoAnime = collectionOf(entry(id = 1, title = "Demo"))
        )
        val pools = TierLibraryPools(repo, this, userId = null, isDemo = true)

        assertTrue(pools.canLoadAnime)
        assertFalse(pools.canLoadManga)

        pools.loadAnime()
        advanceUntilIdle()
        assertEquals(listOf(1), pools.anime.value?.map { it.mediaId })

        pools.loadManga()
        advanceUntilIdle()
        assertNull("demo mode has no manga pool to fetch", pools.manga.value)
        assertFalse(pools.isLoadingManga.value)
        assertFalse(pools.mangaLoadFailed.value)
    }

    private fun collectionOf(vararg entries: MediaListEntryItem): MediaListCollection =
        MediaListCollection(lists = listOf(MediaListGroup(entries = entries.toList())))

    private fun entry(id: Int, title: String, type: String = "ANIME"): MediaListEntryItem =
        MediaListEntryItem(
            media = MediaNode(
                id = id,
                title = MediaTitle(english = title),
                type = type
            )
        )

    private class FakeTierRepository(
        private val animeResults: ArrayDeque<Result<MediaListCollection>> = ArrayDeque(),
        private val mangaResults: ArrayDeque<Result<MediaListCollection>> = ArrayDeque(),
        private val demoAnime: MediaListCollection = MediaListCollection()
    ) : AniListRepository {
        override suspend fun getViewer(): Result<ViewerProfile> = unused()
        override suspend fun getUserByName(userName: String): Result<ViewerProfile> = unused()
        override suspend fun getUserAnimeList(userId: Int, forceRefresh: Boolean): Result<MediaListCollection> =
            if (animeResults.isEmpty()) unused() else animeResults.removeFirst()
        override suspend fun getUserAnimeListByUsername(userName: String, forceRefresh: Boolean): Result<MediaListCollection> = unused()
        override suspend fun getUserMangaList(userId: Int, forceRefresh: Boolean): Result<MediaListCollection> =
            if (mangaResults.isEmpty()) unused() else mangaResults.removeFirst()
        override suspend fun getMediaDetail(mediaId: Int, mediaType: String?): Result<MediaNode> = unused()
        override suspend fun addToPlanning(mediaId: Int): Result<SimpleMediaListEntry> = unused()
        override fun getDemoProfile(): ViewerProfile = unused()
        override fun getDemoAnimeList(): MediaListCollection = demoAnime

        private fun <T> unused(): T = error("unused in TierLibraryPoolsTest")
    }
}
