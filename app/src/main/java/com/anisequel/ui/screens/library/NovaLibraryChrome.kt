package com.anisequel.ui.screens.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.ProfileAvatarButton
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.ExpressiveStateChip
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.nova.NovaSurface
import com.anisequel.ui.screens.nova.NovaSearchRow
import com.anisequel.ui.screens.profile.LibraryFilter
import com.anisequel.ui.screens.profile.LibrarySort
import com.anisequel.ui.screens.profile.LibraryType

/**
 * The fixed part of the new Library: the shelf header, the search and filter
 * controls, the active sort row and the status pills.
 *
 * Split out of [NovaLibraryScreen] so that file stays the page's state machine.
 * Nothing here owns data: every control reports, and the screen writes.
 */
@Composable
internal fun NovaLibraryHeader(
    isManga: Boolean,
    scopedSize: Int,
    username: String?,
    avatarUrl: String?,
    searchOpen: Boolean,
    type: LibraryType,
    animeCount: Int,
    mangaCount: Int,
    isLoadingManga: Boolean,
    activeFilterCount: Int,
    onSearchOpenChange: (Boolean) -> Unit,
    onOpenFilters: () -> Unit,
    onOpenProfile: () -> Unit,
    onSelectType: (LibraryType) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    NovaSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        tonalElevation = true
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatarButton(
                    avatarUrl = avatarUrl,
                    enabled = true,
                    onClick = onOpenProfile,
                    testTag = "nova_library_profile_button"
                )
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isManga) "Manga List" else "Anime List",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = librarySubtitle(size = scopedSize, username = username),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSearchOpenChange(!searchOpen)
                    },
                    // 48dp, not 38: this is a bare icon button in a header full
                    // of other targets, and the platform's minimum is what makes
                    // it findable without aiming. The glyph stays 19dp.
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (searchOpen) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                Color.Transparent
                            }
                        )
                        .testTag("nova_library_search_toggle")
                ) {
                    Icon(
                        imageVector = AppVectorIcons.Search,
                        contentDescription = "Search this list",
                        modifier = Modifier.size(19.dp)
                    )
                }
                Box {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenFilters()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .testTag("nova_library_filter_toggle")
                    ) {
                        Icon(
                            imageVector = AppVectorIcons.Filter,
                            contentDescription = "Filter and sort this list",
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    if (activeFilterCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = activeFilterCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            NovaTypeSwitch(
                type = type,
                animeCount = animeCount,
                mangaCount = mangaCount,
                isLoadingManga = isLoadingManga,
                onSelect = onSelectType
            )
        }
    }
}

private fun librarySubtitle(size: Int, username: String?): String = buildString {
    append(size)
    append(if (size == 1) " title" else " titles")
    username?.takeIf { it.isNotBlank() }?.let {
        append(" · @")
        append(it)
    }
}

/** The search field, the active sort row and the status pills. */
@Composable
internal fun NovaLibraryTools(
    isManga: Boolean,
    searchOpen: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenFilters: () -> Unit,
    sort: LibrarySort,
    sortAscending: Boolean,
    onToggleDirection: () -> Unit,
    filter: LibraryFilter,
    counts: Map<LibraryFilter, Int>,
    onChooseFilter: (LibraryFilter) -> Unit
) {
    AnimatedVisibility(
        visible = searchOpen,
        enter = fadeIn(ExpressiveMotion.FastEffects) +
            expandVertically(ExpressiveMotion.FastSpatialSize),
        exit = fadeOut(ExpressiveMotion.FastEffects) +
            shrinkVertically(ExpressiveMotion.FastSpatialSize)
    ) {
        NovaSearchRow(
            query = query,
            onQueryChange = onQueryChange,
            onOpenFilter = onOpenFilters,
            placeholder = if (isManga) "Search your manga" else "Search your anime",
            modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExpressiveStateChip(
            label = sortLabel(sort, active = true, ascending = sortAscending),
            selected = true,
            onClick = onOpenFilters,
            minTouchTargetHeight = 36.dp,
            modifier = Modifier.testTag("nova_library_sort_summary")
        )
        if (sort.isDirectional) {
            ExpressiveStateChip(
                label = if (sortAscending) "Ascending ↑" else "Descending ↓",
                selected = false,
                onClick = onToggleDirection,
                minTouchTargetHeight = 36.dp,
                modifier = Modifier.testTag("nova_library_sort_direction")
            )
        }
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        items(
            items = LibraryFilter.entries,
            key = { it.name },
            contentType = { "filter" }
        ) { option ->
            NovaFilterPill(
                label = option.labelFor(isManga),
                count = counts[option] ?: 0,
                selected = option == filter,
                onClick = { onChooseFilter(option) },
                testTag = "nova_library_filter_${option.name.lowercase()}"
            )
        }
    }
}

private fun sortLabel(
    option: LibrarySort,
    active: Boolean,
    ascending: Boolean
): String = when {
    !option.isDirectional || !active -> "Sort · ${option.label}"
    ascending -> "Sort · ${option.label} ↑"
    else -> "Sort · ${option.label} ↓"
}

/** Anime/Manga, as the two-way switch the new look uses instead of an "All" bucket. */
@Composable
private fun NovaTypeSwitch(
    type: LibraryType,
    animeCount: Int,
    mangaCount: Int,
    isLoadingManga: Boolean,
    onSelect: (LibraryType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ExpressiveShapes.pill)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(3.dp)
    ) {
        listOf(
            LibraryType.ANIME to animeCount,
            LibraryType.MANGA to mangaCount
        ).forEach { (option, count) ->
            val selected = option == type
            val container by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                },
                animationSpec = ExpressiveMotion.FastColorEffects,
                label = "nova_type_container"
            )
            val content = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(ExpressiveShapes.pill)
                    .background(container)
                    // One of two mutually exclusive types: a radio, not a pair
                    // of unrelated buttons. The minimum height is what keeps the
                    // control above the platform's touch target even though the
                    // icon and label together are only about 20dp. The selected
                    // flag is what makes the role a fact rather than a label:
                    // TalkBack announces "selected" for the active half.
                    .semantics { this.selected = option == type }
                    .clickable(role = Role.RadioButton) { onSelect(option) }
                    .padding(vertical = 8.dp)
                    .testTag("nova_library_type_${option.name.lowercase()}"),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (option == LibraryType.MANGA) {
                        AppVectorIcons.SourceBook
                    } else {
                        AppVectorIcons.Tv
                    },
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = when {
                        option == LibraryType.MANGA && isLoadingManga && !selected -> "Manga..."
                        option == LibraryType.MANGA -> "Manga"
                        else -> "Anime"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = content,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.72f),
                    maxLines = 1
                )
            }
        }
    }
}

/** One status bucket, with the count over the *whole* type scope rather than the narrowed set. */
@Composable
private fun NovaFilterPill(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "nova_filter_container"
    )
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    // Shared with bouncyPress so the press is cancelled the moment a horizontal
    // drag claims the gesture. Without a source the fallback pointer loop kept
    // the pill squashed under a finger that was actually scrolling the filter row.
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .clip(ExpressiveShapes.pill)
            .background(container)
            .bouncyPress(pressedScale = 0.94f, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(start = 13.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = content,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(content.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = content,
                maxLines = 1
            )
        }
    }
}
