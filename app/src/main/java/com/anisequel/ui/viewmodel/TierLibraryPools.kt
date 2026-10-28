package com.anisequel.ui.viewmodel

import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.isManga
import com.anisequel.data.model.studioCredits
import com.anisequel.data.repository.AniListRepository
import com.anisequel.domain.tier.TierCandidate
import com.anisequel.domain.tier.TierMediaType
import com.anisequel.domain.tier.ofMediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The two pools a tier list can rank: the anime half of the account and the manga
 * half.
 *
 * ## Why this is not part of the ViewModel
 *
 * It was, and it pushed `TierListViewModel` past the file ceiling in AGENTS §1. The
 * split is by responsibility rather than by line count: this type answers one
 * question - *what titles can be ranked, and which half are they from* - while the
 * ViewModel answers a different one, *which saved list is open and what is in it*.
 * The two only meet in `TierListViewModel.candidates`, which picks a pool by the
 * open document's media type.
 *
 * ## Why there are two nullable pools rather than one list
 *
 * AniList numbers anime and manga independently, so media id 1 is both *Cowboy
 * Bebop* and *Monster*. A single list would key two titles under one number, and
 * whichever placement was written second would silently replace the first. Null
 * means "not asked yet" and an empty list means "asked, and there is none": that
 * distinction is what lets the manga half be fetched the first time its shelf is
 * opened without an anime response that happened to contain no manga marking the
 * manga list as already-known-and-empty.
 */
