package com.anisequel.ui.components.detail.info

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.displayName
import com.anisequel.data.model.englishName
import com.anisequel.data.model.formatLabel
import com.anisequel.data.model.isManga
import com.anisequel.data.model.pageUrl
import com.anisequel.data.model.posterColor
import com.anisequel.data.model.posterUrl
import com.anisequel.data.model.progressCountLabel
import com.anisequel.data.model.romajiName
import com.anisequel.data.model.statusLabel
import com.anisequel.data.model.studioCredits
import com.anisequel.data.repository.InfoBackdropSource
import com.anisequel.domain.list.ListStatuses
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.detail.HERO_POSTER_ELEVATION
import com.anisequel.ui.components.detail.LocalDetailCoverReveal
import com.anisequel.ui.components.detail.LocalDetailCoverTarget
import com.anisequel.ui.components.cards.cardPosterUrl
import com.anisequel.ui.components.cards.toCoverColorOrNull
import com.anisequel.ui.components.cards.unseamedCover
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.expressive.expressiveHoldGesture
import com.anisequel.ui.components.openExternalUrlOrCopy
import com.anisequel.ui.theme.CoverColors
import com.anisequel.ui.theme.DynamicThemeBuilder
import com.anisequel.ui.theme.LocalAppDarkTheme
import com.anisequel.ui.theme.LocalImmersiveMode
import com.anisequel.ui.theme.asHeroTitle
import com.anisequel.ui.theme.immersiveDisplayInsets
import kotlinx.coroutines.delay

