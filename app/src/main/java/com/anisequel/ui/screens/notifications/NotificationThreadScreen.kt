package com.anisequel.ui.screens.notifications

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anisequel.data.model.ThreadComment
import com.anisequel.data.repository.AniListRepository
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.novamarkdown.NovaMarkdownWithCovers
import com.anisequel.ui.screens.profile.formatFullTimestamp
import com.anisequel.ui.viewmodel.ThreadViewModel

/**
 * A forum thread notification, opened as a readable page rather than a browser.
 *
 * The inbox carries the thread id, the comment id and the comment body. The body
 * is enough to show the notification's target even when AniList's 5,000-entry page
 * depth stops before that comment; the ids are enough for the two public reads
 * that draw the thread around it. The page is deliberately read-only: replying is
 * a write this route has no draft state for, and a disabled-looking reply box is
 * worse than no box at all. It opens on the newest page AniList will serve and
 * marks the target when that page happens to contain it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotificationThreadScreen(
    threadId: Int,
    commentId: Int?,
    repository: AniListRepository,
    seedComment: ThreadComment? = null,
    onBack: () -> Unit
) {
    val viewModel: ThreadViewModel = viewModel(
        factory = ThreadViewModel.Factory(
            repository = repository,
            threadId = threadId,
            commentId = commentId,
            seedComment = seedComment
        )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.thread?.title.orEmpty().ifBlank { "Thread" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = AppVectorIcons.ChevronRight,
                            contentDescription = "Back",
                            modifier = Modifier.graphicsLayer { rotationZ = 180f }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when {
                state.loading && state.thread == null -> item(key = "thread_loading") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    }
                }

                state.error != null -> item(key = "thread_error") {
                    MessageCard(
                        title = "That thread didn't open",
                        message = state.error.orEmpty(),
                        action = "Try again",
                        onAction = viewModel::load
                    )
                }

                else -> {
                    val thread = state.thread
                    if (thread != null) {
                        item(key = "thread_head") {
                            ThreadHeadCard(
                                title = thread.title.orEmpty().ifBlank { "Thread" },
                                body = thread.body.orEmpty(),
                                author = thread.user?.name,
                                createdAt = thread.createdAt,
                                category = thread.categories?.firstOrNull()?.name,
                                replyCount = thread.replyCount,
                                viewCount = thread.viewCount
                            )
                        }
                    }

                    if (state.threadError != null && state.thread == null) {
                        item(key = "thread_notice") {
                            MessageCard(
                                title = "The opening post is missing",
                                message = state.threadError.orEmpty(),
                                action = "Reload",
                                onAction = viewModel::load
                            )
                        }
                    }

                    // The row already carries the comment this notification is
                    // about. It is drawn before the page because a thread can be
                    // deeper than AniList's 5,000-entry limit, so the target may
                    // never appear in a page this screen is allowed to fetch.
                    val seed = state.seedComment
                    if (seed != null && state.comments.none { it.id == seed.id }) {
                        item(key = "thread_seed") {
                            CommentCard(
                                author = seed.user?.name ?: "Someone",
                                text = seed.comment.orEmpty(),
                                createdAt = seed.createdAt,
                                highlighted = true,
                                label = "The comment this notification is about"
                            )
                        }
                    }

                    item(key = "comments_heading") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Comments",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = state.thread?.replyCount?.toString().orEmpty(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (state.comments.isEmpty() && state.commentsError == null) {
                        item(key = "comments_empty") {
                            Text(
                                text = "No comments yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }

                    items(state.comments, key = { it.id }) { comment ->
                        CommentCard(
                            author = comment.user?.name ?: "Someone",
                            text = comment.comment.orEmpty(),
                            createdAt = comment.createdAt,
                            highlighted = comment.id == state.highlightCommentId
                        )
                    }

                    if (state.commentsError != null) {
                        item(key = "comments_error") {
                            MessageCard(
                                title = "Comments didn't load",
                                message = state.commentsError.orEmpty(),
                                action = "Try again",
                                onAction = viewModel::retryComments
                            )
                        }
                    } else if (state.loadingMore) {
                        item(key = "comments_loading_more") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    } else if (state.hasMore) {
                        item(key = "comments_more") {
                            TextButton(
                                onClick = viewModel::loadMore,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Load older comments")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadHeadCard(
    title: String,
    body: String,
    author: String?,
    createdAt: Int?,
    category: String?,
    replyCount: Int?,
    viewCount: Int?
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            if (!category.isNullOrBlank()) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            if (!author.isNullOrBlank() || createdAt != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = listOfNotNull(
                        author?.takeIf { it.isNotBlank() },
                        createdAt?.let(::formatFullTimestamp)
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (body.isBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "This thread has no opening post.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                NovaMarkdownWithCovers(markdown = body)
            }
            if (replyCount != null || viewCount != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = listOfNotNull(
                        replyCount?.let { "$it comments" },
                        viewCount?.let { "$it views" }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CommentCard(
    author: String,
    text: String,
    createdAt: Int?,
    highlighted: Boolean,
    label: String? = null
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = if (highlighted) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            label?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = author,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                createdAt?.let {
                    Text(
                        text = formatFullTimestamp(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (text.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                NovaMarkdownWithCovers(markdown = text)
            }
        }
    }
}

@Composable
private fun MessageCard(
    title: String,
    message: String,
    action: String,
    onAction: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}
