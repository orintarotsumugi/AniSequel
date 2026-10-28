package com.anisequel.ui.screens.tier

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anisequel.domain.tier.TIER_LIST_NAME_MAX_LENGTH
import com.anisequel.domain.tier.TierListDocument
import com.anisequel.domain.tier.TierMediaType
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveEmptyOrb
import com.anisequel.ui.components.expressive.ExpressivePolygonSegmentedBar
import com.anisequel.ui.components.expressive.ExpressivePrimaryButton
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.SegmentedOption
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.theme.AniSequelTheme
import kotlin.math.max

/**
 * One shelf of the tier library, as the screen needs to draw it.
 *
 * The counts are per section because the pools are separate: a manga shelf that
 * showed the anime library's size would promise titles it cannot rank. The three
 * flags are the honest states an unloaded pool can be in - still arriving, not
 * available on this account, or asked for and failed - and they are distinct
 * because they need different copy and only the last one is a retry.
 *
 * [isLoaded] is separate from [librarySize] because "asked and there is none" is
 * not "not asked yet". Both have a size of zero, and telling somebody with an
 * empty manga list that it "has not loaded yet" is a promise that it is still
 * coming.
 */
internal data class TierLibrarySection(
    val mediaType: TierMediaType,
    val librarySize: Int,
    val isLoaded: Boolean,
    val isLoading: Boolean,
    val isUnavailable: Boolean,
    val loadFailed: Boolean
)

/**
 * The tier lists themselves: the screen the trophy opens.
 *
 * ## Why this screen exists at all
 *
 * The trophy used to open the board. With one list in two fixed keys there was
 * nothing to choose between, so "start something new" and "keep what I have" were
 * the same button, and a person could keep exactly one ranking of exactly one kind.
 * This is the difference between a screen and a thing you keep: the lists are
 * objects with names, they can be made, opened, renamed and deleted, and the board
 * is a destination you pick rather than the only place the feature can be.
 *
 * ## The card at the top is the last one *opened*
 *
 * Not the last one edited. A seeding pass or a single drag in a list somebody was
 * comparing against is an edit; opening a list is what a person means by "carrying
 * on with", and the difference shows up the first time an edit of a list they do
 * not care about outranks the one they were working in.
 *
 * ## "Start empty" is a button here, not a caveat on the board
 *
 * The two ways in are the same two as ever - rank everything from AniList scores,
 * or place them by hand - but making the list is now the first step, so starting
 * empty is simply the state a new list is in. Nothing needs to be explained before
 * the person is allowed to look around.
 *
 * ## The shelf is chosen above the list of lists
 *
 * Anime and manga are two libraries, not one library with a filter: their ids
 * collide and their lists cannot rank each other's titles. The switch is above the
 * "New list" button because that button creates a list *of the selected shelf*.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TierLibraryScreen(
    documents: List<TierListDocument>,
    recent: TierListDocument?,
    section: TierLibrarySection,
    onSelectMediaType: (TierMediaType) -> Unit,
    onRetryLoad: () -> Unit,
    onOpen: (String) -> Unit,
    onCreate: (TierMediaType) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var renaming by remember { mutableStateOf<TierListDocument?>(null) }
    var deleting by remember { mutableStateOf<TierListDocument?>(null) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("tier_library_screen"),
        topBar = {
            TopAppBar(
                title = { Text("Tier lists", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("tier_library_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "sections") {
                TierLibrarySectionBar(
                    selected = section.mediaType,
                    onSelect = onSelectMediaType,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }

            item(key = "new") {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    ExpressivePrimaryButton(
                        text = "New ${section.mediaType.label.lowercase()} tier list",
                        onClick = { onCreate(section.mediaType) },
                        icon = AppVectorIcons.NewList,
                        // Nothing to rank on a shelf this account does not have.
                        // The empty state below says why rather than leaving a
                        // button that opens a board with no pool behind it.
                        enabled = !section.isUnavailable,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "A new list starts empty: rank it from your AniList " +
                            "scores, or drag the titles in yourself.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (documents.isEmpty()) {
                item(key = "empty") {
                    TierLibraryEmptyState(
                        section = section,
                        onRetry = onRetryLoad
                    )
                }
            } else {
                recent?.let { list ->
                    item(key = "recent") {
                        TierContinueCard(list = list, onOpen = { onOpen(list.id) })
                    }
                }

                item(key = "header") {
                    Text(
                        text = tierLibrarySubtitle(documents.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp)
                    )
                }

                items(documents, key = { it.id }) { document ->
                    TierListRow(
                        document = document,
                        isRecent = document.id == recent?.id,
                        onOpen = { onOpen(document.id) },
                        onRename = { renaming = document },
                        onDelete = { deleting = document }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(start = 20.dp)
                    )
                }
            }
        }
    }

    renaming?.let { document ->
        TierListNameDialog(
            initial = document.name,
            title = "Rename list",
            confirmLabel = "Save",
            onDismiss = { renaming = null },
            onConfirm = { name ->
                onRename(document.id, name)
                renaming = null
            }
        )
    }

    deleting?.let { document ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${document.name}\"?") },
            text = {
                Text(
                    text = if (document.rankedCount == 0) {
                        "This list is empty, so nothing else changes."
                    } else {
                        "The ${document.rankedCount} " +
                            (if (document.rankedCount == 1) "title" else "titles") +
                            " ranked in it go with it, and this cannot be undone."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(document.id)
                    deleting = null
                }) {
                    Text("Delete list", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Keep it") }
            }
        )
    }
}

/**
 * The card that offers to carry on where you left off.
 *
 * The one list a person is most likely to want, given the space of a card rather
 * than a row: which one it is, how much of it is done, and the last time it moved.
 * Below it the same list appears in the full list as well, on purpose - the card is
 * a shortcut, and a shortcut that removed the row it points at would make the list
 * of your lists depend on which one you opened last.
 */
