package com.example.ui.components.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.FuzzyDate
import com.example.data.model.SimpleMediaListEntry
import com.example.ui.components.AppVectorIcons
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The pieces the entry editor is assembled from.
 *
 * Split out of `DetailEditTab` so that file stays about *what* the form edits
 * rather than *how* a row is drawn: the tab is already the longest composable in
 * the detail package, and the three row shapes here are the part that would need
 * to change together if the design did.
 *
 * Everything is `internal` rather than file-private so the tab in the next file
 * can use it while nothing outside `ui.components.detail` can.
 */

/** One selectable list status, with the wording each medium uses for it. */
internal data class EntryStatusOption(
    val apiValue: String,
    val animeLabel: String,
    val mangaLabel: String
) {
    fun labelFor(isManga: Boolean): String = if (isManga) mangaLabel else animeLabel
}

/**
 * The six AniList list statuses, in the order a person is most likely to reach
 * for them: what they are doing now, then what they would do next, then the ends.
 *
 * Both spellings are carried because "Watching" is wrong on a manga list, and the
 * filter labels were shared between the two media once before - a manga list that
 * offered "Watching" instead of "Reading".
 */
internal val ENTRY_STATUSES = listOf(
    EntryStatusOption("CURRENT", "Watching", "Reading"),
    EntryStatusOption("REPEATING", "Rewatching", "Rereading"),
    EntryStatusOption("COMPLETED", "Completed", "Completed"),
    EntryStatusOption("PLANNING", "Planning", "Planning"),
    EntryStatusOption("PAUSED", "Paused", "Paused"),
    EntryStatusOption("DROPPED", "Dropped", "Dropped")
)

/** Which of the two dates a picker is open for. */
internal enum class DateField { START, FINISH }

/** The editable copy of an entry. Everything the form can change lives here. */
internal data class EntryDraft(
    val entryId: Int?,
    val status: String?,
    val progress: Int,
    /**
     * Volumes read, carried through the edit untouched.
     *
     * The form has no volume control, but the save is a whole snapshot - so a
     * value that is loaded and then not sent would be *cleared* on the server.
     * Preserving it here is what stops editing a manga's status from also
     * deleting how far through it the reader is.
     */
    val progressVolumes: Int?,
    /**
     * The score, in the user's own AniList scale - *not* converted to a ten.
     *
     * AniList holds a score in whichever format the account chose (`POINT_10`,
     * `POINT_100`, `POINT_5` or `POINT_3`), and `SaveMediaListEntry` takes the raw
     * number in that format. Rescaling it to out-of-ten here would silently
     * rewrite an 85/100 score to 8/100 the first time anything on this form was
     * saved, which is a worse failure than showing the wrong denominator.
     */
    val score: Double,
    /**
     * The top of the slider, derived once from the loaded score.
     *
     * A stored score above ten can only be a hundred-point account, so the form
     * follows the data rather than asking AniList for the format. It is captured
     * in the draft rather than recomputed, or moving the slider under a hundred
     * would shrink the scale - and the thumb - mid-drag.
     */
    val scoreMax: Int,
    val startedAt: FuzzyDate?,
    val completedAt: FuzzyDate?,
    val isPrivate: Boolean
)

internal fun SimpleMediaListEntry.toDraft(): EntryDraft {
    val rawScore = score ?: 0.0
    return EntryDraft(
        entryId = id,
        status = status,
        progress = progress ?: 0,
        progressVolumes = progressVolumes,
        score = rawScore,
        scoreMax = if (rawScore > 10.0) 100 else 10,
        startedAt = startedAt,
        completedAt = completedAt,
        isPrivate = isPrivate ?: false
    )
}

/** A whole score as "8", a fractional one as "8.5" - and never as "8.0". */
internal fun formatScore(score: Double): String =
    if (score == score.toInt().toDouble()) score.toInt().toString() else "$score"

/** A label for a status the entry already has, including one this build predates. */
internal fun statusLabel(status: String?, isManga: Boolean): String {
    if (status == null) return "Not on your list"
    return ENTRY_STATUSES.firstOrNull { it.apiValue.equals(status, ignoreCase = true) }
        ?.labelFor(isManga)
        ?: status.lowercase().replaceFirstChar { it.uppercase() }
}

/**
 * One labelled control row, with an optional body underneath.
 *
 * The row is the shape the whole tab is built from, so a new field cannot invent
 * its own spacing or icon size: it either is one of these or it is not a field.
 */
@Composable
internal fun EditorCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: (() -> Unit)? = null,
    content: (@Composable () -> Unit)? = null
) {
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .then(clickable)
            .testTag(testTag)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (onClick != null) {
                    Icon(
                        imageVector = AppVectorIcons.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            content?.invoke()
        }
    }
}

/** The start/finish date row: tap to pick, and an X to clear once it is set. */
@Composable
internal fun DateRow(
    label: String,
    value: FuzzyDate?,
    testTag: String,
    onPick: () -> Unit,
    onClear: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppVectorIcons.Calendar,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = value?.formatted ?: "Not set",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (value != null) {
                Icon(
                    imageVector = AppVectorIcons.Close,
                    contentDescription = "Clear $label",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onClear)
                )
            } else {
                Icon(
                    imageVector = AppVectorIcons.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** One side of the progress stepper. */
@Composable
internal fun StepButton(
    icon: ImageVector,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier
            .size(42.dp)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * AniList's `FuzzyDate` as a UTC epoch millisecond for the date picker.
 *
 * A partial date resolves to its earliest expressible day - a year with no month
 * becomes January, a month with no day becomes the first - because the picker
 * needs one day to show and the entry keeps whatever the user then picks. The
 * zone is UTC, matching `BuildActivityCalendarUseCase` and for the same reason:
 * the same entry must open on the same day on two phones.
 */
internal fun FuzzyDate?.toEpochMillisUtc(): Long? {
    val y = this?.year ?: return null
    return runCatching {
        LocalDate.of(y, month ?: 1, day ?: 1)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    }.getOrNull()
}

internal fun Long.toFuzzyDateUtc(): FuzzyDate {
    val date = Instant.ofEpochMilli(this).atOffset(ZoneOffset.UTC).toLocalDate()
    return FuzzyDate(year = date.year, month = date.monthValue, day = date.dayOfMonth)
}
