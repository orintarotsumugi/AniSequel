package com.anisequel.data.network

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
 */
class ImageUploader(
    private val catbox: CatboxApi = CatboxApi(),
    private val pomf: PomfApi = PomfApi(),
    private val imgbb: ImgbbApi = ImgbbApi()
) {

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
        val primary = catbox.upload(bytes, fileName, mimeType)
        if (primary.isSuccess) return primary
        val fallback = pomf.upload(bytes, fileName, mimeType)
        if (fallback.isSuccess) return fallback
        return imgbb.upload(bytes, fileName, mimeType)
    }
}