@Composable
private fun TierContinueCard(list: TierListDocument, onOpen: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(ExpressiveShapes.MediumCorner))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .bouncyPress(pressedScale = 0.98f, interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, onClick = onOpen)
            .padding(16.dp)
            .testTag("tier_continue_card")
    ) {
        Text(
            text = "Carry on with",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = list.name,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = tierListCardStatus(list),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        ExpressivePrimaryButton(
            text = "Open",
            onClick = onOpen,
            icon = AppVectorIcons.Trophy,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** One saved list, as a row: what it is, how far along, and what can be done to it. */
@Composable
private fun TierListRow(
    document: TierListDocument,
    isRecent: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .bouncyPress(pressedScale = 0.985f, interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, onClick = onOpen)
            .padding(start = 20.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = document.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isRecent) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Last opened",
                        style = MaterialTheme.typography.labelSmall,
                        color = AniSequelTheme.statusColors.info
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = tierListCardStatus(document),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = AppVectorIcons.OverflowMenu,
                    contentDescription = "Options for ${document.name}"
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = {
                        menuOpen = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    }
                )
            }
        }
    }
}

/**
 * The Anime | Manga switch over the shelf.
 *
 * The two pools are separate, so this is a switch between two libraries rather
 * than a filter over one. It sits above the "New list" button because the button
 * creates a list *of the selected shelf*, and a control that changes what a button
 * below it means has to be above it.
 */
@Composable
private fun TierLibrarySectionBar(
    selected: TierMediaType,
    onSelect: (TierMediaType) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = TierMediaType.entries
    ExpressivePolygonSegmentedBar(
        options = options.map { type ->
            SegmentedOption(
                label = type.label,
                icon = when (type) {
                    TierMediaType.ANIME -> AppVectorIcons.FavouriteAnime
                    TierMediaType.MANGA -> AppVectorIcons.FavouriteManga
                }
            )
        },
        selectedIndex = options.indexOf(selected).coerceAtLeast(0),
        onSelect = { index -> onSelect(options[index]) },
        modifier = modifier.testTag("tier_library_sections")
    )
}

/**
 * The state before there is anything to show on this shelf.
 *
 * Four different empty shelves, because "no lists yet" is only one of them. A
 * manga shelf whose list is still arriving, one this account cannot have (the
 * demo), and one whose fetch failed all look the same if the copy assumes the
 * first - and only the last one can be fixed by a tap.
 */
