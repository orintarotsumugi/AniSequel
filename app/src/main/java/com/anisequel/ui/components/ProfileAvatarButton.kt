package com.anisequel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.expressiveHoldGesture

/**
 * The avatar, as the way into the person's own AniList profile.
 *
 * It was a picture until the profile screen existed. Three things changed with
 * it, and all three are the reason it is not an `IconButton`:
 *
 *  - **Tap *and* hold both open it.** [expressiveHoldGesture] takes both
 *    callbacks, and a gesture that answers to either one means a user who does
 *    not know which of the two they did still gets the screen. The one thing a
 *    hold-to-open target must never do is nothing at all.
 *  - **No ripple, no indirection.** The gesture modifier draws its own press
 *    scale from the same spring the `IconButton`s beside it use, so a bar that
 *    mixes the two does not read as two kinds of control.
 *  - **A `Role.Button` and a description, by hand.** `expressiveHoldGesture`
 *    installs a pointer handler, not a clickable, so nothing sets the semantics
 *    a screen reader and TalkBack's keyboard navigation rely on. A control that
 *    only looks like a button is a control that cannot be reached without a
 *    screen reader seeing.
 *
 * The avatar is 36dp, which is under the 48dp minimum tap target. The gesture
 * area is padded out past that by the padding below, which is why the image and
 * the padding are one element rather than two.
 *
 * Inert while [enabled] is false, which is the state the dashboard is in for the
 * second or two before the viewer query returns, and the Library page is in until
 * its overview does. Without that, the placeholder box is tappable and the only
 * profile it could open is the demo one - so a tap during a cold start would
 * answer with a stranger's bio and five studios nobody chose. A control that
 * cannot do the thing must not accept the gesture.
 *
 * ## Why this is a shared file
 *
 * The dashboard's top bar and the Library page both lead with it, and both go to
 * the same place: the reader's own profile. A second copy is how the two entries
 * drift apart - one keeping the `Role`, the other losing the inert state - and a
 * pair of controls that look identical while behaving differently is the worst
 * version of both.
 */
@Composable
fun ProfileAvatarButton(
    avatarUrl: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** What this screen's tests find the button by; the two entries differ. */
    testTag: String = "profile_button"
) {
    Box(
        modifier = modifier
            .then(
                if (enabled) {
                    Modifier.expressiveHoldGesture(onHold = onClick, onClick = onClick)
                } else {
                    Modifier
                }
            )
            // Inside the gesture, not outside it: a padding *before* a pointer-input
            // node shrinks the node, so the tap target stayed the 36dp avatar and the
            // comment promising a padded-out area described something that was not
            // there. Outside the gesture the target is the 48dp the file claims.
            .padding(6.dp)
            .semantics {
                role = Role.Button
                if (enabled) {
                    contentDescription = "Open your AniList profile"
                } else {
                    disabled()
                }
            }
            .testTag(testTag)
    ) {
        Avatar(avatarUrl = avatarUrl)
    }
}

@Composable
private fun Avatar(avatarUrl: String?) {
    if (avatarUrl != null) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = null,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(ExpressiveShapes.ExtraSmallCorner))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(ExpressiveShapes.ExtraSmallCorner))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppVectorIcons.SequelJump,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
