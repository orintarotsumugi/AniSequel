package com.anisequel.data.repository

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import com.anisequel.domain.usecase.MediaAttachment
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns a picked content URI into the bytes an upload needs.
 *
 * ## Why this is its own file
 *
 * It is the only part of the attach flow that talks to another app's content
 * provider, and that is where the surprises live: a provider that reports no
 * display name, one that reports a size of -1, one that hands back
 * `application/octet-stream` for a JPEG, and the camera roll's HEIC that every
 * other reader's browser cannot open. All of it is kept here so
 * [com.anisequel.data.network.CatboxApi] only ever sees bytes, a name and a
 * content type it can trust, and so the decisions that do not need a device stay
 * in [MediaAttachment], where a JVM test can reach them.
 *
 * ## The size cap is applied while copying, not before
 *
 * A provider gives a size for a file on disk and -1 for almost everything else, so
 * a pre-flight check would refuse real files and vouch for none. The copy stops one
 * byte past the host's ceiling instead: reaching it means the file is too big, and
 * the app learns that without holding 200 MB in memory to find out.
 */
object PickedImageReader {

    /** A file the app is ready to upload. */
    data class PickedImage(
        val bytes: ByteArray,
        val fileName: String,
        val mimeType: String
    ) {
        val sizeBytes: Long get() = bytes.size.toLong()
    }

    /**
     * Reads [uri] and returns the file to upload, or a failure whose message is
     * written for the composer.
     *
     * A HEIC/HEIF is decoded and re-encoded as JPEG here, because a comment is read
     * by other people on other devices and the container Android chose is not one
     * they can open. Every other accepted type goes up byte for byte - a GIF in
     * particular must not come near the decoder, or the animation the reader picked
     * becomes whatever single frame the decoder happened to keep.
     */
    suspend fun read(context: Context, uri: Uri): Result<PickedImage> =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val declaredName = displayName(resolver, uri)
            val declaredType = runCatching { resolver.getType(uri) }.getOrNull()?.lowercase()

            val raw = readBytes(resolver, uri).getOrElse { return@withContext Result.failure(it) }
            if (raw.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException(MediaAttachment.reasonFor(MediaAttachment.Verdict.Empty))
                )
            }

            // The bytes win over whatever the carrier of the file said it was: a
            // cloud document reports `application/octet-stream` for a JPEG, and a
            // gallery can label a PNG `.jpg`. Nothing is uploaded until the sniffed
            // type is one the policy accepts.
            val actualType = MediaAttachment.sniffMimeType(raw)
                ?: declaredType?.takeIf { it in MediaAttachment.SUPPORTED_TYPES }
            if (actualType == null) {
                return@withContext Result.failure(
                    IllegalStateException(MediaAttachment.reasonFor(MediaAttachment.Verdict.UnsupportedType))
                )
            }

            val verdict = MediaAttachment.verdictFor(actualType, raw.size.toLong())
            if (verdict != MediaAttachment.Verdict.Accept) {
                return@withContext Result.failure(IllegalStateException(MediaAttachment.reasonFor(verdict)))
            }

            if (MediaAttachment.needsRecode(actualType)) {
                return@withContext recodeAsJpeg(raw, declaredName)
            }

            Result.success(
                PickedImage(
                    bytes = raw,
                    fileName = MediaAttachment.storageFileName(actualType, declaredName),
                    mimeType = actualType
                )
            )
        }

    /** The provider's own idea of the file's name, which may be null or a lie. */
    private fun displayName(resolver: ContentResolver, uri: Uri): String? = runCatching {
        // `Array<String?>` rather than `Array<String>`: `ContentResolver.query` is
        // declared against the nullable projection in the platform stubs, and a
        // non-null array is a different type to Kotlin's invariant generics, not a
        // wider one - the call simply does not resolve.
        val projection = arrayOf<String?>(OpenableColumns.DISPLAY_NAME)
        // All five arguments, because `ContentResolver.query` is overloaded and the
        // two-argument form is not the one Kotlin resolves against this SDK: leaving
        // the trailing arguments out resolved a signature that wanted three more
        // parameters, and the cursor's own methods then looked unresolved.
        val cursor = resolver.query(uri, projection, null, null, null) ?: return@runCatching null
        cursor.use {
            val column = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && it.moveToFirst()) {
                it.getString(column)?.takeIf { name -> name.isNotBlank() }
            } else {
                null
            }
        }
    }.getOrNull()

    /**
     * The stream, capped at one byte past the host's ceiling.
     *
     * The cap is enforced during the copy for the reason given in the class
     * documentation: the provider's size claim is unavailable often enough that
     * trusting it would be the only thing standing between a picked 400 MB video
     * and an out-of-memory kill.
     */
    private fun readBytes(resolver: ContentResolver, uri: Uri): Result<ByteArray> = runCatching {
        val input = resolver.openInputStream(uri)
            ?: throw IllegalStateException("That file could not be opened.")
        input.use { source ->
            val out = ByteArrayOutputStream(COPY_BUFFER_SIZE * 4)
            val buffer = ByteArray(COPY_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val read = source.read(buffer)
                if (read <= 0) break
                total += read
                if (total > MediaAttachment.MAX_FILE_BYTES) {
                    throw IllegalStateException(MediaAttachment.reasonFor(MediaAttachment.Verdict.TooLarge))
                }
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
    }

    /**
     * HEIC in, JPEG out.
     *
     * [ImageDecoder] rather than `BitmapFactory`, because it applies the photo's
     * EXIF orientation while decoding; the older path re-encoded a sideways photo
     * sideways, which is the kind of defect that only shows up in somebody else's
     * comment thread. The allocator is forced to software: the default gives a
     * hardware bitmap, and [Bitmap.compress] cannot read one back.
     */
    private fun recodeAsJpeg(raw: ByteArray, declaredName: String?): Result<PickedImage> =
        try {
            val image = ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(java.nio.ByteBuffer.wrap(raw))
            ) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
            val out = ByteArrayOutputStream()
            image.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            val jpeg = out.toByteArray()
            // The source's size was checked against the HEIC ceiling, but the
            // JPEG is a different file and can still be over the host's limit.
            // Checked here so the reader gets "that file is too large" rather
            // than a fourteen-second upload and a refusal from catbox.
            val verdict = MediaAttachment.verdictFor("image/jpeg", jpeg.size.toLong())
            if (verdict != MediaAttachment.Verdict.Accept) {
                return Result.failure(IllegalStateException(MediaAttachment.reasonFor(verdict)))
            }
            Result.success(
                PickedImage(
                    bytes = jpeg,
                    fileName = MediaAttachment.storageFileName("image/jpeg", declaredName),
                    mimeType = "image/jpeg"
                )
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // A container the platform cannot decode is the reader's file, not the
            // app's fault, and the sentence has to tell them what to do about it.
            Result.failure(
                IllegalStateException(
                    "That photo could not be converted. Save it as a JPEG or a PNG and attach that."
                )
            )
        }

    private const val COPY_BUFFER_SIZE = 16 * 1024
    private const val JPEG_QUALITY = 92
}