/**
 * The Nova title page's hero: the key art, the poster and the one control that
 * matters.
 *
 * ## Where this departs from the classic hero
 *
 * The classic hero is a 340dp banner with a poster sitting on its lower edge and
 * the title beside it, and it *collapses*: the lockup fades as the page scrolls
 * and a docked bar takes over. That page is still the shipped one, and this does
 * not replace it - see [com.anisequel.domain.ui.InfoPageStyle].
 *
 * The redesign follows the reference screenshots instead: the artwork runs from
 * under the status bar to the first card, the poster and title are a lockup near
 * the bottom, and the status is not a chip but a full-width capsule the reader can
 * press. The layout is a different *reading order*: art, then identity, then "where
 * am I in this", then the numbers - all before the first scroll.
 *
 * ## Why the artwork is sharp here and blurred under the page
 *
 * The page backdrop is the reader's blur choice; the hero is the one place the
 * title's own picture is the subject, so it stays sharp and the gradient does the
 * legibility work. The same rule the classic hero adopted after its 26dp blur was
 * removed: a hero is what a reader decides a title looks like.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NovaInfoHero(
    media: MediaNode,
    relationLabel: String?,
    collapse: () -> Float,
    onOpenCover: (Rect?) -> Unit,
    onClose: () -> Unit,
    onOpenAppearance: (() -> Unit)?,
    onEditEntry: (() -> Unit)?,
    canWriteToAniList: Boolean,
    onOpenStudio: (Int, String?) -> Unit,
    backdropSource: InfoBackdropSource,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val immersive = LocalImmersiveMode.current
    var posterBounds by remember(media.id, media.type) { mutableStateOf<Rect?>(null) }
    // The classic hero's hold-to-copy shortcut, kept: the title is the one thing a
    // reader takes out of this page, and the full name sheet in Extras is the
    // discoverable path rather than a replacement for the gesture.
    // Keyed on the type as well as the id, like `posterBounds` above: following a
    // relation across the anime/manga boundary reaches a different title that can
    // share this number, and the left-behind confirmation must not follow it.
    var titleCopied by remember(media.id, media.type) { mutableStateOf(false) }
    // `expressiveHoldGesture` keeps the first lambda it saw; the sheet can swap
    // the list node for the detail node under a live hero, so read the title
    // through state rather than capturing it.
    val currentTitle = rememberUpdatedState(media.displayName)
    LaunchedEffect(titleCopied) {
        if (!titleCopied) return@LaunchedEffect
        delay(COPY_CONFIRMATION_MILLIS)
        titleCopied = false
    }
    val openCover: () -> Unit = { onOpenCover(posterBounds) }
    // The backdrop's tap target draws no ripple - inking a rectangle over the
    // artwork is the thing the classic hero deliberately avoids - but it still
    // needs an interaction source to be a well-formed clickable.
    val backdropInteraction = remember { MutableInteractionSource() }

    // The same artwork the grid draws when BetterPosters is chosen. The hero is a
    // card in that sense: opening a title from a poster should not hand the reader
    // a different picture at the top.
    val poster = cardPosterUrl(media) ?: media.posterUrl
    val banner = media.bannerImage
        ?.takeIf { backdropSource == InfoBackdropSource.BANNER && it.isNotBlank() }
    val artwork = banner ?: poster

    // The cover's own colour, held back toward the sheet's surface. It is the
    // placeholder before the image decodes, the poster's shadow, and the wash the
    // bottom scrim lands on - one colour from one picture, as in the classic hero.
    val coverSeed = CoverColors.color(poster)
        ?: CoverColors.color(media.posterUrl)
        ?: media.posterColor.toCoverColorOrNull()
    val dark = LocalAppDarkTheme.current
    val coverFallback = MaterialTheme.colorScheme.surfaceContainerHigh
    val coverColor = remember(coverSeed, dark, coverFallback) {
        coverSeed?.let { DynamicThemeBuilder.accentColor(it, dark = dark) } ?: coverFallback
    }
    val surface = MaterialTheme.colorScheme.surface

    val altTitle = media.romajiName?.takeIf { it != media.displayName }
        ?: media.englishName?.takeIf { it != media.displayName }
    val status = media.mediaListEntry?.status
    val statusLabel = status?.let { ListStatuses.label(it, media.isManga) }
    val progressLabel = media.progressCountLabel
    val entryText = listOfNotNull(statusLabel, progressLabel).joinToString(" · ")
    val entryEditable = status != null && canWriteToAniList && onEditEntry != null

    Box(
        modifier = modifier
            .fillMaxWidth()
            // `heightIn`, not `height`: a 420dp hero is right at the default type
            // scale, and a reader at 200% needs the lockup to grow past it rather
            // than clipping the title. The artwork is a background, so the box
            // growing costs nothing.
            .heightIn(min = HERO_MIN_HEIGHT)
            // The artwork is over-covered and translated for the parallax, and
            // `graphicsLayer` does not clip: at the end of a scroll its bottom edge
            // sat up to ~47dp below the hero's item, drawing a strip of the banner
            // at the hero's own zoom into the gap above the first card - artwork
            // that belonged to the hero appearing to be part of the page. The hero
            // is the picture's frame, so the picture is clipped to it.
            .clipToBounds()
            .background(coverColor)
    ) {
        if (artwork != null) {
            AsyncImage(
                model = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Read in the draw phase: the hero scrolls at 120Hz and
                        // nothing here should recompose to follow it.
                        translationY = collapse() * ART_PARALLAX.toPx()
                        scaleX = ART_SCALE
                        scaleY = ART_SCALE
                    }
                    .unseamedCover()
            )
        }

        // The whole picture is the way into the full-screen artwork. The poster
        // keeps its own hold gesture; this layer only claims the art behind it.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = backdropInteraction,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = "Open the full-screen artwork",
                    onClick = openCover
                )
        )

        // The status bar's own short scrim. A separate stop rather than a stronger
        // top on the gradient below: darkening the whole upper fifth would turn the
        // picture into a tinted panel.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.46f),
                        0.16f to Color.Transparent
                    )
                )
        )

        // The hand-off to the page. It lands on `surface`, which is what
        // `InfoPageBackdrop` draws, so the first card begins on the same colour the
        // artwork ends on.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.36f to surface.copy(alpha = 0.22f),
                        0.62f to surface.copy(alpha = 0.72f),
                        0.84f to surface.copy(alpha = 0.96f),
                        1f to surface
                    )
                )
        )

        HeroTopControls(
            immersive = immersive,
            onClose = onClose,
            onSearch = { openExternalUrlOrCopy(context, webSearchUrl(media)) },
            onOpenAniList = { openExternalUrlOrCopy(context, media.pageUrl) },
            onOpenAppearance = onOpenAppearance
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
                .sectionReveal(0, entranceKey = media.id to media.type),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HeroLockup(
                media = media,
                relationLabel = relationLabel,
                poster = poster,
                coverColor = coverColor,
                altTitle = altTitle,
                statusLabel = statusLabel,
                onOpenCover = openCover,
                onPosterBounds = { posterBounds = it },
                onCopyTitle = {
                    copyTitle(context, currentTitle.value)
                    titleCopied = true
                },
                onOpenStudio = onOpenStudio
            )

            // The one control the reference puts above the fold. A tracked title
            // reads its status and progress here and one tap opens the editor; an
            // untracked one gets the planning row in the first card below instead,
            // because that row is a *choice* rather than a state.
            if (entryText.isNotBlank()) {
                HeroEntryPill(
                    text = entryText,
                    editable = entryEditable,
                    onClick = { onEditEntry?.invoke() }
                )
            }
        }

        HeroCopyConfirmation(visible = titleCopied)

    }
}


@Composable
private fun BoxScope.HeroTopControls(
    immersive: Boolean,
    onClose: () -> Unit,
    onSearch: () -> Unit,
    onOpenAniList: () -> Unit,
    onOpenAppearance: (() -> Unit)?
) {
    Surface(
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.44f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
        shadowElevation = 6.dp,
        modifier = Modifier
            .align(Alignment.TopStart)
            .statusBarsPadding()
            .immersiveDisplayInsets(immersive)
            .padding(12.dp)
            .testTag("nova_detail_back")
    ) {
        HeroIconButton(
            icon = AppVectorIcons.Close,
            label = "Close",
            onClick = onClose
        )
    }

    Row(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .statusBarsPadding()
            .immersiveDisplayInsets(immersive)
            .padding(12.dp)
    ) {
        Surface(
            shape = ExpressiveShapes.pill,
            color = Color.Black.copy(alpha = 0.42f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
            shadowElevation = 8.dp,
            modifier = Modifier.testTag("nova_detail_hero_actions")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeroIconButton(
                    icon = AppVectorIcons.Search,
                    label = "Search the web",
                    onClick = onSearch
                )
                HeroDivider()
                HeroIconButton(
                    icon = AppVectorIcons.OpenInBrowser,
                    label = "Open on AniList",
                    onClick = onOpenAniList
                )
                if (onOpenAppearance != null) {
                    HeroDivider()
                    HeroIconButton(
                        icon = AppVectorIcons.SectionAppearance,
                        label = "Page appearance",
                        onClick = onOpenAppearance
                    )
                }
            }
        }
    }
}

private fun webSearchUrl(media: MediaNode): String {
    val kind = if (media.isManga) "manga" else "anime"
    val query = media.title?.displayTitle.orEmpty().ifBlank { media.id.toString() }
    return "https://duckduckgo.com/?q=" + Uri.encode("$query $kind")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroLockup(
    media: MediaNode,
    relationLabel: String?,
    poster: String?,
    coverColor: Color,
    altTitle: String?,
    statusLabel: String?,
    onOpenCover: () -> Unit,
    onPosterBounds: (Rect?) -> Unit,
    onCopyTitle: () -> Unit,
    onOpenStudio: (Int, String?) -> Unit
) {
    val coverReveal = LocalDetailCoverReveal.current
    val coverTarget = LocalDetailCoverTarget.current
    val posterShape = MaterialTheme.shapes.large
    Row(verticalAlignment = Alignment.Bottom) {
        val posterInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .width(116.dp)
                .height(174.dp)
                // The page's poster steps aside while the copy is in flight - the
                // two are the same picture at the same rectangle, so the hand-off
                // is a swap between identical pixels. Read in the draw phase so a
                // flight does not recompose the hero.
                .graphicsLayer { alpha = coverReveal() }
                .bouncyPress(pressedScale = 0.97f, interactionSource = posterInteraction)
                .shadow(
                    elevation = HERO_POSTER_ELEVATION,
                    shape = posterShape,
                    ambientColor = coverColor,
                    spotColor = Color.Black
                )
                .clip(posterShape)
                .border(
                    1.dp,
                    Color.White.copy(alpha = 0.24f),
                    posterShape
                )
                .background(coverColor)
                .onGloballyPositioned {
                    val rect = it.boundsInWindow()
                    onPosterBounds(rect)
                    // Where the copy lands. Plain fields in the holder, not state:
                    // this runs on every scroll frame.
                    coverTarget?.record(rect, posterShape, coverColor, HERO_POSTER_ELEVATION)
                }
                .expressiveHoldGesture(onHold = onOpenCover, onClick = onOpenCover)
                .semantics {
                    role = Role.Button
                    contentDescription = media.displayName
                    onClick(label = "View full artwork") {
                        onOpenCover()
                        true
                    }
                }
                .testTag("nova_detail_hero_poster")
        ) {
            AsyncImage(
                model = poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .unseamedCover()
            )
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.58f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(7.dp)
            ) {
                Icon(
                    imageVector = AppVectorIcons.FullscreenExpand,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .padding(5.dp)
                        .size(15.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // The title used to be white text with a rectangular drop shadow,
            // directly over whatever the key art happened to be. On a bright sky
            // or a white shirt the name disappeared, and the "shadow" read as a
            // grey box behind it. This is the opposite treatment: a quiet scrim
            // whose tone is chosen against the cover colour, so dark art gets a
            // light panel and bright art gets a dark one. The panel is the
            // contrast, not a second decoration on the text.
            val titleOnLight = coverColor.luminance() < 0.45f
            val titleBackdrop = if (titleOnLight) {
                Color.White.copy(alpha = 0.82f)
            } else {
                Color.Black.copy(alpha = 0.62f)
            }
            val titleInk = if (titleOnLight) Color.Black else Color.White
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = titleBackdrop,
                contentColor = titleInk,
                border = BorderStroke(1.dp, titleInk.copy(alpha = 0.10f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = media.displayName,
                        style = MaterialTheme.typography.headlineSmall.asHeroTitle(),
                        color = titleInk,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .expressiveHoldGesture(onHold = onCopyTitle)
                            .semantics {
                                onLongClick(label = "Copy title") {
                                    onCopyTitle()
                                    true
                                }
                            }
                            .testTag("nova_detail_hero_title")
                    )
                    if (altTitle != null) {
                        Text(
                            text = altTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = titleInk.copy(alpha = 0.76f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = relationLabel != null || statusLabel != null || media.formatLabel.isNotBlank(),
                // Expanded with the fade, not faded in place. The strip is absent
                // on the summary node and arrives with the detail, so a fade-only
                // entrance drew the chips at full height from the first frame and
                // the hero's lockup jumped down by a chip row the instant the
                // answer landed - under the reader's eye, while the stagger was
                // still running.
                enter = fadeIn(animationSpec = ExpressiveMotion.FastEffects) +
                    expandVertically(animationSpec = ExpressiveMotion.FastSpatialSize),
                exit = fadeOut(animationSpec = ExpressiveMotion.FastEffects) +
                    shrinkVertically(animationSpec = ExpressiveMotion.FastSpatialSize)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (relationLabel != null) {
                        HeroChip(
                            icon = AppVectorIcons.FranchiseBranch,
                            label = relationLabel
                        )
                    }
                    if (statusLabel != null) {
                        HeroChip(
                            icon = AppVectorIcons.Info,
                            label = statusLabel.uppercase(java.util.Locale.ROOT),
                            accent = true
                        )
                    }
                    // The medium before the format: "TV" beside "MANGA" would
                    // otherwise describe the same title as two different things.
                    HeroChip(
                        icon = if (media.isManga) {
                            AppVectorIcons.SourceBook
                        } else {
                            AppVectorIcons.Tv
                        },
                        label = media.formatLabel.uppercase(java.util.Locale.ROOT)
                    )
                    media.studioCredits.firstOrNull()?.let { studio ->
                        HeroChip(
                            icon = AppVectorIcons.Studio,
                            label = studio.name,
                            onClick = studio.id?.let { id ->
                                { onOpenStudio(id, studio.name) }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroEntryPill(
    text: String,
    editable: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape = ExpressiveShapes.pill,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(
                if (editable) {
                    Modifier
                        .bouncyPress(pressedScale = 0.97f, interactionSource = interactionSource)
                        .clickable(
                            interactionSource = interactionSource,
                            role = Role.Button,
                            onClickLabel = "Edit your AniList entry",
                            onClick = onClick
                        )
                } else {
                    Modifier
                }
            )
            .testTag("nova_info_entry_pill")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Crossfade(
                targetState = editable,
                animationSpec = ExpressiveMotion.FastEffects,
                label = "nova_entry_pill_icon"
            ) { canEdit ->
                Icon(
                    imageVector = if (canEdit) {
                        AppVectorIcons.EditEntry
                    } else {
                        AppVectorIcons.CheckCircle
                    },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            AnimatedContent(
                targetState = text,
                transitionSpec = {
                    fadeIn(animationSpec = ExpressiveMotion.FastEffects) togetherWith
                        fadeOut(animationSpec = ExpressiveMotion.FastEffects) using null
                },
                label = "nova_entry_pill_text",
                modifier = Modifier.weight(1f)
            ) { pillText ->
                Text(
                    text = pillText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            AnimatedVisibility(
                visible = editable,
                // The label shares a weighted row with the pill's text, so it
                // expands rather than appearing: a fade-only entrance changed the
                // text column's width in one frame, and the label being drawn over
                // a title that had not made room yet read as a clipped string.
                enter = fadeIn(animationSpec = ExpressiveMotion.FastEffects) +
                    expandHorizontally(animationSpec = ExpressiveMotion.FastSpatialSize),
                exit = fadeOut(animationSpec = ExpressiveMotion.FastEffects) +
                    shrinkHorizontally(animationSpec = ExpressiveMotion.FastSpatialSize)
            ) {
                Text(
                    text = "Edit",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * The acknowledgement floats above the lockup rather than taking a row: a
 * layout that shifted when a copy landed would move the entry capsule under the
 * reader's thumb for the whole confirmation window. Bottom centre sits clear of
 * the back control and the action capsule.
 */
