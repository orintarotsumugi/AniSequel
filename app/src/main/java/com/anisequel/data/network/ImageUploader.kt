package com.anisequel.data.network

/** One anonymous file host: bytes in, a public URL or a reason out. */
fun interface ImageHost {
    suspend fun upload(bytes: ByteArray, fileName: String, mimeType: String): Result<String>
}

/**
 * One image, three hosts, first one that takes it.
 *
 * ## Why a chain rather than a host setting
 *
 * The reader is attaching a photo to a comment, not choosing infrastructure. But
 * the hosts fail for different reasons and the order matters: catbox is the app's
 * documented host and keeps files until two years of no views, pomf.lain.la is the
 * anonymous clone that answered when catbox stopped taking uploads altogether, and
 * imgbb.com is the third attempt for the day both of those are having a bad
 * afternoon. Trying them in that order keeps the promise Settings makes for as long
 * as the first host can keep it, and the later hosts are what turn a paused host
 * back into a working attach button instead of a sentence about somebody else's
 * storage.
 *
 * A failure from the last host is the one reported: the earlier refusals are the
 * reason the app tried elsewhere, not the reason the reader's file did not upload.
 * The [UploadFailure] message is still written for the composer, whichever host
 * produced it.
 *
 * ## Why a host that just refused is skipped for a while
 *
 * Every attempt sends the whole file before the host can say no. With catbox
 * paused, the chain used to push every photo to catbox first - megabytes over a
 * phone connection, and the slowest leg of the upload - only to read "Uploads
 * paused" and start again at pomf. A host that refused is now benched for
 * [cooldownMillis] and the chain starts at the next one. When every host is
 * benched they are all tried again in order, because a chain that skips everything
 * is a button that cannot work until a timer runs out.
 */
class ImageUploader(
    private val hosts: List<ImageHost> = listOf(CatboxApi(), PomfApi(), ImgbbApi()),
    private val cooldownMillis: Long = DEFAULT_COOLDOWN_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis
) {

    /** Per host index, the time before which it is not asked first. */
    private val benchedUntil = LongArray(hosts.size)

    /**
     * Uploads [bytes] and returns a URL that can be embedded in a comment.
     *
     * The bytes are the same for every attempt, so nothing is re-read from the
     * provider and a fallback costs one request, not a second picker.
     */
    suspend fun upload(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): Result<String> {
        val now = clock()
        val order = hosts.indices.filter { synchronized(benchedUntil) { benchedUntil[it] <= now } }
            .ifEmpty { hosts.indices.toList() }
        var last: Result<String> = Result.failure(UploadFailure("No image host is configured."))
        for (index in order) {
            last = hosts[index].upload(bytes, fileName, mimeType)
            synchronized(benchedUntil) {
                benchedUntil[index] = if (last.isSuccess) 0L else clock() + cooldownMillis
            }
            if (last.isSuccess) return last
        }
        return last
    }

    companion object {
        /**
         * The app's one chain, so a host benched by one sheet stays benched for the
         * next: a chain per composer forgot catbox's refusal every time a sheet
         * opened, and paid for the same paused host again.
         */
        val shared: ImageUploader by lazy { ImageUploader() }

        /** Long enough to cover one composing session, short enough to notice a host coming back. */
        const val DEFAULT_COOLDOWN_MILLIS: Long = 10 * 60 * 1000L
    }
}
