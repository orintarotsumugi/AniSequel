package com.anisequel.ui.screens.notifications

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anisequel.data.model.ActivityDetail
import com.anisequel.data.model.ActivityReply
import com.anisequel.data.repository.AniListRepository
import com.anisequel.data.repository.CommentMediaSettings
import com.anisequel.data.repository.MediaPreferences
import com.anisequel.ui.screens.profile.ActivityCommentsSheet
import com.anisequel.ui.screens.profile.ActivityLikersSheet
import com.anisequel.ui.screens.profile.rememberNowEpochSecond
import com.anisequel.ui.viewmodel.UserActivityViewModel

/**
 * One activity from a notification, opened as the real comment thread.
 *
 * A notification carries an activity id and, at most, a line of context. That is
 * enough to fetch the root and open the same sheet the profile feed uses - no
 * browser, no second comment implementation, and no fabricated post when the
 * activity still exists. If the root fetch fails the replies are still opened;
 * the thread is useful even without the sentence that started it.
 */
@Composable
internal fun NotificationActivityScreen(
    activityId: Int,
    viewerId: Int?,
    repository: AniListRepository,
    onBack: () -> Unit,
    onOpenUser: (Int, String) -> Unit
) {
    val context = LocalContext.current
    val mediaPreferences = remember(context) { MediaPreferences(context.applicationContext) }
    val settings by mediaPreferences.settings.collectAsStateWithLifecycle(
        initialValue = CommentMediaSettings()
    )
    val viewModel: UserActivityViewModel = viewModel(
        factory = UserActivityViewModel.Factory(
            aniListRepository = repository,
            viewerId = viewerId,
            isDemo = false
        )
    )
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val likers by viewModel.likers.collectAsStateWithLifecycle()
    val nowEpochSecond = rememberNowEpochSecond()
    var detail by remember(activityId) { mutableStateOf<ActivityDetail?>(null) }

    // Keyed on chat view as well as the id because DataStore hands the collector
    // its default before the saved answer. Opening once on that default would
    // ignore a reader who turned chat view off until they left the page.
    LaunchedEffect(activityId, settings.chatView) {
        val loaded = repository.getActivityDetail(activityId).getOrNull()
        detail = loaded
        // Open even when the root did not arrive. The sheet's own read is the
        // important half, and a failed context fetch must not turn into a screen
        // that never opens the conversation.
        viewModel.openComments(
            activityId = activityId,
            knownReplyCount = loaded?.replyCount ?: 0,
            chatView = settings.chatView,
            head = loaded?.toThreadHead()
        )
    }

    if (comments == null) BackHandler(onBack = onBack)

    val current = comments
    if (current == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp))
        }
    } else {
        ActivityCommentsSheet(
            state = current,
            canPost = viewerId != null,
            viewerId = viewerId,
            onDismiss = {
                viewModel.closeComments()
                onBack()
            },
            onRetry = {
                viewModel.openComments(
                    activityId = activityId,
                    knownReplyCount = detail?.replyCount ?: current.replyCount,
                    chatView = current.isChatView,
                    head = detail?.toThreadHead() ?: current.head
                )
            },
            onPost = viewModel::postComment,
            onLikeReply = viewModel::toggleReplyLike,
            onShowCommentLikers = { replyId -> viewModel.openReplyLikers(activityId, replyId) },
            onLoadOlder = viewModel::loadOlderComments,
            onRevealNewComments = viewModel::revealNewComments,
            onCheckForNewComments = viewModel::checkForNewComments,
            onRefresh = { atBottom -> viewModel.refreshComments(atBottom) },
            pollIntervalMillis = viewModel.commentPollIntervalMillis,
            polling = settings.pollForNewComments,
            gifProvider = settings.resolvedGifProvider,
            tenorApiKey = settings.tenorApiKey,
            klipyApiKey = settings.klipyApiKey,
            giphyApiKey = settings.giphyApiKey,
            nowEpochSecond = nowEpochSecond,
            onOpenReplyActions = viewModel::openReplyActions,
            onReplyActionMode = viewModel::setReplyActionMode,
            onReplyActionDraft = viewModel::updateReplyActionDraft,
            onSubmitReplyEdit = viewModel::submitReplyEdit,
            onSubmitReplyDelete = viewModel::submitReplyDelete,
            onDismissReplyAction = viewModel::dismissReplyAction
        )
    }

    likers?.let { state ->
        ActivityLikersSheet(
            state = state,
            onDismiss = viewModel::dismissLikers,
            onRetry = {
                val replyId = state.replyId
                if (replyId != null) {
                    viewModel.openReplyLikers(state.activityId, replyId)
                } else {
                    viewModel.openLikers(state.activityId)
                }
            },
            onOpenUser = { user -> onOpenUser(user.id, user.name) }
        )
    }
}

/**
 * Turns the fetched root into the row the comment sheet draws at the top.
 *
 * A `ListActivity` has no body: its sentence is the status, the progress and the
 * title. Building it here keeps that union knowledge out of the sheet, which
 * only knows how to draw an `ActivityReply`. Null means there is nothing honest
 * to show - an empty head is better than an invented post.
 */
private fun ActivityDetail.toThreadHead(): ActivityReply? {
    val id = id ?: return null
    val body = when {
        !text.isNullOrBlank() -> text
        !status.isNullOrBlank() -> buildString {
            append(status.orEmpty().replace('_', ' ').lowercase())
            if (!progress.isNullOrBlank()) append(" ").append(progress)
            media?.title?.userPreferred?.takeIf { it.isNotBlank() }?.let { title ->
                append(" of ").append(title)
            }
        }
        else -> null
    } ?: return null
    return ActivityReply(
        id = id,
        text = body,
        likeCount = likeCount,
        isLiked = isLiked,
        createdAt = createdAt,
        user = user
    )
}
