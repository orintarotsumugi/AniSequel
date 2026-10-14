package com.anisequel.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.ActivityReply
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import com.anisequel.ui.components.markdown.AniListMarkdownParser
import com.anisequel.ui.components.markdown.AniListMarkdownView
import com.anisequel.ui.viewmodel.UserActivityViewModel

/**
 * The comment thread for one activity, with a composer.
 *
 * Everything lives in a single [LazyColumn] rather than a pinned composer under
 * a scrolling list. A pinned composer needs the list and the bar to share a
 * bounded height through a `weight`, and this is a sheet whose height is decided
 * by its content - a `weight` here is a crash waiting for the frame where the
 * sheet is measured unbounded. One list is taller to reach the composer when a
 * thread is long, and cannot fail to lay out.
 *
 * Posting is gated by [canPost]: reading a thread needs no session (AniList
 * serves replies to anyone), but `SaveActivityReply` does, so a signed-out
 * reader gets the thread and a note instead of a text field that will fail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityCommentsSheet(
    state: UserActivityViewModel.CommentsState,
    canPost: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onPost: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .testTag("activity_comments_sheet"),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            item(key = "header") {
                CommentsHeader(count = state.replies.size)
            }

            when {
                state.isLoading -> item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ExpressiveLoadingIndicator(
                            modifier = Modifier.size(28.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                state.loadError != null -> item(key = "load_error") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.loadError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = onRetry) {
                            Text("Try again")
                        }
                    }
                }

                state.replies.isEmpty() -> item(key = "empty") {
                    Text(
                        text = "No comments yet. Be the first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
                    )
                }

                else -> items(
                    items = state.replies,
                    key = { it.id },
                    contentType = { "reply" }
                ) { reply ->
                    CommentRow(reply = reply)
                }
            }

            item(key = "composer") {
                CommentComposer(
                    canPost = canPost,
                    isPosting = state.isPosting,
                    postError = state.postError,
                    onPost = onPost
                )
            }
        }
    }
}

@Composable
private fun CommentsHeader(count: Int) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = AppVectorIcons.ActivityReplies,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Comments",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (count > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun CommentRow(reply: ActivityReply) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        CommentAvatar(
            url = reply.user?.avatar?.large ?: reply.user?.avatar?.medium,
            name = reply.user?.name.orEmpty()
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = reply.user?.name.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (reply.createdAt != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatRelativeSeconds(reply.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            ReplyBody(text = reply.text)
        }
    }
}

@Composable
private fun ReplyBody(text: String?) {
    if (text.isNullOrBlank()) return
    // Parsed once per reply; a comment thread is exactly the kind of list that
    // recomposes on every scroll, and reparsing markdown per frame is the cost
    // this `remember` removes.
    val nodes = remember(text) { AniListMarkdownParser.parse(text) }
    AniListMarkdownView(nodes = nodes)
}

@Composable
private fun CommentAvatar(url: String?, name: String) {
    val shape = CircleShape
    val base = Modifier
        .size(34.dp)
        .clip(shape)
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = base.background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = base.background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.trim().take(1).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun CommentComposer(
    canPost: Boolean,
    isPosting: Boolean,
    postError: String?,
    onPost: (String) -> Unit
) {
    // The draft is owned here and cleared only when the post succeeds: clearing
    // optimistically would delete what the user wrote the moment a request
    // failed, which is the worst possible time to lose it. `awaitingPost` is what
    // distinguishes "a post finished" from "the thread loaded".
    var draft by rememberSaveable { mutableStateOf("") }
    var awaitingPost by remember { mutableStateOf(false) }

    LaunchedEffect(isPosting, postError) {
        if (awaitingPost && !isPosting) {
            if (postError == null) draft = ""
            awaitingPost = false
        }
    }

    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).navigationBarsPadding()) {
        if (!canPost) {
            Text(
                text = "Sign in to AniList to join the conversation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("activity_comment_field"),
                placeholder = { Text("Add a comment") },
                enabled = !isPosting,
                maxLines = 4,
                shape = MaterialTheme.shapes.large
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilledIconButton(
                onClick = {
                    val trimmed = draft.trim()
                    if (trimmed.isEmpty() || isPosting) return@FilledIconButton
                    awaitingPost = true
                    onPost(trimmed)
                },
                enabled = draft.isNotBlank() && !isPosting,
                modifier = Modifier.testTag("activity_comment_send")
            ) {
                Icon(
                    imageVector = AppVectorIcons.ActivitySend,
                    contentDescription = "Post comment"
                )
            }
        }

        if (postError != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = postError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
