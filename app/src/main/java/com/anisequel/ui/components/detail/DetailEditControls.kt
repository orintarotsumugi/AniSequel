package com.anisequel.ui.components.detail

import com.anisequel.ui.components.expressive.ExpressiveShapes

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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.FuzzyDate
import com.anisequel.data.model.SimpleMediaListEntry
import com.anisequel.domain.list.ListStatuses
import com.anisequel.ui.components.AppVectorIcons
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

/**
 * The six AniList list statuses, in the order a person is most likely to reach
 * for them, read from [ListStatuses].
 *
 * This used to be the second of four hand-copied versions of the same list, which
 * is how the picker and the Library chips ended up calling `PAUSED` "Paused" while
 * the Stats tally called it "On-Hold" - the same status, two names, one screen
 * apart. The vocabulary lives in `domain/list` now and every surface quotes it.
 */
internal val ENTRY_STATUSES = ListStatuses.options

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
     * The top of the slider, from the account's own score format.
     *
     * This used to be inferred from the loaded value - anything above ten was
     * read as a hundred-point account - which was wrong for a hundred-point
     * entry scored 8 or 10 (it opened on a 0-10 slider and could not reach the
     * score the reader wanted) and for the five- and three-point formats (they
     * opened on a 0-10 slider and could save a value the server rejects). The
     * format now comes from `Viewer.mediaListOptions.scoreFormat` in the same
     * request as the entry; [scoreScaleFor] keeps the old inference as the
     * fallback for callers that cannot answer.
     *
     * Captured in the draft rather than recomputed, or moving the slider under a
     * hundred would shrink the scale - and the thumb - mid-drag.
     */
    val scoreMax: Int,
    /**
     * One tick of the slider, in the account's own units.
     *
     * A tenth on a decimal account, a whole point everywhere else. Defaulted to
     * 0.5 so a draft built without a format still lets the half score the row
     * prints be set - see [scoreScaleFor].
     */
    val scoreStep: Double = 0.5,
    /**
     * The account's score format, when this draft was loaded through the
     * repository.
     *
     * Kept on the draft so a save can rebuild the draft from AniList's answer
     * with the same scale: the mutation's payload has no `Viewer` in it, so the
     * reply alone cannot say how the score should be drawn.
     */
    val scoreFormat: ScoreFormat? = null,
    val startedAt: FuzzyDate?,
    val completedAt: FuzzyDate?,
    val isPrivate: Boolean,
    /**
     * The entry's private note, empty when there is none.
     *
     * Not nullable: the form draws a text field, and "no note" and "a note the
     * reader just emptied" are the same thing to this form. AniList clears a note
     * when it receives `""`, which is what an empty field sends.
     */
    val notes: String = ""
)

/**
 * The draft, on the scale the entry's own account uses.
 *
 * Two overloads rather than a defaulted argument that reads `scoreFormat` off
 * the receiver: a save response carries no format of its own, and the caller
 * that has one - the draft being saved - has to be able to insist on it.
 */
internal fun SimpleMediaListEntry.toDraft(): EntryDraft = toDraft(ScoreFormat.fromApi(scoreFormat))

internal fun SimpleMediaListEntry.toDraft(format: ScoreFormat?): EntryDraft {
    val rawScore = score ?: 0.0
    // The loaded score is *not* quantised. A POINT_10_DECIMAL entry holding 8.3
    // must keep 8.3 through a save the reader never touched - snapping it to the
    // slider's nearest tick here would rewrite a score nobody edited. Only a
    // drag goes through `ScoreScale.quantise`.
    val scale = scoreScaleFor(format, rawScore)
    return EntryDraft(
        entryId = id,
        status = status,
        progress = progress ?: 0,
        progressVolumes = progressVolumes,
        score = rawScore,
        scoreMax = scale.max,
        scoreStep = scale.step,
        scoreFormat = format,
        startedAt = startedAt,
        completedAt = completedAt,
        isPrivate = isPrivate ?: false,
        notes = notes.orEmpty()
    )
}

