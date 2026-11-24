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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisequel.ui.components.cards.PosterPlaceholder
import coil.compose.AsyncImage
import com.anisequel.data.model.ArrivingEntry
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.mediaCoverKey
import com.anisequel.data.model.detailFor
import com.anisequel.data.model.withSummaryProgress
import com.anisequel.data.repository.AniListRepository
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.cards.cardPosterUrl
import com.anisequel.ui.components.cards.toCoverColorOrNull
import com.anisequel.ui.components.detail.AnimeDetailSubject
import com.anisequel.ui.components.detail.EntryEditor
import com.anisequel.ui.components.detail.SequelDetailSheet
import com.anisequel.ui.components.detail.toDetailMedia
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.icons.Icons
import com.anisequel.ui.components.icons.automirrored.filled.ArrowBack
import com.anisequel.ui.components.nova.NovaBackdrop
import com.anisequel.ui.components.nova.NovaSurface
import com.anisequel.ui.viewmodel.MediaDetailHost
import com.anisequel.ui.viewmodel.rememberPersonProfileHost
import com.anisequel.ui.viewmodel.rememberPlanningAddHost
import com.anisequel.ui.viewmodel.toMediaNode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The viewer's own airing and announced titles, as a schedule.
 *
 * The shape follows the list reference rather than the old poster rail: a section
 * heading with a live count, then one calm card per title, the poster large enough
 * to recognise, and the next episode in a filled pill where the eye lands first.
 * A schedule is read in a hurry - "what is on, and when" - so the date is the
 * loudest line and the progress is the quiet one beneath it.
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
    val friendsLoadingIds by detailHost.friendsLoading.collectAsStateWithLifecycle()
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
                            bottom = 32.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (airing.isNotEmpty()) {
                            item(key = "airing_heading", contentType = "heading") {
                                ScheduleSectionHeading(
                                    title = "Airing now",
                                    count = airing.size,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
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
                                ScheduleSectionHeading(
                                    title = "Upcoming",
                                    count = upcoming.size,
                                    modifier = Modifier.padding(
                                        top = if (airing.isEmpty()) 8.dp else 18.dp,
                                        bottom = 2.dp
                                    )
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
            friends = friends[subject.media.mediaCoverKey].orEmpty(),
            isFriendsLoading = subject.media.mediaCoverKey in friendsLoadingIds,
            isDetailLoading = subject.media.mediaCoverKey in loadingDetailIds,
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
            .padding(start = 10.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(48.dp)
                .bouncyPress(pressedScale = 0.9f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to Other Features"
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Schedule",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$airingCount airing · $upcomingCount upcoming",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppVectorIcons.Calendar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun ScheduleSectionHeading(
    title: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Box(
            modifier = Modifier
                .clip(ExpressiveShapes.pill)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 9.dp, vertical = 3.dp)
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
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
            )
            .testTag("nova_schedule_row"),
        shape = RoundedCornerShape(26.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(84.dp)
                    .height(120.dp)
                    .clip(RoundedCornerShape(18.dp))
            ) {
                PosterPlaceholder(
                    title = entry.title,
                    tint = entry.coverColor.toCoverColorOrNull(),
                    modifier = Modifier.matchParentSize()
                )
                AsyncImage(
                    model = cardPosterUrl(entry.mediaId, entry.coverUrl, entry.title, entry.mediaType),
                    contentDescription = entry.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                ScheduleDatePill(entry)
                Spacer(modifier = Modifier.height(7.dp))
                Text(
                    text = progressLabel(entry),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = AppVectorIcons.SequelArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun ScheduleDatePill(entry: ArrivingEntry) {
    val nextAiringAt = entry.nextAiringAt
    val airingFormatter = rememberAiringDateFormatter()
    val text = when {
        entry.isAiring && nextAiringAt != null -> {
            val episode = entry.nextEpisodeNumber?.let { "Ep $it · " }.orEmpty()
            "$episode${airingDate(nextAiringAt, airingFormatter)}"
        }

        entry.isAiring -> "Airing now"
        entry.startLabel != null -> "Starts ${entry.startLabel}"
        else -> "Date to be announced"
    }
    val container = if (entry.isAiring) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = if (entry.isAiring) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .clip(ExpressiveShapes.pill)
            .background(container)
            .padding(start = 9.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (entry.isAiring) AppVectorIcons.Play else AppVectorIcons.Calendar,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = content,
            maxLines = 1,
            // The pill is sized from its text, but the reader's Text size
            // setting can push it past the width the row has left. Without an
            // ellipsis that last resort is a glyph cut in half.
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun progressLabel(entry: ArrivingEntry): String {
    val progress = entry.progress
    // Zero is AniList's "the length is not announced yet", not a length: the
    // unguarded total read "4 of 0 episodes" and "0 episodes", the same failure
    // the arriving card's meta line was fixed for. A single episode also gets its
    // own wording rather than "1 episodes".
    val total = entry.episodes?.takeIf { it > 0 }
    return when {
        progress != null && total != null ->
            "$progress of $total ${if (total == 1) "episode" else "episodes"}"
        progress != null && progress > 0 ->
            if (progress == 1) "1 episode watched" else "$progress episodes watched"
        progress != null -> "0 episodes watched"
        total != null -> if (total == 1) "1 episode" else "$total episodes"
        else -> "Episode count to be announced"
    }
}

private const val AIRING_DATE_PATTERN = "EEE d MMM · HH:mm"

/**
 * One formatter per locale, remembered for as long as that locale is current.
 *
 * The formatter used to be a file-level `val` built with `Locale.getDefault()`.
 * That freezes the locale at class-load, so a reader who switched the app's language
 * while it was open kept the schedule's date pill in the old one for the rest of the
 * process - and lint is right that a `Locale.getDefault()` in a constant is the shape
 * of that bug. Keyed on the observable configuration locale, a change re-derives it.
 */
@Composable
private fun rememberAiringDateFormatter(): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { DateTimeFormatter.ofPattern(AIRING_DATE_PATTERN, locale) }
}

private fun airingDate(epochSeconds: Long, formatter: DateTimeFormatter): String =
    Instant.ofEpochSecond(epochSeconds)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
