package com.anisequel.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.expressive.expressiveShape
import com.anisequel.ui.components.icons.Icons
import com.anisequel.ui.components.icons.automirrored.filled.KeyboardArrowRight
import com.anisequel.ui.components.icons.automirrored.filled.OpenInNew
import com.anisequel.ui.components.openExternalUrl
import com.anisequel.ui.theme.AniSequelTheme

/**
 * Who made AniSequel, and who helped without being asked.
 *
 * ## Why this is a page of its own
 *
 * A reader who wants to know who is behind the app has a different question from
 * one who is checking a version number or looking for the issue tracker, and the
 * About page was already six cards deep. It is a shelf row like the rest: one tap,
 * one screen, no scrolling past diagnostics to find a name.
 *
 * ## Why every credit is a face, a handle and a link
 *
 * A credit is only a thank-you if the reader can reach the thing being thanked.
 * "Thanks to itsmechinmoy" is a dead end unless the profile and the app they wrote
 * are one tap away, and the interesting thing about a helper *here* is the app -
 * a reader who wonders what Dantotsu is should not have to type it into a search
 * box. So each helper is a whole tappable row (photo, name, `github.com/<handle>`)
 * that opens their GitHub, with the app they wrote as a chip underneath.
 *
 * The avatars are GitHub's own. `https://github.com/<user>.png?size=256` answers
 * with a 302 to `avatars.githubusercontent.com/u/<id>?s=256`; these are the
 * resolved URLs, so the image request is one hop rather than two. They are pinned
 * as data rather than fetched from the GitHub API: one more network call and a
 * rate limit to render a page of four names is not a trade this screen needs, and
 * a profile picture is the least time-sensitive thing in the app.
 *
 * ## Why there is a quote on the developer card
 *
 * The person who wrote the app wrote the sentence too. Paraphrasing it into
 * marketing copy would be a small lie about the voice of the thing, and this
 * screen is the one place in the app that is about a person rather than a setting.
 */
@Composable
internal fun CreditsSectionBody(modifier: Modifier = Modifier) {
    SettingsScrollColumn(modifier) {
        DeveloperCard()
        HelpersCard()
        MentionsCard()
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * The developer, as a hero card rather than a `SectionCard`.
 *
 * The same treatment the account gets on the Settings shelf, for the same reason:
 * this is the answer to "who is this from", and it is glanced at before the rows
 * below it are read. The profile link is a full-width button rather than a chip:
 * on this card it is the action, not a footnote.
 */
@Composable
private fun DeveloperCard() {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = ExpressiveShapes.extraLarge,
        color = scheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        // The same edge the rest of Settings uses. A 38% outlineVariant is about
        // 1.3:1 in the light scheme - a card border that is not there.
        border = BorderStroke(1.dp, AniSequelTheme.emphasis.boundary),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("credits_developer_card")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CreditAvatar(
                    url = DEVELOPER.avatarUrl,
                    name = DEVELOPER.name,
                    size = 72.dp,
                    shape = AvatarShape.FLOWER,
                    testTag = "credits_dev_avatar"
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = DEVELOPER.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = handleLine(DEVELOPER),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "\"A student and vibe coder. I'm not that smart, but I make " +
                    "good things for Android and web apps. No tracking. You can read " +
                    "my code.\"",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            // "Open on GitHub" rather than the address as the label: the handle is
            // printed above, the button is one line by design, and a label that is
            // 27 characters long is a label that ellipsises on a small phone.
            SettingsButton(
                text = "Open on GitHub",
                onClick = { openExternalUrl(context, DEVELOPER.profileUrl) },
                icon = AppVectorIcons.GitHub,
                variant = SettingsButtonVariant.Tonal,
                fillWidth = true,
                testTag = "credits_dev_github"
            )
            SettingsButton(
                text = "Read the source",
                onClick = { openExternalUrl(context, GITHUB_REPOSITORY_URL) },
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                variant = SettingsButtonVariant.Text,
                fillWidth = true,
                testTag = "credits_dev_source"
            )
        }
    }
}

/**
 * Three developers whose apps this one learned from.
 *
 * A `SectionCard` rather than three hero cards: they are one subject ("the help"),
 * and three surfaces with a border each would read as three sections of the page.
 */
@Composable
private fun HelpersCard() {
    SectionCard(
        title = "Helpers",
        icon = AppVectorIcons.SectionHelp,
        subtitle = "Other app developers who made this one better"
    ) {
        HELPERS.forEachIndexed { index, credit ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 14.dp),
                    color = AniSequelTheme.emphasis.divider
                )
            }
            CreditBlock(credit = credit)
        }
    }
}