/** A whole score as "8", a fractional one as "8.5" - and never as "8.0". */
internal fun formatScore(score: Double): String =
    if (score == score.toInt().toDouble()) score.toInt().toString() else "$score"

/**
 * The edit form's state, held *above* the tab switch rather than inside the tab.
 *
 * ## Why this is not just `remember` inside [DetailEditTab]
 *
 * Because the tabs live in an `AnimatedContent`, which disposes the tab it
 * animates away from. Everything remembered inside the tab therefore died with
 * it, and coming back rebuilt the form from nothing: the draft was gone, the
 * loading spinner was up again, and `editor.load()` fired a second request for an
 * entry AniList had already answered. That is the worst possible moment for it -
 * a person flicking between tabs to compare a score against a status is exactly
 * who does that - and it spent the account's request budget on nothing.
 *
 * So the state is remembered by the sheet, which outlives the tab. The draft
 * survives every switch and the entry is fetched once per title.
 *
 * ## Why this is still not in the ViewModel
 *
 * The original argument for keeping the draft local was layering, not lifetime:
 * nothing outside this tab renders an in-progress edit, so hoisting it that far
 * up would make every owner of the sheet carry a saving flag and an error string
 * it never reads. That argument still holds - this sits directly under the sheet,
 * one level up, and never leaves the detail surface.
 */
@Stable
internal class EntryEditState {
    private var draftState by mutableStateOf<EntryDraft?>(null)

    /**
     * The entry AniList last confirmed: what [load] fetched, or the last save.
     *
     * This is what makes "nothing to send" distinguishable from "changes waiting",
     * which is what the Save button reads. It also has to be moved by [markSaved]:
     * a save that left the baseline behind would leave the button enabled forever.
     */
    private var confirmed by mutableStateOf<EntryDraft?>(null)

    /**
     * The editable copy of the entry, and the one thing that hangs off it changing.
     *
     * The setter clears [statusMessage] on *any* change, because a message describes
     * the *entry* as it was when the message was written. It used to survive the next
     * edit, so a form whose score had already been dragged to a new value still read
     * "Saved to your AniList list" until the next save. It also survived a *revert* -
     * the comparison was against [confirmed], so dragging a score back to where it
     * started kept "Couldn't save your changes." on screen above a form with nothing
     * left to save. The save and delete paths set their message *after* the draft,
     * so their own line still lands.
     */
    var draft: EntryDraft?
        get() = draftState
        set(value) {
            val changed = value != draftState
            draftState = value
            if (changed) statusMessage = null
        }

    /** Whether the form holds anything AniList has not been told about yet. */
    val hasUnsavedChanges: Boolean
        get() = draft != null && draft != confirmed

    var loadError by mutableStateOf<String?>(null)
    var statusMessage by mutableStateOf<String?>(null)
    var isSaving by mutableStateOf(false)
    var statusMenuOpen by mutableStateOf(false)

    /** Which date, if any, has its picker open. One value, not a flag per field. */
    var openDatePicker by mutableStateOf<DateField?>(null)

    /**
     * Fetch the entry once.
     *
     * A no-op when a draft is already loaded, so a recomposition - or a second
     * `LaunchedEffect` firing on the same media - cannot turn into a second
     * request. The error is cleared before the request rather than only on
     * success: the tab reads "no draft and no error" as loading, so a retry
     * shows the spinner instead of the failed first attempt's message while the
     * second one is on the wire.
     */
    suspend fun load(editor: EntryEditor, mediaId: Int) {
        if (draft != null) return
        loadError = null
        editor.load(mediaId).fold(
            onSuccess = {
                val loaded = it.toDraft()
                draft = loaded
                confirmed = loaded
                loadError = null
            },
            onFailure = { loadError = it.message ?: "Couldn't load your entry." }
        )
    }

