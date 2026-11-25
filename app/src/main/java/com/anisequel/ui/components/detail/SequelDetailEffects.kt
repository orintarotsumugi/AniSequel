package com.anisequel.ui.components.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.posterUrl
import com.anisequel.data.repository.CoverColorRepository
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.domain.color.ArgbHex
import com.anisequel.domain.ui.PosterSource
import com.anisequel.domain.ui.SequelSeason
import com.anisequel.ui.components.cards.LocalBetterPosters
import com.anisequel.ui.components.cards.LocalPosterOverlays
import com.anisequel.ui.components.cards.LocalPosterSource
import com.anisequel.ui.components.cards.LocalPosterUniqueSeasons
import com.anisequel.ui.components.cards.posterLookupTitle
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.rememberIsResumed
import com.anisequel.ui.theme.CoverColors
import com.anisequel.ui.viewmodel.MediaDetailStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Extracts the on-screen cover's palette and, when the anime layer is on, writes
 * the page's colour from it.
 *
 * This used to be inline in [SequelDetailSheet] and read the preferences during
 * the first composition, before DataStore had loaded. A sheet opened in that
 * window saw the feature as off and never re-ran, which is why recolouring
 * appeared to work only sometimes. Keying on `isLoaded` keeps the effect inert
 * until the stored answer is real.
 *
 * The same seed is extracted even with the anime layer off: the hero's backdrop
 * and shadow use it whether or not the global recolour is enabled.
 */
@Composable
internal fun DetailPaletteEffect(
    subject: AnimeDetailSubject,
    media: MediaNode,
    themeSettings: ThemePreferences.ThemeSettings,
    themePreferences: ThemePreferences
) {
    val context = LocalContext.current
    val posterSource = LocalPosterSource.current
    val posterOverlays = LocalPosterOverlays.current
    val betterPosters = LocalBetterPosters.current
    val uniqueSeasons = LocalPosterUniqueSeasons.current

    LaunchedEffect(
        subject.mediaId,
        subject.media.type,
        media.posterUrl,
        posterSource,
        posterOverlays,
        uniqueSeasons,
        themeSettings.isLoaded,
        themeSettings.animeThemeEnabled,
        themeSettings.animeThemeRetainColor
    ) {
        if (!themeSettings.isLoaded) return@LaunchedEffect

        // The picture on screen, resolved the same way `cardPosterUrl` resolves it:
        // the same source, the same season rule, the same cache. A sequel season
        // keeps its own cover, so the sheet must not read a palette from a btttr
        // poster the grid is not drawing.
        val useBetterPoster = posterSource == PosterSource.BETTER_POSTERS &&
            !(uniqueSeasons && SequelSeason.isSequel(posterLookupTitle(media)))
        val shownPoster = if (useBetterPoster) {
            betterPosters?.posterUrl(
                subject.mediaId,
                posterOverlays,
                skipSequels = uniqueSeasons
            ) ?: media.posterUrl
        } else {
            media.posterUrl
        }
        val palette = CoverColorRepository.extractPalette(context, shownPoster)
        val seed = palette?.seed
        if (palette != null) {
            // Both keys, because the surfaces read different ones: the sheet knows
            // the resolved URL, and the hero still falls back to the AniList cover.
            CoverColors.put(shownPoster, palette.seed)
            CoverColors.put(media.posterUrl, palette.seed)
            palette.accent?.let { accent ->
                CoverColors.putAccent(shownPoster, accent)
                CoverColors.putAccent(media.posterUrl, accent)
            }
        }

        if (!themeSettings.animeThemeEnabled) return@LaunchedEffect

        // Most specific first: the cover's own pixels, then AniList's dominant
        // colour, then the brand. A grey cover produces no seed, and the page
        // should still recolour to something the reader recognises.
        val colorHex = seed?.let { ArgbHex.format(it) }
            ?: media.posterColor
            ?: "#0057B7"

        themeWrite {
            themePreferences.applyAnimeColorLayer(
                colorHex = colorHex,
                retain = themeSettings.animeThemeRetainColor
            )
        }
    }
}

