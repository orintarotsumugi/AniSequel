package com.anisequel.ui.screens.notifications

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.MediaNode
import com.anisequel.data.repository.AniListRepository
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.detail.AnimeDetailSubject
import com.anisequel.ui.components.detail.RepositoryEntryEditor
import com.anisequel.ui.components.detail.SequelDetailSheet

/**
 * A notification's media target, opened as the real title sheet inside the app.
 *
 * The old route left for `anilist.co` because the detail page is a sheet over a
 * list, not a standalone destination. That was true of the *host*, not of the
 * data: the repository can fetch a title by id alone, so the inbox can open the
 * same sheet with the same write access the Library has.
 *
 * The fetch happens here rather than through `MediaDetailHost` because this is
 * the only page that opens one title from an id with no summary in hand. Going
 * through the shared store would draw a sheet with no title while it waited and
 * would need a host parameter for no benefit; the fetch already returns every
 * field the sheet reads.
 */
@Composable
internal fun NotificationMediaScreen(
    mediaId: Int,
    mediaType: String?,
    repository: AniListRepository,
    entryEditor: RepositoryEntryEditor?,
    onBack: () -> Unit,
    onOpenMedia: (Int, String?) -> Unit,
    onOpenStudio: (Int, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var media by remember(mediaId, mediaType) { mutableStateOf<MediaNode?>(null) }
    var error by remember(mediaId, mediaType) { mutableStateOf<String?>(null) }
    var attempt by remember(mediaId, mediaType) { mutableIntStateOf(0) }

    LaunchedEffect(mediaId, mediaType, attempt) {
        media = null
        error = null
        repository.getMediaDetail(mediaId, mediaType)
            .onSuccess { media = it }
            .onFailure { error = it.message ?: "Couldn't open that title." }
    }

    BackHandler(onBack = onBack)

    val loaded = media
    when {
        loaded != null -> SequelDetailSheet(
            subject = AnimeDetailSubject.Media(loaded),
            onDismiss = onBack,
            onAddToPlanning = { _ -> },
            onLoadDetail = { _, _ -> },
            isDetailLoading = false,
            canWriteToAniList = entryEditor != null,
            entryEditor = entryEditor,
            onOpenMedia = { node -> onOpenMedia(node.id, node.type) },
            onOpenStudio = onOpenStudio
        )

        error != null -> MediaMessage(
            message = error.orEmpty(),
            onBack = onBack,
            onRetry = { attempt++ },
            modifier = modifier
        )

        else -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
private fun MediaMessage(
    message: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Icon(
                imageVector = AppVectorIcons.ChevronRight,
                contentDescription = "Back",
                modifier = Modifier.graphicsLayer { rotationZ = 180f }
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "That title didn't open",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onRetry) { Text("Try again") }
        }
    }
}