@Composable
private fun BoxScope.HeroCopyConfirmation(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = ExpressiveMotion.FastEffects) +
            slideInVertically(animationSpec = ExpressiveMotion.FastSpatialOffset) { -it / 2 },
        exit = fadeOut(animationSpec = ExpressiveMotion.FastEffects) +
            slideOutVertically(animationSpec = ExpressiveMotion.FastSpatialOffset) { -it / 2 },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 86.dp)
    ) {
        TitleCopiedPill()
    }
}

/** The hairline between two actions in a glass capsule. */
@Composable
private fun HeroDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(22.dp)
            .background(Color.White.copy(alpha = 0.18f))
    )
}

/**
 * Tall enough for a 116x174 poster, a three-line title at 200% type and the entry
 * capsule, with the top controls still clear of the artwork.
 */
private val HERO_MIN_HEIGHT = 430.dp

/**
 * How far the artwork drifts against the scroll.
 *
 * Smaller than the classic hero's lockup lift: the art here is full-bleed and the
 * lockup does not leave, so a large translation would expose the surface at the top
 * edge during the first 200dp rather than creating depth.
 */
private val ART_PARALLAX = 34.dp

/** One percent of over-cover, for `Crop`'s whole-pixel rounding. */
private const val ART_SCALE = 1.06f
