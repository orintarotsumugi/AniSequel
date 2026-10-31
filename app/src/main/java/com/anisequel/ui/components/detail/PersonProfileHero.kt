package com.anisequel.ui.components.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.PersonKind
import com.anisequel.data.model.PersonProfileState
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.openExternalUrl

/**
 * The cover: the portrait behind its own name, and the portrait again as the cover.
 *
 * Split out of [PersonProfileSheet] when the sheet crossed the file ceiling: this is
 * artwork, and a change to it (a scrim stop, a corner, the action in the corner) has
 * nothing to do with the sheet's scroll accounting or its three body states. The
 * shared subject is the layout, not a reason to change.
 *
 * The title page's hero, at the size a person's artwork can carry it. The artwork is
 * the key visual (see `InfoHero` for why it is the poster and not the banner, and why
 * it is sharp), the scrim does the legibility, and the cover itself is a rounded
 * rectangle - the shape every other cover in the app is.
 *
 * The corner is `extraSmall` (10dp), not `medium` (20dp). On a portrait this narrow
 * 20dp is more than a third of its width rounded away on each side, which stops
 * reading as a cover and starts reading as a lozenge; the same correction was made to
 * every poster on this sheet. See `PersonStaffCredits`.
 *
 * This replaces a header that was one 84dp face clipped to a pill, on the sheet's
 * container colour: a portrait is a rectangle, and a pill drawn around one takes the
 * corners off the art and reads as a placeholder rather than as a cover.
 */
@Composable
internal fun ProfileHero(state: PersonProfileState.Loaded) {
    // The scrim has to end on the colour the sheet itself is drawn on, or the cover
    // hands over to the body with a visible seam. The sheet's container is
    // `surfaceContainerLow` - see the `ModalBottomSheet` call in `PersonProfileSheet`.
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HERO_HEIGHT)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .testTag("profile_hero")
    ) {
        if (state.portraitUrl != null) {
            AsyncImage(
                model = state.portraitUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }

        // Weighted for sharp artwork: light at the top so the art still reads, strong
        // at the bottom where the name and the cover are. The same stops as the title
        // page's hero, for the same reason.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.10f),
                        0.34f to Color.Black.copy(alpha = 0.24f),
                        0.60f to Color.Black.copy(alpha = 0.54f),
                        0.84f to Color.Black.copy(alpha = 0.74f),
                        1f to surface
                    )
                )
        )

        // The escape hatch, on the one part of the page that is on screen the moment
        // the sheet opens. It used to be only the text button under the body, which
        // on a creator's page sits under up to twenty-five credits - a reader who
        // wanted AniList's comment thread had to scroll the whole filmography to
        // find the link to it.
        state.siteUrl?.let { siteUrl ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 12.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.34f))
                    .bouncyPress(pressedScale = 0.9f)
                    .clickable(onClickLabel = "Open on AniList") {
                        openExternalUrl(context, siteUrl)
                    }
                    .testTag("profile_anilist_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppVectorIcons.OpenInBrowser,
                    contentDescription = "Open on AniList",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Box(
                modifier = Modifier
                    .width(PORTRAIT_WIDTH)
                    .height(PORTRAIT_HEIGHT)
                    .shadow(
                        elevation = 16.dp,
                        shape = MaterialTheme.shapes.extraSmall,
                        ambientColor = Color.Black,
                        spotColor = Color.Black
                    )
                    .clip(MaterialTheme.shapes.extraSmall)
                    .border(1.dp, Color.White.copy(alpha = 0.22f), MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .testTag("profile_portrait")
            ) {
                AsyncImage(
                    model = state.portraitUrl,
                    contentDescription = state.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.name ?: "Unknown",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    // One shadow on one short block of text, exactly as the title
                    // page's lockup does it: what keeps white type legible over a pale
                    // portrait without darkening the whole cover.
                    modifier = Modifier.shadow(
                        elevation = 6.dp,
                        shape = RectangleShape,
                        ambientColor = Color.Black,
                        spotColor = Color.Black
                    )
                )
                // The name in its own script, asked for by both queries and until
                // now never drawn - a staff page said "Hayao Miyazaki" and never
                // 宮崎駿. One line: the romanised name above is what the page is
                // named after, and this is the identifier beside it, not a second
                // title.
                state.nativeName?.let { native ->
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = native,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                // A creator's occupations where a character says "Character": the
                // staff hero used to say the literal word "Person", which is the
                // one thing on the page the reader already knew. "Director ·
                // Animator" is what AniList's own page leads with, and it is
                // already on the wire - `primaryOccupations`, not a new request.
                Text(
                    text = heroSubtitle(state),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.88f),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (state.aliases.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = state.aliases.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * The line under the name on the hero.
 *
 * A creator leads with what they do, because that is how AniList introduces them
 * too - "Director, Animator" is the answer to "who is this?". A character has no
 * occupation, so the line is the kind itself; "Creator" is the fallback for a
 * staff member whose occupations AniList did not answer, and it still says more
 * than "Person" did.
 */
private fun heroSubtitle(state: PersonProfileState.Loaded): String = when {
    state.occupations.isNotEmpty() -> state.occupations.joinToString(" · ")
    state.kind == PersonKind.STAFF -> "Creator"
    else -> "Character"
}

/** Tall enough for the artwork to be the header, short enough to leave the page below. */
private val HERO_HEIGHT = 236.dp

/** The cover on the hero: poster proportions, drawn over the artwork it repeats. */
private val PORTRAIT_WIDTH = 92.dp
private val PORTRAIT_HEIGHT = 128.dp
