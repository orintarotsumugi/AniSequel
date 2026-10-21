package com.anisequel.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anisequel.data.network.AniListErrorKind
import com.anisequel.ui.components.ApiFailureBody
import com.anisequel.ui.components.ShimmerBox
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import com.anisequel.ui.viewmodel.ApiStatusUi

/**
 * The profile screen's waiting state.
 *
 * A spinner and a sentence, not the dashboard's shimmer cards. There is nothing
 * shaped like a list to sketch: the profile is one header and a few rows that
 * change shape entirely between a person who has pinned a lot and one who has
 * pinned nothing, and a skeleton of the wrong shape would be a lie about the
 * page for the length of the fetch.
 */
@Composable
fun ProfileLoadingView(
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp)
            .testTag("profile_loading"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ExpressiveLoadingIndicator(
            modifier = Modifier.size(44.dp),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * The profile screen's failure state.
 *
 * A dead session offers the way back to sign-in rather than a Retry button that
 * fails identically every time - the same rule the dashboard follows, and for
 * the same reason: a retry that cannot work reads as the app not knowing what is
 * wrong, and here the user has just been told their sign-in succeeded.
 */
@Composable
fun ProfileErrorView(
    message: String,
    kind: AniListErrorKind?,
    canRetry: Boolean,
    isAuthError: Boolean,
    apiStatus: ApiStatusUi,
    onCheckApiStatus: () -> Unit,
    onRetry: () -> Unit,
    onSignInAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp)
            .testTag("profile_error"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ApiFailureBody(
            kind = kind,
            message = message,
            isAuthError = isAuthError,
            canRetry = canRetry,
            status = apiStatus,
            onCheckStatus = onCheckApiStatus,
            onRetry = onRetry,
            onSignInAgain = onSignInAgain,
            // Existing automation selects these by name; keep the profile's
            // tags rather than letting the shared body rename them.
            retryTestTag = "profile_retry_button",
            signInTestTag = "profile_sign_in_again_button"
        )
    }
}

/**
 * "Still counting", for the surfaces whose numbers come from the media list.
 *
 * The profile is published as soon as AniList answers and the list - one request
 * per 500 entries - is merged in afterwards, so for a second or two the Stats tab,
 * and the Library page beside it, have a real, complete profile and an unfinished
 * tally.
 *
 * Rendering that as zeroes would be the worst of the options: the summary card
 * would read "0 / 0" for a person with 900 entries, and it would read as an
 * answer rather than as a wait. A spinner over the whole tab would be the other
 * wrong answer, discarding the charts that *did* arrive with the profile. This is
 * the middle: the page states clearly which part is missing and continues.
 */
@Composable
internal fun PendingDataNote(
    message: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .widthIn(max = ProfileMaxContentWidth)
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("profile_pending_data"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExpressiveLoadingIndicator(
            modifier = Modifier.size(18.dp),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** AniList covers are 2:3, the same constant the card uses. */
private const val ACTIVITY_POSTER_ASPECT = 2f / 3f

/** Cards drawn while the feed is loading. Four fill a phone screen. */
private const val ACTIVITY_SKELETON_ROWS = 4

/**
 * The activity feed's waiting state, sketched as the cards that are coming.
 *
 * The tab used to draw a single spinner in the place those cards were about to
 * occupy, so opening it twice looked like two different screens: first a lone
 * dot under a heading, then twenty-five dense rows. The feed is the one place on
 * this screen where the shape *is* known in advance - every card is a poster, a
 * status pill, two lines and a row of controls in the same place - so the
 * waiting state is that shape, and the header the data arrives *into* is already
 * drawn when it does.
 *
 * The phase is hoisted and passed to every block - one driver for the whole
 * stack, phase-locked, which is the rule `rememberShimmerPhase` exists to
 * state. The rows are a plain `Column`, not a second lazy list: four cards do
 * not need a recycler, and this composable is itself one item of the feed's
 * `LazyColumn`.
 */
@Composable
internal fun ActivityFeedSkeleton(
    phase: State<Float>,
    modifier: Modifier = Modifier,
    rows: Int = ACTIVITY_SKELETON_ROWS
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        repeat(rows) { ActivityCardSkeleton(phase = phase) }
    }
}

/**
 * One card's skeleton, at the card's own paddings and proportions.
 *
 * Sized from the real card rather than from a taste: the header line is 28dp
 * because the avatar it stands in for is, and the poster is 96dp by 2:3 because
 * that is what [ActivityCard] lays out. The height a reader pays for the
 * placeholder is the height of the thing that replaces it, which is the whole
 * point of the exercise.
 */
@Composable
private fun ActivityCardSkeleton(
    phase: State<Float>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .widthIn(max = ProfileMaxContentWidth)
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShimmerBox(
                    modifier = Modifier.size(28.dp),
                    shape = MaterialTheme.shapes.extraSmall,
                    phase = phase
                )
                Spacer(modifier = Modifier.width(10.dp))
                ShimmerBox(
                    modifier = Modifier
                        .width(92.dp)
                        .height(12.dp),
                    phase = phase
                )
                Spacer(modifier = Modifier.weight(1f))
                ShimmerBox(
                    modifier = Modifier
                        .width(44.dp)
                        .height(10.dp),
                    phase = phase
                )
            }

            Row {
                ShimmerBox(
                    modifier = Modifier
                        .width(96.dp)
                        .aspectRatio(ACTIVITY_POSTER_ASPECT),
                    shape = MaterialTheme.shapes.medium,
                    phase = phase
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    ShimmerBox(
                        modifier = Modifier
                            .width(72.dp)
                            .height(22.dp),
                        shape = MaterialTheme.shapes.small,
                        phase = phase
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(11.dp),
                        phase = phase
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .height(16.dp),
                        phase = phase
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShimmerBox(
                            modifier = Modifier
                                .width(58.dp)
                                .height(40.dp),
                            shape = MaterialTheme.shapes.small,
                            phase = phase
                        )
                        ShimmerBox(
                            modifier = Modifier
                                .width(50.dp)
                                .height(40.dp),
                            shape = MaterialTheme.shapes.small,
                            phase = phase
                        )
                        ShimmerBox(
                            modifier = Modifier
                                .width(50.dp)
                                .height(40.dp),
                            shape = MaterialTheme.shapes.small,
                            phase = phase
                        )
                    }
                }
            }
        }
    }
}

