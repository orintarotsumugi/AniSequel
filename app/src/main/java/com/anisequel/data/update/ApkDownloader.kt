package com.anisequel.data.update

import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/** What one HTTP transfer into a `.part` file produced. */
internal sealed interface TransferResult {
    data class Completed(val totalBytes: Long) : TransferResult
    data class Failed(val reason: String) : TransferResult
}

/**
 * 206: the server answered with the range that was asked for.
 *
 * Top level rather than in [ApkDownloader]'s companion because [planResume] is
 * top level too, and a top-level function cannot see a private companion's
 * members - which the compiler says out loud, once.
 */
private const val HTTP_PARTIAL_CONTENT = 206

/** 416: the bytes on disk run past the end of the file. */
private const val HTTP_RANGE_NOT_SATISFIABLE = 416

/**
 * How a response lines up with the bytes already on disk.
 *
 * Pure, and separated from the transfer, because this is the part of resuming
 * that is easy to get subtly wrong and impossible to see: it is three different
 * answers to "the server did not do what the `Range` header asked", and each
 * one has to be handled or the bytes on disk are wrong.
 */
internal data class ResumePlan(
    /** Where this response starts in the finished file. */
    val baseOffset: Long,
    /** Whether the response is appended to what is already there. */
    val append: Boolean,
    /** Whether the existing `.part` is unusable and has to go. */
    val discardPart: Boolean
)

/**
 * Whether a response continues the `.part` or replaces it.
 *
 * A `.part` is kept between attempts precisely so that a dropped connection does
 * not cost the whole download again - but only while the bytes on disk are still
 * bytes of *this* asset. Two things invalidate them:
 *
 *  - **The server ignored the range.** A 200 answering a request that carried
 *    `Range: bytes=1048576-` means the body starts at byte zero, so appending
 *    it would produce a file with 1 MB of one release doubled onto the front of
 *    another. No length arithmetic catches that; the plan discards the `.part`
 *    and the transfer starts from nothing, which is no worse than the behaviour
 *    this whole class exists to improve on.
 *  - **The asset behind the URL changed.** A release asset is replaced in place,
 *    so a `.part` written before the replacement and a body fetched after it do
 *    not join into anything. [UpdateManager]'s signature check is a better judge
 *    of the joined file than any length arithmetic, and it discards the `.part`
 *    when it fails so the next attempt starts clean.
 *
 * What the plan deliberately does not do is invent a total. `totalBytes` is the
 * server's `Content-Length` plus the base offset; when the server declines to
 * say, the bar is indeterminate rather than a number that is wrong.
 */
internal fun planResume(requestedFrom: Long, responseCode: Int): ResumePlan = when {
    requestedFrom <= 0 -> ResumePlan(0, append = false, discardPart = false)
    responseCode == HTTP_PARTIAL_CONTENT -> ResumePlan(requestedFrom, append = true, discardPart = false)
    else -> ResumePlan(0, append = false, discardPart = true)
}

/**
 * Streams a release APK into a `.part` file, continuing one that is already there.
 *
 * ## Why this is its own class
 *
 * [UpdateManager] is what *judges* a release: is this body an APK, is it ours,
 * is it signed by us. That logic has a manifest to compare against and a file it
 * does not trust. The transfer is the other half - headers, offsets, buffering -
 * and it changed for an unrelated reason: an interrupted download restarted from
 * zero (#64). One class doing both was over 500 lines with two reasons to change.
 *
 * ## Why resuming at all
 *
 * This is a ~2.6 MB file over a phone connection, and the old behaviour was "if
 * anything interrupts the transfer, throw the partial file away". A call, a lift,
 * one bar of signal, or the OS reclaiming the app while the user is off in
 * Settings each cost the whole download again. The `.part` already exists to
 * stop an *interrupted* transfer leaving something a later run would hand to the
 * package installer, so keeping it across attempts costs nothing and turns every
 * one of those cases into a continuation.
 *
 * ## What it does not do
 *
 * It verifies nothing and renames nothing. Every byte it writes is untrusted
 * until [UpdateManager] has looked at it, and only that class may move a file
 * into its final name.
 */
internal class ApkDownloader(private val client: OkHttpClient) {

    /**
     * Downloads [manifest]'s asset into [partFile], continuing it if it is there.
     *
     * Blocking, and deliberately so: the caller owns the dispatcher. On failure
     * the `.part` survives if its bytes are still worth resuming from, and is
     * deleted if they are not - which is decided by the failure, not here.
     */
    fun transfer(
        manifest: UpdateManifest,
        partFile: File,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit
    ): TransferResult {
        val url = manifest.downloadUrl
        if (url.isNullOrBlank()) {
            return TransferResult.Failed("That release has no download.")
        }

        val declaredSize = manifest.sizeBytes?.takeIf { it > 0 } ?: 0L
        val alreadyOnDisk = partFile.takeIf { it.isFile }?.length() ?: 0L

        val first = attempt(url, partFile, from = alreadyOnDisk, declaredSize, onProgress)

        // A 416 means the server believes the file ends before the `.part` does,
        // so those bytes cannot belong to this asset. They go, and the single
        // retry starts from nothing. Bounded at one retry on purpose: a server
        // that answers a plain request with a 416 as well is broken, and looping
        // on it would turn a broken server into an endless retry loop on a
        // phone's battery.
        if (first !is Attempt.RangeRefused) return first.settled(partFile)

        partFile.delete()

        return attempt(url, partFile, from = 0L, declaredSize, onProgress).settled(partFile)
    }

