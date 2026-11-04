package com.anisequel.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisequel.domain.usecase.ContentLimits
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.rememberFullHeightSheetState
import com.anisequel.ui.viewmodel.ActivityComposerStore
import com.anisequel.ui.viewmodel.UserActivityViewModel
import kotlinx.coroutines.delay

/**
 * Write a new activity - a text post on the viewer's own AniList stream.
 *
 * ## Why this is its own sheet rather than a fourth comment composer
 *
 * A text activity is not a reply: there is no thread to answer, the body is a post
 * in its own right, and AniList's floor is five characters rather than two (see
 * [ContentLimits.checkStatus]). It also has no GIF picker or photo upload, because
 * it is the one thing on this tab that is meant to be read as a status rather than
 * a conversation - borrowing the comment composer would have offered controls the
 * mutation has nothing to do with.
 *
 * ## The write lives in the ViewModel
 *
 * [ActivityComposerStore.ComposerState] owns the draft, the in-flight flag and the
 * failure, so a rotation or a recomposition cannot drop the reason the last
 * attempt failed. This file only draws it and asks for changes; the success path -
 * inserting the posted row at the top of the feed - is the ViewModel's, because it
 * is the only place that has the feed.
 *
 * ## Focus and the keyboard
 *
 * The field is focused once, after the sheet's entrance has settled. Requesting
 * focus on the first frame races Material's sheet animation: the window is not
 * accepting input yet, so the keyboard would open a beat late or not at all. The
 * short delay is the same one a hand-built sheet needs before `requestFocus`
 * takes effect, and it is a frame-window, not a network wait.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActivityComposerSheet(
    state: ActivityComposerStore.ComposerState,
    onDraftChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    val sheetState = rememberFullHeightSheetState()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    val verdict = ContentLimits.checkStatus(state.text)
    val canPost = verdict == ContentLimits.StatusVerdict.Valid && !state.isPosting

    LaunchedEffect(Unit) {
        delay(FOCUS_SETTLE_MILLIS)
        focusRequester.requestFocus()
    }

    // Hide the keyboard when the post goes out: the sheet closes on success, and
    // on the failure path the error belongs beside the field rather than behind a
    // keyboard that already covers the button.
    LaunchedEffect(state.isPosting) {
        if (state.isPosting) keyboard?.hide()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // The sheet pads for the keyboard itself, once, the same way the comment
        // sheet does: Material's default insets resize the measured surface while
        // the keyboard animates, which is exactly when the field is being focused.
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 16.dp)
                .testTag("activity_composer_sheet")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppVectorIcons.NewActivity,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "New activity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Posts to your AniList profile stream",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    enabled = !state.isPosting,
                    modifier = Modifier
                        .size(44.dp)
                        .bouncyPress(pressedScale = 0.9f)
                        .testTag("activity_composer_close")
                ) {
                    Icon(
                        imageVector = AppVectorIcons.Close,
                        contentDescription = "Close the activity composer",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = state.text,
                onValueChange = onDraftChange,
                enabled = !state.isPosting,
                placeholder = {
                    Text("What are you watching, reading or thinking about?")
                },
                minLines = 4,
                maxLines = 8,
                shape = MaterialTheme.shapes.large,
                supportingText = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = supportingLine(verdict),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (verdict == ContentLimits.StatusVerdict.TooShort) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Text(
                            text = "${state.text.length} / ${ContentLimits.STATUS_MAX}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag("activity_composer_field")
            )

            state.error?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("activity_composer_error")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Markdown works: **bold**, *italics*, links.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onDismiss,
                    enabled = !state.isPosting,
                    modifier = Modifier
                        .bouncyPress(pressedScale = 0.95f)
                        .testTag("activity_composer_cancel")
                ) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onSubmit,
                    enabled = canPost,
                    modifier = Modifier
                        .bouncyPress(pressedScale = 0.95f)
                        .testTag("activity_composer_post")
                ) {
                    // The app's own expressive spinner rather than a renamed label
                    // on its own: a button that only changes its text reads as a
                    // relabelled control, and the write is the one thing on this
                    // sheet that is ever in flight.
                    if (state.isPosting) {
                        ExpressiveLoadingIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (state.isPosting) "Posting…" else "Post")
                }
            }
        }
    }
}

/**
 * The line under the field: the markdown hint while it is empty, the API's floor
 * while it is too short, and nothing to say once it can be posted.
 *
 * The counter is drawn beside this rather than folded into it, so the two never
 * fight over the same line on a narrow phone.
 */
private fun supportingLine(verdict: ContentLimits.StatusVerdict): String = when (verdict) {
    ContentLimits.StatusVerdict.TooShort ->
        "At least ${ContentLimits.STATUS_MIN} characters."
    ContentLimits.StatusVerdict.Blank -> "AniList Markdown is supported."
    ContentLimits.StatusVerdict.TooLong -> "That is longer than AniList accepts."
    ContentLimits.StatusVerdict.Valid -> ""
}

/**
 * Draws the composer whenever the feed has one open, and nothing otherwise.
 *
 * Called by the screen rather than by the Activity tab for the reason every other
 * sheet is: a `ModalBottomSheet` is a window of its own, and one drawn from inside
 * the tab `AnimatedContent` disposes is torn down with the tab - a half-written
 * post included.
 */
@Composable
internal fun ActivityComposerSheetHost(activityViewModel: UserActivityViewModel) {
    val composer by activityViewModel.composer.collectAsStateWithLifecycle()
    val state = composer ?: return

    ActivityComposerSheet(
        state = state,
        onDraftChange = activityViewModel::updateComposerDraft,
        onDismiss = activityViewModel::dismissComposer,
        onSubmit = activityViewModel::submitTextActivity
    )
}

/**
 * How long the sheet takes to become an input target before the field asks for
 * focus. Long enough for the entrance to start, short enough not to feel staged.
 */
private const val FOCUS_SETTLE_MILLIS = 180L
