package com.anisequel.ui.components.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.MissedSequel
import com.anisequel.data.model.genreList
import com.anisequel.data.model.posterColor
import com.anisequel.data.model.synopsis
import com.anisequel.data.repository.ThemeMode
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.cards.toCoverColorOrNull
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveTabBar
import com.anisequel.ui.theme.AniSequelTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SequelDetailSheet(
    subject: AnimeDetailSubject?,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onAddToPlanning: (MissedSequel) -> Unit,
    onLoadDetail: (Int) -> Unit,
    isDetailLoading: Boolean,
    canWriteToAniList: Boolean,
    modifier: Modifier = Modifier,
    onAddToWatching: (MissedSequel) -> Unit = {},
    holdDurationSeconds: Int = 3,
    swipeEnabled: Boolean = true,
    /**
     * Read/write access to the viewer's own entry, or null when the sheet is
     * read-only.
     *
     * Null removes the Edit tab entirely rather than drawing it disabled: an
     * editor that cannot save looks identical to one that can until the moment it
     * silently does nothing. The dashboard passes a real editor for the signed-in
     * viewer; a scanned profile passes nothing, because the entry on screen is
     * somebody else's.
     */
    entryEditor: EntryEditor? = null
) {
    if (subject == null) return
    val media = subject.media
    // Non-null only for a franchise recommendation. Its presence is what adds
    // the parent card and the add-to-list action, and what names the first tab.
    val sequel = subject.sequel
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themePreferences = remember(context) { ThemePreferences(context) }
    val themeSettings by themePreferences.settings.collectAsState(
        initial = ThemePreferences.ThemeSettings(
            themeMode = ThemeMode.DEFAULT,
            useDynamicColor = false
        )
    )

    var isDescriptionExpanded by remember(subject.mediaId) { mutableStateOf(false) }
    var detailTab by rememberSaveable(subject.mediaId) { mutableIntStateOf(0) }

    // One scroll state for the whole sheet, but re-based to the top on every tab
    // change.
    //
    // It used to be `rememberScrollState()` written inline in the modifier chain,
    // which kept the offset across tabs. The three tabs are wildly different
    // heights - "Story & Gaps" is a scrolling column, "Anime Info & Specs" is a
    // stack of cards - so carrying an offset from a tall tab into a short one
    // lands past that tab's `maxValue`, the scroll state clamps, and the content
    // jumps *up* under the finger. That clamp races the `AnimatedContent` size
    // animation underneath it, which is what read as the sheet reversing at
    // random: it only bit when the offset was far enough down to be clamped, and
    // which tab you came from decided whether it was.
    //
    // Re-basing to 0 cannot be clamped, so the fight is gone. The profile screen
    // deliberately does the opposite - it keeps a scroll position *per* tab,
    // because `AnimatedContent` disposes the tab it animates away from and would
    // otherwise lose it. That needs one remembered state per tab; here there are
    // three fixed tabs sharing a sheet, and starting each at the top is both
    // cheaper and what a person switching tabs expects.
    val detailScrollState = rememberScrollState()
    LaunchedEffect(detailTab) { detailScrollState.scrollTo(0) }

    // Hoisted above `AnimatedContent` for the same disposal reason, and it is a
    // network request rather than a scroll offset.
    //
    // `AnimatedContent` disposes the tab it animates away from, so everything
    // `remember`ed inside `DetailEditTab` died with it: switch to another tab and
    // back and the draft was gone, the spinner was showing again, and
    // `editor.load()` had fired a second time for a list AniList had already
    // answered once. Held here, the entry loads once per media and the draft
    // survives every switch. Still deliberately not in the ViewModel - nothing
    // outside this tab renders an in-progress edit.
    val editState = remember(subject.mediaId) { EntryEditState() }
    val editor = entryEditor
    LaunchedEffect(subject.mediaId, editor) {
        if (editor != null) editState.load(editor, subject.mediaId)
    }
    val statusColors = AniSequelTheme.statusColors

    LaunchedEffect(subject.mediaId) {
        onLoadDetail(subject.mediaId)
        if (themeSettings.animeThemeActive) {
            val colorHex = media.posterColor ?: "#0057B7"
            themePreferences.setAnimeThemeActive(true, colorHex)
            if (themeSettings.animeThemeRetainColor) {
                // "Retain" means the anime's colour becomes the app's accent,
                // not just a glow around this sheet: persist it as a custom
                // hue so the whole app — and the next launch — keeps it. There
                // is an exit hatch: picking a palette or a hue in the Theme
                // tab clears it, as does Reset appearance.
                themePreferences.setCustomColorHex(colorHex)
            }
        }
    }

    // The revert timer belongs to the *temporary* layer only. While Retain is
    // on the colour was persisted as a custom hue, so deactivating the layer
    // after a delay would flick the app between schemes for no gain — the
    // retained accent is already what the user asked to keep.
    LaunchedEffect(
        themeSettings.animeThemeActive,
        themeSettings.animeThemeRevertDuration,
        themeSettings.animeThemeRetainColor
    ) {
        if (themeSettings.animeThemeActive &&
            themeSettings.animeThemeRevertDuration > 0 &&
            !themeSettings.animeThemeRetainColor
        ) {
            val delayMillis = themeSettings.animeThemeRevertDuration * 1000L
            kotlinx.coroutines.delay(delayMillis)
            themePreferences.setAnimeThemeActive(false, null)
        }
    }

    val handleDismiss: () -> Unit = {
        scope.launch {
            if (themeSettings.animeThemeRevertDuration == 0) {
                themePreferences.setAnimeThemeActive(false, null)
            }
        }
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = handleDismiss,
        sheetState = sheetState,
        modifier = modifier.testTag("sequel_detail_sheet"),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(detailScrollState)
                .padding(bottom = 32.dp)
        ) {
            // Full-bleed, above the padded content rather than inside it.
            //
            // The hero used to sit inside a column with `padding(horizontal =
            // 20.dp)`, so the artwork was inset on both sides - and an image that
            // does not reach the edges of a screen reads as a card floating in
            // space, not as the top of the page. The padding belongs to the text
            // below it, not to the picture above it.
            DetailHeader(media = media)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                val isManga = media.type.equals("MANGA", ignoreCase = true)
                // The Edit tab is appended, never inserted: the first tab's index
                // is persisted by `rememberSaveable`, so a tab that moved would
                // reopen a sheet on a different tab than it was left on.
                // Short labels, because [ExpressiveTabBar] splits the width evenly
                // and three tabs cannot spell "Anime Info & Specs" on a phone.
                //
                // They were "Story & Gaps" and "Anime Info & Specs", which left
                // about 79dp of label room against the ~133dp the second one needs
                // - so both ellipsized, and the first tab of the detail sheet read
                // "Story & Ga...". No amount of geometry fixes that: three labels
                // that long do not fit three across on a 360dp screen, so the fix
                // is the copy rather than the layout. In a sheet that is already
                // about one title, "Specs" and "Story" are not less clear for
                // being short.
                val firstLabel = if (sequel != null) "Story" else "About"
                val firstIcon = if (sequel != null) AppVectorIcons.FranchiseBranch else AppVectorIcons.Info
                val specsLabel = "Specs"
                val specsIcon = if (isManga) AppVectorIcons.SourceBook else AppVectorIcons.AnimeSparkle
                val tabLabels = buildList {
                    add(firstLabel)
                    add(specsLabel)
                    if (editor != null) add("My Entry")
                }
                val tabIcons = buildList {
                    add(firstIcon)
                    add(specsIcon)
                    if (editor != null) add(AppVectorIcons.EditEntry)
                }
                ExpressiveTabBar(
                    tabs = tabLabels,
                    selectedIndex = detailTab,
                    onSelect = { detailTab = it },
                    icons = tabIcons,
                    modifier = Modifier.padding(horizontal = 0.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                AnimatedContent(
                    targetState = detailTab,
                    transitionSpec = {
                        fadeIn(ExpressiveMotion.FastEffects) togetherWith
                            fadeOut(ExpressiveMotion.FastEffects)
                    },
                    label = "detail_tab_switch"
                ) { tab ->
                    if (editor != null && tab == 2) {
                        DetailEditTab(media = media, editor = editor, state = editState)
                    } else if (tab == 1) {
                        DetailSpecsView(
                            media = media,
                            isDetailLoading = isDetailLoading
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // The watched entry this one hangs off, with its own
                            // artwork. This is the relationship that explains why the
                            // entry is on the screen at all, so it gets an image
                            // rather than a bare title - and it costs nothing,
                            // because the list query already returned the parent's
                            // cover. Only a recommendation has a parent; an arriving
                            // or activity row is a show the viewer already tracks.
                            if (sequel != null) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = AppVectorIcons.CheckCircle,
                                            contentDescription = null,
                                            tint = statusColors.success,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        if (sequel.parentCoverUrl != null) {
                                            AsyncImage(
                                                model = sequel.parentCoverUrl,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(MaterialTheme.shapes.extraSmall)
                                                    .background(
                                                        sequel.parentCoverColor.toCoverColorOrNull()
                                                            ?: MaterialTheme.colorScheme.surfaceContainerHighest
                                                    )
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                        }

                                        Column {
                                            Text(
                                                text = sequel.relationLabel,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = sequel.parentTitle,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }

                            DetailSpecsGrid(media = media, isDetailLoading = isDetailLoading)

                            if (media.genreList.isNotEmpty()) {
                                Text(
                                    text = "Genres",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    media.genreList.forEach { genre ->
                                        Surface(
                                            shape = MaterialTheme.shapes.extraSmall,
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                                        ) {
                                            Text(
                                                text = genre,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            val desc = media.synopsis
                            if (isDetailLoading && desc.isNullOrBlank()) {
                                Text(
                                    text = "Synopsis",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Loading synopsis from AniList...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else if (!desc.isNullOrBlank()) {
                                Text(
                                    text = "Synopsis",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (desc.length > 160) {
                                    TextButton(
                                        onClick = { isDescriptionExpanded = !isDescriptionExpanded },
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        Text(if (isDescriptionExpanded) "Show less" else "Read more")
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                DetailActionRow(
                    subject = subject,
                    canWriteToAniList = canWriteToAniList,
                    onAddToPlanning = onAddToPlanning,
                    onAddToWatching = onAddToWatching,
                    holdDurationSeconds = holdDurationSeconds,
                    swipeEnabled = swipeEnabled,
                    context = context
                )
            }
        }
    }
}