@Composable
private fun TierLibraryEmptyState(
    section: TierLibrarySection,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ExpressiveEmptyOrb(
            icon = AppVectorIcons.Trophy,
            containerColor = AniSequelTheme.statusColors.infoContainer,
            iconTint = AniSequelTheme.statusColors.info
        )
        Text(
            text = when {
                section.isUnavailable -> "No ${section.mediaType.label.lowercase()} list to rank"
                section.isLoading -> "Loading your ${section.mediaType.label.lowercase()} list"
                section.loadFailed -> "Your ${section.mediaType.libraryLabel} did not load"
                else -> "No tier lists yet"
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            text = when {
                // Demo mode has an anime list and nothing else, because there is
                // no account behind it to have a manga list. Saying so is the
                // honest empty state; inventing manga titles would be the app
                // ranking somebody else's books as if they were the reader's.
                section.isUnavailable ->
                    "The demo account has no ${section.mediaType.label.lowercase()} list. " +
                        "Sign in to rank your own."

                section.isLoading ->
                    "The list is on its way. You can still make an empty list and fill " +
                        "it in when it arrives."

                section.loadFailed ->
                    "AniList did not answer. Nothing is lost - try again, or make an " +
                        "empty list in the meantime."

                section.librarySize == 0 && !section.isLoaded ->
                    "Your ${section.mediaType.libraryLabel} has not loaded yet, so there " +
                        "is nothing to rank. You can still make an empty list and fill it " +
                        "in once it arrives."

                section.librarySize == 0 ->
                    "There is nothing on your ${section.mediaType.libraryLabel} to rank " +
                        "yet. You can still make an empty list and fill it in later."

                else ->
                    "Make one to rank the ${section.librarySize} titles in your " +
                        "${section.mediaType.libraryLabel}. Rank them all from their " +
                        "AniList scores in one tap, or place them by hand."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (section.loadFailed) {
            TextButton(onClick = onRetry, modifier = Modifier.testTag("tier_library_retry")) {
                Text("Try again")
            }
        }
    }
}

/**
 * A short field for a list's name.
 *
 * The same shape as renaming a tier - one field, one cap, a confirm and a cancel -
 * because they are the same act with different words.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TierListNameDialog(
    initial: String,
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            // A dialog has its own confirm and dismiss, so this is a bare field
            // rather than the tier renamer's row - that one carries its own pair
            // of buttons, which one screen below would be two confirms for one
            // edit.
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(TIER_LIST_NAME_MAX_LENGTH) },
                singleLine = true,
                shape = ExpressiveShapes.pill,
                supportingText = { Text("${text.length}/$TIER_LIST_NAME_MAX_LENGTH") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tier_list_name_field")
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank()
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** The line above the rows. Counted, so an empty library cannot be misread. */
internal fun tierLibrarySubtitle(count: Int): String =
    if (count == 1) "1 saved list" else "$count saved lists"

/**
 * A list's second line: what is in it and when it last moved.
 *
 * Both facts, because either alone is misleading. "12 of 271 titles ranked" without
 * a time reads as current work even when the list is a year old, and "updated 3
 * days ago" without a count says nothing about whether the list is finished.
 */
internal fun tierListCardStatus(list: TierListDocument, now: Long = System.currentTimeMillis()): String {
    val ranked = when (list.rankedCount) {
        0 -> "Nothing ranked yet"
        1 -> "1 title ranked"
        else -> "${list.rankedCount} titles ranked"
    }
    val when_ = list.updatedAt.takeIf { it > 0L }?.let { "updated ${relativeTimeLabel(now, it)}" }
    return if (when_ == null) ranked else "$ranked - $when_"
}

/**
 * A coarse "how long ago", for a card that only has to be roughly right.
 *
 * Coarse on purpose: the exact minute a list was last written is not a fact anybody
 * acts on, and a live "2 minutes ago" that a screen has to recompute to stay honest
 * is a timer running behind a list of names. Under a minute is "just now" rather
 * than "0 minutes ago", and a future timestamp - a device clock that has moved
 * backwards - reads as "just now" instead of counting up from a negative.
 */
internal fun relativeTimeLabel(now: Long, then: Long): String {
    val elapsed = now - then
    if (elapsed < MINUTE_MS) return "just now"
    val minutes = elapsed / MINUTE_MS
    if (minutes < 60) return if (minutes == 1L) "1 minute ago" else "$minutes minutes ago"
    val hours = minutes / 60
    if (hours < 24) return if (hours == 1L) "1 hour ago" else "$hours hours ago"
    val days = hours / 24
    if (days < 7) return if (days == 1L) "yesterday" else "$days days ago"
    val weeks = days / 7
    if (weeks < 5) return if (weeks == 1L) "last week" else "$weeks weeks ago"
    val months = max(1, days / 30)
    return if (months == 1L) "last month" else "$months months ago"
}

private const val MINUTE_MS = 60_000L