/** Comment rows drawn while a thread is loading. */
private const val COMMENT_SKELETON_ROWS = 3

/**
 * The comment sheet's waiting state, shaped like the rows that are coming.
 *
 * A comment row is an avatar, a name, a body and a heart, and the sheet draws
 * the same three rows every time it opens; a spinner in their place was the one
 * screen in the app where the layout was knowable and thrown away. The widths
 * vary a little row to row so the stack reads as text arriving rather than as a
 * table of grey rectangles.
 */
@Composable
internal fun CommentRowsSkeleton(
    phase: State<Float>,
    modifier: Modifier = Modifier,
    rows: Int = COMMENT_SKELETON_ROWS
) {
    val nameWidths = listOf(88.dp, 64.dp, 104.dp)
    val bodyWidths = listOf(0.88f, 0.6f, 0.42f)

    Column(modifier = modifier.fillMaxWidth()) {
        repeat(rows) { index ->
            val bodyWidth = bodyWidths[index % bodyWidths.size]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Top
            ) {
                ShimmerBox(
                    modifier = Modifier.size(34.dp),
                    shape = CircleShape,
                    phase = phase
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    ShimmerBox(
                        modifier = Modifier
                            .width(nameWidths[index % nameWidths.size])
                            .height(12.dp),
                        phase = phase
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(bodyWidth)
                            .height(12.dp),
                        phase = phase
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(bodyWidth * 0.7f)
                            .height(12.dp),
                        phase = phase
                    )
                }
            }
        }
    }
}

/** People rows drawn while a list of followers or likers is loading. */
private const val PERSON_SKELETON_ROWS = 5

/**
 * A list of people, waiting: an avatar and a name per row.
 *
 * Shared by the likers sheet and the Social tab because the two lists are the
 * same row - the sheets both draw a face and a name - and differ only in the
 * avatar's size and the shape it is clipped to, which is what the parameters
 * carry. The widths vary so five identical bars do not read as a loading
 * *table*.
 */
@Composable
internal fun PersonRowsSkeleton(
    phase: State<Float>,
    avatarSize: Dp,
    avatarShape: Shape,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
    rows: Int = PERSON_SKELETON_ROWS
) {
    val nameWidths = listOf(0.5f, 0.34f, 0.58f, 0.4f, 0.46f)

    Column(modifier = modifier.fillMaxWidth()) {
        repeat(rows) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShimmerBox(
                    modifier = Modifier.size(avatarSize),
                    shape = avatarShape,
                    phase = phase
                )
                Spacer(modifier = Modifier.width(12.dp))
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(nameWidths[index % nameWidths.size])
                        .height(13.dp),
                    phase = phase
                )
            }
        }
    }
}