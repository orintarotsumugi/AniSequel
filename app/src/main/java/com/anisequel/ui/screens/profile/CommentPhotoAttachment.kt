package com.anisequel.ui.screens.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.anisequel.data.network.ImageUploader
import com.anisequel.data.repository.PickedImageReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * One draft's photo picker: the launcher, and the upload it is currently doing.
 *
 * The picker is a round trip through an image host, so it has to report two things
 * the field must draw - "Uploading…" and why an upload failed - beside the
 * composer. Its own file because the edit pane needs the same picker against a
 * different draft, and two copies of the upload would drift the first time either
 * was fixed.
 *
 * [ImageUploader] is the host chain, not a single host: catbox has paused anonymous
 * uploads, and the two fallbacks are what keep this button working while it is
 * paused. The outcome carries which host took the file, because one of them stores a
 * photo AniList then cannot display - and the reader is told on the row that earned
 * it, rather than finding out from somebody else's reply.
 */
internal data class PhotoAttachment(
    val launch: () -> Unit,
    val isUploading: Boolean,
    val note: String?
)

@Composable
internal fun rememberPhotoAttachment(
    onUploaded: (String) -> Unit
): PhotoAttachment {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val uploader = ImageUploader.shared
    var isUploading by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isUploading = true
        note = "Uploading…"
        scope.launch {
            // Written with a `finally` rather than `mapCatching`/`fold`, because a
            // cancelled upload is neither a success nor a failure: `mapCatching`
            // catches `Throwable`, so closing the sheet mid-transfer took the
            // cancellation as its "failure", wrote a reason the reader never asked
            // for, and left `isUploading` true on the way out - a Send button
            // disabled by an upload that had already been thrown away.
            try {
                val image = PickedImageReader.read(context, uri).getOrThrow()
                val outcome = uploader.upload(image.bytes, image.fileName, image.mimeType).getOrThrow()
                onUploaded(outcome.url)
                // The file is stored, but not every host lets the site read it back:
                // pomf answers a request carrying AniList's own referer with a
                // connection reset, so the embed is a broken image on the page
                // everybody else will read it on, and a still here in this app. The
                // reader is told on the row that earned it rather than finding out
                // from somebody else's reply.
                note = if (outcome.showsOnAniList) {
                    null
                } else {
                    "Uploaded, but this host hides files from AniList - the image may " +
                        "not show for other readers."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                note = failure.message ?: "The upload did not go through."
            } finally {
                isUploading = false
            }
        }
    }

    return PhotoAttachment(
        launch = {
            picker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        },
        isUploading = isUploading,
        note = note
    )
}
