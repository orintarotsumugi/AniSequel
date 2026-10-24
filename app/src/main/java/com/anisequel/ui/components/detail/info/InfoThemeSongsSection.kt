package com.anisequel.ui.components.detail.info

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.ThemeSong
import com.anisequel.data.model.themeSongsSummary
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.openExternalUrlOrCopy
import com.anisequel.ui.components.expressive.bouncyPress

/**
 * "Openings & Endings": the one row on the title page fed by a second service.
 *
 * The data comes from AnimeThemes (see
 * [com.anisequel.data.network.AnimeThemesApi]); everything about its absence is
 * handled above this composable, which draws only a non-empty list. That split is
 * deliberate: a section that renders its own spinner would need the page to give it
 * a `LazyColumn` slot before it knows whether it has anything to show, and an empty
 * slot still costs the page's 14dp gap.
 *
 * ## Why the cards open the video externally
 *
 * The API hands out `.webm` stream links. Playing them in-process means a video
 * player dependency (ExoPlayer/Media3) for one row of six-second clips, a new
 * surface that has to pause, resume and release itself, and a codec story on every
 * device - against the `Intent` the play button already means. The reader gets the
 * clip in whatever they already watch video with.
 *
 * ## Why the card art is the title's own
 *
 * AnimeThemes' video entries carry no thumbnail and its images are the anime's
 * covers, so six cards would all show the same poster. The page passes the title's
 * banner (falling back to its cover), which reads as one row of this title rather
 * than a gallery of its DVD case.
 */
@Composable
fun InfoThemeSongsSection(
    themes: List<ThemeSong>,
    modifier: Modifier = Modifier
) {
    if (themes.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("info_theme_songs_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        InfoAccentHeading(
            title = "Openings & Endings",
            subtitle = themes.themeSongsSummary(),
            trailing = {
                Icon(
                    imageVector = AppVectorIcons.SequelArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        )

        LazyRow(
            // Edge-to-edge, like the cast and relations rows: the artwork is the
            // point, and a card row clipped at the page's 20dp inset reads as a
            // grid that failed to fill.
            contentPadding = PaddingValues(horizontal = PAGE_INSET),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // The label is the card's identity *unless* the service answers two
            // themes with the same one - an OP whose slug is shared, or two
            // entries with no slug and no sequence, both of which fall back to
            // `OP`. A repeated key is not a doubled row: Compose throws
            // `Key "..." was already used`, and the sheet that was opening the
            // title closes the app instead. The position disambiguates without
            // affecting anything visible, and the row is static per title.
            itemsIndexed(
                items = themes,
                key = { index, theme -> "theme_${theme.label}#$index" }
            ) { _, theme ->
                ThemeSongCard(theme = theme)
            }
        }
    }
}

@Composable
private fun ThemeSongCard(theme: ThemeSong) {
    val context = LocalContext.current
    val playable = !theme.videoUrl.isNullOrBlank()
    val open: () -> Unit = {
        theme.videoUrl?.takeIf { it.isNotBlank() }?.let { openExternalUrlOrCopy(context, it) }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .size(width = 172.dp, height = 96.dp)
            .then(if (playable) Modifier.bouncyPress(pressedScale = 0.96f) else Modifier)
            .then(
                if (playable) {
                    Modifier.clickable(role = Role.Button, onClick = open)
                } else {
                    Modifier
                }
            )
            .testTag("theme_song_${theme.label}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            theme.artworkUrl?.let { art ->
                AsyncImage(
                    model = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // The scrim is black rather than a theme colour because the artwork
            // underneath is arbitrary and has to darken under every palette.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f))
            )

            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Text(
                    text = theme.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = if (playable) 0.92f else 0.4f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = AppVectorIcons.Play,
                        // The card's one spoken label: the badge above is
                        // decoration, the song is what the row is about.
                        contentDescription = theme.title ?: theme.label,
                        tint = Color.Black.copy(alpha = 0.85f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            theme.title?.let { title ->
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