    /**
     * One request, starting [from] bytes in.
     */
    private fun attempt(
        url: String,
        partFile: File,
        from: Long,
        declaredSize: Long,
        onProgress: (Long, Long) -> Unit
    ): Attempt {
        // The request is built inside the try: a malformed `download_url` throws
        // from `url(...)` rather than from the call, and a manifest is a public
        // file on a network that can be intercepted - so that throw has to come
        // back as a message the user can read, not as a crash.
        return try {
            val request = Request.Builder()
                .url(url)
                .apply { if (from > 0) header("Range", "bytes=$from-") }
                .build()

            client.newCall(request).execute().use { response ->
                when {
                    from > 0 && response.code == HTTP_RANGE_NOT_SATISFIABLE ->
                        Attempt.RangeRefused

                    !response.isSuccessful ->
                        Attempt.Failed(
                            "The download failed (${response.code}).",
                            keepPartial = partFile.length() > 0
                        )

                    else -> write(response, partFile, from, declaredSize, onProgress)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            // The connection dropped, which is the entire reason the `.part`
            // exists. It stays, and the next press continues from here.
            Attempt.Failed(
                e.message?.takeIf { it.isNotBlank() }
                    ?: "The connection dropped. Press Download again to continue where it stopped.",
                keepPartial = partFile.length() > 0
            )
        } catch (e: Exception) {
            Attempt.Failed(
                e.message?.takeIf { it.isNotBlank() }
                    ?: "The download failed. Check your connection.",
                keepPartial = false
            )
        }
    }

    /**
     * Streams the response body into the `.part` and reports progress.
     *
     * The total is the server's `Content-Length` plus whatever is already on
     * disk, so a resumed download does not show a bar that restarts at zero.
     */
    private fun write(
        response: Response,
        partFile: File,
        from: Long,
        declaredSize: Long,
        onProgress: (Long, Long) -> Unit
    ): Attempt {
        val body = response.body ?: return Attempt.Failed(
            "The download came back empty.",
            keepPartial = partFile.length() > 0
        )

        val plan = planResume(from, response.code)
        val bodyLength = body.contentLength()

        // Bounded: the manifest declares a size and it matched in every release
        // so far. A wildly larger body means the URL is not serving an APK, and
        // writing it would fill the user's storage on a download they never
        // asked for.
        val sizeLimit = if (declaredSize > 0) declaredSize * 2 else Long.MAX_VALUE
        if (declaredSize > 0 && bodyLength > declaredSize * 2) {
            return Attempt.Failed(OVERSIZED_MESSAGE, keepPartial = false)
        }

        val total = when {
            plan.baseOffset + bodyLength > 0 -> plan.baseOffset + bodyLength
            declaredSize > 0 -> declaredSize
            else -> 0L
        }

        val written = body.byteStream().use { input ->
            copy(input, partFile, plan, total, sizeLimit, onProgress)
        }

        // No delete here: `keepPartial = false` already tells `settled` these
        // bytes are not a prefix of anything worth resuming onto.
        if (written == OVERSIZED) {
            return Attempt.Failed(OVERSIZED_MESSAGE, keepPartial = false)
        }

        return Attempt.Completed(written)
    }

    /**
     * The byte loop.
     *
     * Progress is reported per 32 KB rather than per read. Every callback writes
     * Compose state and recomposes the progress bar, and the default buffer is
     * 8 KB: a 2.6 MB APK is ~325 of them, each recomposing a dialog for a change
     * no eye can see. A step per 32 KB is still ~80 updates on a real download -
     * smooth to watch, and a quarter of the work.
     *
     * Returns the bytes now in the file, or [OVERSIZED] if the body outgrew
     * [sizeLimit]. That bound is enforced on bytes actually read as well as on
     * `Content-Length` above, so a chunked response with no length header cannot
     * grow without limit.
     */
    private fun copy(
        input: InputStream,
        partFile: File,
        plan: ResumePlan,
        total: Long,
        sizeLimit: Long,
        onProgress: (Long, Long) -> Unit
    ): Long {
        if (plan.discardPart) partFile.delete()

        var read = plan.baseOffset
        var lastReported = read

        FileOutputStream(partFile, /* append = */ plan.append && plan.baseOffset > 0).use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                read += count
                if (read > sizeLimit) return OVERSIZED
                output.write(buffer, 0, count)

                if (read - lastReported >= PROGRESS_STEP_BYTES) {
                    lastReported = read
                    onProgress(read, total)
                }
            }

            output.flush()
        }

        // Emitted unconditionally, so a body that stops short of the next 32 KB
        // step cannot leave the bar showing 97%.
        onProgress(read, if (total > 0) total else read)
        return read
    }

    /** What a single request produced, before the range-refused retry. */
    private sealed interface Attempt {
        data class Completed(val bytes: Long) : Attempt
        data class Failed(val reason: String, val keepPartial: Boolean) : Attempt
        data object RangeRefused : Attempt
    }

    /**
     * Turns one attempt into the result the caller sees, applying the decision it
     * made about the `.part` on the way.
     */
    private fun Attempt.settled(partFile: File): TransferResult = when (this) {
        is Attempt.Completed -> TransferResult.Completed(bytes)
        is Attempt.Failed -> {
            if (!keepPartial) partFile.delete()
            TransferResult.Failed(reason)
        }
        Attempt.RangeRefused ->
            TransferResult.Failed("The server would not send the update at all.")
    }

    private companion object {
        /**
         * How many bytes must land before the progress bar is told about them.
         *
         * 32 KB: about eighty updates across a real APK, which animates
         * smoothly, against three hundred for an unthrottled per-read callback.
         */
        const val PROGRESS_STEP_BYTES = 32L * 1024

        /** [copy]'s way of saying the body outgrew the size limit. */
        const val OVERSIZED = -1L

        const val OVERSIZED_MESSAGE = "The download was much larger than expected. Skipped."
    }
}