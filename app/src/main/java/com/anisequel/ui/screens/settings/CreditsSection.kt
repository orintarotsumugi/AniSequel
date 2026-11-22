package com.anisequel.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.expressive.expressiveShape
import com.anisequel.ui.components.icons.Icons
import com.anisequel.ui.components.icons.automirrored.filled.OpenInNew
import com.anisequel.ui.components.openExternalUrl

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
 * ## Why every credit is a link and not a sentence
 *
 * "Thanks to itsmechinmoy" is a dead end - the app this one borrowed an idea from
 * is one tap away only if that tap exists. Each helper carries the profile *and*
 * the app, because the interesting thing about a helper here is the app they
 * wrote, and the reader who wonders what Dantotsu is should not have to type it
 * into a search box. Every link is a real `https://github.com` address; the chips
 * use `openExternalUrl`, which copies the address when nothing can open it, so
 * even a device with no browser leaves the reader with something they can paste.
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
 * below it are read. The GitHub mark is the tile because it is the only claim on
 * the card that can be checked - "read my code" is a link, not a promise.
 */
@Composable
private fun DeveloperCard() {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = ExpressiveShapes.extraLarge,
        color = scheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.38f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("credits_developer_card")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(expressiveShape(ExpressiveShapes.flower))
                        .background(scheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppVectorIcons.GitHub,
                        contentDescription = null,
                        tint = scheme.onPrimaryContainer,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "orintarotsumugi",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Solo developer of AniSequel",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.primary
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
            CreditLinks(
                links = listOf(
                    CreditLink("GitHub", DEV_GITHUB_URL, AppVectorIcons.GitHub, "credits_dev_github"),
                    CreditLink(
                        label = "The code",
                        url = GITHUB_REPOSITORY_URL,
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        testTag = "credits_dev_source"
                    )
                )
            )
        }
    }
}

/**
 * Three developers whose apps this one learned from, each with both addresses.
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
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
            CreditBlock(credit = credit, index = index)
        }
    }
}

/** One helper: a tile, who they are, why they are thanked, and where to go. */
@Composable
private fun CreditBlock(credit: Credit, index: Int) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(settingsTileShape(index))
                    .background(scheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = credit.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onTertiaryContainer
                )
            }
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
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        CreditLinks(links = credit.links)
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
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
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
    Surface(
        shape = ExpressiveShapes.pill,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .bouncyPress(pressedScale = 0.96f)
            .clip(ExpressiveShapes.pill)
            .clickable(role = Role.Button, onClickLabel = "Open ${link.label}") {
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

/** Who is being thanked, and where their work is. */
private data class Credit(
    val name: String,
    val note: String,
    val links: List<CreditLink>
)

/** One address on a credit chip. */
private data class CreditLink(
    val label: String,
    val url: String,
    val icon: ImageVector,
    val testTag: String
)

/**
 * The developer's profile.
 *
 * The repository link is `GITHUB_REPOSITORY_URL` from `InfoSettingsTab` rather than
 * a second copy of the same string: two copies of an address are two addresses the
 * day one of them is edited.
 */
private const val DEV_GITHUB_URL = "https://github.com/orintarotsumugi"

private val HELPERS = listOf(
    Credit(
        name = "itsmechinmoy",
        note = "Made Dantotsu, and helped a lot with this app and its suggestions.",
        links = listOf(
            CreditLink(
                label = "GitHub",
                url = "https://github.com/itsmechinmoy",
                icon = AppVectorIcons.GitHub,
                testTag = "credits_itsmechinmoy_github"
            ),
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
        note = "Made Dartotsu, and helped a lot with this app and its suggestions.",
        links = listOf(
            CreditLink(
                label = "GitHub",
                url = "https://github.com/aayush2622",
                icon = AppVectorIcons.GitHub,
                testTag = "credits_aayush_github"
            ),
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
        note = "Made AnymeX, which this app's polish is measured against.",
        links = listOf(
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