internal class TierLibraryPools(
    private val aniListRepository: AniListRepository,
    private val scope: CoroutineScope,
    private val userId: Int?,
    private val isDemo: Boolean
) {
    /** The anime half, or null before it has been asked for. */
    private val _anime = MutableStateFlow<List<TierCandidate>?>(null)
    val anime: StateFlow<List<TierCandidate>?> = _anime.asStateFlow()

    /** The manga half, or null before it has been asked for. */
    private val _manga = MutableStateFlow<List<TierCandidate>?>(null)
    val manga: StateFlow<List<TierCandidate>?> = _manga.asStateFlow()

    private val _isLoadingManga = MutableStateFlow(false)
    val isLoadingManga: StateFlow<Boolean> = _isLoadingManga.asStateFlow()

    /**
     * True when the last manga fetch failed.
     *
     * Left separate from the null pool because a failure has to be a retry rather
     * than "this account tracks no manga": a rate-limited request is not an empty
     * list, and the person needs a tap that asks again.
     */
    private val _mangaLoadFailed = MutableStateFlow(false)
    val mangaLoadFailed: StateFlow<Boolean> = _mangaLoadFailed.asStateFlow()

    /**
     * Whether this account has a manga list to rank at all.
     *
     * False in demo mode, where there is no account behind the screen: the manga
     * section says so rather than pretending the list is still arriving. A
     * signed-out viewer is the same answer for the same reason.
     */
    val canLoadManga: Boolean get() = !isDemo && userId != null

    /**
     * Reads the viewer's anime list, then hands it to [setLibrary].
     *
     * The repository caches the list under `user:<id>`, so this is a **cache hit**
     * for anyone who has already opened their profile in this session and costs no
     * request at all. Fetching it again anyway would spend one of AniList's
     * 30-a-minute budget to re-read a list the app is already looking at.
     *
     * The candidates are the *library*, not a list: they are shared by every tier
     * list the person makes of that type, so this runs once per visit rather than
     * per list. Manga is deliberately not fetched here - see [loadManga].
     */
    fun loadAnime() {
        val id = userId
        if (id == null && !isDemo) return
        if (_anime.value != null) return
        scope.launch {
            val result = if (isDemo) {
                Result.success(aniListRepository.getDemoAnimeList())
            } else {
                aniListRepository.getUserAnimeList(id!!, forceRefresh = false)
            }
            result.onSuccess { collection ->
                setLibrary(
                    collection.lists.orEmpty().flatMap { it.entries.orEmpty() },
                    TierMediaType.ANIME
                )
            }
        }
    }

    /**
     * Reads the viewer's manga list, once, when the manga section is first opened.
     *
     * Lazy on purpose. The Library page already documents the same rule: the anime
     * list is fetched because the Stats tab tallies it, while manga is a second
     * request against AniList's 30-a-minute budget that half the visits never look
     * at. The repository caches it under its own key, so a second visit in the
     * same session is free.
     */
    fun loadManga() {
        if (!canLoadManga) return
        if (_manga.value != null || _isLoadingManga.value) return
        val id = userId ?: return

        _isLoadingManga.value = true
        _mangaLoadFailed.value = false
        scope.launch {
            aniListRepository.getUserMangaList(id, forceRefresh = false).fold(
                onSuccess = { collection ->
                    setLibrary(
                        collection.lists.orEmpty().flatMap { it.entries.orEmpty() },
                        TierMediaType.MANGA
                    )
                },
                // Left null so the section is still a retry. The failure is
                // reported separately, because "asked and there is none" and
                // "could not ask" are different answers to the person looking at
                // an empty shelf.
                onFailure = { _mangaLoadFailed.value = true }
            )
            _isLoadingManga.value = false
        }
    }

    /**
     * Hands one half of the person's library to the pool it belongs to.
     *
     * Called with the entries the repository already holds rather than reading for
     * them here: re-reading the list would spend one of AniList's 30-a-minute
     * requests to obtain data the app is already looking at.
     *
     * [requestedType] is the query that produced the answer. AniList returns the
     * media's own `type` as well, and that wins when it is present - a response
     * that crossed namespaces must not file a manga under the anime pool. When it
     * is absent (the demo list, and a cache written before the field was asked
     * for) the query is the only evidence there is.
     */
    private fun setLibrary(entries: List<MediaListEntryItem>, requestedType: TierMediaType) {
        val mapped = entries.mapNotNull { entry ->
            val media = entry.media
            val title = media.title?.displayTitle?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            TierCandidate(
                mediaId = media.id,
                title = title,
                coverUrl = media.coverImage?.bestUrl,
                averageScore = media.averageScore,
                // Two different statuses on purpose: `entry.status` is where the
                // viewer put it, `media.status` is whether it is still airing.
                // The filter asks about both and they are not interchangeable.
                listStatus = entry.status,
                airingStatus = media.status,
                // Empty rather than null on both: a list snapshot written before
                // the query asked for these fields has neither, and the filter is
                // the reader that must not crash on an older cache.
                genres = media.genres.orEmpty()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct(),
                // Main animation studios only. `studioCredits` already falls back
                // to the flat `nodes` shape for payloads that carry no edges, and
                // marks those main, which is what keeps an old list response
                // filterable instead of silently studio-less.
                studios = media.studioCredits
                    .filter { it.isMain }
                    .map { it.name.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct(),
                // Every name AniList has for the title, so the name filter finds
                // "Attack on Titan" from `shingeki` and the reverse. The caption
                // is still `title` alone; these exist only to be searched, which
                // is what keeps a poster from growing a second line of aliases.
                alternativeTitles = listOfNotNull(
                    media.title?.romaji,
                    media.title?.english,
                    media.title?.native
                ),
                mediaType = media.mediaTypeOr(requestedType)
            )
        }

        // Only the half that was asked for is replaced. Writing both from one
        // response would mark the other half as "asked, and empty" whenever the
        // query happened to return nothing of that type - and the lazy manga
        // fetch would then never run.
        when (requestedType) {
            TierMediaType.ANIME -> _anime.value = mapped.ofMediaType(TierMediaType.ANIME)
            TierMediaType.MANGA -> _manga.value = mapped.ofMediaType(TierMediaType.MANGA)
        }
    }

    /** One half of the library as it currently stands, or empty before it arrives. */
    fun poolFor(mediaType: TierMediaType): List<TierCandidate> = when (mediaType) {
        TierMediaType.ANIME -> _anime.value
        TierMediaType.MANGA -> _manga.value
    }.orEmpty()

    /**
     * The media's own type, or the query's when the payload does not carry one.
     *
     * The demo list and list snapshots written before the query asked for `type`
     * are the two cases that fall back. A payload that carries the *other* type
     * wins over the query: it is evidence the response crossed namespaces, and
     * filing it under the query would put a manga in the anime pool.
     */
    private fun MediaNode.mediaTypeOr(fallback: TierMediaType): TierMediaType = when {
        type.isNullOrBlank() -> fallback
        isManga -> TierMediaType.MANGA
        else -> TierMediaType.ANIME
    }
}
