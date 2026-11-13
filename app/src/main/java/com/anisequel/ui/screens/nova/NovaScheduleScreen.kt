package com.anisequel.ui.screens.nova

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.anisequel.data.model.ArrivingEntry
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.detailFor
import com.anisequel.data.model.withSummaryProgress
import com.anisequel.data.repository.AniListRepository
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.cards.toCoverColorOrNull
import com.anisequel.ui.components.detail.AnimeDetailSubject
import com.anisequel.ui.components.detail.EntryEditor
import com.anisequel.ui.components.detail.SequelDetailSheet
import com.anisequel.ui.components.detail.toDetailMedia
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.nova.NovaBackdrop
import com.anisequel.ui.components.nova.NovaSectionHeading
import com.anisequel.ui.components.nova.NovaSurface
import com.anisequel.ui.viewmodel.MediaDetailHost
import com.anisequel.ui.viewmodel.rememberPersonProfileHost
import com.anisequel.ui.viewmodel.rememberPlanningAddHost
import com.anisequel.ui.viewmodel.toMediaNode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The viewer's own airing and announced titles, as a schedule.
 *
 * This replaces the Activity door in the new look's hub. Activity was a page about
 * what had already happened; a schedule is the question the list can answer before
 * it happens, and it is built from the same `ArrivingEntry` rows Home already
 * carries - no second request, and no separate account of which titles exist.
 *
 * The rows are deliberately vertical rather than the Home rail's posters: a date
 * is the content here, and a horizontal card leaves no room for "Starts 5 Oct"
 * without setting it over the cover.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovaScheduleScreen(
    entries: List<ArrivingEntry>,
    isLoading: Boolean,
    error: String?,
    repository: AniListRepository,
    detailHost: MediaDetailHost,
    entryEditor: EntryEditor?,
    onBack: () -> Unit,
    onOpenStudio: (Int, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val airing = remember(entries) { entries.filter { it.isAiring } }
    val upcoming = remember(entries) { entries.filterNot { it.isAiring } }

    val details by detailHost.details.collectAsStateWithLifecycle()
    val loadingDetailIds by detailHost.loading.collectAsStateWithLifecycle()
    val friends by detailHost.friends.collectAsStateWithLifecycle()
    val profiles = rememberPersonProfileHost(repository)
    val planningAdd = rememberPlanningAddHost(
        repository = repository,
        canWrite = entryEditor != null
    )
    val quickAddStatuses by planningAdd.statuses.collectAsStateWithLifecycle()

    var selectedMedia by remember { mutableStateOf<MediaNode?>(null) }
    val selectedSubject = selectedMedia?.let { summary ->
        AnimeDetailSubject.Media(
            details.detailFor(summary)?.withSummaryProgress(summary) ?: summary
        )
    }

    NovaBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .navigationBarsPadding()
                .testTag("nova_schedule_screen")
        ) {
            ScheduleHeader(
                airingCount = airing.size,
                upcomingCount = upcoming.size,
                onBack = onBack
            )

            // The body gets what the header leaves, not the whole window.
            // `fillMaxSize` here (and on the list below) measured against the
            // Column's full height and was then placed under a header, so its
            // last rows sat past the bottom edge and could not be scrolled to.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
            when {
                isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    ExpressiveLoadingIndicator(
                        modifier = Modifier.size(30.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                error != null -> ScheduleNote(
                    text = "Couldn't load your schedule - $error"
                )

                entries.isEmpty() -> ScheduleNote(
                    text = "Nothing in your list is airing or announced yet. " +
                        "Titles you plan to watch will appear here with their start date."
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 28.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (airing.isNotEmpty()) {
                        item(key = "airing_heading", contentType = "heading") {
                            NovaSectionHeading(
                                title = "Airing now",
                                count = airing.size,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        items(
                            items = airing,
                            key = { "airing_${it.mediaId}" },
                            contentType = { "schedule_row" }
                        ) { entry ->
                            ScheduleRow(
                                entry = entry,
                                onClick = { selectedMedia = entry.toDetailMedia() },
                                modifier = Modifier.animateItem(
                                    placementSpec = ExpressiveMotion.DefaultSpatialOffset,
                                    fadeInSpec = ExpressiveMotion.ListItemFadeIn,
                                    fadeOutSpec = ExpressiveMotion.ListItemFadeOut
                                )
                            )
                        }
                    }

                    if (upcoming.isNotEmpty()) {
                        item(key = "upcoming_heading", contentType = "heading") {
                            NovaSectionHeading(
                                title = "Upcoming",
                                count = upcoming.size,
                                modifier = Modifier.padding(top = if (airing.isEmpty()) 6.dp else 14.dp)
                            )
                        }
                        items(
                            items = upcoming,
                            key = { "upcoming_${it.mediaId}" },
                            contentType = { "schedule_row" }
                        ) { entry ->
                            ScheduleRow(
                                entry = entry,
                                onClick = { selectedMedia = entry.toDetailMedia() },
                                modifier = Modifier.animateItem(
                                    placementSpec = ExpressiveMotion.DefaultSpatialOffset,
                                    fadeInSpec = ExpressiveMotion.ListItemFadeIn,
                                    fadeOutSpec = ExpressiveMotion.ListItemFadeOut
                                )
                            )
                        }
                    }
                }
            }
            }
        }
    }

    selectedSubject?.let { subject ->
        SequelDetailSheet(
            subject = subject,
            onDismiss = { selectedMedia = null },
            onAddToPlanning = {},
            onAddToWatching = {},
            quickAddStatuses = quickAddStatuses,
            onAddMediaToPlanning = planningAdd.onAddToPlanning,
            onAddMediaToWatching = planningAdd.onAddToWatching,
            onLoadDetail = detailHost.onLoadDetail,
            onLoadFriends = detailHost.onLoadFriends,
            friends = friends[subject.mediaId].orEmpty(),
            isDetailLoading = subject.mediaId in loadingDetailIds,
            canWriteToAniList = entryEditor != null,
            entryEditor = entryEditor,
            onOpenMedia = { media -> selectedMedia = media },
            onOpenStudio = { id, name ->
                selectedMedia = null
                onOpenStudio(id, name)
            },
            onOpenCharacter = profiles.onOpenCharacter,
            onOpenStaff = profiles.onOpenStaff
        )
    }

    profiles.Sheet { appearance -> selectedMedia = appearance.toMediaNode() }
}

@Composable
private fun ScheduleHeader(
    airingCount: Int,
    upcomingCount: Int,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .bouncyPress(pressedScale = 0.9f)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to Other Features"
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Schedule",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$airingCount airing · $upcomingCount upcoming",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppVectorIcons.Calendar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

@Composable
private fun ScheduleNote(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = AppVectorIcons.Calendar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScheduleRow(
    entry: ArrivingEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    NovaSurface(
        modifier = modifier
            .fillMaxWidth()
            .bouncyPress(pressedScale = 0.985f, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(62.dp)
                    .height(88.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(entry.coverColor.toCoverColorOrNull() ?: Color.Transparent)
            ) {
                AsyncImage(
                    model = entry.coverUrl,
                    contentDescription = entry.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(5.dp))
                ScheduleDatePill(entry)
                if (entry.progress != null || entry.episodes != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = progressLabel(entry),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Icon(
                imageVector = AppVectorIcons.SequelArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ScheduleDatePill(entry: ArrivingEntry) {
    val nextAiringAt = entry.nextAiringAt
    val text = when {
        entry.isAiring && nextAiringAt != null -> {
            val episode = entry.nextEpisodeNumber?.let { "Ep $it · " }.orEmpty()
            "$episode${airingDate(nextAiringAt)}"
        }

        entry.isAiring -> "Airing now"
        entry.startLabel != null -> "Starts ${entry.startLabel}"
        else -> "Date to be announced"
    }
    Row(
        modifier = Modifier
            .clip(ExpressiveShapes.pill)
            .background(
                if (entry.isAiring) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (entry.isAiring) AppVectorIcons.Play else AppVectorIcons.Calendar,
            contentDescription = null,
            tint = if (entry.isAiring) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(11.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (entry.isAiring) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1
        )
    }
}

private fun progressLabel(entry: ArrivingEntry): String {
    val progress = entry.progress
    val total = entry.episodes
    return when {
        progress != null && total != null -> "$progress of $total episodes"
        progress != null -> "$progress episodes watched"
        total != null -> "$total episodes"
        else -> "Episode count to be announced"
    }
}

private val AIRING_DATE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.getDefault())

private fun airingDate(epochSeconds: Long): String =
    Instant.ofEpochSecond(epochSeconds)
        .atZone(ZoneId.systemDefault())
        .format(AIRING_DATE)
