package com.anisequel.data.network

/** One anonymous file host: bytes in, a public URL or a reason out. */
fun interface ImageHost {
    suspend fun upload(bytes: ByteArray, fileName: String, mimeType: String): Result<String>

    /**
     * Whether files from this host are *readable* on AniList, not just storable.
     *
     * The two are different questions, and only one host here fails the second:
     * `pomf2.lain.la` answers an HTTP/2 stream reset to any request carrying
     * `Referer: https://anilist.co/` (measured November 2026), so a photo uploaded
     * through it renders in this app - which sends no referer - and as a broken
     * image on the site where the comment is read. A host that cannot be seen where
     * the file is used is a last resort, and the composer says so rather than
     * letting the reader discover it in somebody else's thread.
     */
    val showsOnAniList: Boolean get() = true
}

/**
 * An upload that happened.
 *
 * [showsOnAniList] rides along with the URL because the reader is about to paste
 * that URL into a comment on that site, and "it uploaded" is not the same answer as
 * "it will be visible" - see [ImageHost.showsOnAniList].
 */
data class UploadOutcome(val url: String, val showsOnAniList: Boolean)

/**
 * One image, three hosts, first one that takes it.
 *
 * ## Why a chain rather than a host setting
 *
 * The reader is attaching a photo to a comment, not choosing infrastructure. But
 * the hosts fail for different reasons and the order matters - and the order is
 * decided by *where the file has to be visible*, not by which host is nicest to
 * upload to.
 *
 * ## The order, and the measurement behind it
 *
 * catbox is first because it is the documented host and keeps files until two years
 * of no views. It is currently paused for anonymous uploads (HTTP 412, "Uploads
 * paused until I can resolve storage issues"), which is what the chain is for.
 *
 * imgbb is second and **pomf is last, and that is not a preference**: a file stored
 * on `pomf2.lain.la` cannot be *read* from AniList. Measured November 2026: a GET of
 * a stored pomf file with `Referer: https://anilist.co/` is answered with an HTTP/2
 * stream reset by nginx (`curl` exit 92) - no referer, or a `github.com` referer,
 * returns 200. Every browser on the site therefore shows a broken image where the
 * reader attached a photo, while this app shows it fine, because Coil sends no
 * referer at all. That is the "my uploaded image doesn't work on Firefox" report:
 * the upload succeeded, the embed is invisible only on the site. `i.ibb.co` answers
 * 200 to the same referer, so it is the fallback that can actually be read where the
 * comment lives.
 *
 * pomf stays in the chain as the last resort - a file the reader can see in this app
 * is worth more than nothing - and its refusal of AniList's own referer is the
 * reason it is last rather than second.
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
    private val hosts: List<ImageHost> = defaultHosts(),
    private val cooldownMillis: Long = DEFAULT_COOLDOWN_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis
) {

    /** Per host index, the time before which it is not asked first. */
    private val benchedUntil = LongArray(hosts.size)

    /**
     * Uploads [bytes] and returns the URL to embed, with the host's visibility.
     *
     * The bytes are the same for every attempt, so nothing is re-read from the
     * provider and a fallback costs one request, not a second picker.
     */
    suspend fun upload(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): Result<UploadOutcome> {
        val now = clock()
        val order = hosts.indices.filter { synchronized(benchedUntil) { benchedUntil[it] <= now } }
            .ifEmpty { hosts.indices.toList() }
        var last: Result<UploadOutcome> = Result.failure(UploadFailure("No image host is configured."))
        for (index in order) {
            val host = hosts[index]
            last = host.upload(bytes, fileName, mimeType)
                .map { UploadOutcome(it, host.showsOnAniList) }
            synchronized(benchedUntil) {
                benchedUntil[index] = if (last.isSuccess) 0L else clock() + cooldownMillis
            }
            if (last.isSuccess) return last
        }
        return last
    }

    companion object {
        /**
         * The production chain, in the order it is tried.
         *
         * Top-level so a test can pin it: the order is a measured fact about the
         * hosts, not a preference, and a "tidy" reorder that puts pomf back ahead of
         * imgbb compiles, lints and ships while quietly sending every reader's photo
         * to a host AniList's own pages cannot read from.
         */
        internal fun defaultHosts(): List<ImageHost> = listOf(CatboxApi(), ImgbbApi(), PomfApi())

        /**
         * The app's one chain, so a host benched by one sheet stays benched for the
         * next: a chain per composer forgot catbox's refusal every time a sheet
         * opened, and paid for the same paused host again.
         */
        val shared: ImageUploader by lazy { ImageUploader(defaultHosts()) }

        /** Long enough to cover one composing session, short enough to notice a host coming back. */
        const val DEFAULT_COOLDOWN_MILLIS: Long = 10 * 60 * 1000L
    }
}