    /**
     * Records a save: AniList's answer is both what the form shows and the new
     * baseline, so the Save button goes quiet until the next edit.
     *
     * The caller writes its "Saved" line *after* this call: assigning the draft
     * clears whatever message was there, and that line describes the entry this
     * call has just recorded.
     */
    fun markSaved(saved: EntryDraft) {
        confirmed = saved
        draft = saved
    }

    /** Back to an entry that does not exist, so the form is ready to create one. */
    fun cleared() {
        // The account's format outlives the entry that was deleted with it, so
        // the blank form keeps the scale the previous draft was drawn with
        // instead of falling back to 0-10. A hundred-point account that removed
        // an entry could otherwise only score the next one out of ten.
        val format = draft?.scoreFormat
        val scale = scoreScaleFor(format, loadedScore = 0.0)
        draft = EntryDraft(
            entryId = null,
            status = null,
            progress = 0,
            progressVolumes = null,
            score = 0.0,
            scoreMax = scale.max,
            scoreStep = scale.step,
            scoreFormat = format,
            startedAt = null,
            completedAt = null,
            isPrivate = false,
            notes = ""
        )
        // The deleted entry is not "unsaved changes"; it is gone. The blank form is
        // the baseline, so the pin stays disabled until the reader actually asks for
        // something to be written.
        confirmed = draft
    }
}

/**
 * A label for a status the entry already has, including one this build predates.
 *
 * A thin alias for [ListStatuses.label] kept because the form and its tests read
 * the wording as a property of the editor; the *rules* - which token means what,
 * and what an unrecognised one is shown as - are in the domain file, beside the
 * three other surfaces that print the same words.
 */
internal fun statusLabel(status: String?, isManga: Boolean): String =
    ListStatuses.label(status, isManga)

/**
 * How much of a row's colour survives while it is disabled.
 *
 * Not M3's 0.38 content alpha: a row dimmed that far stops being readable, and
 * the point of dimming during a save is to say "not now", not to hide the values
 * the reader is about to send.
 */
private const val DISABLED_ROW_ALPHA = 0.65f

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
    /**
     * Whether the row takes input.
     *
     * Dead while a save is on the wire, because the save response *replaces* the
     * draft with AniList's answer - so a drag that happened during the request
     * was silently thrown away when the answer landed. The form dimmed as a whole
     * is the honest signal that nothing typed into it would be kept.
     */
    enabled: Boolean = true,
    content: (@Composable () -> Unit)? = null
) {
    val clickable = if (onClick != null) {
        Modifier.clickable(enabled = enabled, onClick = onClick)
    } else {
        Modifier
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else DISABLED_ROW_ALPHA)
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
                            shape = RoundedCornerShape(ExpressiveShapes.ExtraSmallCorner)
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
    onClear: () -> Unit,
    enabled: Boolean = true
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else DISABLED_ROW_ALPHA)
            .clickable(enabled = enabled, onClick = onPick)
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
                        shape = RoundedCornerShape(ExpressiveShapes.ExtraSmallCorner)
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
                // A 48dp hit area around a 20dp glyph. The X used to be a 20dp
                // clickable icon inside a row that is itself clickable to pick a
                // date, so clearing a date was both the smallest target on the form
                // and the one easiest to miss into the picker instead.
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(enabled = enabled, onClick = onClear)
                        .testTag("${testTag}_clear"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppVectorIcons.Close,
                        contentDescription = "Clear $label",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
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

/**
 * One side of the progress stepper.
 *
 * 48dp, not the 42dp it was: Android's minimum touch target. The two steppers are
 * the controls a thumb aims at while looking at a poster rather than the form,
 * and they were the smallest on a page full of 48dp targets. The glyph inside is
 * unchanged - only the box a finger has to hit grew.
 */
@Composable
internal fun StepButton(
    icon: ImageVector,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier
            .size(48.dp)
            .alpha(if (enabled) 1f else DISABLED_ROW_ALPHA)
            .clickable(enabled = enabled, onClick = onClick)
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