/**
 * One helper: a face, who they are, why they are thanked, and where to go.
 *
 * The whole top row is the profile link - a 52dp target with the handle printed
 * under the name - because a name is what the reader wants to look up, and asking
 * them to hit a 32dp chip for it was making the most likely tap the smallest one.
 * The app they wrote stays a chip, since it is a *second* destination.
 */
@Composable
private fun CreditBlock(credit: Credit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    // Shared with `bouncyPress`, like the hub, so the press comes from this row's
    // own clickable and cancels when the gesture becomes a scroll. Without it the
    // spring falls back to a pointer loop and every credit springs as the thumb
    // passes over it on the way down the page.
    val interaction = remember { MutableInteractionSource() }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .bouncyPress(pressedScale = 0.98f, interactionSource = interaction)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = "Open ${credit.name} on GitHub"
                ) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    openExternalUrl(context, credit.profileUrl)
                }
                .testTag("credits_${credit.handle}_profile")
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CreditAvatar(
                url = credit.avatarUrl,
                name = credit.name,
                size = 52.dp,
                shape = AvatarShape.CIRCLE,
                testTag = "credits_${credit.handle}_avatar"
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = credit.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface
                )
                Text(
                    text = credit.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = handleLine(credit),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = AniSequelTheme.emphasis.muted,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        CreditLinks(links = credit.apps)
    }
}

/**
 * The honourable mentions, as pills, plus the closing sentence.
 *
 * A flow of capsules rather than a fourth list: none of these is somebody with an
 * address to open, and a row of names that look like links but do nothing would
 * be worse than a row of names that look like a thank-you.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MentionsCard() {
    SectionCard(
        title = "Honourable mentions",
        icon = AppVectorIcons.Star,
        subtitle = "Friends, readers, and one closing thank you"
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MENTIONS.forEach { mention ->
                MentionPill(text = mention)
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        CollapsibleText(
            text = "And you - the reader we build for. AniSequel asks for no " +
                "account to browse the demo list, tracks nothing, and has nothing to " +
                "buy; Privacy spells out exactly what leaves the device and what " +
                "never does.",
            collapsedLines = 4
        )
    }
}

@Composable
private fun MentionPill(text: String) {
    Surface(
        shape = ExpressiveShapes.pill,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, AniSequelTheme.emphasis.divider)
    ) {
        // No `maxLines`: a thank-you that ends in an ellipsis is a thank-you the
        // reader cannot finish, and a wrapped pill is still a pill.
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

/** A row of link chips that wraps rather than squeezing two long labels. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreditLinks(links: List<CreditLink>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        links.forEach { link -> CreditChip(link = link) }
    }
}

/** Which outline a face is cut to. */
private enum class AvatarShape {
    /** The developer's hero tile: the app's own expressive flower. */
    FLOWER,

    /** A helper's face: a circle, because a photograph in a polygon reads as a sticker. */
    CIRCLE
}

/**
 * One face, with the initial underneath it.
 *
 * The initial is not a placeholder so much as the floor: `AsyncImage` draws nothing
 * while it loads and nothing if it fails, so the letter shows through and the tile
 * is never an empty hole. Coil's app-wide 180ms crossfade fades the photo in over
 * it, which is the only animation this needs - a scale or a slide on every face on
 * the page would be four things moving for one screen that the reader is reading.
 */
