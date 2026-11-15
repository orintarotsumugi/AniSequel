package com.anisequel.ui.screens.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.anisequel.data.discord.DiscordRuntimeState
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.domain.discord.DiscordActivity
import com.anisequel.domain.discord.DiscordActivityKind
import com.anisequel.domain.discord.DiscordSettings

/**
 * The status dot's colour: green only when a relay actually accepted a presence.
 *
 * Extracted with [sampleActivity] because both are preview-only facts. The page
 * file is the layout and the editors; these answer what the dock's dot and the
 * quick-action chips should say.
 */
@Composable
internal fun discordStatusColor(
    settings: DiscordSettings,
    runtime: DiscordRuntimeState
): Color = when {
    runtime is DiscordRuntimeState.Failed -> MaterialTheme.colorScheme.error
    runtime is DiscordRuntimeState.Live -> Color(0xFF23A55A)
    runtime is DiscordRuntimeState.PreviewOnly -> Color(0xFFF0B232)
    settings.enabled -> Color(0xFFF0B232)
    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
}

/**
 * A sample activity for the preview chips.
 *
 * Deliberately not invented artwork: an empty cover draws the Discord glyph, which
 * is what the card looks like when the reader has not opened a title anyway. The
 * numbers on the stats line are real in the sense that matters - they come from
 * the same composer - and the title is the reader's when one is available.
 */
internal fun sampleActivity(
    kind: DiscordActivityKind,
    now: Long,
    entries: List<MediaListEntryItem>
): DiscordActivity {
    if (kind == DiscordActivityKind.BROWSING) {
        return DiscordActivity(kind = kind, status = "Home", startedAtMillis = now)
    }
    val reading = kind == DiscordActivityKind.READING_MANGA ||
        kind == DiscordActivityKind.READING_NOVEL ||
        kind == DiscordActivityKind.VIEWING_MANGA_DETAILS ||
        kind == DiscordActivityKind.VIEWING_NOVEL_DETAILS
    val fallbackTitle = when (kind) {
        DiscordActivityKind.READING_MANGA, DiscordActivityKind.VIEWING_MANGA_DETAILS ->
            "Vinland Saga"
        DiscordActivityKind.READING_NOVEL, DiscordActivityKind.VIEWING_NOVEL_DETAILS ->
            "The Apothecary Diaries"
        else -> "Frieren: Beyond Journey's End"
    }
    // A matching entry only. Falling back to the first entry regardless of type
    // drew an anime cover under "Reading manga" for a reader whose preview pool
    // happened to be all anime; an honest empty cover is better than the wrong art.
    val entry = entries.firstOrNull { candidate ->
        val type = candidate.media.type
        if (reading) type.equals("MANGA", ignoreCase = true)
        else type.equals("ANIME", ignoreCase = true)
    }
    val realTitle = entry?.media?.title?.displayTitle
        ?.takeIf { it.isNotBlank() && it != "Unknown Title" }
    return DiscordActivity(
        kind = kind,
        title = realTitle ?: fallbackTitle,
        coverUrl = entry?.media?.coverImage?.bestUrl,
        pageUrl = entry?.media?.siteUrl,
        progress = entry?.progress
            ?.takeIf { it > 0 }
            ?.let { progress -> if (reading) "Chapter $progress" else "Episode $progress" },
        status = if (reading) "currently reading" else "currently watching",
        startedAtMillis = now
    )
}