/**
 * Loads the detail and its friends, then keeps the open title fresh while the
 * window is resumed.
 *
 * The ticker stops with the window. A sheet left open behind another app is still
 * composed, and this loop used to keep requesting every ten minutes for a page
 * nobody could see. The first load is deliberately not gated: the page needs its
 * answer the moment it comes back.
 */
@Composable
internal fun DetailRefreshEffect(
    mediaId: Int,
    mediaType: String?,
    onLoadDetail: (Int, String?) -> Unit,
    onLoadFriends: (Int, String?) -> Unit
) {
    val resumed = rememberIsResumed()
    LaunchedEffect(mediaId, mediaType, resumed) {
        onLoadDetail(mediaId, mediaType)
        onLoadFriends(mediaId, mediaType)
        if (!resumed) return@LaunchedEffect

        while (true) {
            delay(MediaDetailStore.DETAIL_REFRESH_INTERVAL_MILLIS)
            onLoadDetail(mediaId, mediaType)
        }
    }
}

/** The entry overlay's open state and drag settle, hoisted out of the sheet. */
internal class DetailEntrySheetMotion(
    val show: Boolean,
    val onShowChange: (Boolean) -> Unit,
    val offset: Animatable<Float, AnimationVector1D>,
    val dragging: Boolean,
    val onDraggingChange: (Boolean) -> Unit,
    val dragFraction: Float,
    val onDragFractionChange: (Float) -> Unit,
    val close: () -> Unit
)

/**
 * Owns the entry overlay's slide. Kept beside [SequelDetailSheet] rather than
 * inside it so the sheet's own composition is the page and the effects, not the
 * overlay's pointer state as well.
 */
@Composable
internal fun rememberDetailEntrySheetMotion(
    mediaId: Int,
    mediaType: String?,
    entryEditor: EntryEditor?,
    editState: EntryEditState,
    scope: CoroutineScope
): DetailEntrySheetMotion {
    var showEntrySheet by rememberSaveable(mediaId, mediaType) { mutableStateOf(false) }
    val sheetOffset = remember(mediaId, mediaType) { Animatable(1f) }
    var sheetDragging by remember(mediaId, mediaType) { mutableStateOf(false) }
    var dragFraction by remember(mediaId, mediaType) { mutableFloatStateOf(1f) }

    val close: () -> Unit = {
        scope.launch {
            sheetOffset.animateTo(1f, ExpressiveMotion.SettleSpatial)
            showEntrySheet = false
        }
    }
    LaunchedEffect(showEntrySheet) {
        if (showEntrySheet) {
            // Opened at full height, not a peek: the form is longer than a peek
            // suggests, and a sheet that stopped at 60% hid every field below Score
            // behind a scroll nothing advertised.
            sheetDragging = false
            sheetOffset.snapTo(1f)
            sheetOffset.animateTo(0f, ExpressiveMotion.DefaultSpatial)
        } else {
            sheetDragging = false
            sheetOffset.snapTo(1f)
        }
    }

    // Opening My Entry is also the moment to retry an entry load that never landed.
    // `load` is a no-op once a draft exists, so this cannot spend a second request
    // on a healthy form.
    LaunchedEffect(showEntrySheet, mediaId, mediaType, entryEditor) {
        if (showEntrySheet && entryEditor != null && editState.loadError != null) {
            editState.load(entryEditor, mediaId)
        }
    }

    return DetailEntrySheetMotion(
        show = showEntrySheet,
        onShowChange = { showEntrySheet = it },
        offset = sheetOffset,
        dragging = sheetDragging,
        onDraggingChange = { sheetDragging = it },
        dragFraction = dragFraction,
        onDragFractionChange = { dragFraction = it },
        close = close
    )
}