@Composable
private fun CreditAvatar(
    url: String,
    name: String,
    size: Dp,
    shape: AvatarShape,
    testTag: String
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(size)
            .clip(
                when (shape) {
                    AvatarShape.FLOWER -> expressiveShape(ExpressiveShapes.flower)
                    AvatarShape.CIRCLE -> CircleShape
                }
            )
            .background(
                when (shape) {
                    AvatarShape.FLOWER -> scheme.primaryContainer
                    AvatarShape.CIRCLE -> scheme.tertiaryContainer
                }
            )
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = when (shape) {
                AvatarShape.FLOWER -> scheme.onPrimaryContainer
                AvatarShape.CIRCLE -> scheme.onTertiaryContainer
            }
        )
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * One outbound address.
 *
 * `openExternalUrl` rather than the clipboard fallback the bio renderer uses: these
 * are addresses this app vouches for - a `https://github.com` page - and the copy
 * fallback exists for links a *reader* wrote, where the app cannot tell a typo from
 * a missing app. The opener still copies when nothing can open the link, so the
 * tap is never silent.
 */
@Composable
private fun CreditChip(link: CreditLink) {
    val context = LocalContext.current
    // Shared for the reason the helper row records: this chip is inside the
    // settings scroll, and an unshared spring fires for every thumb that passes it.
    val interaction = remember { MutableInteractionSource() }
    Surface(
        shape = ExpressiveShapes.pill,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .bouncyPress(pressedScale = 0.96f, interactionSource = interaction)
            .clip(ExpressiveShapes.pill)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = "Open ${link.label}"
            ) {
                openExternalUrl(context, link.url)
            }
            .testTag(link.testTag)
    ) {
        Row(
            // 48dp rather than the pill's natural height: a tap target is the
            // whole capsule, and the app's other link chips are this tall.
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = link.icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = link.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** `github.com/<handle>`, the address the row opens. */
private fun handleLine(credit: Credit): String = "github.com/${credit.handle}"

/** Who is being thanked, their face, and where their work is. */
private data class Credit(
    val name: String,
    val handle: String,
    val avatarUrl: String,
    val profileUrl: String,
    val note: String,
    /** The apps they wrote: a second destination, so a chip rather than the row. */
    val apps: List<CreditLink>
)

/** One address on a credit chip. */
private data class CreditLink(
    val label: String,
    val url: String,
    val icon: ImageVector,
    val testTag: String
)

/**
 * The developer.
 *
 * The repository link is `GITHUB_REPOSITORY_URL` from `InfoSettingsTab` rather than
 * a second copy of the same string: two copies of an address are two addresses the
 * day one of them is edited.
 */
private val DEVELOPER = Credit(
    name = "orintarotsumugi",
    handle = "orintarotsumugi",
    avatarUrl = "https://avatars.githubusercontent.com/u/139042138?s=256",
    profileUrl = "https://github.com/orintarotsumugi",
    note = "Solo developer of AniSequel",
    apps = emptyList()
)

private val HELPERS = listOf(
    Credit(
        name = "itsmechinmoy",
        handle = "itsmechinmoy",
        avatarUrl = "https://avatars.githubusercontent.com/u/167056923?s=256",
        profileUrl = "https://github.com/itsmechinmoy",
        note = "Maintaining Dantotsu from Rebelonion, and a lot of help with this " +
            "app and its suggestions.",
        apps = listOf(
            CreditLink(
                label = "Dantotsu",
                url = "https://github.com/itsmechinmoy/Dantotsu",
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                testTag = "credits_dantotsu_app"
            )
        )
    ),
    Credit(
        name = "Aayush",
        handle = "aayush2622",
        avatarUrl = "https://avatars.githubusercontent.com/u/99584765?s=256",
        profileUrl = "https://github.com/aayush2622",
        note = "Made Dartotsu, and helped a lot with this app and its suggestions.",
        apps = listOf(
            CreditLink(
                label = "Dartotsu",
                url = "https://github.com/aayush2622/Dartotsu",
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                testTag = "credits_dartotsu_app"
            )
        )
    ),
    Credit(
        name = "RyanYuuki",
        handle = "RyanYuuki",
        avatarUrl = "https://avatars.githubusercontent.com/u/108048963?s=256",
        profileUrl = "https://github.com/RyanYuuki",
        note = "Made AnymeX, which this app's polish is measured against.",
        apps = listOf(
            CreditLink(
                label = "AnymeX",
                url = "https://github.com/RyanYuuki/AnymeX",
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                testTag = "credits_anymex_app"
            )
        )
    )
)

private val MENTIONS = listOf(
    "titangen - a friend",
    "kusokohei - a friend, and a lot of suggestions",
    "you - the reader this is for",
    "thank you"
)
