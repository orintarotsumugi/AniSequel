package com.anisequel.ui.viewmodel

import com.anisequel.data.model.ProfileActivity
import com.anisequel.data.repository.AniListRepository
import com.anisequel.domain.usecase.ContentLimits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The new-activity composer, and the one write it makes.
 *
 * ## Why this is not part of `UserActivityViewModel`
 *
 * The same split [CommentThreadStore] makes, for the same reason: the feed is a
 * paged read of somebody's history, while this is one draft and one mutation with
 * its own failure state. They share exactly one thing - the posted row lands at
 * the top of the feed - and that is passed in as [onPosted] rather than this class
 * reaching back into a list it does not own.
 *
 * ## Why the state is nullable
 *
 * Null means the sheet is closed. The sheet's visibility is derived from it, so
 * there is no frame where the composer is up with no draft under it, and a
 * successful post closes it by writing null rather than by setting a flag the
 * caller has to remember to read.
 *
 * ## Why [enabled] is decided here
 *
 * A text activity is written to the *viewer's* account. The friends and site-wide
 * feeds are selections rather than somebody's page, and the demo has no session -
 * so none of them may open this composer. The screen also hides the control, but
 * the store refuses too: a caller that forgot the gate would otherwise post into a
 * feed it does not belong to.
 */
class ActivityComposerStore(
    private val repository: AniListRepository,
    private val scope: CoroutineScope,
    /** False for the demo, a signed-out session, and the two selection feeds. */
    val enabled: Boolean,
    /** Hands the stored post back to the feed that will draw it. */
    private val onPosted: (ProfileActivity) -> Unit
) {

    /**
     * The composer's draft, its request and its last failure.
     *
     * [isPosting] is in the state rather than beside it so the field, the button
     * and the scrim all read one flag: a second tap before the first answer lands
     * must not send the post twice.
     */
    data class ComposerState(
        val text: String = "",
        val isPosting: Boolean = false,
        val error: String? = null
    )

    private val _state = MutableStateFlow<ComposerState?>(null)
    val state: StateFlow<ComposerState?> = _state.asStateFlow()

    /** Opens the composer with an empty draft, if this feed may post at all. */
    fun open() {
        if (!enabled) return
        _state.value = ComposerState()
    }

    /** Rewrites the draft, clipped to `SaveTextActivity`'s ceiling. */
    fun updateDraft(text: String) {
        val current = _state.value ?: return
        if (current.isPosting) return
        _state.value = current.copy(text = ContentLimits.clipStatus(text), error = null)
    }

    /**
     * Closes the composer.
     *
     * Refused while the post is in flight: the answer still has to be written
     * somewhere, and a sheet that vanished mid-request would drop a failure the
     * reader needs to see. This is the scrim tap and the back gesture.
     */
    fun dismiss() {
        if (_state.value?.isPosting == true) return
        _state.value = null
    }

    /**
     * Posts the draft as a text activity.
     *
     * The draft is checked against the API's window before a request is spent -
     * `SaveTextActivity.text` is Min 5, Max 10000 - and a failure keeps the text
     * and the reason in the sheet so the retry is one tap rather than a rewrite.
     */
    fun post() {
        val current = _state.value ?: return
        if (current.isPosting) return
        val text = current.text.trim()
        if (ContentLimits.checkStatus(text) != ContentLimits.StatusVerdict.Valid) return

        _state.value = current.copy(isPosting = true, error = null)
        scope.launch {
            repositoryResult { repository.postTextActivity(text) }.fold(
                onSuccess = { post ->
                    _state.value = null
                    onPosted(post)
                },
                onFailure = { error ->
                    _state.value = _state.value
                        ?.takeIf { it.isPosting }
                        ?.copy(
                            isPosting = false,
                            error = error.message ?: "Couldn't post that activity."
                        )
                }
            )
        }
    }
}
